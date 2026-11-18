package org.tarish.adhan.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.tarish.adhan.Fixtures
import org.tarish.adhan.Fixtures.D_DEC1
import org.tarish.adhan.Fixtures.D_NOV1
import org.tarish.adhan.Fixtures.D_NOV2
import org.tarish.adhan.Fixtures.D_NOV30
import org.tarish.adhan.Fixtures.LONDON
import org.tarish.adhan.Fixtures.at
import org.tarish.adhan.core.format.countdownText
import org.tarish.adhan.domain.model.PrayerDay
import org.tarish.adhan.domain.model.PrayerName
import org.tarish.adhan.domain.model.PrayerName.ASR
import org.tarish.adhan.domain.model.PrayerName.FAJR
import org.tarish.adhan.domain.model.PrayerName.ISHA
import org.tarish.adhan.domain.model.PrayerName.MAGHRIB
import org.tarish.adhan.domain.model.PrayerName.SUNRISE
import org.tarish.adhan.domain.model.PrayerName.ZUHR
import org.tarish.adhan.domain.model.Segment
import org.tarish.adhan.domain.model.Urgency
import org.tarish.adhan.domain.model.Urgency.GREEN
import org.tarish.adhan.domain.model.Urgency.NEUTRAL
import org.tarish.adhan.domain.model.Urgency.RED
import org.tarish.adhan.domain.model.Urgency.YELLOW
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

class PrayerWindowCalculatorTest {

    private data class Row(
        val ms: Long, val start: PrayerName, val end: PrayerName, val progress: Double, val urgency: Urgency,
        val next: PrayerName, val nextLocal: String?, val tomorrow: Boolean, val countdown: String,
        val y: PrayerDay?, val t: PrayerDay, val tm: PrayerDay?,
    )

    // §18.1 table
    private val rows = listOf(
        Row(1793511600000, FAJR, SUNRISE, 0.1778, GREEN, ZUHR, "11:49", false, "06:09:00", null, D_NOV1, D_NOV2),
        Row(1793512800000, FAJR, SUNRISE, 0.4000, YELLOW, ZUHR, null, false, "05:49:00", null, D_NOV1, D_NOV2),
        Row(1793515560000, FAJR, SUNRISE, 0.9111, RED, ZUHR, null, false, "05:03:00", null, D_NOV1, D_NOV2),
        Row(1793523600000, SUNRISE, ZUHR, 0.4271, NEUTRAL, ZUHR, null, false, "02:49:00", null, D_NOV1, D_NOV2),
        Row(1793536200000, ZUHR, ASR, 0.3015, GREEN, ASR, "14:05", false, "01:35:00", null, D_NOV1, D_NOV2),
        Row(1793538000000, ZUHR, ASR, 0.5221, YELLOW, ASR, null, false, "01:05:00", null, D_NOV1, D_NOV2),
        Row(1793551200000, MAGHRIB, ISHA, 0.0588, GREEN, ISHA, "18:00", false, "01:20:00", null, D_NOV1, D_NOV2),
        Row(1793552400000, MAGHRIB, ISHA, 0.2941, YELLOW, ISHA, null, false, "01:00:00", null, D_NOV1, D_NOV2),
        Row(1793555520000, MAGHRIB, ISHA, 0.9059, RED, ISHA, null, false, "00:08:00", null, D_NOV1, D_NOV2),
        Row(1793563200000, ISHA, FAJR, 0.1749, GREEN, FAJR, "05:26", true, "09:26:00", null, D_NOV1, D_NOV2),
        Row(1793575800000, ISHA, FAJR, 0.4810, YELLOW, FAJR, null, true, "05:56:00", null, D_NOV1, D_NOV2),
        Row(1793588400000, ISHA, FAJR, 0.7872, YELLOW, FAJR, "05:26", false, "02:26:00", D_NOV1, D_NOV2, null),
    )

    @Test fun table() {
        for (r in rows) {
            val now = at(r.ms)
            val w = PrayerWindowCalculator.compute(now, r.y, r.t, r.tm)
            assertThat(w).isNotNull()
            assertThat(w!!.start).isEqualTo(r.start)
            assertThat(w.end).isEqualTo(r.end)
            assertThat(w.progress.toDouble()).isWithin(0.0001).of(r.progress)
            assertThat(w.urgency).isEqualTo(r.urgency)

            val n = NextPrayerFinder.nextSalah(now, r.t, r.tm)!!
            assertThat(n.prayer).isEqualTo(r.next)
            assertThat(n.isTomorrow).isEqualTo(r.tomorrow)
            r.nextLocal?.let { assertThat(n.time.atZone(LONDON).toLocalTime()).isEqualTo(LocalTime.parse(it)) }
            assertThat(countdownText(n.time, now)).isEqualTo(r.countdown)
        }
    }

    private fun assertSegments(actual: List<Segment>, vararg expected: Triple<Double, Double, Urgency>) {
        assertThat(actual).hasSize(expected.size)
        actual.zip(expected).forEach { (a, e) ->
            assertThat(a.start).isWithin(0.00001).of(e.first)
            assertThat(a.end).isWithin(0.00001).of(e.second)
            assertThat(a.urgency).isEqualTo(e.third)
        }
    }

