package com.ryan.pollenwitan.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryan.pollenwitan.data.repository.AirQualityRepository
import com.ryan.pollenwitan.data.repository.LocationRepository
import com.ryan.pollenwitan.data.repository.ProfileRepository
import com.ryan.pollenwitan.domain.model.ForecastDay
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
    data class Success(val days: List<ForecastDay>) : ForecastState
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

class ForecastViewModel(
    private val airQualityRepository: AirQualityRepository,
    private val profileRepository: ProfileRepository,
    private val locationRepository: LocationRepository
) : ViewModel() {

    private val _forecastState = MutableStateFlow<ForecastState>(ForecastState.Loading)
    private val _expandedDayIndex = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<ForecastUiState> = combine(
        _forecastState,
        profileRepository.getProfiles(),
        profileRepository.getSelectedProfileId(),
        _expandedDayIndex,
        locationRepository.getLocation()
    ) { forecast, profiles, selectedId, expandedIndex, location ->
        ForecastUiState(
            forecastState = forecast,
            profiles = profiles,
            selectedProfileId = selectedId,
            expandedDayIndex = expandedIndex,
            locationDisplayName = location.displayName
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ForecastUiState())

    init {
        refresh()
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
            _forecastState.value = ForecastState.Loading
            val location = locationRepository.getLocation().first()
            airQualityRepository.getForecast(
                location.latitude,
                location.longitude
            ).fold(
                onSuccess = { _forecastState.value = ForecastState.Success(it) },
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
