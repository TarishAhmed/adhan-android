package org.tarish.adhan.domain

import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/** §7.9 — tabular Umm al-Qura via java.time; caller appends "(approx.)". */
object HijriFormatter {
    fun format(date: LocalDate, monthNames: List<String>): String? = try {
        val h = HijrahDate.from(date)
        val d = h.get(ChronoField.DAY_OF_MONTH)
        val m = h.get(ChronoField.MONTH_OF_YEAR)
        val y = h.get(ChronoField.YEAR)
        "$d ${monthNames[m - 1]} $y AH"
    } catch (_: Exception) {
        null
    }
}
