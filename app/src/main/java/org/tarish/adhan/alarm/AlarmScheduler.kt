package org.tarish.adhan.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.tarish.adhan.MainActivity
import org.tarish.adhan.core.time.Clock
import org.tarish.adhan.data.PrayerTimesRepository
import org.tarish.adhan.data.local.SettingsStore
import org.tarish.adhan.domain.AlarmKind
import org.tarish.adhan.domain.AlarmPlanner
import org.tarish.adhan.domain.AlarmSpec
import org.tarish.adhan.domain.model.PrayerName
import timber.log.Timber

/** §10.4 — AlarmManager wrapper. Never plain setExact (BUG-05). */
@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
    private val prayerRepo: PrayerTimesRepository,
    private val clock: Clock,
) {
    private val am: AlarmManager = context.getSystemService(AlarmManager::class.java)
    private val mutex = Mutex()

    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    fun notificationsAllowed(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Builds the plan from cached data, fetching tomorrow (10 s max) if it is missing. */
    suspend fun currentPlan(allowNetwork: Boolean = true): List<AlarmSpec> {
        val loc = settings.location.first() ?: return emptyList()
        val zone = loc.zone ?: clock.deviceZone()
        val today = clock.now().atZone(zone).toLocalDate()
        var days = prayerRepo.daysOnce(loc.key, today, today.plusDays(1))
        if (allowNetwork && days.none { it.date == today.plusDays(1) }) {
            withTimeoutOrNull(10_000) { prayerRepo.ensureCoverage(loc) }
            days = prayerRepo.daysOnce(loc.key, today, today.plusDays(1))
        }
        val prefs = settings.prayerPrefs.first()
        return AlarmPlanner.plan(clock.now(), days, prefs, notificationsAllowed())
    }

    suspend fun rescheduleAll(reason: String) = mutex.withLock {
        try {
            val loc = settings.location.first()
            val plan = if (loc == null) emptyList() else currentPlan()
            val newKeys = plan.map { it.requestCode }.toSet()
            settings.scheduledAlarmKeys().filter { it !in newKeys }.forEach { cancel(it) }
            plan.forEach { schedule(it) }
            settings.setScheduledAlarmKeys(newKeys)
            scheduleWidgetTick()
            val next = plan.firstOrNull { it.kind == AlarmKind.ADHAN }
            Timber.d("rescheduleAll(%s): %d alarms, next=%s %s", reason, plan.size, next?.prayer?.displayName, next?.fireAt)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "rescheduleAll(%s) failed", reason)
        }
    }

    fun schedule(spec: AlarmSpec) {
        try {
            val pi = alarmPendingIntent(spec.requestCode, spec, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
            val at = spec.fireAt.toEpochMilli()
            if (canScheduleExact()) {
                if (spec.kind == AlarmKind.REMINDER) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                } else {
                    am.setAlarmClock(AlarmManager.AlarmClockInfo(at, showIntent()), pi)
                }
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: Exception) {
            Timber.e(e, "schedule %s failed", spec.requestCode)
        }
    }

    fun scheduleTest(prayer: PrayerName, delaySeconds: Long = 5) {
        val now = clock.now()
        val date = LocalDate.now()
        val code = AlarmPlanner.requestCode(date, AlarmKind.TEST, prayer)
        schedule(AlarmSpec(AlarmKind.TEST, prayer, now, now.plusSeconds(delaySeconds), date, code))
    }

    private fun cancel(requestCode: Int) {
        val intent = Intent(context, AdhanAlarmReceiver::class.java).setAction(ACTION_ALARM)
        PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            ?.let { am.cancel(it); it.cancel() }
    }

    /** Non-wakeup refresh at the next boundary so the widget stays correct even with alarms off. */
    private suspend fun scheduleWidgetTick() {
        val loc = settings.location.first() ?: return
        val zone = loc.zone ?: clock.deviceZone()
        val today = clock.now().atZone(zone).toLocalDate()
        val days = prayerRepo.daysOnce(loc.key, today, today.plusDays(1))
        val at = AlarmPlanner.nextBoundary(clock.now(), days)
            ?: today.plusDays(1).atStartOfDay(zone).toInstant()
        val pi = PendingIntent.getBroadcast(
            context, WIDGET_TICK_CODE,
            Intent(context, AdhanAlarmReceiver::class.java).setAction(ACTION_WIDGET_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.set(AlarmManager.RTC, at.toEpochMilli() + 1_000, pi)
    }

    private fun alarmPendingIntent(code: Int, spec: AlarmSpec, flags: Int): PendingIntent? {
        val intent = Intent(context, AdhanAlarmReceiver::class.java).setAction(ACTION_ALARM)
            .putExtra(EXTRA_KIND, spec.kind.name)
            .putExtra(EXTRA_PRAYER, spec.prayer.name)
            .putExtra(EXTRA_PRAYER_TIME, spec.prayerTime.toEpochMilli())
            .putExtra(EXTRA_FIRE_AT, spec.fireAt.toEpochMilli())
            .putExtra(EXTRA_DATE, spec.date.toString())
            .putExtra(EXTRA_REQUEST_CODE, code)
        return PendingIntent.getBroadcast(context, code, intent, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun showIntent(): PendingIntent = PendingIntent.getActivity(
        context, SHOW_CODE,
        Intent(Intent.ACTION_VIEW, "adhan://home".toUri(), context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_ALARM = "org.tarish.adhan.ALARM"
        const val ACTION_WIDGET_TICK = "org.tarish.adhan.WIDGET_TICK"
        const val EXTRA_KIND = "kind"
        const val EXTRA_PRAYER = "prayer"
        const val EXTRA_PRAYER_TIME = "prayerTimeMillis"
        const val EXTRA_FIRE_AT = "fireAtMillis"
        const val EXTRA_DATE = "date"
        const val EXTRA_REQUEST_CODE = "requestCode"
        private const val WIDGET_TICK_CODE = 1
        private const val SHOW_CODE = 2

        fun spec(intent: Intent): AlarmSpec? = runCatching {
            AlarmSpec(
                kind = AlarmKind.valueOf(intent.getStringExtra(EXTRA_KIND)!!),
                prayer = PrayerName.valueOf(intent.getStringExtra(EXTRA_PRAYER)!!),
                prayerTime = Instant.ofEpochMilli(intent.getLongExtra(EXTRA_PRAYER_TIME, 0)),
                fireAt = Instant.ofEpochMilli(intent.getLongExtra(EXTRA_FIRE_AT, 0)),
                date = LocalDate.parse(intent.getStringExtra(EXTRA_DATE)!!),
                requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, 0),
            )
        }.getOrNull()
    }
}
