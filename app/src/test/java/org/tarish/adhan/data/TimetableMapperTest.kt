package org.tarish.adhan.data

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test
import org.tarish.adhan.Fixtures
import org.tarish.adhan.data.remote.MonthResponseDto
import org.tarish.adhan.data.remote.TimetableMapper
import org.tarish.adhan.domain.model.PrayerName
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class TimetableMapperTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
    private val raw = javaClass.classLoader!!.getResource("fixtures/london_2026_11.json")!!.readText()
    private val ym = YearMonth.of(2026, 11)
    private val utc = ZoneId.of("UTC")

    private fun dto() = json.decodeFromString<MonthResponseDto>(raw)
    private fun map(d: MonthResponseDto, fallback: ZoneId = utc) = TimetableMapper.map(d, ym, Fixtures.KEY, fallback)

    @Test fun realFixture_parsesTo30ValidDays() {
        val out = map(dto()) as TimetableMapper.Outcome.Ok
        assertThat(out.zone).isEqualTo(ZoneId.of("Europe/London"))
        assertThat(out.days).hasSize(30)
        assertThat(out.days.first().date).isEqualTo(LocalDate.of(2026, 11, 1))
        assertThat(out.days.first().times.mapValues { it.value.toEpochMilli() }).isEqualTo(Fixtures.NOV1)
        assertThat(out.days[1].times.mapValues { it.value.toEpochMilli() }).isEqualTo(Fixtures.NOV2)
        assertThat(out.days.last().times.mapValues { it.value.toEpochMilli() }).isEqualTo(Fixtures.NOV30)
    }

    @Test fun shuffledPrayers_mapByName() {
        val d = dto().let { it.copy(multiDayTimings = it.multiDayTimings.map { day -> day.copy(prayers = day.prayers.reversed()) }) }
        val out = map(d) as TimetableMapper.Outcome.Ok
        assertThat(out.days.first()[PrayerName.FAJR]!!.toEpochMilli()).isEqualTo(Fixtures.NOV1[PrayerName.FAJR])
        assertThat(out.days.first()[PrayerName.ISHA]!!.toEpochMilli()).isEqualTo(Fixtures.NOV1[PrayerName.ISHA])
    }

    @Test fun unknownNameIgnored() {
        val d = dto().let { r ->
            r.copy(multiDayTimings = r.multiDayTimings.map { day ->
                day.copy(prayers = day.prayers + org.tarish.adhan.data.remote.PrayerDto("Tahajjud", 1L))
            })
        }
        assertThat((map(d) as TimetableMapper.Outcome.Ok).days).hasSize(30)
    }

    @Test fun dayMissingIsha_dropped() {
        val d = dto().let { r ->
            r.copy(multiDayTimings = r.multiDayTimings.mapIndexed { i, day ->
                if (i == 4) day.copy(prayers = day.prayers.filterNot { it.name == "Isha" }) else day
            })
        }
        val out = map(d) as TimetableMapper.Outcome.Ok
        assertThat(out.days).hasSize(29)
        assertThat(out.days.none { it.date == LocalDate.of(2026, 11, 5) }).isTrue()
    }

    @Test fun twoBadDays_wholeResponseIsBadData() {
        val d = dto().let { r ->
            r.copy(multiDayTimings = r.multiDayTimings.mapIndexed { i, day ->
                if (i == 4 || i == 9) day.copy(prayers = day.prayers.filterNot { it.name == "Isha" }) else day
            })
        }
        assertThat(map(d)).isEqualTo(TimetableMapper.Outcome.BadData)
    }

    @Test fun dateFieldHasNoEffect() {
        val d = dto().let { r -> r.copy(multiDayTimings = r.multiDayTimings.map { it.copy(date = 0L) }) }
        assertThat((map(d) as TimetableMapper.Outcome.Ok).days.map { it.date })
            .isEqualTo((map(dto()) as TimetableMapper.Outcome.Ok).days.map { it.date })
    }

    @Test fun invalidTimezone_fallsBack() {
        val d = dto().let { it.copy(locationInfo = it.locationInfo!!.copy(timezone = "Not/AZone")) }
        val out = map(d, ZoneId.of("Europe/London")) as TimetableMapper.Outcome.Ok
        assertThat(out.zone).isEqualTo(ZoneId.of("Europe/London"))
        assertThat(out.days).hasSize(30)
    }
}
