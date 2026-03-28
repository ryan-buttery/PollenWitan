package com.ryan.pollenwitan.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ryan.pollenwitan.data.repository.AirQualityRepository
import com.ryan.pollenwitan.data.repository.DoseTrackingRepository
import com.ryan.pollenwitan.data.repository.LocationRepository
import com.ryan.pollenwitan.data.repository.MedicineRepository
import com.ryan.pollenwitan.data.repository.ProfileRepository
import com.ryan.pollenwitan.data.repository.SymptomDiaryRepository
import com.ryan.pollenwitan.domain.model.CurrentConditions
import com.ryan.pollenwitan.domain.model.DoseConfirmation
import com.ryan.pollenwitan.domain.model.Medicine
import com.ryan.pollenwitan.domain.model.MedicineType
import com.ryan.pollenwitan.domain.model.SymptomDiaryEntry
import com.ryan.pollenwitan.domain.model.UserProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface WeatherState {
    data object Loading : WeatherState
    data class Success(val conditions: CurrentConditions) : WeatherState
    data class Error(val message: String) : WeatherState
}

data class MedicineSlot(
    val medicineId: String,
    val medicineName: String,
    val dose: Int,
    val medicineType: MedicineType,
    val hour: Int,
    val slotIndex: Int,
    val confirmed: Boolean
)

data class DashboardUiState(
    val weatherState: WeatherState = WeatherState.Loading,
    val profiles: List<UserProfile> = emptyList(),
    val selectedProfileId: String = "",
    val locationDisplayName: String = "",
    val medicineSlots: List<MedicineSlot> = emptyList(),
    val todaySymptomEntry: SymptomDiaryEntry? = null
) {
    val selectedProfile: UserProfile?
        get() = profiles.find { it.id == selectedProfileId }
    val allDosesConfirmed: Boolean
        get() = medicineSlots.isNotEmpty() && medicineSlots.all { it.confirmed }
}

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val airQualityRepository = AirQualityRepository(application)
    private val profileRepository = ProfileRepository(application)
    private val locationRepository = LocationRepository(application)
    private val medicineRepository = MedicineRepository(application)
    private val doseTrackingRepository = DoseTrackingRepository(application)
    private val symptomDiaryRepository = SymptomDiaryRepository(application)

    private val _weatherState = MutableStateFlow<WeatherState>(WeatherState.Loading)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DashboardUiState> = combine(
        _weatherState,
        profileRepository.getProfiles(),
        profileRepository.getSelectedProfileId(),
        locationRepository.getLocation(),
        medicineRepository.getMedicines()
    ) { weather, profiles, selectedId, globalLocation, medicines ->
        val selectedProfile = profiles.find { it.id == selectedId }
        val effectiveLocation = ProfileRepository.resolveLocation(selectedProfile, globalLocation)
        Triple(
            DashboardUiState(
                weatherState = weather,
                profiles = profiles,
                selectedProfileId = selectedId,
                locationDisplayName = effectiveLocation.displayName
            ),
            selectedProfile,
            medicines
        )
    }.flatMapLatest { (baseState, selectedProfile, medicines) ->
        if (selectedProfile == null) {
            flowOf(baseState)
        } else {
            val doseFlow = if (selectedProfile.medicineAssignments.isNotEmpty()) {
                doseTrackingRepository.getConfirmations(selectedProfile.id)
            } else {
                flowOf(emptySet())
            }
            val symptomFlow = symptomDiaryRepository.observeTodayEntry(selectedProfile.id)

            combine(doseFlow, symptomFlow) { confirmations, symptomEntry ->
                val slots = if (selectedProfile.medicineAssignments.isNotEmpty()) {
                    buildMedicineSlots(selectedProfile, medicines, confirmations)
                } else emptyList()
                baseState.copy(
                    medicineSlots = slots,
                    todaySymptomEntry = symptomEntry
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    init {
        refresh()
        // Re-fetch when effective location changes (global location or profile selection)
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
            _weatherState.value = WeatherState.Loading
            val globalLocation = locationRepository.getLocation().first()
            val profiles = profileRepository.getProfiles().first()
            val selectedId = profileRepository.getSelectedProfileId().first()
            val selectedProfile = profiles.find { it.id == selectedId }
            val location = ProfileRepository.resolveLocation(selectedProfile, globalLocation)
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

    fun confirmDose(medicineId: String, slotIndex: Int) {
        val profileId = uiState.value.selectedProfileId
        if (profileId.isBlank()) return
        val slot = uiState.value.medicineSlots.find {
            it.medicineId == medicineId && it.slotIndex == slotIndex
        } ?: return
        viewModelScope.launch {
            doseTrackingRepository.confirmDose(
                profileId, medicineId, slotIndex,
                slot.medicineName, slot.dose, slot.medicineType.name, slot.hour
            )
        }
    }

    fun unconfirmDose(medicineId: String, slotIndex: Int) {
        val profileId = uiState.value.selectedProfileId
        if (profileId.isBlank()) return
        val slot = uiState.value.medicineSlots.find {
            it.medicineId == medicineId && it.slotIndex == slotIndex
        } ?: return
        viewModelScope.launch {
            doseTrackingRepository.unconfirmDose(
                profileId, medicineId, slotIndex,
                slot.medicineName, slot.dose, slot.medicineType.name, slot.hour
            )
        }
    }

    private fun buildMedicineSlots(
        profile: UserProfile,
        medicines: List<Medicine>,
        confirmations: Set<DoseConfirmation>
    ): List<MedicineSlot> = DashboardLogic.buildMedicineSlots(profile, medicines, confirmations)
}
