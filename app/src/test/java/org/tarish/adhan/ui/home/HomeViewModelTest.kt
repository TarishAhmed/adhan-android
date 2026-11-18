package org.tarish.adhan.ui.home

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.tarish.adhan.Fixtures
import org.tarish.adhan.Fixtures.LONDON
import org.tarish.adhan.alarm.AlarmCoordinator
import org.tarish.adhan.core.time.FixedClock
import org.tarish.adhan.data.LocationRepository
import org.tarish.adhan.data.PlaceResult
import org.tarish.adhan.data.PrayerTimesRepository
import org.tarish.adhan.data.local.SettingsStore
import org.tarish.adhan.domain.model.PrayerDay
import org.tarish.adhan.domain.model.PrayerName
import org.tarish.adhan.domain.model.PrayerNotificationPrefs
import org.tarish.adhan.domain.model.StoredLocation
import org.tarish.adhan.domain.model.ThemeMode
import org.tarish.adhan.widget.WidgetUpdater
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val loc = StoredLocation(51.5074, -0.1278, "London", null, "UK", LONDON, StoredLocation.Source.MANUAL, Instant.EPOCH)
    /** Nov 30 2026, 12:00 London: the last day of the month, between Zuhr and Asr. */
    private val clock = FixedClock(LocalDateTime.of(2026, 11, 30, 12, 0).atZone(LONDON).toInstant(), LONDON)
    private val repo = FakePrayerRepo(listOf(Fixtures.D_NOV30, Fixtures.D_DEC1))
    private val settings = FakeSettings(loc)

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = HomeViewModel(settings, repo, FakeLocationRepo, NoopCoordinator, NoopWidget, clock)

    private fun HomeViewModel.awaitState(predicate: (HomeUiState) -> Boolean): HomeUiState =
        runBlocking { withTimeout(5_000) { state.first(predicate) } }

    @Test fun today_showsSixRowsWithZuhrCurrent() {
        val s = vm().awaitState { it.timeline.isNotEmpty() }
        assertThat(s.selectedDate).isEqualTo(LocalDate.of(2026, 11, 30))
        assertThat(s.timeline.map { it.prayer }).containsExactlyElementsIn(PrayerName.REQUIRED).inOrder()
        assertThat(s.timeline.single { it.isCurrent }.prayer).isEqualTo(PrayerName.ZUHR)
        assertThat(s.timeline.first { it.prayer == PrayerName.SUNRISE }.notifOn).isNull()
    }

    @Test fun nextDay_crossesMonthBoundary_andFetchesThatMonth() {
        val vm = vm()
        vm.awaitState { it.timeline.isNotEmpty() }
        vm.shiftDay(1)
        val s = vm.awaitState { it.selectedDate == LocalDate.of(2026, 12, 1) && it.timeline.isNotEmpty() }
        assertThat(repo.ensured).contains(YearMonth.of(2026, 12))
        // Another day is shown, so nothing is "now", and the hero still tracks today's next prayer.
        assertThat(s.timeline.none { it.isCurrent }).isTrue()
        assertThat(s.next?.prayer).isEqualTo(PrayerName.ASR)
        assertThat(s.timeline.first().time).isEqualTo(Fixtures.D_DEC1[PrayerName.FAJR])
    }

    @Test fun shiftDay_isClampedAndGoToTodayResets() {
        val vm = vm()
        vm.shiftDay(-100)
        vm.awaitState { it.selectedDate == LocalDate.of(2026, 11, 30).minusDays(HomeViewModel.MAX_DAYS_BACK.toLong()) }
        vm.goToToday()
        vm.awaitState { it.selectedDate == LocalDate.of(2026, 11, 30) }
    }

    @Test fun bellToggle_turnsAdhanOffThenBackOnForShownWeekday() {
        settings.prefs.update { it + (PrayerName.ASR to PrayerNotificationPrefs(PrayerName.ASR, days = setOf(DayOfWeek.FRIDAY))) }
        val vm = vm()
        // Nov 30 2026 is a Monday, which Asr's prefs exclude, so its bell shows off.
        assertThat(vm.awaitState { it.timeline.isNotEmpty() }.timeline.first { it.prayer == PrayerName.ASR }.notifOn).isFalse()

        vm.toggleNotification(PrayerName.ASR)
        vm.awaitState { s -> s.timeline.first { it.prayer == PrayerName.ASR }.notifOn == true }
        assertThat(settings.prefs.value.getValue(PrayerName.ASR).days).containsExactly(DayOfWeek.FRIDAY, DayOfWeek.MONDAY)

        vm.toggleNotification(PrayerName.ASR)
        vm.awaitState { s -> s.timeline.first { it.prayer == PrayerName.ASR }.notifOn == false }
        assertThat(settings.prefs.value.getValue(PrayerName.ASR).enabled).isFalse()
    }

    private class FakePrayerRepo(days: List<PrayerDay>) : PrayerTimesRepository {
        private val all = MutableStateFlow(days)
        val ensured = mutableListOf<YearMonth>()
        override fun observeDays(key: String, from: LocalDate, to: LocalDate): Flow<List<PrayerDay>> =
            all.map { list -> list.filter { it.date in from..to } }
        override suspend fun daysOnce(key: String, from: LocalDate, to: LocalDate) = all.value.filter { it.date in from..to }
        override suspend fun getDay(loc: StoredLocation, date: LocalDate, allowNetwork: Boolean) =
            all.value.firstOrNull { it.date == date }?.let { Result.success(it) } ?: Result.failure(NoSuchElementException())
        override suspend fun ensureMonths(loc: StoredLocation, months: List<YearMonth>, force: Boolean): Result<Unit> {
            ensured += months; return Result.success(Unit)
        }
        override suspend fun ensureCoverage(loc: StoredLocation, force: Boolean) = Result.success(Unit)
        override suspend fun refreshCurrent(loc: StoredLocation) = Result.success(Unit)
        override suspend fun cleanup(currentKey: String?) {}
        override suspend fun clearAll() {}
        override suspend fun stats() = 0 to 0
        override val monthStored: SharedFlow<String> = MutableSharedFlow()
    }

    private object FakeLocationRepo : LocationRepository {
        override val current: Flow<StoredLocation?> = flowOf(null)
        override fun hasPermission() = false
        override fun servicesEnabled() = true
        override fun geocoderAvailable() = false
        override suspend fun refreshFromGps(force: Boolean): Result<StoredLocation?> = Result.success(null)
        override suspend fun search(query: String): Result<List<PlaceResult>> = Result.success(emptyList())
        override suspend fun setManual(place: PlaceResult): StoredLocation = error("unused")
        override suspend fun settingsResolution() = null
    }

    private object NoopCoordinator : AlarmCoordinator {
        override fun rescheduleAll(reason: String) {}
        override suspend fun rescheduleNow(reason: String) {}
    }

    private object NoopWidget : WidgetUpdater {
        override suspend fun updateAll() {}
    }

    private class FakeSettings(loc: StoredLocation) : SettingsStore {
        val prefs = MutableStateFlow(PrayerName.SALAH.associateWith { PrayerNotificationPrefs(it) })
        override val onboardingDone: Flow<Boolean> = flowOf(true)
        override suspend fun setOnboardingDone() {}
        override val themeMode: Flow<ThemeMode> = flowOf(ThemeMode.SYSTEM)
        override suspend fun setThemeMode(m: ThemeMode) {}
        override val autoLocation: Flow<Boolean> = flowOf(false)
        override suspend fun setAutoLocation(v: Boolean) {}
        override val location = MutableStateFlow<StoredLocation?>(loc)
        override suspend fun setLocation(l: StoredLocation?) { location.value = l }
        override suspend fun setLocationZone(key: String, zone: ZoneId) {}
        override val prayerPrefs: Flow<Map<PrayerName, PrayerNotificationPrefs>> = prefs
        override suspend fun updatePrayerPrefs(p: PrayerName, transform: (PrayerNotificationPrefs) -> PrayerNotificationPrefs) {
            prefs.update { it + (p to transform(it.getValue(p))) }
        }
        override val batteryPromptShown: Flow<Boolean> = flowOf(false)
        override suspend fun setBatteryPromptShown() {}
        override val exactAlarmPromptShown: Flow<Boolean> = flowOf(false)
        override suspend fun setExactAlarmPromptShown() {}
        override val lastSyncAt: Flow<Long> = flowOf(0)
        override suspend fun setLastSyncAt(ms: Long) {}
        override suspend fun lastGpsRefreshAt() = 0L
        override suspend fun setLastGpsRefreshAt(ms: Long) {}
        override suspend fun scheduledAlarmKeys() = emptySet<Int>()
        override suspend fun setScheduledAlarmKeys(keys: Set<Int>) {}
        override suspend fun flutterMigrated() = true
        override suspend fun applyMigration(values: Map<String, Any>) {}
    }
}
