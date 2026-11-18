package org.tarish.adhan.ui.settings

import android.content.Context
import android.text.format.Formatter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.tarish.adhan.alarm.AlarmCoordinator
import org.tarish.adhan.alarm.AlarmScheduler
import org.tarish.adhan.data.AppUpdater
import org.tarish.adhan.data.PrayerTimesRepository
import org.tarish.adhan.data.SoundRepository
import org.tarish.adhan.data.local.SettingsStore
import org.tarish.adhan.domain.AlarmSpec
import org.tarish.adhan.domain.model.PrayerName
import org.tarish.adhan.ui.util.isIgnoringBatteryOptimizations
import org.tarish.adhan.widget.WidgetUpdater
import org.tarish.adhan.work.WorkScheduler
import timber.log.Timber
import java.time.ZoneId
import javax.inject.Inject

data class CacheInfo(val days: Int, val locations: Int, val sounds: Int, val soundsSize: String, val lastSync: Long)

/** Values shown inline on the Settings rows so users needn't open a screen to see them. */
data class SettingsSummary(val locationLabel: String? = null, val adhansOn: Int = 0)

/** A maintenance action that is running; its row shows a spinner instead of a chevron. */
enum class Busy { REFRESH, CLEAR_TIMES, DELETE_SOUNDS }

enum class SettingsEvent { REFRESHED, REFRESH_FAILED, TIMES_CLEARED, SOUNDS_DELETED }

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
    private val prayerRepo: PrayerTimesRepository,
    private val sounds: SoundRepository,
    private val scheduler: AlarmScheduler,
    private val coordinator: AlarmCoordinator,
    private val widgetUpdater: WidgetUpdater,
    private val workScheduler: WorkScheduler,
    private val updater: AppUpdater,
) : ViewModel() {

    val updates = updater.state

    fun checkForUpdates() { viewModelScope.launch { updater.check() } }
    fun startUpdate() = updater.update()

    private val _batteryOk = MutableStateFlow(true)
    val batteryOk = _batteryOk.asStateFlow()
    private val _exactOk = MutableStateFlow(true)
    val exactOk = _exactOk.asStateFlow()
    private val _plan = MutableStateFlow<Pair<List<AlarmSpec>, ZoneId>?>(null)
    val plan = _plan.asStateFlow()
    private val _cache = MutableStateFlow<CacheInfo?>(null)
    val cache = _cache.asStateFlow()
    private val _busy = MutableStateFlow<Busy?>(null)
    val busy = _busy.asStateFlow()
    private val events = Channel<SettingsEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    val summary = combine(settings.location, settings.prayerPrefs) { loc, prefs ->
        SettingsSummary(loc?.displayName, prefs.values.count { it.enabled })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsSummary())

    fun refreshStatus() {
        _batteryOk.value = context.isIgnoringBatteryOptimizations()
        _exactOk.value = scheduler.canScheduleExact()
    }

    fun loadPlan() {
        viewModelScope.launch {
            val zone = settings.location.first()?.zone ?: ZoneId.systemDefault()
            _plan.value = scheduler.currentPlan(allowNetwork = false).take(10) to zone
        }
    }

    fun loadCache() {
        viewModelScope.launch {
            val (days, locs) = prayerRepo.stats()
            val (n, size) = sounds.downloadedStats()
            _cache.value = CacheInfo(days, locs, n, Formatter.formatShortFileSize(context, size), settings.lastSyncAt.first())
        }
    }

    fun refreshNow() = run(Busy.REFRESH) {
        val r = settings.location.first()?.let { prayerRepo.refreshCurrent(it) }
        coordinator.rescheduleAll("manual")
        widgetUpdater.updateAll()
        if (r == null || r.isSuccess) SettingsEvent.REFRESHED else SettingsEvent.REFRESH_FAILED
    }

    /** Clear then immediately refetch so Today doesn't go blank (BUG-22). */
    fun clearTimes() = run(Busy.CLEAR_TIMES) {
        prayerRepo.clearAll()
        settings.location.first()?.let { prayerRepo.ensureCoverage(it) }
        coordinator.rescheduleAll("cleared")
        SettingsEvent.TIMES_CLEARED
    }

    fun deleteSounds() = run(Busy.DELETE_SOUNDS) {
        val inUse = settings.prayerPrefs.first().values.map { it.soundId }.toSet()
        sounds.deleteDownloaded(inUse)
        SettingsEvent.SOUNDS_DELETED
    }

    /** One maintenance action at a time; reports its outcome and refreshes the cache figures. */
    private fun run(kind: Busy, action: suspend () -> SettingsEvent) {
        if (_busy.value != null) return
        viewModelScope.launch {
            _busy.value = kind
            try {
                events.send(action())
            } finally {
                _busy.value = null
                loadCache()
                loadPlan()
            }
        }
    }

    // Debug tools
    fun debugSync() = workScheduler.syncNow()
    fun debugWidget() { viewModelScope.launch { widgetUpdater.updateAll() } }
    fun debugTestAll() {
        PrayerName.SALAH.forEachIndexed { i, p -> scheduler.scheduleTest(p, 10L * (i + 1)) }
    }
    fun debugDump() {
        viewModelScope.launch {
            Timber.d("location=%s", settings.location.first())
            Timber.d("prefs=%s", settings.prayerPrefs.first())
            Timber.d("keys=%s", settings.scheduledAlarmKeys())
            scheduler.currentPlan(false).forEach { Timber.d("plan %s", it) }
        }
    }
}
