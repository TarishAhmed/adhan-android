package org.tarish.adhan.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.tarish.adhan.R
import org.tarish.adhan.core.di.IoDispatcher
import org.tarish.adhan.core.result.AppError
import org.tarish.adhan.core.result.toAppError
import org.tarish.adhan.domain.model.AdhanSound

sealed interface SoundSource {
    data class FileSource(val file: File) : SoundSource
    data class RawRes(val resId: Int) : SoundSource
    data object SystemDefault : SoundSource
    data object Silent : SoundSource
    data object Missing : SoundSource
}

/** `android.resource://` Uri for a raw resource; ExoPlayer's default data source reads it. */
fun Context.rawResourceUri(resId: Int): Uri =
    Uri.Builder().scheme(ContentResolver.SCHEME_ANDROID_RESOURCE).authority(packageName).appendPath(resId.toString()).build()

sealed interface DownloadState {
    /** [fraction] is null when Content-Length is unknown. */
    data class Progress(val fraction: Float?) : DownloadState
    data object Done : DownloadState
    data class Failed(val error: AppError) : DownloadState
}

interface SoundRepository {
    val catalog: List<AdhanSound>
    fun isAvailable(id: String): Boolean
    fun download(id: String): Flow<DownloadState>
    fun resolve(id: String): SoundSource
    suspend fun downloadedStats(): Pair<Int, Long>
    suspend fun deleteDownloaded(keep: Set<String>)
    suspend fun deleteOrphanParts()
}

@Singleton
class SoundRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    @IoDispatcher private val io: CoroutineDispatcher,
) : SoundRepository {

    override val catalog: List<AdhanSound> = AdhanSound.CATALOG

    private val dir: File get() = File(context.filesDir, "adhan").apply { mkdirs() }
    private fun fileFor(id: String) = File(dir, "$id.mp3")

    override fun isAvailable(id: String): Boolean {
        val s = AdhanSound.byId(id) ?: return false
        return when (s.kind) {
            AdhanSound.Kind.SYSTEM_DEFAULT, AdhanSound.Kind.SILENT -> true
            AdhanSound.Kind.ADHAN -> s.bundled || fileFor(id).let { it.exists() && it.length() > MIN_BYTES }
        }
    }

    override fun resolve(id: String): SoundSource {
        val s = AdhanSound.byId(id) ?: return SoundSource.Missing
        return when (s.kind) {
            AdhanSound.Kind.SYSTEM_DEFAULT -> SoundSource.SystemDefault
            AdhanSound.Kind.SILENT -> SoundSource.Silent
            AdhanSound.Kind.ADHAN -> {
                val f = fileFor(id)
                when {
                    f.exists() && f.length() > MIN_BYTES -> SoundSource.FileSource(f)
                    s.id == AdhanSound.DEFAULT_ID -> SoundSource.RawRes(R.raw.adhan_makkah)
                    else -> SoundSource.Missing
                }
            }
        }
    }

    override fun download(id: String): Flow<DownloadState> = flow {
        val s = AdhanSound.byId(id)
        val url = s?.let { AdhanSound.urlFor(it) }
        if (url == null || isAvailable(id)) {
            emit(DownloadState.Done); return@flow
        }
        val part = File(dir, "$id.mp3.part")
        try {
            emit(DownloadState.Progress(null))
            client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (resp.code != 200) throw HttpCodeException(resp.code)
                val body = resp.body ?: throw IOException("empty body")
                val type = body.contentType()
                val okType = type == null || type.type == "audio" ||
                    (type.type == "application" && type.subtype == "octet-stream")
                if (!okType) throw IOException("bad content type $type")
                val total = body.contentLength().takeIf { it > 0 }
                var read = 0L
                var lastEmit = 0f
                part.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(16 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            read += n
                            if (total != null) {
                                val f = read.toFloat() / total
                                if (f - lastEmit >= 0.05f) { lastEmit = f; emit(DownloadState.Progress(f)) }
                            }
                        }
                    }
                }
                if (read <= MIN_BYTES) throw IOException("too small")
            }
            if (!part.renameTo(fileFor(id))) throw IOException("rename failed")
            emit(DownloadState.Done)
        } catch (e: kotlinx.coroutines.CancellationException) {
            part.delete(); throw e
        } catch (e: HttpCodeException) {
            part.delete(); emit(DownloadState.Failed(AppError.Http(e.code)))
        } catch (e: Exception) {
            part.delete(); emit(DownloadState.Failed(e.toAppError()))
        }
    }.flowOn(io)

    override suspend fun downloadedStats(): Pair<Int, Long> = withContext(io) {
        val files = dir.listFiles { f -> f.name.endsWith(".mp3") }.orEmpty()
        files.size to files.sumOf { it.length() }
    }

    override suspend fun deleteDownloaded(keep: Set<String>) = withContext(io) {
        dir.listFiles().orEmpty().forEach { f ->
            val id = f.name.removeSuffix(".part").removeSuffix(".mp3")
            if (id !in keep) f.delete()
        }
    }

    override suspend fun deleteOrphanParts() = withContext(io) {
        dir.listFiles { f -> f.name.endsWith(".part") }.orEmpty().forEach { it.delete() }
    }

    private class HttpCodeException(val code: Int) : IOException("HTTP $code")

    companion object { const val MIN_BYTES = 10 * 1024L }
}
