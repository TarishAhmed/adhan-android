package org.tarish.adhan.domain

import org.tarish.adhan.domain.model.PrayerDay
import org.tarish.adhan.domain.model.PrayerName
import org.tarish.adhan.domain.model.PrayerName.ASR
import org.tarish.adhan.domain.model.PrayerName.FAJR
import org.tarish.adhan.domain.model.PrayerName.ISHA
import org.tarish.adhan.domain.model.PrayerName.MAGHRIB
import org.tarish.adhan.domain.model.PrayerName.SUNRISE
import org.tarish.adhan.domain.model.PrayerName.ZUHR
import org.tarish.adhan.domain.model.PrayerWindow
import org.tarish.adhan.domain.model.Segment
import org.tarish.adhan.domain.model.Urgency
import java.time.Duration
import java.time.Instant

/** §7.4 — current window, static segments and urgency (D-02, D-08, BUG-11/12/13). */
object PrayerWindowCalculator {

    private const val RED_TAIL_SECONDS = 600.0

    private class Candidate(
        val start: PrayerName, val startAt: Instant,
        val end: PrayerName, val endAt: Instant,
        /** Maghrib of the same day as the opening Isha; only for Isha windows. */
        val maghrib: Instant? = null,
    )

    fun compute(now: Instant, yesterday: PrayerDay?, today: PrayerDay?, tomorrow: PrayerDay?): PrayerWindow? {
        if (today == null) return null
        val c = candidates(yesterday, today, tomorrow).firstOrNull { it.startAt <= now && now < it.endAt }
            ?: return null
        return build(c, now)
    }

    private fun candidates(y: PrayerDay?, t: PrayerDay, tm: PrayerDay?): List<Candidate> = buildList {
        fun add(s: PrayerName, sa: Instant?, e: PrayerName, ea: Instant?, m: Instant? = null) {
            if (sa != null && ea != null && ea > sa) add(Candidate(s, sa, e, ea, m))
        }
        add(ISHA, y?.get(ISHA), FAJR, t[FAJR], y?.get(MAGHRIB))
        add(FAJR, t[FAJR], SUNRISE, t[SUNRISE])
        add(SUNRISE, t[SUNRISE], ZUHR, t[ZUHR])
        add(ZUHR, t[ZUHR], ASR, t[ASR])
        add(ASR, t[ASR], MAGHRIB, t[MAGHRIB])
        add(MAGHRIB, t[MAGHRIB], ISHA, t[ISHA])
        add(ISHA, t[ISHA], FAJR, tm?.get(FAJR), t[MAGHRIB])
    }

    private fun seconds(a: Instant, b: Instant): Double = Duration.between(a, b).toMillis() / 1000.0

    private fun build(c: Candidate, now: Instant): PrayerWindow {
        val total = seconds(c.startAt, c.endAt)
        val elapsed = seconds(c.startAt, now)
        val remaining = seconds(now, c.endAt)
        val progress = (elapsed / total).coerceIn(0.0, 1.0)

        if (c.start == SUNRISE) {
            return PrayerWindow(c.start, c.startAt, c.end, c.endAt, progress.toFloat(), Urgency.NEUTRAL,
                listOf(Segment(0.0, 1.0, Urgency.NEUTRAL)))
        }

        val greenEnd = greenEnd(c, total)
        val redStart = maxOf(greenEnd, (total - RED_TAIL_SECONDS) / total)
        val segments = buildList {
            if (greenEnd > 0) add(Segment(0.0, greenEnd, Urgency.GREEN))
            if (redStart > greenEnd) add(Segment(greenEnd, redStart, Urgency.YELLOW))
            add(Segment(redStart, 1.0, Urgency.RED))
        }
        val urgency = when {
            remaining <= RED_TAIL_SECONDS -> Urgency.RED
            progress < greenEnd -> Urgency.GREEN
            else -> Urgency.YELLOW
        }
        return PrayerWindow(c.start, c.startAt, c.end, c.endAt, progress.toFloat(), urgency, segments)
    }

    private fun greenEnd(c: Candidate, total: Double): Double = when (c.start) {
        FAJR -> 1.0 / 3.0
        ZUHR, ASR -> 0.5
        MAGHRIB -> 0.2
        ISHA -> {
            val maghrib = c.maghrib
            if (maghrib == null) 0.5 else {
                val half = Duration.between(maghrib, c.endAt).toMillis() / 2
                val midnight = maghrib.plusMillis(half)
                (seconds(c.startAt, midnight) / total).coerceIn(0.0, 1.0)
            }
        }
        else -> 0.5
    }
}
