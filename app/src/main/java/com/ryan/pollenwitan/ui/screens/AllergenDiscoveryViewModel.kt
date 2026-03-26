package com.ryan.pollenwitan.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ryan.pollenwitan.data.repository.DoseTrackingRepository
import com.ryan.pollenwitan.data.repository.ProfileRepository
import com.ryan.pollenwitan.data.repository.SymptomDiaryRepository
import com.ryan.pollenwitan.domain.calibration.AllergenDiscoveryEngine
import com.ryan.pollenwitan.domain.calibration.DiscoveryAnalysis
import com.ryan.pollenwitan.domain.calibration.DiscoveryDataPoint
import com.ryan.pollenwitan.domain.model.PollenType
import com.ryan.pollenwitan.domain.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.LocalDate

data class AllergenDiscoveryUiState(
    val isLoading: Boolean = true,
    val profileName: String = "",
    val analysis: DiscoveryAnalysis? = null,
    val addedAllergens: Set<PollenType> = emptySet(),
    val dismissedAllergens: Set<PollenType> = emptySet()
)

class AllergenDiscoveryViewModel(application: Application) : AndroidViewModel(application) {

    private val profileRepository = ProfileRepository(application)
    private val symptomDiaryRepository = SymptomDiaryRepository(application)
    private val doseTrackingRepository = DoseTrackingRepository(application)
    private val engine = AllergenDiscoveryEngine()
    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(AllergenDiscoveryUiState())
    val uiState: StateFlow<AllergenDiscoveryUiState> = _uiState

    private var currentProfile: UserProfile? = null

    fun loadProfile(profileId: String) {
        viewModelScope.launch {
            _uiState.value = AllergenDiscoveryUiState(isLoading = true)

            val profiles = profileRepository.getProfiles().first()
            val profile = profiles.find { it.id == profileId }
            if (profile == null) {
                _uiState.value = AllergenDiscoveryUiState(isLoading = false)
                return@launch
            }
            currentProfile = profile

            val endDate = LocalDate.now()
            val startDate = endDate.minusDays(90)

            val entries = symptomDiaryRepository.getHistory(profile.id, startDate, endDate).first()
            val doseHistory = doseTrackingRepository.getHistoryForDateRange(
                profile.id, startDate, endDate
            ).first()

            val dosesByDate = doseHistory.groupBy { it.date }
            val expectedDosesPerDay = profile.medicineAssignments.sumOf { it.reminderHours.size }

            val dataPoints = entries.mapNotNull { entry ->
                val pollenLevels = try {
                    json.decodeFromString<Map<String, Double>>(entry.peakPollenJson)
                } catch (_: Exception) {
                    emptyMap()
                }

                if (entry.ratings.isEmpty()) return@mapNotNull null

                val concentrations = PollenType.entries.associateWith { type ->
                    pollenLevels[type.name] ?: 0.0
                }

                val compositeScore = entry.ratings.map { it.severity }.average()
                val aqiConfounded = entry.peakAqi >= 60

                val doses = dosesByDate[entry.date.toString()] ?: emptyList()
                val medicationAdherence = if (expectedDosesPerDay > 0) {
                    doses.size.toDouble() / expectedDosesPerDay
                } else 0.0

                DiscoveryDataPoint(
                    date = entry.date,
                    pollenConcentrations = concentrations,
                    compositeSymptomScore = compositeScore,
                    aqiConfounded = aqiConfounded,
                    medicationAdherence = medicationAdherence
                )
            }

            val analysis = engine.analyze(dataPoints)

            _uiState.value = AllergenDiscoveryUiState(
                isLoading = false,
                profileName = profile.displayName,
                analysis = analysis
            )
        }
    }

    fun addAllergen(pollenType: PollenType) {
        val profile = currentProfile ?: return
        val updatedAllergens = profile.trackedAllergens.toMutableMap()
        updatedAllergens[pollenType] = UserProfile.defaultThreshold(pollenType)
        val updatedProfile = profile.copy(trackedAllergens = updatedAllergens)

        viewModelScope.launch {
            profileRepository.updateProfile(updatedProfile)
            currentProfile = updatedProfile
            _uiState.value = _uiState.value.copy(
                addedAllergens = _uiState.value.addedAllergens + pollenType
            )
        }
    }

    fun dismissAllergen(pollenType: PollenType) {
        _uiState.value = _uiState.value.copy(
            dismissedAllergens = _uiState.value.dismissedAllergens + pollenType
        )
    }

    fun exitDiscoveryMode() {
        val profile = currentProfile ?: return
        val updatedProfile = profile.copy(discoveryMode = false)
        viewModelScope.launch {
            profileRepository.updateProfile(updatedProfile)
            currentProfile = updatedProfile
        }
    }
}
