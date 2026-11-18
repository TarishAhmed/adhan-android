package org.tarish.adhan.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.tarish.adhan.Fixtures.D_NOV1
import org.tarish.adhan.Fixtures.D_NOV2
import org.tarish.adhan.Fixtures.LONDON
import org.tarish.adhan.Fixtures.at
import org.tarish.adhan.domain.model.PrayerName
import org.tarish.adhan.domain.model.PrayerName.ASR
import org.tarish.adhan.domain.model.PrayerName.FAJR
import org.tarish.adhan.domain.model.PrayerName.ISHA
import org.tarish.adhan.domain.model.PrayerName.MAGHRIB
import org.tarish.adhan.domain.model.PrayerName.ZUHR
import org.tarish.adhan.domain.model.PrayerNotificationPrefs
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class AlarmPlannerTest {
    private val now = at(1793563200000)   // Nov 1 20:00 London
    private val days = listOf(D_NOV1, D_NOV2)

    private fun prefs(transform: (PrayerNotificationPrefs) -> PrayerNotificationPrefs = { it }) =
        PrayerName.SALAH.associateWith { transform(PrayerNotificationPrefs(it)) }

    @Test fun spec_example() {
        val p = prefs {
            when (it.prayer) {
                FAJR -> it.copy(advanceMinutes = 15)
                ASR -> it.copy(days = DayOfWeek.entries.toSet() - DayOfWeek.MONDAY)
                else -> it
            }
        }
        val plan = AlarmPlanner.plan(now, days, p, notificationsAllowed = true)
        val nov2 = LocalDate.of(2026, 11, 2)
        fun london(h: Int, m: Int) = LocalDateTime.of(2026, 11, 2, h, m).atZone(LONDON).toInstant()

        assertThat(plan.map { Triple(it.kind, it.prayer, it.requestCode) }).containsExactly(
            Triple(AlarmKind.REMINDER, FAJR, 2075910),
            Triple(AlarmKind.ADHAN, FAJR, 2075900),
            Triple(AlarmKind.ADHAN, ZUHR, 2075902),
            Triple(AlarmKind.ADHAN, MAGHRIB, 2075905),
            Triple(AlarmKind.ADHAN, ISHA, 2075906),
        ).inOrder()
        assertThat(plan.map { it.fireAt }).containsExactly(
            london(5, 11), london(5, 26), london(11, 49), london(16, 33), london(17, 59),
        ).inOrder()
        assertThat(plan.all { it.date == nov2 }).isTrue()
        assertThat(nov2.toEpochDay()).isEqualTo(20759)
    }

    @Test fun notificationsBlocked_empty() {
        assertThat(AlarmPlanner.plan(now, days, prefs(), notificationsAllowed = false)).isEmpty()
    }

    @Test fun allDisabled_empty() {
        assertThat(AlarmPlanner.plan(now, days, prefs { it.copy(enabled = false) }, true)).isEmpty()
    }

    @Test fun reminderPassed_adhanFuture_onlyAdhan() {
        // 5 min before Nov 2 Fajr with a 15 min reminder.
        val t = D_NOV2[FAJR]!!.minusSeconds(300)
        val plan = AlarmPlanner.plan(t, days, prefs { if (it.prayer == FAJR) it.copy(advanceMinutes = 15) else it }, true)
        val fajr = plan.filter { it.prayer == FAJR }
        assertThat(fajr.map { it.kind }).containsExactly(AlarmKind.ADHAN)
    }

    @Test fun neverMoreThan20() {
        val plan = AlarmPlanner.plan(at(D_NOV1[FAJR]!!.toEpochMilli() - 3_600_000), days, prefs { it.copy(advanceMinutes = 5) }, true)
        assertThat(plan.size).isAtMost(AlarmPlanner.MAX_ALARMS)
        assertThat(plan.size).isEqualTo(20)
    }
}
