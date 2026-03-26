package com.ryan.pollenwitan.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ryan.pollenwitan.data.repository.DoseTrackingRepository
import com.ryan.pollenwitan.data.repository.ProfileRepository
import com.ryan.pollenwitan.data.repository.SymptomDiaryRepository
import com.ryan.pollenwitan.domain.calibration.CalibrationDataPoint
import com.ryan.pollenwitan.domain.calibration.CalibrationResult
import com.ryan.pollenwitan.domain.calibration.ThresholdCalibrationEngine
import com.ryan.pollenwitan.domain.model.PollenType
import com.ryan.pollenwitan.domain.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.LocalDate

data class CalibrationUiState(
    val isLoading: Boolean = true,
    val profileName: String = "",
    val results: List<CalibrationResult> = emptyList(),
    val dismissedSuggestions: Set<Pair<PollenType, String>> = emptySet(),
    val acceptedSuggestions: Set<Pair<PollenType, String>> = emptySet()
) {
    val hasAnySuggestions: Boolean
        get() = results.any { result ->
            result.suggestions.any { suggestion ->
                val key = result.pollenType to suggestion.level
                key !in dismissedSuggestions && key !in acceptedSuggestions
            }
        }
}

class ThresholdCalibrationViewModel(application: Application) : AndroidViewModel(application) {

    private val profileRepository = ProfileRepository(application)
    private val symptomDiaryRepository = SymptomDiaryRepository(application)
    private val doseTrackingRepository = DoseTrackingRepository(application)
    private val engine = ThresholdCalibrationEngine()
    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(CalibrationUiState())
    val uiState: StateFlow<CalibrationUiState> = _uiState

    private var currentProfile: UserProfile? = null

    fun loadProfile(profileId: String) {
        viewModelScope.launch {
            _uiState.value = CalibrationUiState(isLoading = true)

            val profiles = profileRepository.getProfiles().first()
            val profile = profiles.find { it.id == profileId }
            if (profile == null) {
                _uiState.value = CalibrationUiState(isLoading = false)
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

            val trackedAllergenNames = profile.trackedAllergens.keys.map { it.name }.toSet()

            val results = profile.trackedAllergens.map { (pollenType, threshold) ->
                val dataPoints = entries.mapNotNull { entry ->
                    val pollenLevels = try {
                        json.decodeFromString<Map<String, Double>>(entry.peakPollenJson)
                    } catch (_: Exception) {
                        emptyMap()
                    }

                    val concentration = pollenLevels[pollenType.name] ?: return@mapNotNull null
                    if (concentration <= 0.0) return@mapNotNull null

                    if (entry.ratings.isEmpty()) return@mapNotNull null

                    val compositeScore = entry.ratings.map { it.severity }.average()

                    val activeTrackedPollens = pollenLevels.count { (name, value) ->
                        name in trackedAllergenNames && value > 0.0
                    }
                    val multiPollenWeight = if (activeTrackedPollens > 1) 0.5 else 1.0

                    val aqiConfounded = entry.peakAqi >= 60

                    val doses = dosesByDate[entry.date.toString()] ?: emptyList()
                    val medicationAdherence = if (expectedDosesPerDay > 0) {
                        doses.size.toDouble() / expectedDosesPerDay
                    } else 0.0

                    CalibrationDataPoint(
                        date = entry.date,
                        pollenConcentration = concentration,
                        compositeSymptomScore = compositeScore,
                        aqiConfounded = aqiConfounded,
                        multiPollenWeight = multiPollenWeight,
                        medicationAdherence = medicationAdherence
                    )
                }

                engine.calibrate(pollenType, threshold, dataPoints)
            }

            _uiState.value = CalibrationUiState(
                isLoading = false,
                profileName = profile.displayName,
                results = results
            )
        }
    }

    fun acceptSuggestion(pollenType: PollenType, level: String) {
        val profile = currentProfile ?: return
        val result = _uiState.value.results.find { it.pollenType == pollenType } ?: return
        val suggestion = result.suggestions.find { it.level == level } ?: return

        val currentThreshold = profile.trackedAllergens[pollenType] ?: return
        val updatedThreshold = when (level) {
            "moderate" -> currentThreshold.copy(moderate = suggestion.suggestedValue)
            "high" -> currentThreshold.copy(high = suggestion.suggestedValue)
            "veryHigh" -> currentThreshold.copy(veryHigh = suggestion.suggestedValue)
            else -> return
        }

        val updatedAllergens = profile.trackedAllergens.toMutableMap()
        updatedAllergens[pollenType] = updatedThreshold
        val updatedProfile = profile.copy(trackedAllergens = updatedAllergens)

        viewModelScope.launch {
            profileRepository.updateProfile(updatedProfile)
            currentProfile = updatedProfile
            _uiState.value = _uiState.value.copy(
                acceptedSuggestions = _uiState.value.acceptedSuggestions + (pollenType to level)
            )
        }
    }

    fun dismissSuggestion(pollenType: PollenType, level: String) {
        _uiState.value = _uiState.value.copy(
            dismissedSuggestions = _uiState.value.dismissedSuggestions + (pollenType to level)
        )
    }
}
