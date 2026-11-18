package org.tarish.adhan.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.tarish.adhan.alarm.AlarmCoordinator
import org.tarish.adhan.core.result.AppError
import org.tarish.adhan.core.result.appError
import org.tarish.adhan.core.time.Clock
import org.tarish.adhan.data.LocationRepository
import org.tarish.adhan.data.PrayerTimesRepository
import org.tarish.adhan.data.local.SettingsStore
import org.tarish.adhan.domain.AlarmPlanner
import org.tarish.adhan.domain.Greeting
import org.tarish.adhan.domain.NextPrayerFinder
import org.tarish.adhan.domain.PrayerWindowCalculator
import org.tarish.adhan.domain.model.NextPrayer
import org.tarish.adhan.domain.model.PrayerDay
import org.tarish.adhan.domain.model.PrayerName
import org.tarish.adhan.domain.model.PrayerNotificationPrefs
import org.tarish.adhan.domain.model.PrayerWindow
import org.tarish.adhan.domain.model.StoredLocation
import org.tarish.adhan.widget.WidgetUpdater
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/** One row of the day timeline. [notifOn] is null for rows without an adhan (Sunrise). */
data class TimelineItem(
    val prayer: PrayerName,
    val time: Instant,
    val isCurrent: Boolean,
    val isPast: Boolean,
    val notifOn: Boolean?,
)

sealed interface HomeStatus {
    data object Loading : HomeStatus
    data object Ready : HomeStatus
    data object NoLocation : HomeStatus
    data class Error(val error: AppError) : HomeStatus
}

data class HomeUiState(
    val greeting: Greeting = Greeting.MORNING,
    val locationLabel: String? = null,
    val locationLoading: Boolean = false,
    val zone: ZoneId = ZoneId.systemDefault(),
    val zoneHint: ZoneId? = null,
    val next: NextPrayer? = null,
    val window: PrayerWindow? = null,
    /** Rows for [selectedDate]; empty while that day is still loading. */
    val timeline: List<TimelineItem> = emptyList(),
    val date: LocalDate = LocalDate.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val status: HomeStatus = HomeStatus.Loading,
    val refreshing: Boolean = false,
    val stale: Boolean = false,
)

