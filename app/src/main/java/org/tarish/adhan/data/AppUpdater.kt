package org.tarish.adhan.data

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.tarish.adhan.core.di.ApplicationScope
import org.tarish.adhan.core.di.IoDispatcher
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

sealed interface UpdateDownload {
    data object Idle : UpdateDownload
    /** [progress] is 0..1, or null when the size is unknown. */
    data class Downloading(val progress: Float?) : UpdateDownload
    data object Ready : UpdateDownload
    data object Failed : UpdateDownload
}

data class UpdaterState(
    val info: UpdateInfo = UpdateInfo.NONE,
    val checking: Boolean = false,
    /** True once a check has finished in this process, so "up to date" can be shown. */
    val checked: Boolean = false,
    val download: UpdateDownload = UpdateDownload.Idle,
)

/**
 * Check → download → verify → install for sideloaded builds. The APK is checked against the SHA-256
 * GitHub reports for the asset; Android itself rejects an APK signed with a different key.
 */
@Singleton
class AppUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: UpdateRepository,
    private val client: OkHttpClient,
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val _state = MutableStateFlow(UpdaterState())
    val state: StateFlow<UpdaterState> = _state.asStateFlow()
    private var download: Job? = null

    private val apkFile get() = File(File(context.cacheDir, DIR).apply { mkdirs() }, "adhan-${_state.value.info.version}.apk")

    suspend fun check(): UpdateInfo {
        _state.update { it.copy(checking = true) }
        val info = repo.check()
        _state.update { s ->
            // Keep a finished download if the same version is still the latest.
            val keep = s.info.version == info.version && s.download != UpdateDownload.Failed
            s.copy(info = info, checking = false, checked = true, download = if (keep) s.download else UpdateDownload.Idle)
        }
        return info
    }

    /** Downloads (if needed) then opens the system installer. */
    fun update() {
        val info = _state.value.info
        if (info.type == UpdateInfo.Type.NONE || download?.isActive == true) return
        if (_state.value.download == UpdateDownload.Ready && apkFile.exists()) { install(); return }
        download = scope.launch {
            _state.update { it.copy(download = UpdateDownload.Downloading(null)) }
            val ok = runCatching { fetch(info, apkFile) }.onFailure { Timber.w("Update download failed: %s", it.javaClass.simpleName) }
                .getOrDefault(false)
            _state.update { it.copy(download = if (ok) UpdateDownload.Ready else UpdateDownload.Failed) }
            if (ok) install()
        }
    }

    private suspend fun fetch(info: UpdateInfo, out: File): Boolean = withContext(io) {
        File(context.cacheDir, DIR).listFiles()?.filter { it != out }?.forEach { it.delete() }
        val req = Request.Builder().url(info.apkUrl).build()
        val digest = MessageDigest.getInstance("SHA-256")
        client.newCall(req).execute().use { res ->
            val body = res.body ?: return@withContext false
            if (!res.isSuccessful) return@withContext false
            val total = body.contentLength().takeIf { it > 0 } ?: info.sizeBytes.takeIf { it > 0 }
            var read = 0L
            var lastReported = -1
            out.outputStream().use { sink ->
                body.byteStream().use { src ->
                    val buf = ByteArray(64 * 1024)
                    while (isActive) {
                        val n = src.read(buf)
                        if (n < 0) break
                        sink.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        read += n
                        val pct = total?.let { (read * 100 / it).toInt() }
                        if (pct != null && pct != lastReported) {
                            lastReported = pct
                            _state.update { it.copy(download = UpdateDownload.Downloading(pct / 100f)) }
                        }
                    }
                }
            }
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        val expected = info.sha256
        (expected == null || hex == expected).also { if (!it) { Timber.w("Update checksum mismatch"); out.delete() } }
    }

    /** True when Android will let this app open the installer ("Install unknown apps" allowed). */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Opens the per-app "Install unknown apps" setting; [update] again after the user returns. */
    fun openInstallPermissionSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private fun install() {
        if (!canInstall()) { openInstallPermissionSettings(); return }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apkFile)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure { Timber.w("Installer launch failed: %s", it.javaClass.simpleName) }
    }

    companion object { private const val DIR = "updates" }
}
