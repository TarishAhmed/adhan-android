package org.tarish.adhan.domain

import org.tarish.adhan.domain.model.PrayerDay
import org.tarish.adhan.domain.model.PrayerName
import org.tarish.adhan.domain.model.PrayerNotificationPrefs
import java.time.Instant
import java.time.LocalDate

enum class AlarmKind { ADHAN, REMINDER, TEST }

data class AlarmSpec(
    val kind: AlarmKind,
    val prayer: PrayerName,
    val prayerTime: Instant,
    val fireAt: Instant,
    val date: LocalDate,
    val requestCode: Int,
)

/** §10.3 — pure: prefs + days → alarms over a rolling today+tomorrow horizon. */
object AlarmPlanner {
    const val MAX_ALARMS = 20

    fun requestCode(date: LocalDate, kind: AlarmKind, prayer: PrayerName): Int =
        (date.toEpochDay() % 100_000).toInt() * 100 + kind.ordinal * 10 + prayer.ordinal

    fun plan(
        now: Instant,
        days: List<PrayerDay>,
        prefs: Map<PrayerName, PrayerNotificationPrefs>,
        notificationsAllowed: Boolean,
    ): List<AlarmSpec> {
        if (!notificationsAllowed) return emptyList()
        val threshold = now.plusSeconds(1)
        val out = mutableListOf<AlarmSpec>()
        for (day in days.sortedBy { it.date }) {
            for (p in PrayerName.SALAH) {
                val pref = prefs[p] ?: PrayerNotificationPrefs(p)
                if (!pref.enabled) continue
                if (day.date.dayOfWeek !in pref.days) continue
                val t = day[p] ?: continue
                if (t > threshold) {
                    out += AlarmSpec(AlarmKind.ADHAN, p, t, t, day.date, requestCode(day.date, AlarmKind.ADHAN, p))
                }
                if (pref.advanceMinutes > 0) {
                    val r = t.minusSeconds(pref.advanceMinutes * 60L)
                    if (r > threshold) {
                        out += AlarmSpec(AlarmKind.REMINDER, p, t, r, day.date, requestCode(day.date, AlarmKind.REMINDER, p))
                    }
                }
            }
        }
        return out.sortedWith(compareBy({ it.fireAt }, { it.kind.ordinal })).take(MAX_ALARMS)
    }

    /** Next boundary for the widget tick (§10.4): next salah, sunrise or local midnight. */
    fun nextBoundary(now: Instant, days: List<PrayerDay>): Instant? {
        val candidates = days.flatMap { d ->
            val midnight = d.date.plusDays(1).atStartOfDay(d.zone).toInstant()
            (PrayerName.SALAH + PrayerName.SUNRISE).mapNotNull { d[it] } + midnight
        }
        return candidates.filter { it > now }.minOrNull()
    }
}
