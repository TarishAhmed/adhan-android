package org.tarish.adhan.data.remote

import org.tarish.adhan.domain.model.PrayerDay
import org.tarish.adhan.domain.model.PrayerName
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** §5.1.4 — DTO → validated days. The `date` field is never used (BUG-03). */
object TimetableMapper {

    sealed interface Outcome {
        data class Ok(val zone: ZoneId, val days: List<PrayerDay>) : Outcome
        data object BadData : Outcome
    }

    fun map(dto: MonthResponseDto, ym: YearMonth, key: String, fallbackZone: ZoneId): Outcome {
        val zone = dto.locationInfo?.timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: fallbackZone.also { Timber.w("Missing/invalid timezone in response; falling back to %s", it) }
        val len = ym.lengthOfMonth()
        val byDate = LinkedHashMap<LocalDate, PrayerDay>()

        dto.multiDayTimings.forEachIndexed { i, dayDto ->
            if (i + 1 > len) return@forEachIndexed
            var date = ym.atDay(i + 1)
            val times = buildMap<PrayerName, Instant> {
                for (p in dayDto.prayers) {
                    val name = PrayerName.fromApi(p.name) ?: continue
                    val t = p.time ?: continue
                    put(name, Instant.ofEpochMilli(t))
                }
            }
            val fajr = times[PrayerName.FAJR]
            if (fajr != null) {
                val fajrDate = fajr.atZone(zone).toLocalDate()
                if (fajrDate != date) {
                    if (YearMonth.from(fajrDate) == ym && fajrDate !in byDate) {
                        Timber.w("Day index %d had Fajr on %s; re-keyed", i, fajrDate)
                        date = fajrDate
                    } else {
                        Timber.w("Dropping day index %d: Fajr date mismatch", i); return@forEachIndexed
                    }
                }
            }
            if (!isValid(times)) {
                Timber.w("Dropping invalid day %s", date); return@forEachIndexed
            }
            if (date !in byDate) byDate[date] = PrayerDay(key, date, zone, times)
        }

        if (byDate.size < len - 1) return Outcome.BadData
        return Outcome.Ok(zone, byDate.values.sortedBy { it.date })
    }

    fun isValid(times: Map<PrayerName, Instant>): Boolean {
        val seq = PrayerName.REQUIRED.map { times[it] ?: return false }
        return seq.zipWithNext().all { (a, b) -> a < b }
    }
}
