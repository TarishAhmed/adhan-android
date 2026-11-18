package org.tarish.adhan.domain

import org.tarish.adhan.domain.model.NextPrayer
import org.tarish.adhan.domain.model.PrayerDay
import org.tarish.adhan.domain.model.PrayerName
import java.time.Instant

/** §7.3. Sunrise is never "next"; strictly greater than now. */
object NextPrayerFinder {
    fun nextSalah(now: Instant, today: PrayerDay?, tomorrow: PrayerDay?): NextPrayer? {
        if (today != null) {
            for (p in PrayerName.SALAH) {
                val t = today[p] ?: continue
                if (t > now) return NextPrayer(p, t, isTomorrow = false)
            }
        }
        val fajr = tomorrow?.get(PrayerName.FAJR) ?: return null
        return if (fajr > now) NextPrayer(PrayerName.FAJR, fajr, isTomorrow = true) else null
    }
}
