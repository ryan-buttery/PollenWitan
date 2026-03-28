package com.ryan.pollenwitan.ui.screens

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ryan.pollenwitan.R
import com.ryan.pollenwitan.data.repository.DoseTrackingRepository
import com.ryan.pollenwitan.data.repository.ProfileRepository
import com.ryan.pollenwitan.data.repository.SymptomDiaryRepository
import com.ryan.pollenwitan.domain.model.UserProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.LocalDate

enum class TrendRange(val days: Long, @StringRes val labelRes: Int) {
    WEEK(7, R.string.trends_range_week),
    MONTH(30, R.string.trends_range_month),
    QUARTER(90, R.string.trends_range_quarter)
}

data class DaySnapshot(
    val date: LocalDate,
    val symptomRatings: Map<String, Int> = emptyMap(),
    val pollenLevels: Map<String, Double> = emptyMap(),
    val peakAqi: Int = 0,
    val peakPm25: Double = 0.0,
    val peakPm10: Double = 0.0,
    val dosesConfirmed: Int = 0,
    val dosesExpected: Int = 0
)

data class SymptomTrendsUiState(
    val profiles: List<UserProfile> = emptyList(),
    val selectedProfileId: String = "",
    val range: TrendRange = TrendRange.MONTH,
    val snapshots: List<DaySnapshot> = emptyList(),
    val selectedDay: DaySnapshot? = null,
    val symptomNames: List<String> = emptyList(),
    val allergenNames: List<String> = emptyList()
) {
    val selectedProfile: UserProfile?
        get() = profiles.find { it.id == selectedProfileId }
}

class SymptomTrendsViewModel(application: Application) : AndroidViewModel(application) {

    private val profileRepository = ProfileRepository(application)
    private val symptomDiaryRepository = SymptomDiaryRepository(application)
    private val doseTrackingRepository = DoseTrackingRepository(application)
    private val json = Json { ignoreUnknownKeys = true }

    private val _range = MutableStateFlow(TrendRange.MONTH)
    private val _selectedDay = MutableStateFlow<LocalDate?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SymptomTrendsUiState> = combine(
        profileRepository.getProfiles(),
        profileRepository.getSelectedProfileId(),
        _range,
        _selectedDay
    ) { profiles, selectedId, range, selectedDay ->
        data class Params(
            val profiles: List<UserProfile>,
            val selectedId: String,
            val range: TrendRange,
            val selectedDay: LocalDate?
        )
        Params(profiles, selectedId, range, selectedDay)
    }.flatMapLatest { params ->
        val profile = params.profiles.find { it.id == params.selectedId }
        if (profile == null) {
            flowOf(
                SymptomTrendsUiState(
                    profiles = params.profiles,
                    selectedProfileId = params.selectedId,
                    range = params.range
                )
            )
        } else {
            val endDate = LocalDate.now()
            val startDate = endDate.minusDays(params.range.days)
            val expectedDosesPerDay = SymptomTrendsLogic.expectedDosesPerDay(
                profile.medicineAssignments
            )

            val diaryFlow = symptomDiaryRepository.getHistory(profile.id, startDate, endDate)
            val doseFlow = if (expectedDosesPerDay > 0) {
                doseTrackingRepository.getHistoryForDateRange(profile.id, startDate, endDate)
            } else {
                flowOf(emptyList())
            }

            combine(diaryFlow, doseFlow) { entries, doseHistory ->
                val snapshots = SymptomTrendsLogic.buildSnapshots(
                    entries, doseHistory, expectedDosesPerDay,
                    startDate, params.range.days, json
                )

                val symptomNames = snapshots.flatMap { it.symptomRatings.keys }.distinct()
                val allergenNames = profile.trackedAllergens.keys.map { it.name }

                SymptomTrendsUiState(
                    profiles = params.profiles,
                    selectedProfileId = params.selectedId,
                    range = params.range,
                    snapshots = snapshots,
                    selectedDay = snapshots.find { it.date == params.selectedDay },
                    symptomNames = symptomNames,
                    allergenNames = allergenNames
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SymptomTrendsUiState())

    fun selectProfile(profileId: String) {
        viewModelScope.launch {
            profileRepository.selectProfile(profileId)
        }
    }

    fun selectRange(range: TrendRange) {
        _range.value = range
        _selectedDay.value = null
    }

    fun selectDay(date: LocalDate?) {
        _selectedDay.value = date
    }
}
