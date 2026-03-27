package com.ryan.pollenwitan.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ryan.pollenwitan.data.export.AppDataImporter
import com.ryan.pollenwitan.data.location.GpsLocationProvider
import com.ryan.pollenwitan.data.repository.LocationRepository
import com.ryan.pollenwitan.data.repository.ProfileRepository
import com.ryan.pollenwitan.R
import com.ryan.pollenwitan.domain.model.LocationMode
import com.ryan.pollenwitan.domain.model.PollenType
import com.ryan.pollenwitan.domain.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.InputStream
import java.util.UUID

enum class OnboardingStep { Welcome, Location, Profile, Done }
enum class LocationChoice { Default, Gps, Manual }

enum class OnboardingPath { KnownAllergies, DiscoveryMode, ImportBackup }

sealed interface ImportStatus {
    data object Idle : ImportStatus
    data object Importing : ImportStatus
    data class Success(val summary: String) : ImportStatus
    data class Error(val message: String) : ImportStatus
}

data class OnboardingUiState(
    val currentStep: OnboardingStep = OnboardingStep.Welcome,
    val onboardingPath: OnboardingPath = OnboardingPath.KnownAllergies,
    val locationChoice: LocationChoice = LocationChoice.Default,
    val manualLatitude: String = "",
    val manualLongitude: String = "",
    val manualDisplayName: String = "",
    val gpsStatus: GpsStatus = GpsStatus.Idle,
    val profileName: String = "",
    val hasAsthma: Boolean = false,
    val selectedAllergens: Set<PollenType> = emptySet(),
    val isSaving: Boolean = false,
    val isComplete: Boolean = false,
    val validationError: String? = null,
    val importStatus: ImportStatus = ImportStatus.Idle
)

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val locationRepository = LocationRepository(application)
    private val profileRepository = ProfileRepository(application)
    private val gpsLocationProvider = GpsLocationProvider(application)
    private val importer = AppDataImporter(application)

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun importData(inputStream: InputStream, onSuccess: () -> Unit) {
        _uiState.value = _uiState.value.copy(importStatus = ImportStatus.Importing)
        viewModelScope.launch {
            try {
                val summary = importer.import(inputStream)
                _uiState.value = _uiState.value.copy(importStatus = ImportStatus.Success(summary))
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    importStatus = ImportStatus.Error(e.message ?: "Unknown error")
                )
            }
        }
    }

    fun nextStep() {
        val state = _uiState.value
        when (state.currentStep) {
            OnboardingStep.Welcome -> {
                if (state.onboardingPath == OnboardingPath.ImportBackup) return
                _uiState.value = state.copy(currentStep = OnboardingStep.Location)
            }
            OnboardingStep.Location -> {
                _uiState.value = state.copy(currentStep = OnboardingStep.Profile)
            }
            OnboardingStep.Profile -> {
                finish()
            }
            OnboardingStep.Done -> {}
        }
    }

    fun previousStep() {
        val state = _uiState.value
        when (state.currentStep) {
            OnboardingStep.Location -> {
                _uiState.value = state.copy(currentStep = OnboardingStep.Welcome)
            }
            OnboardingStep.Profile -> {
                _uiState.value = state.copy(currentStep = OnboardingStep.Location, validationError = null)
            }
            else -> {}
        }
    }

    fun setLocationChoice(choice: LocationChoice) {
        _uiState.value = _uiState.value.copy(locationChoice = choice)
    }

    fun setManualLatitude(value: String) {
        _uiState.value = _uiState.value.copy(manualLatitude = value)
    }

    fun setManualLongitude(value: String) {
        _uiState.value = _uiState.value.copy(manualLongitude = value)
    }

    fun setManualDisplayName(value: String) {
        _uiState.value = _uiState.value.copy(manualDisplayName = value)
    }

    fun requestGpsFix() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(gpsStatus = GpsStatus.Requesting)
            val location = gpsLocationProvider.requestSingleUpdate()
            if (location != null) {
                locationRepository.updateGpsLocation(location.latitude, location.longitude, location.displayName)
                _uiState.value = _uiState.value.copy(
                    gpsStatus = GpsStatus.Success(
                        "%.4f, %.4f".format(location.latitude, location.longitude)
                    )
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    gpsStatus = GpsStatus.Error("Could not get location. Check GPS is enabled.")
                )
            }
        }
    }

    fun setProfileName(name: String) {
        _uiState.value = _uiState.value.copy(profileName = name, validationError = null)
    }

    fun setHasAsthma(value: Boolean) {
        _uiState.value = _uiState.value.copy(hasAsthma = value)
    }

    fun setOnboardingPath(path: OnboardingPath) {
        _uiState.value = _uiState.value.copy(
            onboardingPath = path,
            validationError = null,
            importStatus = ImportStatus.Idle
        )
    }

    fun toggleAllergen(type: PollenType) {
        val current = _uiState.value.selectedAllergens.toMutableSet()
        if (type in current) current.remove(type) else current.add(type)
        _uiState.value = _uiState.value.copy(selectedAllergens = current, validationError = null)
    }

    private fun finish() {
        val state = _uiState.value
        if (state.profileName.isBlank()) {
            _uiState.value = state.copy(validationError = getApplication<Application>().getString(R.string.validation_name_empty))
            return
        }
        val isDiscovery = state.onboardingPath == OnboardingPath.DiscoveryMode
        if (!isDiscovery && state.selectedAllergens.isEmpty()) {
            _uiState.value = state.copy(validationError = getApplication<Application>().getString(R.string.validation_select_allergen))
            return
        }

        _uiState.value = state.copy(isSaving = true, validationError = null)
        viewModelScope.launch {
            // Save location
            when (state.locationChoice) {
                LocationChoice.Manual -> {
                    val lat = state.manualLatitude.toDoubleOrNull()
                    val lon = state.manualLongitude.toDoubleOrNull()
                    if (lat != null && lon != null && state.manualDisplayName.isNotBlank()) {
                        locationRepository.setManualLocation(lat, lon, state.manualDisplayName.trim())
                    }
                    locationRepository.setLocationMode(LocationMode.Manual)
                }
                LocationChoice.Gps -> {
                    locationRepository.setLocationMode(LocationMode.Gps)
                }
                LocationChoice.Default -> {
                    // Keep Poznan defaults
                }
            }

            // Create profile with default thresholds
            val trackedAllergens = state.selectedAllergens.associateWith { type ->
                UserProfile.defaultThreshold(type)
            }
            val profile = UserProfile(
                id = UUID.randomUUID().toString(),
                displayName = state.profileName.trim(),
                trackedAllergens = trackedAllergens,
                hasAsthma = state.hasAsthma,
                discoveryMode = isDiscovery
            )
            profileRepository.addProfile(profile)

            _uiState.value = _uiState.value.copy(
                isSaving = false,
                isComplete = true,
                currentStep = OnboardingStep.Done
            )
        }
    }
}
