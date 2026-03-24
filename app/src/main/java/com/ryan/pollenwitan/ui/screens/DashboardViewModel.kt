package com.ryan.pollenwitan.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ryan.pollenwitan.data.repository.AirQualityRepository
import com.ryan.pollenwitan.data.repository.LocationRepository
import com.ryan.pollenwitan.data.repository.ProfileRepository
import com.ryan.pollenwitan.domain.model.CurrentConditions
import com.ryan.pollenwitan.domain.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface WeatherState {
    data object Loading : WeatherState
    data class Success(val conditions: CurrentConditions) : WeatherState
    data class Error(val message: String) : WeatherState
}

data class DashboardUiState(
    val weatherState: WeatherState = WeatherState.Loading,
    val profiles: List<UserProfile> = emptyList(),
    val selectedProfileId: String = "",
    val locationDisplayName: String = ""
) {
    val selectedProfile: UserProfile?
        get() = profiles.find { it.id == selectedProfileId }
}

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val airQualityRepository = AirQualityRepository(application)
    private val profileRepository = ProfileRepository(application)
    private val locationRepository = LocationRepository(application)

    private val _weatherState = MutableStateFlow<WeatherState>(WeatherState.Loading)

    val uiState: StateFlow<DashboardUiState> = combine(
        _weatherState,
        profileRepository.getProfiles(),
        profileRepository.getSelectedProfileId(),
        locationRepository.getLocation()
    ) { weather, profiles, selectedId, location ->
        DashboardUiState(
            weatherState = weather,
            profiles = profiles,
            selectedProfileId = selectedId,
            locationDisplayName = location.displayName
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    init {
        refresh()
        // Re-fetch when location changes
        viewModelScope.launch {
            var previousLocation: com.ryan.pollenwitan.domain.model.AppLocation? = null
            locationRepository.getLocation().collect { location ->
                if (previousLocation != null &&
                    (previousLocation!!.latitude != location.latitude || previousLocation!!.longitude != location.longitude)
                ) {
                    refresh()
                }
                previousLocation = location
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _weatherState.value = WeatherState.Loading
            val location = locationRepository.getLocation().first()
            airQualityRepository.getCurrentConditions(
                location.latitude,
                location.longitude
            ).fold(
                onSuccess = { _weatherState.value = WeatherState.Success(it) },
                onFailure = { _weatherState.value = WeatherState.Error(it.message ?: "Unknown error") }
            )
        }
    }

    fun selectProfile(profileId: String) {
        viewModelScope.launch {
            profileRepository.selectProfile(profileId)
        }
    }
}
