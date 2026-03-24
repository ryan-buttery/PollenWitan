package com.ryan.pollenwitan.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ryan.pollenwitan.data.location.GpsLocationProvider
import com.ryan.pollenwitan.data.repository.LocationRepository
import com.ryan.pollenwitan.data.repository.NotificationPrefs
import com.ryan.pollenwitan.data.repository.NotificationPrefsRepository
import com.ryan.pollenwitan.domain.model.LocationMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val locationMode: LocationMode = LocationMode.Manual,
    val manualLatitude: String = "",
    val manualLongitude: String = "",
    val manualDisplayName: String = "",
    val gpsStatus: GpsStatus = GpsStatus.Idle,
    val notificationPrefs: NotificationPrefs = NotificationPrefs()
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val locationRepository = LocationRepository(application)
    private val gpsLocationProvider = GpsLocationProvider(application)
    private val notificationPrefsRepository = NotificationPrefsRepository(application)

    private val _gpsStatus = MutableStateFlow<GpsStatus>(GpsStatus.Idle)

    val uiState: StateFlow<SettingsUiState> = combine(
        locationRepository.getLocationMode(),
        locationRepository.getLocation(),
        _gpsStatus,
        notificationPrefsRepository.getPrefs()
    ) { mode, location, gpsStatus, notifPrefs ->
        SettingsUiState(
            locationMode = mode,
            manualLatitude = if (mode == LocationMode.Manual) location.latitude.toString() else "",
            manualLongitude = if (mode == LocationMode.Manual) location.longitude.toString() else "",
            manualDisplayName = if (mode == LocationMode.Manual) location.displayName else "",
            gpsStatus = gpsStatus,
            notificationPrefs = notifPrefs
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setLocationMode(mode: LocationMode) {
        viewModelScope.launch {
            locationRepository.setLocationMode(mode)
        }
    }

    fun saveManualLocation(latitude: String, longitude: String, displayName: String) {
        val lat = latitude.toDoubleOrNull() ?: return
        val lon = longitude.toDoubleOrNull() ?: return
        if (displayName.isBlank()) return
        viewModelScope.launch {
            locationRepository.setManualLocation(lat, lon, displayName)
        }
    }

    fun requestGpsFix() {
        viewModelScope.launch {
            _gpsStatus.value = GpsStatus.Requesting
            val location = gpsLocationProvider.requestSingleUpdate()
            if (location != null) {
                locationRepository.updateGpsLocation(location.latitude, location.longitude, location.displayName)
                _gpsStatus.value = GpsStatus.Success(
                    "%.4f, %.4f".format(location.latitude, location.longitude)
                )
            } else {
                _gpsStatus.value = GpsStatus.Error("Could not get location. Check GPS is enabled.")
            }
        }
    }

    fun setMorningBriefingEnabled(enabled: Boolean) {
        viewModelScope.launch { notificationPrefsRepository.setMorningBriefingEnabled(enabled) }
    }

    fun setMorningBriefingHour(hour: Int) {
        viewModelScope.launch { notificationPrefsRepository.setMorningBriefingHour(hour) }
    }

    fun setThresholdAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch { notificationPrefsRepository.setThresholdAlertsEnabled(enabled) }
    }

    fun setCompoundRiskAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch { notificationPrefsRepository.setCompoundRiskAlertsEnabled(enabled) }
    }
}
