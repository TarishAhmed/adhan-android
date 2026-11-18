package org.tarish.adhan.domain.model

import org.tarish.adhan.core.format.locationKey
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

enum class PrayerName(val key: String, val displayName: String, val isSalah: Boolean) {
    FAJR("fajr", "Fajr", true),
    SUNRISE("sunrise", "Sunrise", false),
    ZUHR("zuhr", "Zuhr", true),
    ASR("asr", "Asr", true),
    SUNSET("sunset", "Sunset", false),
    MAGHRIB("maghrib", "Maghrib", true),
    ISHA("isha", "Isha", true);

    companion object {
        /** API sends "Fajr", "Sunrise", ... Compare case-insensitively. Unknown → null (ignored). */
        fun fromApi(raw: String): PrayerName? = entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
        fun fromKey(key: String): PrayerName? = entries.firstOrNull { it.key == key }
        val SALAH = listOf(FAJR, ZUHR, ASR, MAGHRIB, ISHA)
        /** Required for a valid day, in strictly increasing order (§5.1.4). */
        val REQUIRED = listOf(FAJR, SUNRISE, ZUHR, ASR, MAGHRIB, ISHA)
    }
}

/** One calendar day of times for one location. All instants are absolute. */
data class PrayerDay(
    val locationKey: String,
    val date: LocalDate,
    val zone: ZoneId,
    val times: Map<PrayerName, Instant>,
) {
    operator fun get(p: PrayerName): Instant? = times[p]
}

data class StoredLocation(
    val lat: Double,
    val lng: Double,
    val city: String?,
    val region: String?,
    val country: String?,
    val zone: ZoneId?,
    val source: Source,
    val updatedAt: Instant,
) {
    enum class Source { GPS, MANUAL }

    val key: String get() = locationKey(lat, lng)
    val displayName: String get() = city ?: region ?: String.format(Locale.US, "%.3f, %.3f", lat, lng)
}

data class PrayerNotificationPrefs(
    val prayer: PrayerName,
    val enabled: Boolean = true,
    val soundId: String = AdhanSound.DEFAULT_ID,
    val advanceMinutes: Int = 0,
    val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
)

data class NextPrayer(val prayer: PrayerName, val time: Instant, val isTomorrow: Boolean)

enum class ThemeMode(val key: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark");

    companion object {
        fun fromKey(k: String?) = entries.firstOrNull { it.key == k } ?: SYSTEM
    }
}
