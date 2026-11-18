package org.tarish.adhan.ui.qibla

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.tarish.adhan.data.LocationRepository
import org.tarish.adhan.data.local.SettingsStore
import org.tarish.adhan.domain.QiblaMath
import org.tarish.adhan.domain.model.StoredLocation
import javax.inject.Inject

data class QiblaInfo(val lat: Double, val lng: Double, val bearing: Double, val distanceKm: Double)

@HiltViewModel
class QiblaViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val locationRepo: LocationRepository,
) : ViewModel() {

    val info: StateFlow<QiblaInfo?> = settings.location.map { it?.toInfo() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            if (settings.autoLocation.first() && locationRepo.hasPermission()) locationRepo.refreshFromGps(force = true)
            _refreshing.value = false
        }
    }

    private fun StoredLocation.toInfo(): QiblaInfo? =
        if (!QiblaMath.isValid(lat, lng)) null
        else QiblaInfo(lat, lng, QiblaMath.qiblaBearing(lat, lng), QiblaMath.distanceKm(lat, lng))
}
