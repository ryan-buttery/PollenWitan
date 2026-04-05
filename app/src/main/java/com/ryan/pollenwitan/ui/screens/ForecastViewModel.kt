package com.ryan.pollenwitan.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ryan.pollenwitan.data.repository.AirQualityRepository
import com.ryan.pollenwitan.data.repository.LocationRepository
import com.ryan.pollenwitan.data.repository.ProfileRepository
import com.ryan.pollenwitan.domain.model.ForecastDay
import com.ryan.pollenwitan.domain.model.ForecastResult
import com.ryan.pollenwitan.domain.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ForecastState {
    data object Loading : ForecastState
    data class Success(val days: List<ForecastDay>, val fetchedAtMillis: Long) : ForecastState
    data class Error(val message: String) : ForecastState
}

data class ForecastUiState(
    val forecastState: ForecastState = ForecastState.Loading,
    val profiles: List<UserProfile> = emptyList(),
    val selectedProfileId: String = "",
    val expandedDayIndex: Int? = null,
    val locationDisplayName: String = ""
) {
    val selectedProfile: UserProfile?
        get() = profiles.find { it.id == selectedProfileId }
}

class ForecastViewModel(application: Application) : AndroidViewModel(application) {

    private val airQualityRepository = AirQualityRepository(application)
    private val profileRepository = ProfileRepository(application)
    private val locationRepository = LocationRepository(application)

    private val _forecastState = MutableStateFlow<ForecastState>(ForecastState.Loading)
    private val _expandedDayIndex = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<ForecastUiState> = combine(
        _forecastState,
        profileRepository.getProfiles(),
        profileRepository.getSelectedProfileId(),
        _expandedDayIndex,
        locationRepository.getLocation()
    ) { forecast, profiles, selectedId, expandedIndex, globalLocation ->
        val selectedProfile = profiles.find { it.id == selectedId }
        val effectiveLocation = ProfileRepository.resolveLocation(selectedProfile, globalLocation)
        ForecastUiState(
            forecastState = forecast,
            profiles = profiles,
            selectedProfileId = selectedId,
            expandedDayIndex = expandedIndex,
            locationDisplayName = effectiveLocation.displayName
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ForecastUiState())

    init {
        refresh()
        viewModelScope.launch {
            var previousLat: Double? = null
            var previousLon: Double? = null
            combine(
                locationRepository.getLocation(),
                profileRepository.getProfiles(),
                profileRepository.getSelectedProfileId()
            ) { globalLocation, profiles, selectedId ->
                val selectedProfile = profiles.find { it.id == selectedId }
                ProfileRepository.resolveLocation(selectedProfile, globalLocation)
            }.collect { location ->
                if (previousLat != null &&
                    (previousLat != location.latitude || previousLon != location.longitude)
                ) {
                    refresh()
                }
                previousLat = location.latitude
                previousLon = location.longitude
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _forecastState.value = ForecastState.Loading
            val globalLocation = locationRepository.getLocation().first()
            val profiles = profileRepository.getProfiles().first()
            val selectedId = profileRepository.getSelectedProfileId().first()
            val selectedProfile = profiles.find { it.id == selectedId }
            val location = ProfileRepository.resolveLocation(selectedProfile, globalLocation)
            airQualityRepository.getForecastWithTimestamp(
                location.latitude,
                location.longitude
            ).fold(
                onSuccess = { _forecastState.value = ForecastState.Success(it.days, it.fetchedAtMillis) },
                onFailure = { _forecastState.value = ForecastState.Error(it.message ?: "Unknown error") }
            )
        }
    }

    fun selectProfile(profileId: String) {
        viewModelScope.launch {
            profileRepository.selectProfile(profileId)
        }
    }

    fun toggleDay(index: Int) {
        _expandedDayIndex.value = if (_expandedDayIndex.value == index) null else index
    }
}
