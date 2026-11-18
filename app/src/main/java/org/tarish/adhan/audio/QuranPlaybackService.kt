package org.tarish.adhan.audio

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import org.tarish.adhan.BuildConfig
import org.tarish.adhan.MainActivity
import org.tarish.adhan.R
import java.io.File

/**
 * Plays Quran recitation and spoken translation in the background, with the standard media
 * notification and lock-screen controls. The UI drives it through [QuranPlayer]. Audio focus is
 * handled by ExoPlayer, so an adhan or a call pauses it.
 */
@OptIn(UnstableApi::class)
class QuranPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val speech = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build()
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(QuranAudioCache.dataSourceFactory(this)))
            .setAudioAttributes(speech, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        // Stopped from the app: the playlist is cleared, so let the service go too.
        player.addListener(object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) {
                if (p.playbackState == Player.STATE_IDLE && p.mediaItemCount == 0) stopSelf()
            }
        })
        session = MediaSession.Builder(this, player).setSessionActivity(open).build()
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_stat_adhan) })
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Swiping the app away stops a paused player; one that is playing carries on. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}

/** Verses already heard play again offline. One instance per process, as SimpleCache requires. */
@OptIn(UnstableApi::class)
internal object QuranAudioCache {
    private const val MAX_BYTES = 300L * 1024 * 1024
    @Volatile private var cache: SimpleCache? = null

    private fun cache(context: Context): SimpleCache = cache ?: synchronized(this) {
        cache ?: SimpleCache(
            File(context.cacheDir, "quran-audio"),
            LeastRecentlyUsedCacheEvictor(MAX_BYTES),
            StandaloneDatabaseProvider(context),
        ).also { cache = it }
    }

    fun dataSourceFactory(context: Context): DataSource.Factory = CacheDataSource.Factory()
        .setCache(cache(context))
        .setUpstreamDataSourceFactory(
            DefaultDataSource.Factory(context, DefaultHttpDataSource.Factory().setUserAgent("Adhan/${BuildConfig.VERSION_NAME}")),
        )
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
}
