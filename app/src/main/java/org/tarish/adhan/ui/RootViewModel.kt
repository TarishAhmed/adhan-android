package org.tarish.adhan.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.tarish.adhan.alarm.AlarmCoordinator
import org.tarish.adhan.alarm.AlarmScheduler
import org.tarish.adhan.data.LocationRepository
import org.tarish.adhan.data.UpdateInfo
import org.tarish.adhan.data.AppUpdater
import org.tarish.adhan.data.UpdaterState
import org.tarish.adhan.data.local.SettingsStore
import org.tarish.adhan.domain.model.ThemeMode
import org.tarish.adhan.ui.util.isIgnoringBatteryOptimizations
import org.tarish.adhan.work.WorkScheduler
import javax.inject.Inject

enum class GlobalDialog { NONE, GENERAL_UPDATE, EXACT_ALARM, BATTERY }

@HiltViewModel
class RootViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
    private val updater: AppUpdater,
    private val scheduler: AlarmScheduler,
    private val coordinator: AlarmCoordinator,
    private val locationRepo: LocationRepository,
    private val workScheduler: WorkScheduler,
) : ViewModel() {

    /** null until DataStore has been read (splash stays up). */
    private val _startOnboarding = MutableStateFlow<Boolean?>(null)
    val startOnboarding = _startOnboarding.asStateFlow()

    val themeMode: StateFlow<ThemeMode?> = settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val update: StateFlow<UpdaterState> = updater.state

    private val _dialog = MutableStateFlow(GlobalDialog.NONE)
    val dialog = _dialog.asStateFlow()

    private var batteryCandidate = false

    init {
        viewModelScope.launch { _startOnboarding.value = !settings.onboardingDone.first() }
    }

    /** After first frame: one GitHub release check per cold start (§5.4). */
    fun checkUpdates() {
        viewModelScope.launch {
            val info = updater.check()
            if (info.type == UpdateInfo.Type.GENERAL && !generalShownThisProcess) {
                generalShownThisProcess = true
                show(GlobalDialog.GENERAL_UPDATE)
            }
        }
    }

    fun startUpdate() = updater.update()

    /** Re-check on resume so the force gate disappears after updating elsewhere. */
    fun onResume() {
        if (updater.state.value.info.type == UpdateInfo.Type.FORCE) checkUpdates()
    }

    /** Home first shown: queue exact-alarm prompt, then the battery prompt ≥ 2 s later (§8.1). */
    fun onHomeShown() {
        viewModelScope.launch {
            if (!settings.onboardingDone.first()) return@launch
            val anyEnabled = settings.prayerPrefs.first().values.any { it.enabled }
            if (!scheduler.canScheduleExact() && anyEnabled && !settings.exactAlarmPromptShown.first()) {
                show(GlobalDialog.EXACT_ALARM)
            }
            delay(2_000)
            if (!context.isIgnoringBatteryOptimizations() && !settings.batteryPromptShown.first()) {
                batteryCandidate = true
                show(GlobalDialog.BATTERY)
            }
        }
    }

    private fun show(d: GlobalDialog) {
        val cur = _dialog.value
        // Priority: General update > Exact alarm > Battery (force is a full screen).
        if (cur == GlobalDialog.NONE || d.ordinal < cur.ordinal) _dialog.value = d
    }

    fun dismissDialog() {
        val cur = _dialog.value
        viewModelScope.launch {
            when (cur) {
                GlobalDialog.EXACT_ALARM -> settings.setExactAlarmPromptShown()
                GlobalDialog.BATTERY -> settings.setBatteryPromptShown()
                else -> Unit
            }
            _dialog.value = GlobalDialog.NONE
            // A lower-priority prompt queued behind this one shows next.
            if (cur != GlobalDialog.BATTERY && batteryCandidate && !settings.batteryPromptShown.first() &&
                !context.isIgnoringBatteryOptimizations()
            ) {
                _dialog.value = GlobalDialog.BATTERY
            }
        }
    }

    fun setTheme(m: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(m) }
    }

    /** [reliabilityShown]: onboarding already covered exact alarms and battery, so Today won't prompt again. */
    fun finishOnboarding(reliabilityShown: Boolean) {
        viewModelScope.launch {
            settings.setOnboardingDone()
            if (reliabilityShown) {
                settings.setExactAlarmPromptShown()
                settings.setBatteryPromptShown()
            }
            if (locationRepo.hasPermission()) {
                locationRepo.refreshFromGps(force = true)
                workScheduler.syncNow()
            }
            coordinator.rescheduleAll("onboarding")
        }
    }

    companion object {
        @Volatile private var generalShownThisProcess = false
    }
}
