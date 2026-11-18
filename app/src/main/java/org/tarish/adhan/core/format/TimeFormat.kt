package org.tarish.adhan.core.format

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

/** "HH:MM:SS" countdown, floored, never negative, capped at 99:59:59 (§7.5). */
fun countdownText(next: Instant, now: Instant): String {
    val total = countdownSeconds(next, now)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
}

fun countdownSeconds(next: Instant, now: Instant): Long {
    val secs = max(0L, Math.floorDiv(Duration.between(now, next).toMillis(), 1000L))
    return minOf(secs, 99L * 3600 + 59 * 60 + 59)
}

/** Remaining-time phrase for the widget (§7.6). */
sealed interface Remaining {
    data object Now : Remaining
    data class Minutes(val m: Int) : Remaining
    data class Hours(val h: Int) : Remaining
    data class HoursMinutes(val h: Int, val m: Int) : Remaining
}

fun widgetRemaining(next: Instant, now: Instant): Remaining {
    val ms = max(0L, Duration.between(now, next).toMillis())
    val mins = ((ms + 59_999) / 60_000).toInt()   // ceil
    val h = mins / 60
    val m = mins % 60
    return when {
        mins == 0 -> Remaining.Now
        mins < 60 -> Remaining.Minutes(mins)
        m == 0 -> Remaining.Hours(h)
        else -> Remaining.HoursMinutes(h, m)
    }
}

fun Remaining.toEnglish(): String = when (this) {
    Remaining.Now -> "now"
    is Remaining.Minutes -> "in $m min"
    is Remaining.Hours -> "in $h h"
    is Remaining.HoursMinutes -> "in $h h $m min"
}

/** Prayer clock time in the LOCATION zone, honouring the system 12/24 h toggle (§7.10). */
fun formatClock(instant: Instant, zone: ZoneId, is24h: Boolean, locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm a", locale).format(instant.atZone(zone))
