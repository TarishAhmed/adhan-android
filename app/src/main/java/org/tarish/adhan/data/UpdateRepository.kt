package org.tarish.adhan.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.tarish.adhan.BuildConfig
import org.tarish.adhan.core.di.IoDispatcher
import org.tarish.adhan.domain.VersionCompare
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A published release newer than the installed app. [apkUrl] is empty when the release has no APK,
 * in which case the UI falls back to opening [pageUrl]. [sha256] is lowercase hex, or null if GitHub
 * didn't report a digest.
 */
data class UpdateInfo(
    val type: Type,
    val version: String,
    val pageUrl: String = "",
    val apkUrl: String = "",
    val sha256: String? = null,
    val sizeBytes: Long = 0,
    val notes: String = "",
) {
    enum class Type { NONE, GENERAL, FORCE }
    companion object { val NONE = UpdateInfo(Type.NONE, "") }
}

interface UpdateRepository {
    suspend fun check(): UpdateInfo
}

@Serializable
private data class ReleaseDto(
    @SerialName("tag_name") val tag: String,
    @SerialName("html_url") val htmlUrl: String = "",
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<AssetDto> = emptyList(),
)

@Serializable
private data class AssetDto(
    val name: String,
    @SerialName("browser_download_url") val url: String,
    val size: Long = 0,
    /** "sha256:<hex>", reported by GitHub for uploaded assets. */
    val digest: String? = null,
)

/**
 * Reads the latest GitHub release of [BuildConfig.UPDATE_REPO] (see `.github/workflows/release.yml`).
 * A release is a forced update when its notes contain `<!-- min-supported: X.Y.Z -->` and the
 * installed version is older than X.Y.Z.
 */
@Singleton
class GitHubUpdateRepository @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    @IoDispatcher private val io: CoroutineDispatcher,
) : UpdateRepository {

    override suspend fun check(): UpdateInfo = withContext(io) {
        try {
            val req = Request.Builder()
                .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(req).execute().use { res ->
                val body = res.body?.string()
                if (!res.isSuccessful || body == null) return@withContext UpdateInfo.NONE
                decide(BuildConfig.VERSION_NAME, json.decodeFromString<ReleaseDto>(body))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w("Update check failed: %s", e.javaClass.simpleName)
            UpdateInfo.NONE
        }
    }

    companion object {
        private val MIN_SUPPORTED = Regex("""min-supported:\s*v?([0-9]+(?:\.[0-9]+)*)""")
        private val HTML_COMMENT = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)
        private const val MAX_NOTES = 800

        fun minSupported(body: String?): String? = body?.let { MIN_SUPPORTED.find(it)?.groupValues?.get(1) }

        private fun decide(current: String, r: ReleaseDto): UpdateInfo {
            if (r.draft || r.prerelease) return UpdateInfo.NONE
            val apk = r.assets.firstOrNull { it.name.endsWith(".apk") }
            return decide(
                current = current,
                latest = r.tag.removePrefix("v"),
                minSupported = minSupported(r.body),
                pageUrl = r.htmlUrl,
                apkUrl = apk?.url.orEmpty(),
                sha256 = apk?.digest?.removePrefix("sha256:")?.lowercase(),
                sizeBytes = apk?.size ?: 0,
                notes = r.body.orEmpty().replace(HTML_COMMENT, "").trim().take(MAX_NOTES),
            )
        }

        fun decide(
            current: String,
            latest: String,
            minSupported: String?,
            pageUrl: String = "",
            apkUrl: String = "",
            sha256: String? = null,
            sizeBytes: Long = 0,
            notes: String = "",
        ): UpdateInfo {
            if (!VersionCompare.isHigher(latest, current)) return UpdateInfo.NONE
            val forced = minSupported != null && VersionCompare.isHigher(minSupported, current)
            val type = if (forced) UpdateInfo.Type.FORCE else UpdateInfo.Type.GENERAL
            return UpdateInfo(type, latest, pageUrl, apkUrl, sha256, sizeBytes, notes)
        }
    }
}
