package com.ryan.pollenwitan.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ryan.pollenwitan.data.repository.ProfileRepository
import com.ryan.pollenwitan.data.repository.SymptomDiaryRepository
import com.ryan.pollenwitan.domain.model.SymptomDiaryEntry
import com.ryan.pollenwitan.domain.model.UserProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class SymptomDiaryUiState(
    val profiles: List<UserProfile> = emptyList(),
    val selectedProfileId: String = "",
    val entries: List<SymptomDiaryEntry> = emptyList(),
    val rangeStart: LocalDate = LocalDate.now().minusDays(30),
    val rangeEnd: LocalDate = LocalDate.now()
) {
    val selectedProfile: UserProfile?
        get() = profiles.find { it.id == selectedProfileId }
}

class SymptomDiaryViewModel(application: Application) : AndroidViewModel(application) {

    private val profileRepository = ProfileRepository(application)
    private val symptomDiaryRepository = SymptomDiaryRepository(application)

    private val _rangeStart = MutableStateFlow(LocalDate.now().minusDays(30))
    private val _rangeEnd = MutableStateFlow(LocalDate.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SymptomDiaryUiState> = combine(
        profileRepository.getProfiles(),
        profileRepository.getSelectedProfileId(),
        _rangeStart,
        _rangeEnd
    ) { profiles, selectedId, start, end ->
        Triple(profiles, selectedId, start to end)
    }.flatMapLatest { (profiles, selectedId, range) ->
        val (start, end) = range
        val entriesFlow = if (selectedId.isNotBlank()) {
            symptomDiaryRepository.getHistory(selectedId, start, end)
        } else {
            flowOf(emptyList())
        }
        entriesFlow.map { entries ->
            SymptomDiaryUiState(
                profiles = profiles,
                selectedProfileId = selectedId,
                entries = entries,
                rangeStart = start,
                rangeEnd = end
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SymptomDiaryUiState())

    fun selectProfile(profileId: String) {
        viewModelScope.launch {
            profileRepository.selectProfile(profileId)
        }
    }

    fun navigateRange(forward: Boolean) {
        val days = 30L
        if (forward) {
            val newEnd = _rangeEnd.value.plusDays(days)
            if (newEnd.isAfter(LocalDate.now())) return
            _rangeStart.value = _rangeStart.value.plusDays(days)
            _rangeEnd.value = newEnd
        } else {
            _rangeStart.value = _rangeStart.value.minusDays(days)
            _rangeEnd.value = _rangeEnd.value.minusDays(days)
        }
    }
}
