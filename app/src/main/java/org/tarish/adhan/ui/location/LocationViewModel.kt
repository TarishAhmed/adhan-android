package org.tarish.adhan.ui.location

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import org.tarish.adhan.core.result.AppError
import org.tarish.adhan.core.result.appError
import org.tarish.adhan.data.LocationRepository
import org.tarish.adhan.data.PlaceResult
import org.tarish.adhan.data.local.SettingsStore
import org.tarish.adhan.domain.model.StoredLocation
import javax.inject.Inject

sealed interface SearchState {
    data object Idle : SearchState
    data object Searching : SearchState
    data class Results(val items: List<PlaceResult>) : SearchState
    data class Failed(val error: AppError) : SearchState
}

sealed interface LocationEvent {
    data class Selected(val name: String) : LocationEvent
    data class Error(val error: AppError) : LocationEvent
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class LocationViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val locationRepo: LocationRepository,
) : ViewModel() {

    val location: StateFlow<StoredLocation?> = settings.location.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val autoLocation: StateFlow<Boolean> = settings.autoLocation.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val _gpsBusy = MutableStateFlow(false)
    val gpsBusy = _gpsBusy.asStateFlow()
    private val _gpsError = MutableStateFlow<AppError?>(null)
    val gpsError = _gpsError.asStateFlow()

    val query = MutableStateFlow("")
    val geocoderAvailable = locationRepo.geocoderAvailable()

    private val events = Channel<LocationEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    /** Debounce 400 ms, min 2 chars, cancel previous (§8.7). */
    val search: StateFlow<SearchState> = query
        .map { it.trim() }
        .distinctUntilChanged()
        .debounce(400)
        .transformLatest { q ->
            if (q.length < 2) { emit(SearchState.Idle); return@transformLatest }
            emit(SearchState.Searching)
            val r = locationRepo.search(q)
            emit(r.fold({ SearchState.Results(it) }, { SearchState.Failed(r.appError ?: AppError.NoNetwork) }))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchState.Idle)

    /** Fresh GPS fix, no throttle (BUG-07). */
    fun useCurrentLocation() {
        if (_gpsBusy.value) return
        viewModelScope.launch {
            settings.setAutoLocation(true)
            _gpsBusy.value = true
            val r = locationRepo.refreshFromGps(force = true)
            _gpsBusy.value = false
            _gpsError.value = r.appError
            r.appError?.let { events.send(LocationEvent.Error(it)) }
        }
    }

    fun setAuto(on: Boolean) {
        if (on) useCurrentLocation()
        else viewModelScope.launch {
            settings.setAutoLocation(false)
            location.value?.let { settings.setLocation(it.copy(source = StoredLocation.Source.MANUAL)) }
        }
    }

    fun select(place: PlaceResult) {
        viewModelScope.launch {
            val loc = locationRepo.setManual(place)
            query.value = ""
            events.send(LocationEvent.Selected(loc.displayName))
        }
    }

    suspend fun settingsResolution() = locationRepo.settingsResolution()
}
