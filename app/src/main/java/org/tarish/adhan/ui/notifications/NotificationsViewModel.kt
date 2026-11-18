package org.tarish.adhan.ui.notifications

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.tarish.adhan.alarm.AlarmCoordinator
import org.tarish.adhan.alarm.AlarmScheduler
import org.tarish.adhan.alarm.NotificationChannels
import org.tarish.adhan.core.time.Clock
import org.tarish.adhan.data.DownloadState
import org.tarish.adhan.data.PrayerTimesRepository
import org.tarish.adhan.data.SoundRepository
import org.tarish.adhan.data.local.SettingsStore
import org.tarish.adhan.domain.model.AdhanSound
import org.tarish.adhan.domain.model.PrayerDay
import org.tarish.adhan.domain.model.PrayerName
import org.tarish.adhan.domain.model.PrayerNotificationPrefs
import org.tarish.adhan.ui.util.isIgnoringBatteryOptimizations
import org.tarish.adhan.work.WorkScheduler
import java.time.DayOfWeek
import java.time.ZoneId
import javax.inject.Inject

data class PermissionState(
    val notificationsEnabled: Boolean = true,
    val exactAllowed: Boolean = true,
    val batteryOk: Boolean = true,
    val blockedChannels: List<PrayerName> = emptyList(),
)

sealed interface NotifEvent {
    data class DownloadFailed(val soundName: String) : NotifEvent
    data object TestScheduled : NotifEvent
    data object KeepOneDay : NotifEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotificationsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
    private val prayerRepo: PrayerTimesRepository,
    private val sounds: SoundRepository,
    private val scheduler: AlarmScheduler,
    private val coordinator: AlarmCoordinator,
    private val workScheduler: WorkScheduler,
    private val clock: Clock,
) : ViewModel() {

    val prefs: StateFlow<Map<PrayerName, PrayerNotificationPrefs>> =
        settings.prayerPrefs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Today's times — optional, never blocks the list (BUG-20). */
    val today: StateFlow<Pair<PrayerDay, ZoneId>?> = settings.location.flatMapLatest { loc ->
        if (loc == null) flowOf(null) else {
            val zone = loc.zone ?: clock.deviceZone()
            val d = clock.now().atZone(zone).toLocalDate()
            prayerRepo.observeDays(loc.key, d, d).map { it.firstOrNull()?.let { day -> day to zone } }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _permissions = MutableStateFlow(PermissionState())
    val permissions = _permissions.asStateFlow()

    private val _downloads = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloads = _downloads.asStateFlow()

    private val events = Channel<NotifEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    val catalog: List<AdhanSound> = sounds.catalog
    fun isDownloaded(id: String) = sounds.isAvailable(id)
    fun resolve(id: String) = sounds.resolve(id)

    fun refreshPermissions() {
        val before = _permissions.value.notificationsEnabled
        val now = PermissionState(
            notificationsEnabled = scheduler.notificationsAllowed(),
            exactAllowed = scheduler.canScheduleExact(),
            batteryOk = context.isIgnoringBatteryOptimizations(),
            blockedChannels = NotificationChannels.blockedPrayers(context),
        )
        _permissions.value = now
        if (!before && now.notificationsEnabled) coordinator.rescheduleAll("permission")
    }

    fun setEnabled(p: PrayerName, v: Boolean) = update(p) { it.copy(enabled = v) }

    fun toggleDay(p: PrayerName, day: DayOfWeek) {
        val current = prefs.value[p]?.days ?: DayOfWeek.entries.toSet()
        if (day in current && current.size == 1) {
            viewModelScope.launch { events.send(NotifEvent.KeepOneDay) }; return
        }
        update(p) { it.copy(days = if (day in current) current - day else current + day) }
    }

    fun setAdvance(p: PrayerName, minutes: Int) = update(p) { it.copy(advanceMinutes = minutes.coerceIn(0, 30)) }

    /** Persist → ensure file (download if needed). Preview is handled by the UI once [downloads] reports Done. */
    fun selectSound(p: PrayerName, id: String) {
        update(p) { it.copy(soundId = id) }
        if (sounds.isAvailable(id)) return
        viewModelScope.launch {
            sounds.download(id).collect { st ->
                _downloads.update { it + (id to st) }
                if (st is DownloadState.Failed) {
                    workScheduler.downloadSound(id)
                    events.send(NotifEvent.DownloadFailed(AdhanSound.byId(id)?.displayName ?: id))
                }
            }
        }
    }

    fun testAdhan(p: PrayerName) {
        scheduler.scheduleTest(p, 5)
        viewModelScope.launch { events.send(NotifEvent.TestScheduled) }
    }

    private fun update(p: PrayerName, t: (PrayerNotificationPrefs) -> PrayerNotificationPrefs) {
        viewModelScope.launch { settings.updatePrayerPrefs(p, t) }
    }
}