sealed interface HomeEvent {
    data object RefreshOk : HomeEvent
    data object RefreshFailed : HomeEvent
    data class NotificationToggled(val prayer: PrayerName, val on: Boolean) : HomeEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val prayerRepo: PrayerTimesRepository,
    private val locationRepo: LocationRepository,
    private val coordinator: AlarmCoordinator,
    private val widgetUpdater: WidgetUpdater,
    private val clock: Clock,
) : ViewModel() {

    private data class Flags(
        val loading: Boolean = false,
        val error: AppError? = null,
        val refreshing: Boolean = false,
        val stale: Boolean = false,
        val gpsBusy: Boolean = false,
    )

    private val flags = MutableStateFlow(Flags())
    /** Days from today shown in the timeline; the hero always tracks the real next prayer. */
    private val dayOffset = MutableStateFlow(0)
    private val now = MutableStateFlow(clock.now())
    private val events = Channel<HomeEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    @Volatile private var latestDays: List<PrayerDay> = emptyList()

    private val location = settings.location

    /** (location, location-local today) — recomputed each tick so the screen rolls over at midnight (BUG-06). */
    private val keyAndToday = combine(location, now) { loc, n ->
        loc?.let { it.key to n.atZone(it.zone ?: clock.deviceZone()).toLocalDate() }
    }.distinctUntilChanged()

    private val days: kotlinx.coroutines.flow.Flow<List<PrayerDay>> = combine(keyAndToday, dayOffset) { kt, o -> kt to o }
        .flatMapLatest { (kt, offset) ->
            if (kt == null) flowOf(emptyList())
            else {
                val selected = kt.second.plusDays(offset.toLong())
                prayerRepo.observeDays(kt.first, minOf(kt.second.minusDays(1), selected), maxOf(kt.second.plusDays(1), selected))
            }
        }

    val state: StateFlow<HomeUiState> =
        combine(location, days, settings.prayerPrefs, now, combine(flags, dayOffset, ::Pair)) { loc, d, prefs, n, (f, offset) ->
            latestDays = d
            build(loc, d, prefs, n, f, offset)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch { tickLoop() }
        viewModelScope.launch {
            keyAndToday.collect { kt -> if (kt != null) ensureData() }
        }
        viewModelScope.launch {
            if (location.first() == null) bootstrapLocation()
        }
    }

    /** Minute-aligned ticks, plus an exact wake at the next boundary (prayer / window end / midnight). */
    private suspend fun tickLoop() {
        while (true) {
            val n = clock.now()
            now.value = n
            val toMinute = 60_000 - (n.toEpochMilli() % 60_000)
            val boundary = AlarmPlanner.nextBoundary(n, latestDays)?.let { it.toEpochMilli() - n.toEpochMilli() }
            delay(minOf(toMinute, boundary?.coerceAtLeast(50) ?: Long.MAX_VALUE))
        }
    }

    private fun build(
        loc: StoredLocation?, d: List<PrayerDay>, prefs: Map<PrayerName, PrayerNotificationPrefs>, n: Instant, f: Flags, offset: Int,
    ): HomeUiState {
        val deviceZone = clock.deviceZone()
        val greeting = Greeting.forHour(n.atZone(deviceZone).hour)
        if (loc == null) {
            val status = when {
                f.gpsBusy -> HomeStatus.Loading
                f.error != null && f.error !is AppError.LocationPermissionDenied -> HomeStatus.Error(f.error)
                else -> HomeStatus.NoLocation
            }
            return HomeUiState(greeting = greeting, locationLoading = f.gpsBusy, status = status, date = n.atZone(deviceZone).toLocalDate())
        }
        val zone = loc.zone ?: deviceZone
        val today = n.atZone(zone).toLocalDate()
        val yDay = d.firstOrNull { it.date == today.minusDays(1) }
        val tDay = d.firstOrNull { it.date == today }
        val tmDay = d.firstOrNull { it.date == today.plusDays(1) }

        val window = PrayerWindowCalculator.compute(n, yDay, tDay, tmDay)
        val currentSalah = window?.start?.takeIf { it.isSalah }
        val selected = today.plusDays(offset.toLong())
        val timeline = d.firstOrNull { it.date == selected }?.let { day ->
            PrayerName.REQUIRED.mapNotNull { p ->
                val t = day[p] ?: return@mapNotNull null
                val pref = prefs[p]
                TimelineItem(
                    prayer = p,
                    time = t,
                    isCurrent = offset == 0 && p == currentSalah,
                    isPast = t < n && !(offset == 0 && p == currentSalah),
                    notifOn = if (!p.isSalah) null else pref != null && pref.enabled && selected.dayOfWeek in pref.days,
                )
            }
        }.orEmpty()

        val status = when {
            tDay != null -> HomeStatus.Ready
            f.error != null -> HomeStatus.Error(f.error)
            else -> HomeStatus.Loading
        }
        return HomeUiState(
            greeting = greeting,
            locationLabel = loc.displayName,
            locationLoading = f.gpsBusy,
            zone = zone,
            zoneHint = loc.zone?.takeIf { it.rules != deviceZone.rules },
            next = NextPrayerFinder.nextSalah(n, tDay, tmDay),
            window = window,
            timeline = timeline,
            date = today,
            selectedDate = selected,
            status = status,
            refreshing = f.refreshing,
            stale = f.stale && tDay != null,
        )
    }

    private suspend fun ensureData() {
        val loc = location.first() ?: return
        flags.update { it.copy(loading = true) }
        val r = prayerRepo.ensureCoverage(loc)
        flags.update { it.copy(loading = false, error = r.appError) }
    }

    private suspend fun bootstrapLocation() {
        if (!locationRepo.hasPermission()) return
        flags.update { it.copy(gpsBusy = true, error = null) }
        val r = locationRepo.refreshFromGps(force = true)
        flags.update { it.copy(gpsBusy = false, error = r.appError) }
    }

    /** Called on resume and after a permission grant. */
    fun onResume() {
        now.value = clock.now()
        viewModelScope.launch {
            if (location.first() == null && locationRepo.hasPermission() && !flags.value.gpsBusy) bootstrapLocation()
        }
    }

    fun retry() {
        viewModelScope.launch {
            if (location.first() == null) bootstrapLocation() else ensureData()
        }
    }

    fun onPermissionDenied(permanently: Boolean) {
        flags.update {
            it.copy(error = if (permanently) AppError.LocationPermissionPermanentlyDenied else AppError.LocationPermissionDenied)
        }
    }

    fun refresh() {
        if (flags.value.refreshing) return
        viewModelScope.launch {
            flags.update { it.copy(refreshing = true) }
            if (settings.autoLocation.first() && locationRepo.hasPermission()) {
                flags.update { it.copy(gpsBusy = true) }
                locationRepo.refreshFromGps(force = true)
                flags.update { it.copy(gpsBusy = false) }
            }
            val loc = location.first()
            val r = if (loc != null) prayerRepo.refreshCurrent(loc) else Result.failure(Exception())
            coordinator.rescheduleAll("refresh")
            widgetUpdater.updateAll()
            flags.update { it.copy(refreshing = false, stale = r.isFailure, error = if (r.isFailure && loc != null) r.appError else it.error) }
            events.send(if (r.isSuccess) HomeEvent.RefreshOk else HomeEvent.RefreshFailed)
        }
    }

    /** Moves the timeline by [delta] days within the cached range (a week back, a month ahead). */
    fun shiftDay(delta: Int) {
        val next = (dayOffset.value + delta).coerceIn(-MAX_DAYS_BACK, MAX_DAYS_AHEAD)
        if (next == dayOffset.value) return
        dayOffset.value = next
        viewModelScope.launch {
            val loc = location.first() ?: return@launch
            val date = clock.now().atZone(loc.zone ?: clock.deviceZone()).toLocalDate().plusDays(next.toLong())
            prayerRepo.ensureMonths(loc, listOf(YearMonth.from(date)))
        }
    }

    fun goToToday() { dayOffset.value = 0 }

    /**
     * The bell on a timeline row. Turning on also re-includes the shown weekday, so the bell is
     * never "on" while that day stays silent.
     */
    fun toggleNotification(p: PrayerName) {
        val item = state.value.timeline.firstOrNull { it.prayer == p } ?: return
        val on = item.notifOn != true
        val weekday = state.value.selectedDate.dayOfWeek
        viewModelScope.launch {
            settings.updatePrayerPrefs(p) { if (on) it.copy(enabled = true, days = it.days + weekday) else it.copy(enabled = false) }
            events.send(HomeEvent.NotificationToggled(p, on))
        }
    }

    companion object {
        const val MAX_DAYS_BACK = 7
        const val MAX_DAYS_AHEAD = 30
    }
}
