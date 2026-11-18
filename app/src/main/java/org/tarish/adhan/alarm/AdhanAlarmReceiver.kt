package org.tarish.adhan.alarm

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import dagger.hilt.android.AndroidEntryPoint
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import org.tarish.adhan.MainActivity
import org.tarish.adhan.R
import org.tarish.adhan.core.di.ApplicationScope
import org.tarish.adhan.core.format.formatClock
import org.tarish.adhan.core.time.Clock
import org.tarish.adhan.data.local.SettingsStore
import org.tarish.adhan.domain.AlarmKind
import org.tarish.adhan.widget.WidgetUpdater

/** §10.5 — fires at prayer / reminder / test time, and on WIDGET_TICK. */
@AndroidEntryPoint
class AdhanAlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var coordinator: AlarmCoordinator
    @Inject lateinit var widgetUpdater: WidgetUpdater
    @Inject lateinit var settings: SettingsStore
    @Inject lateinit var clock: Clock
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AlarmScheduler.ACTION_WIDGET_TICK -> goAsync(scope, "widgetTick") {
                widgetUpdater.updateAll()
                coordinator.rescheduleNow("widget_tick")
            }
            AlarmScheduler.ACTION_ALARM -> {
                val spec = AlarmScheduler.spec(intent) ?: return
                goAsync(scope, "alarm") {
                    handle(context, spec)
                    coordinator.rescheduleNow("fired")
                    widgetUpdater.updateAll()
                }
            }
        }
    }

    private suspend fun handle(context: Context, spec: org.tarish.adhan.domain.AlarmSpec) {
        val zone = settings.location.first()?.zone ?: clock.deviceZone()
        val timeText = formatClock(spec.prayerTime, zone, DateFormat.is24HourFormat(context))
        val name = context.getString(NotificationChannels.prayerNameRes(spec.prayer))
        val late = Duration.between(spec.fireAt, clock.now()) > Duration.ofMinutes(15)

        when (spec.kind) {
            AlarmKind.ADHAN, AlarmKind.TEST -> {
                if (late && spec.kind == AlarmKind.ADHAN) {
                    notify(context, spec.requestCode, NotificationChannels.prayerChannelId(spec.prayer),
                        name, context.getString(R.string.notif_missed_body, name, timeText), silent = true, timeoutMs = 3_600_000)
                    return
                }
                val soundId = settings.prayerPrefs.first()[spec.prayer]?.soundId
                    ?: org.tarish.adhan.domain.model.AdhanSound.DEFAULT_ID
                ContextCompat.startForegroundService(
                    context,
                    AdhanPlaybackService.intent(context, spec.prayer, spec.kind, spec.prayerTime, soundId, spec.requestCode),
                )
            }
            AlarmKind.REMINDER -> {
                if (late) return
                val mins = Duration.between(spec.fireAt, spec.prayerTime).toMinutes().toInt()
                notify(context, spec.requestCode, NotificationChannels.REMINDER,
                    context.getString(R.string.notif_reminder_title, name, mins),
                    context.getString(R.string.notif_reminder_body, name, timeText),
                    silent = false, timeoutMs = mins * 60_000L)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun notify(context: Context, id: Int, channel: String, title: String, body: String, silent: Boolean, timeoutMs: Long) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_adhan)
            .setColor(ContextCompat.getColor(context, R.color.brand_primary))
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(homeIntent(context))
            .setAutoCancel(true)
            .setSilent(silent)
            .setTimeoutAfter(timeoutMs)
            .build()
        nm.notify(id, n)
    }

    companion object {
        fun homeIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 3,
            Intent(Intent.ACTION_VIEW, "adhan://home".toUri(), context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
