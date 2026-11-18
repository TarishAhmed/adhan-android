package org.tarish.adhan

import org.tarish.adhan.domain.model.PrayerDay
import org.tarish.adhan.domain.model.PrayerName
import org.tarish.adhan.domain.model.PrayerName.ASR
import org.tarish.adhan.domain.model.PrayerName.FAJR
import org.tarish.adhan.domain.model.PrayerName.ISHA
import org.tarish.adhan.domain.model.PrayerName.MAGHRIB
import org.tarish.adhan.domain.model.PrayerName.SUNRISE
import org.tarish.adhan.domain.model.PrayerName.SUNSET
import org.tarish.adhan.domain.model.PrayerName.ZUHR
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** §18.1 fixture (London, real API values). */
object Fixtures {
    val LONDON: ZoneId = ZoneId.of("Europe/London")
    const val KEY = "51.51,-0.13"

    val NOV1 = mapOf(FAJR to 1793510640000, SUNRISE to 1793516040000, ZUHR to 1793533740000, ASR to 1793541900000,
        SUNSET to 1793550840000, MAGHRIB to 1793550900000, ISHA to 1793556000000)
    val NOV2 = mapOf(FAJR to 1793597160000, SUNRISE to 1793602560000, ZUHR to 1793620140000, ASR to 1793628180000,
        SUNSET to 1793637120000, MAGHRIB to 1793637180000, ISHA to 1793642340000)
    val NOV30 = mapOf(FAJR to 1796019120000, SUNRISE to 1796024520000, ZUHR to 1796039640000, ASR to 1796045760000,
        SUNSET to 1796054160000, MAGHRIB to 1796054220000, ISHA to 1796059740000)

    fun day(date: LocalDate, times: Map<PrayerName, Long>, zone: ZoneId = LONDON) =
        PrayerDay(KEY, date, zone, times.mapValues { Instant.ofEpochMilli(it.value) })

    val D_NOV1 = day(LocalDate.of(2026, 11, 1), NOV1)
    val D_NOV2 = day(LocalDate.of(2026, 11, 2), NOV2)
    val D_NOV30 = day(LocalDate.of(2026, 11, 30), NOV30)

    /** Synthetic day from local wall-clock times in [zone]. */
    fun synthetic(date: LocalDate, zone: ZoneId, vararg hm: Pair<PrayerName, String>): PrayerDay =
        PrayerDay(KEY, date, zone, hm.associate { (p, t) ->
            val (h, m) = t.split(":").map { it.toInt() }
            p to LocalDateTime.of(date.year, date.month, date.dayOfMonth, h, m).atZone(zone).toInstant()
        })

    val D_DEC1 = synthetic(LocalDate.of(2026, 12, 1), LONDON,
        FAJR to "06:02", SUNRISE to "07:44", ZUHR to "11:53", ASR to "13:44", SUNSET to "15:54", MAGHRIB to "15:55", ISHA to "17:30")

    fun at(epochMs: Long): Instant = Instant.ofEpochMilli(epochMs)
}
