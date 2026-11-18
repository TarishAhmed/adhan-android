package org.tarish.adhan.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.tarish.adhan.core.format.countdownText
import org.tarish.adhan.core.format.formatCoord
import org.tarish.adhan.core.format.locationKey
import org.tarish.adhan.core.format.toEnglish
import org.tarish.adhan.core.format.widgetRemaining
import java.time.Instant
import java.util.Locale

class FormatTest {
    @Test fun formatCoord_values() {
        assertThat(formatCoord(51.5074)).isEqualTo("51.51")
        assertThat(formatCoord(-0.1278)).isEqualTo("-0.13")
        assertThat(formatCoord(12.9)).isEqualTo("12.90")
        assertThat(formatCoord(-0.001)).isEqualTo("0.00")
        assertThat(formatCoord(0.005)).isEqualTo("0.01")
    }

    @Test fun formatCoord_isLocaleIndependent() {
        val prev = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertThat(formatCoord(51.5074)).isEqualTo("51.51")
            assertThat(locationKey(51.5074, -0.1278)).isEqualTo("51.51,-0.13")
        } finally {
            Locale.setDefault(prev)
        }
    }

    private val base = Instant.ofEpochSecond(1_000_000)
    private fun rem(seconds: Long) = widgetRemaining(base.plusSeconds(seconds), base).toEnglish()

    @Test fun widgetRemaining_table() {
        assertThat(rem(0)).isEqualTo("now")
        assertThat(rem(59)).isEqualTo("in 1 min")
        assertThat(rem(60)).isEqualTo("in 1 min")
        assertThat(rem(61)).isEqualTo("in 2 min")
        assertThat(rem(3600)).isEqualTo("in 1 h")
        assertThat(rem(3900)).isEqualTo("in 1 h 5 min")
        assertThat(rem(-30)).isEqualTo("now")
    }

    @Test fun countdown_flooredClampedCapped() {
        assertThat(countdownText(base.plusMillis(3_661_999), base)).isEqualTo("01:01:01")
        assertThat(countdownText(base.minusSeconds(5), base)).isEqualTo("00:00:00")
        assertThat(countdownText(base.plusSeconds(200L * 3600), base)).isEqualTo("99:59:59")
    }
}
