package com.ryan.pollenwitan.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ryan.pollenwitan.data.repository.AirQualityRepository
import com.ryan.pollenwitan.data.repository.LocationRepository
import com.ryan.pollenwitan.data.repository.ProfileRepository
import com.ryan.pollenwitan.data.repository.SymptomDiaryRepository
import com.ryan.pollenwitan.domain.model.DefaultSymptom
import com.ryan.pollenwitan.domain.model.SymptomDiaryEntry
import com.ryan.pollenwitan.domain.model.SymptomRating
import com.ryan.pollenwitan.domain.model.TrackedSymptom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.temporal.ChronoUnit
import java.time.LocalDate

data class SymptomCheckInUiState(
    val trackedSymptoms: List<TrackedSymptom> = emptyList(),
    val ratings: Map<String, Int> = emptyMap(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val savedSuccessfully: Boolean = false,
    val profileName: String = "",
    val targetDate: LocalDate = LocalDate.now(),
    val isBackfill: Boolean = false
)

class SymptomCheckInViewModel(application: Application) : AndroidViewModel(application) {

    private val profileRepository = ProfileRepository(application)
    private val symptomDiaryRepository = SymptomDiaryRepository(application)
    private val airQualityRepository = AirQualityRepository(application)
    private val locationRepository = LocationRepository(application)

    private var targetDate: LocalDate = LocalDate.now()

    private val _uiState = MutableStateFlow(SymptomCheckInUiState())
    val uiState: StateFlow<SymptomCheckInUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun setTargetDate(dateString: String?) {
        val date = dateString?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
        if (date == targetDate) return
        targetDate = date
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val profiles = profileRepository.getProfiles().first()
            val selectedId = profileRepository.getSelectedProfileId().first()
            val profile = profiles.find { it.id == selectedId } ?: profiles.firstOrNull() ?: return@launch

            val symptoms = profile.trackedSymptoms
            val existing = symptomDiaryRepository.getEntryForDate(profile.id, targetDate)

            val ratings = if (existing != null) {
                existing.ratings.associate { it.symptomId to it.severity }
            } else {
                symptoms.associate { it.id to 0 }
            }

            _uiState.value = SymptomCheckInUiState(
                trackedSymptoms = symptoms,
                ratings = ratings,
                isLoading = false,
                profileName = profile.displayName,
                targetDate = targetDate,
                isBackfill = targetDate != LocalDate.now()
            )
        }
    }

    fun setRating(symptomId: String, severity: Int) {
        val current = _uiState.value
        _uiState.value = current.copy(
            ratings = current.ratings + (symptomId to severity)
        )
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving) return

        _uiState.value = state.copy(isSaving = true)
        viewModelScope.launch {
            val profiles = profileRepository.getProfiles().first()
            val selectedId = profileRepository.getSelectedProfileId().first()
            val profile = profiles.find { it.id == selectedId } ?: profiles.firstOrNull() ?: return@launch
            val globalLocation = locationRepository.getLocation().first()
            val location = ProfileRepository.resolveLocation(profile, globalLocation)

            // Fetch environmental peaks — available for today and up to 16 days back
            var peakPollenJson = "{}"
            var peakAqi = 0
            var peakPm25 = 0.0
            var peakPm10 = 0.0

            val daysBetween = ChronoUnit.DAYS.between(targetDate, LocalDate.now())
            when {
                daysBetween == 0L -> {
                    val forecastResult = airQualityRepository.getForecast(location.latitude, location.longitude, days = 1)
                    forecastResult.getOrNull()?.firstOrNull()?.let { today ->
                        val pollenMap = today.peakPollenReadings.associate { it.type.name to it.value }
                        peakPollenJson = Json.encodeToString(pollenMap)
                        peakAqi = today.peakAqi
                    }
                    val conditionsResult = airQualityRepository.getCurrentConditions(location.latitude, location.longitude)
                    conditionsResult.getOrNull()?.let { conditions ->
                        peakPm25 = conditions.pm25
                        peakPm10 = conditions.pm10
                    }
                }
                daysBetween in 1..16 -> {
                    airQualityRepository.getHistoricalDayPeaks(
                        location.latitude, location.longitude, targetDate
                    ).getOrNull()?.let { day ->
                        val pollenMap = day.peakPollenReadings.associate { it.type.name to it.value }
                        peakPollenJson = Json.encodeToString(pollenMap)
                        peakAqi = day.peakAqi
                        peakPm25 = day.hourlyReadings.maxOfOrNull { it.pm25 } ?: 0.0
                        peakPm10 = day.hourlyReadings.maxOfOrNull { it.pm10 } ?: 0.0
                    }
                }
                // >16 days: leave defaults (no historical data available)
            }

            val displayNameLookup = buildDisplayNameLookup(state.trackedSymptoms)
            val ratings = state.ratings.map { (symptomId, severity) ->
                SymptomRating(
                    symptomId = symptomId,
                    symptomName = displayNameLookup[symptomId] ?: symptomId,
                    severity = severity
                )
            }

            val entry = SymptomDiaryEntry(
                profileId = profile.id,
                date = targetDate,
                ratings = ratings,
                loggedAtMillis = System.currentTimeMillis(),
                peakPollenJson = peakPollenJson,
                peakAqi = peakAqi,
                peakPm25 = peakPm25,
                peakPm10 = peakPm10
            )

            symptomDiaryRepository.logEntry(entry)
            _uiState.value = _uiState.value.copy(isSaving = false, savedSuccessfully = true)
        }
    }

    private fun buildDisplayNameLookup(symptoms: List<TrackedSymptom>): Map<String, String> {
        val context = getApplication<Application>()
        return symptoms.associate { symptom ->
            val name = if (symptom.isDefault) {
                val default = DefaultSymptom.entries.find { it.name == symptom.id }
                default?.let {
                    when (it) {
                        DefaultSymptom.Sneezing -> context.getString(com.ryan.pollenwitan.R.string.symptom_sneezing)
                        DefaultSymptom.ItchyWateryEyes -> context.getString(com.ryan.pollenwitan.R.string.symptom_itchy_watery_eyes)
                        DefaultSymptom.NasalCongestion -> context.getString(com.ryan.pollenwitan.R.string.symptom_nasal_congestion)
                        DefaultSymptom.RunnyNose -> context.getString(com.ryan.pollenwitan.R.string.symptom_runny_nose)
                        DefaultSymptom.ItchyThroat -> context.getString(com.ryan.pollenwitan.R.string.symptom_itchy_throat)
                    }
                } ?: symptom.displayName
            } else {
                symptom.displayName
            }
            symptom.id to name
        }
    }
}
