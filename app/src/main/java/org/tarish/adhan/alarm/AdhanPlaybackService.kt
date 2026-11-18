package org.tarish.adhan.alarm

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import javax.inject.Inject
import org.tarish.adhan.R
import org.tarish.adhan.data.SoundRepository
import org.tarish.adhan.data.SoundSource
import org.tarish.adhan.data.rawResourceUri
import org.tarish.adhan.domain.AlarmKind
import org.tarish.adhan.domain.model.PrayerName
import timber.log.Timber

/** §10.6 — plays the adhan at alarm volume in a mediaPlayback foreground service, with a Stop action. */
@AndroidEntryPoint
class AdhanPlaybackService : Service() {
    @Inject lateinit var sounds: SoundRepository

    private var player: ExoPlayer? = null
    private var current: Playing? = null
    private var focusRequest: AudioFocusRequest? = null
    private val handler = Handler(Looper.getMainLooper())
    private val hardStop = Runnable { finishPlayback() }

    private data class Playing(val id: Int, val prayer: PrayerName, val kind: AlarmKind, val prayerTime: Instant)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            finishPlayback()
            return START_NOT_STICKY
        }
        val prayer = intent?.getStringExtra(EXTRA_PRAYER)?.let { runCatching { PrayerName.valueOf(it) }.getOrNull() }
        if (prayer == null) {
            stopSelf(); return START_NOT_STICKY
        }
        val kind = runCatching { AlarmKind.valueOf(intent.getStringExtra(EXTRA_KIND)!!) }.getOrDefault(AlarmKind.ADHAN)
        val time = Instant.ofEpochMilli(intent.getLongExtra(EXTRA_PRAYER_TIME, System.currentTimeMillis()))
        val id = intent.getIntExtra(EXTRA_ID, NOTIFICATION_ID_FALLBACK)
        val soundId = intent.getStringExtra(EXTRA_SOUND) ?: org.tarish.adhan.domain.model.AdhanSound.DEFAULT_ID

        // A second adhan replaces the first (e.g. test while real).
        current?.let { prev -> releasePlayer(); postFinal(prev) }
        val playing = Playing(id, prayer, kind, time)
        current = playing

        try {
            ServiceCompat.startForeground(
                this, id, buildNotification(playing, withStop = true),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
            )
        } catch (e: Exception) {
            Timber.e(e, "startForeground failed")
            stopSelf(); return START_NOT_STICKY
        }

        try {
            play(sounds.resolve(soundId))
        } catch (e: Exception) {
            Timber.e(e, "Playback failed")
            finishPlayback()
        }
        return START_NOT_STICKY
    }

    private fun play(source: SoundSource) {
        val uri: Uri = when (source) {
            is SoundSource.FileSource -> Uri.fromFile(source.file)
            is SoundSource.RawRes -> rawResourceUri(source.resId)
            SoundSource.Missing -> rawResourceUri(R.raw.adhan_makkah)
            SoundSource.SystemDefault -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: rawResourceUri(R.raw.adhan_makkah)
            SoundSource.Silent -> {
                // Channel vibration only; detach immediately.
                finishPlayback(); return
            }
        }
        requestFocus()
        val p = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_ALARM).setContentType(C.AUDIO_CONTENT_TYPE_SONIFICATION).build(),
                false,
            )
            setWakeMode(C.WAKE_MODE_LOCAL)
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) finishPlayback()
                }
                override fun onPlayerError(error: PlaybackException) {
                    Timber.w("Player error %s", error.errorCodeName)
                    finishPlayback()
                }
            })
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            play()
        }
        player = p
        handler.removeCallbacks(hardStop)
        handler.postDelayed(hardStop, HARD_STOP_MS)
    }

    private fun requestFocus() {
        val am = getSystemService(AudioManager::class.java) ?: return
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            )
            .setOnAudioFocusChangeListener({ change ->
                // Phone call etc. → stop, don't duck.
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) finishPlayback()
            }, handler)
            .build()
        focusRequest = req
        am.requestAudioFocus(req)
    }

    private fun releasePlayer() {
        handler.removeCallbacks(hardStop)
        player?.release()
        player = null
        val am = getSystemService(AudioManager::class.java)
        focusRequest?.let { am?.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    /** Stop audio, keep the notification (without Stop) and stop the service. */
    private fun finishPlayback() {
        releasePlayer()
        val c = current
        current = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        if (c != null) postFinal(c)
        stopSelf()
    }

    @SuppressLint("MissingPermission")
    private fun postFinal(p: Playing) {
        try {
            val nm = NotificationManagerCompat.from(this)
            if (nm.areNotificationsEnabled()) nm.notify(p.id, buildNotification(p, withStop = false))
        } catch (e: Exception) {
            Timber.w(e, "postFinal failed")
        }
    }

    private fun buildNotification(p: Playing, withStop: Boolean): Notification {
        val name = getString(NotificationChannels.prayerNameRes(p.prayer))
        val (title, body) = if (p.kind == AlarmKind.TEST) {
            getString(R.string.notif_test_title, name) to getString(R.string.notif_test_body, name)
        } else {
            getString(R.string.notif_adhan_title, name) to getString(R.string.notif_adhan_body, name)
        }
        val stopPi = PendingIntent.getService(
            this, 0, Intent(this, AdhanPlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, NotificationChannels.prayerChannelId(p.prayer))
            .setSmallIcon(R.drawable.ic_stat_adhan)
            .setColor(ContextCompat.getColor(this, R.color.brand_primary))
            .setContentTitle(title)
            .setContentText(body)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setWhen(p.prayerTime.toEpochMilli())
            .setContentIntent(AdhanAlarmReceiver.homeIntent(this))
            .setAutoCancel(true)
            .setTimeoutAfter(3_600_000)
            .apply {
                if (withStop) {
                    addAction(R.drawable.ic_stop, getString(R.string.notif_action_stop), stopPi)
                    setDeleteIntent(stopPi)
                    setOngoing(false)
                } else {
                    setSilent(true)
                }
            }
            .build()
    }

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "org.tarish.adhan.STOP_ADHAN"
        private const val EXTRA_PRAYER = "prayer"
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_PRAYER_TIME = "prayerTime"
        private const val EXTRA_SOUND = "sound"
        private const val EXTRA_ID = "id"
        private const val NOTIFICATION_ID_FALLBACK = 42
        private const val HARD_STOP_MS = 10 * 60_000L

        fun intent(context: Context, prayer: PrayerName, kind: AlarmKind, prayerTime: Instant, soundId: String, id: Int) =
            Intent(context, AdhanPlaybackService::class.java)
                .putExtra(EXTRA_PRAYER, prayer.name)
                .putExtra(EXTRA_KIND, kind.name)
                .putExtra(EXTRA_PRAYER_TIME, prayerTime.toEpochMilli())
                .putExtra(EXTRA_SOUND, soundId)
                .putExtra(EXTRA_ID, id)
    }
}
