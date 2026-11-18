package org.tarish.adhan.alarm

import android.annotation.SuppressLint
import android.os.SystemClock
import android.media.session.PlaybackState
import android.media.session.MediaSession
import android.media.VolumeProvider
import android.content.IntentFilter
import android.content.BroadcastReceiver
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

/**
 * §10.6 — plays the adhan at alarm volume in a mediaPlayback foreground service, with a Stop action.
 *
 * Never plays over a phone call: if a call is active or ringing (or audio focus is refused) the
 * notification is posted silently instead, and a call that starts mid-adhan stops it. While playing,
 * any physical button stops it: volume keys reach a remote-volume [MediaSession], and the power
 * button is seen as the screen turning off or on.
 */
@AndroidEntryPoint
class AdhanPlaybackService : Service() {
    @Inject lateinit var sounds: SoundRepository

    private var player: ExoPlayer? = null
    private var current: Playing? = null
    private var focusRequest: AudioFocusRequest? = null
    private val handler = Handler(Looper.getMainLooper())
    private val hardStop = Runnable { finishPlayback() }

    private var session: MediaSession? = null
    private var screenReceiver: BroadcastReceiver? = null
    private var modeListener: AudioManager.OnModeChangedListener? = null
    private var startedAt = 0L

    private data class Playing(
        val id: Int,
        val prayer: PrayerName,
        val kind: AlarmKind,
        val prayerTime: Instant,
        val silencedForCall: Boolean = false,
    )

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
        if (inCall() || !requestFocus()) {
            Timber.i("Adhan silenced: phone call in progress")
            current = current?.copy(silencedForCall = true)
            finishPlayback(); return
        }
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
        startedAt = SystemClock.elapsedRealtime()
        listenForButtons()
        handler.removeCallbacks(hardStop)
        handler.postDelayed(hardStop, HARD_STOP_MS)
    }

    private fun inCall(): Boolean = when (getSystemService(AudioManager::class.java)?.mode) {
        AudioManager.MODE_IN_CALL, AudioManager.MODE_IN_COMMUNICATION, AudioManager.MODE_RINGTONE,
        AudioManager.MODE_CALL_SCREENING -> true
        else -> false
    }

    /** Volume keys, the power button and a call starting all stop the adhan. */
    private fun listenForButtons() {
        // An active session with remote volume receives every volume-key press, even at the volume
        // limits and with the screen off; the adjustment itself is ignored.
        session = MediaSession(this, "adhan").apply {
            setPlaybackToRemote(object : VolumeProvider(VOLUME_CONTROL_RELATIVE, 100, 50) {
                override fun onAdjustVolume(direction: Int) = stopFromButton("volume")
                override fun onSetVolumeTo(volume: Int) = stopFromButton("volume")
            })
            setCallback(object : MediaSession.Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean { stopFromButton("media button"); return true }
                override fun onStop() = stopFromButton("media stop")
                override fun onPause() = stopFromButton("media pause")
            }, handler)
            setPlaybackState(
                PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_STOP or PlaybackState.ACTION_PAUSE)
                    .setState(PlaybackState.STATE_PLAYING, 0, 1f)
                    .build()
            )
            isActive = true
        }
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // Ignore the screen waking for the notification itself in the first moments.
                if (SystemClock.elapsedRealtime() - startedAt > SCREEN_GRACE_MS) stopFromButton("power")
            }
        }.also {
            ContextCompat.registerReceiver(
                this, it,
                IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON) },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
        if (Build.VERSION.SDK_INT >= 31) {
            modeListener = AudioManager.OnModeChangedListener { if (inCall()) stopFromButton("call") }.also {
                getSystemService(AudioManager::class.java)?.addOnModeChangedListener(ContextCompat.getMainExecutor(this), it)
            }
        }
    }

    private fun stopFromButton(source: String) {
        if (player == null) return
        Timber.i("Adhan stopped by %s", source)
        finishPlayback()
    }

    private fun stopListening() {
        session?.run { isActive = false; release() }
        session = null
        screenReceiver?.let { runCatching { unregisterReceiver(it) } }
        screenReceiver = null
        if (Build.VERSION.SDK_INT >= 31) {
            modeListener?.let { getSystemService(AudioManager::class.java)?.removeOnModeChangedListener(it) }
        }
        modeListener = null
    }

    /** True if focus was granted; a phone call holds focus, so this fails during calls. */
    private fun requestFocus(): Boolean {
        val am = getSystemService(AudioManager::class.java) ?: return true
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
        return am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun releasePlayer() {
        stopListening()
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
            getString(R.string.notif_adhan_title, name) to
                getString(if (p.silencedForCall) R.string.notif_adhan_silenced_call else R.string.notif_adhan_body, name)
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
                    setSubText(getString(R.string.notif_stop_hint))
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
        private const val SCREEN_GRACE_MS = 1_500L

        fun intent(context: Context, prayer: PrayerName, kind: AlarmKind, prayerTime: Instant, soundId: String, id: Int) =
            Intent(context, AdhanPlaybackService::class.java)
                .putExtra(EXTRA_PRAYER, prayer.name)
                .putExtra(EXTRA_KIND, kind.name)
                .putExtra(EXTRA_PRAYER_TIME, prayerTime.toEpochMilli())
                .putExtra(EXTRA_SOUND, soundId)
                .putExtra(EXTRA_ID, id)
    }
}
