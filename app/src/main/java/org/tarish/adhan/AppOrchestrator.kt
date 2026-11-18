package org.tarish.adhan

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tarish.adhan.alarm.AlarmCoordinator
import org.tarish.adhan.core.di.ApplicationScope
import org.tarish.adhan.data.LocationRepository
import org.tarish.adhan.data.PrayerTimesRepository
import org.tarish.adhan.data.local.SettingsStore
import org.tarish.adhan.widget.WidgetUpdater
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide reactions (§3.3, §6.3 rule 5, §9.2, §12.3): location change → coverage + reschedule + widget;
 * prefs change / new month → reschedule; foreground → throttled GPS refresh and a per-minute widget tick.
 */
@Singleton
class AppOrchestrator @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val settings: SettingsStore,
    private val prayerRepo: PrayerTimesRepository,
    private val locationRepo: LocationRepository,
    private val coordinator: AlarmCoordinator,
    private val widgetUpdater: WidgetUpdater,
) {
    private var foregroundJob: Job? = null

    fun start() {
        scope.launch {
            settings.location.map { it?.key }.distinctUntilChanged().collect { key ->
                val loc = settings.location.first()
                if (key != null && loc != null) prayerRepo.ensureCoverage(loc)
                coordinator.rescheduleAll("location")
                widgetUpdater.updateAll()
            }
        }
        scope.launch {
            settings.prayerPrefs.drop(1).collect { coordinator.rescheduleAll("prefs") }
        }
        scope.launch {
            prayerRepo.monthStored.collect {
                coordinator.rescheduleAll("data")
                widgetUpdater.updateAll()
            }
        }
        coordinator.rescheduleAll("app_start")

        scope.launch(Dispatchers.Main) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = onForeground()
                override fun onStop(owner: LifecycleOwner) { foregroundJob?.cancel() }
            })
        }
    }

    private fun onForeground() {
        foregroundJob?.cancel()
        foregroundJob = scope.launch {
            if (settings.onboardingDone.first() && settings.autoLocation.first() && locationRepo.hasPermission()) {
                locationRepo.refreshFromGps(force = false)
            }
            while (isActive) {
                widgetUpdater.updateAll()
                delay(60_000)
            }
        }
    }
}
