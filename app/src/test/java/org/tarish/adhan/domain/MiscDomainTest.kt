package org.tarish.adhan.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.tarish.adhan.data.GitHubUpdateRepository
import org.tarish.adhan.data.UpdateInfo
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

class MiscDomainTest {

    private data class City(val lat: Double, val lng: Double, val bearing: Double, val km: Double)

    @Test fun qibla_referenceTable() {
        val cities = listOf(
            City(40.7128, -74.0060, 58.48, 10306.0),   // New York
            City(51.5074, -0.1278, 118.99, 4794.0),    // London
            City(35.6762, 139.6503, 293.00, 9472.0),   // Tokyo
            City(24.8607, 67.0011, 267.74, 2800.0),    // Karachi
            City(-6.2088, 106.8456, 295.15, 7920.0),   // Jakarta
            City(31.8190, 75.3760, 260.66, 3699.0),    // Qadian
        )
        for (c in cities) {
            assertThat(QiblaMath.qiblaBearing(c.lat, c.lng)).isWithin(0.1).of(c.bearing)
            assertThat(QiblaMath.distanceKm(c.lat, c.lng)).isWithin(1.0).of(c.km)
        }
    }

    @Test fun qibla_relativeAngle() {
        assertThat(QiblaMath.relativeAngle(10.0, 350.0)).isWithin(1e-9).of(20.0)
        assertThat(QiblaMath.relativeAngle(350.0, 10.0)).isWithin(1e-9).of(-20.0)
        assertThat(QiblaMath.relativeAngle(180.0, 0.0)).isWithin(1e-9).of(-180.0)
    }

    @Test fun qibla_directionNames() {
        assertThat(QiblaMath.directionName(0.0)).isEqualTo("N")
        assertThat(QiblaMath.directionName(337.5)).isEqualTo("N")
        assertThat(QiblaMath.directionName(22.4)).isEqualTo("N")
        assertThat(QiblaMath.directionName(22.5)).isEqualTo("NE")
        assertThat(QiblaMath.directionName(118.99)).isEqualTo("SE")
        assertThat(QiblaMath.directionName(293.0)).isEqualTo("NW")
    }

    @Test fun qibla_greatCircleEndpoints() {
        val pts = QiblaMath.greatCircle(51.5074, -0.1278, QiblaMath.KAABA_LAT, QiblaMath.KAABA_LNG, 64)
        assertThat(pts).hasSize(66)
        assertThat(pts.first().first).isWithin(1e-6).of(51.5074)
        assertThat(pts.last().second).isWithin(1e-6).of(QiblaMath.KAABA_LNG)
    }

    @Test fun versionCompare() {
        assertThat(VersionCompare.isHigher("1.0.11", "1.0.10")).isTrue()
        assertThat(VersionCompare.isHigher("1.0.10", "1.0.10")).isFalse()
        assertThat(VersionCompare.isHigher("2", "1.9.9")).isTrue()
        assertThat(VersionCompare.isHigher("1.0", "1.0.0")).isFalse()
        assertThat(VersionCompare.isHigher("1.0.10-beta", "1.0.9")).isTrue()
        assertThat(VersionCompare.isHigher("", "1.0.0")).isFalse()
        assertThat(VersionCompare.isHigher("abc", "1.0.0")).isFalse()
    }

    @Test fun updateDecision() {
        val r = GitHubUpdateRepository
        assertThat(r.decide("2.0.0", latest = "2.0.1", minSupported = null).type).isEqualTo(UpdateInfo.Type.GENERAL)
        assertThat(r.decide("2.0.0", latest = "2.1.0", minSupported = "2.1.0").type).isEqualTo(UpdateInfo.Type.FORCE)
        // A min-supported the user already meets doesn't force.
        assertThat(r.decide("2.0.5", latest = "2.1.0", minSupported = "2.0.0").type).isEqualTo(UpdateInfo.Type.GENERAL)
        assertThat(r.decide("2.0.0", latest = "2.0.0", minSupported = "9.0.0").type).isEqualTo(UpdateInfo.Type.NONE)
        assertThat(r.decide("2.1.0", latest = "2.0.9", minSupported = null).type).isEqualTo(UpdateInfo.Type.NONE)
    }

    @Test fun minSupported_readFromHiddenCommentInReleaseNotes() {
        assertThat(GitHubUpdateRepository.minSupported("Fixes\n<!-- min-supported: 2.1.0 -->")).isEqualTo("2.1.0")
        assertThat(GitHubUpdateRepository.minSupported("<!-- min-supported: v3.0.0 -->")).isEqualTo("3.0.0")
        assertThat(GitHubUpdateRepository.minSupported("Just fixes")).isNull()
        assertThat(GitHubUpdateRepository.minSupported(null)).isNull()
    }

    @Test fun greetingBoundaries() {
        val cases = mapOf(
            4 to Greeting.NIGHT, 5 to Greeting.MORNING, 11 to Greeting.MORNING, 12 to Greeting.AFTERNOON,
            16 to Greeting.AFTERNOON, 17 to Greeting.EVENING, 20 to Greeting.EVENING, 21 to Greeting.NIGHT, 0 to Greeting.NIGHT,
        )
        cases.forEach { (h, g) -> assertThat(Greeting.forHour(h)).isEqualTo(g) }
    }

    @Test fun hijri() {
        val months = listOf("Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani", "Jumada al-Ula", "Jumada al-Akhirah",
            "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah")
        val date = LocalDate.of(2026, 11, 16)
        val h = HijrahDate.from(date)
        val expected = "${h.get(ChronoField.DAY_OF_MONTH)} ${months[h.get(ChronoField.MONTH_OF_YEAR) - 1]} ${h.get(ChronoField.YEAR)} AH"
        assertThat(HijriFormatter.format(date, months)).isEqualTo(expected)
        assertThat(HijriFormatter.format(LocalDate.of(1800, 1, 1), months)).isNull()
    }
}
