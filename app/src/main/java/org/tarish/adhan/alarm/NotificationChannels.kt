package org.tarish.adhan.alarm

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.tarish.adhan.R
import org.tarish.adhan.domain.model.PrayerName

/** §10.2 — prayer channels are silent; the app plays the adhan itself (D-03). */
object NotificationChannels {
    const val GROUP_PRAYERS = "adhan_prayers"
    const val REMINDER = "prayer_reminder"
    const val PLAYBACK = "adhan_playback"
    const val UPDATES = "app_updates"
    private val VIBRATION = longArrayOf(0, 500, 300, 500)

    fun prayerChannelId(p: PrayerName) = "adhan_${p.key}"

    fun createAll(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannelGroup(NotificationChannelGroup(GROUP_PRAYERS, context.getString(R.string.channel_group_prayers)))
        val prayerChannels = PrayerName.SALAH.map { p ->
            val name = context.getString(prayerNameRes(p))
            NotificationChannel(prayerChannelId(p), context.getString(R.string.channel_adhan_name, name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_adhan_desc, name)
                group = GROUP_PRAYERS
                setSound(null, null)
                enableVibration(true)
                vibrationPattern = VIBRATION
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setBypassDnd(false)
            }
        }
        val reminder = NotificationChannel(REMINDER, context.getString(R.string.channel_reminder_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build(),
            )
        }
        val playback = NotificationChannel(PLAYBACK, context.getString(R.string.channel_playback_name), NotificationManager.IMPORTANCE_LOW).apply {
            setSound(null, null)
            enableVibration(false)
        }
        val updates = NotificationChannel(UPDATES, context.getString(R.string.channel_updates_name), NotificationManager.IMPORTANCE_DEFAULT)
        nm.createNotificationChannels(prayerChannels + listOf(reminder, playback, updates))
    }

    /** Channels the user has blocked in system settings. */
    fun blockedPrayers(context: Context): List<PrayerName> {
        val nm = NotificationManagerCompat.from(context)
        return PrayerName.SALAH.filter { nm.getNotificationChannel(prayerChannelId(it))?.importance == NotificationManager.IMPORTANCE_NONE }
    }

    fun prayerNameRes(p: PrayerName): Int = when (p) {
        PrayerName.FAJR -> R.string.prayer_fajr
        PrayerName.SUNRISE -> R.string.prayer_sunrise
        PrayerName.ZUHR -> R.string.prayer_zuhr
        PrayerName.ASR -> R.string.prayer_asr
        PrayerName.SUNSET -> R.string.prayer_sunset
        PrayerName.MAGHRIB -> R.string.prayer_maghrib
        PrayerName.ISHA -> R.string.prayer_isha
    }
}