    @Test fun segments_fajr() {
        val w = PrayerWindowCalculator.compute(at(1793511600000), null, D_NOV1, D_NOV2)!!
        assertSegments(w.segments, Triple(0.0, 0.33333, GREEN), Triple(0.33333, 0.88889, YELLOW), Triple(0.88889, 1.0, RED))
    }

    @Test fun segments_ishaMidnightSplit() {
        val w = PrayerWindowCalculator.compute(at(1793563200000), null, D_NOV1, D_NOV2)!!
        assertThat(Duration.between(w.startAt, w.endAt).seconds).isEqualTo(41160)
        assertSegments(w.segments, Triple(0.0, 0.43805, GREEN), Triple(0.43805, 0.98542, YELLOW), Triple(0.98542, 1.0, RED))
        // Islamic midnight = 1793574030000 (23:00:30)
        val midnight = w.startAt.plusMillis((0.43805 * 41160 * 1000).toLong())
        assertThat(midnight.toEpochMilli()).isWithin(1000L).of(1793574030000)
    }

    @Test fun segments_sunriseNeutral() {
        val w = PrayerWindowCalculator.compute(at(1793523600000), null, D_NOV1, D_NOV2)!!
        assertSegments(w.segments, Triple(0.0, 1.0, NEUTRAL))
    }

    @Test fun exactlyAtPrayerInstant_switchesWindowAndAdvancesNext() {
        val zuhr = at(Fixtures.NOV1.getValue(ZUHR))
        val w = PrayerWindowCalculator.compute(zuhr, null, D_NOV1, D_NOV2)!!
        assertThat(w.start).isEqualTo(ZUHR)
        assertThat(w.progress).isEqualTo(0f)
        assertThat(NextPrayerFinder.nextSalah(zuhr, D_NOV1, D_NOV2)!!.prayer).isEqualTo(ASR)
    }

    @Test fun missingTomorrow_afterIsha_nullWindowAndNext() {
        val now = at(1793563200000)
        assertThat(PrayerWindowCalculator.compute(now, null, D_NOV1, null)).isNull()
        assertThat(NextPrayerFinder.nextSalah(now, D_NOV1, null)).isNull()
    }

    @Test fun missingYesterday_beforeFajr_nullWindow_nextIsTodayFajr() {
        val now = at(1793588400000)
        assertThat(PrayerWindowCalculator.compute(now, null, D_NOV2, null)).isNull()
        val n = NextPrayerFinder.nextSalah(now, D_NOV2, null)!!
        assertThat(n.prayer).isEqualTo(FAJR)
        assertThat(n.isTomorrow).isFalse()
    }

    @Test fun monthBoundary_nov30IshaToDec1Fajr() {
        val now = at(Fixtures.NOV30.getValue(ISHA)).plusSeconds(3600)
        val w = PrayerWindowCalculator.compute(now, null, D_NOV30, D_DEC1)!!
        assertThat(w.start).isEqualTo(ISHA)
        assertThat(w.endAt).isEqualTo(D_DEC1[FAJR])
        val n = NextPrayerFinder.nextSalah(now, D_NOV30, D_DEC1)!!
        assertThat(n.isTomorrow).isTrue()
        assertThat(n.time).isEqualTo(D_DEC1[FAJR])
    }

    @Test fun dstTransitions_windowsStayPositive() {
        for ((d, d2) in listOf(LocalDate.of(2026, 3, 28) to LocalDate.of(2026, 3, 29), LocalDate.of(2026, 10, 24) to LocalDate.of(2026, 10, 25))) {
            val day1 = Fixtures.synthetic(d, LONDON, FAJR to "04:50", SUNRISE to "06:20", ZUHR to "12:10", ASR to "15:30", MAGHRIB to "18:30", ISHA to "20:00")
            val day2 = Fixtures.synthetic(d2, LONDON, FAJR to "04:48", SUNRISE to "06:18", ZUHR to "12:10", ASR to "15:31", MAGHRIB to "18:32", ISHA to "20:02")
            // Overnight across the DST change
            val now = day1[ISHA]!!.plusSeconds(3 * 3600)
            val w = PrayerWindowCalculator.compute(now, null, day1, day2)!!
            assertThat(w.start).isEqualTo(ISHA)
            assertThat(Duration.between(w.startAt, w.endAt).isNegative).isFalse()
            assertThat(w.progress).isIn(com.google.common.collect.Range.closed(0f, 1f))
            assertThat(w.segments.all { it.end >= it.start }).isTrue()
            // Every window on the DST day itself
            var t = day2[FAJR]!!
            while (t < day2[ISHA]!!) {
                val ww = PrayerWindowCalculator.compute(t, day1, day2, null)!!
                assertThat(Duration.between(ww.startAt, ww.endAt).seconds).isGreaterThan(0)
                t = t.plusSeconds(1800)
            }
        }
    }
}
