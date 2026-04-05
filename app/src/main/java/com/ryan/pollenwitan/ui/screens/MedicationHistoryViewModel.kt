package com.ryan.pollenwitan.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ryan.pollenwitan.data.repository.DoseTrackingRepository
import com.ryan.pollenwitan.data.repository.MedicineRepository
import com.ryan.pollenwitan.data.repository.ProfileRepository
import com.ryan.pollenwitan.domain.model.Medicine
import com.ryan.pollenwitan.domain.model.MedicineType
import com.ryan.pollenwitan.domain.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class MedicationHistorySlot(
    val medicineId: String,
    val medicineName: String,
    val dose: Int,
    val medicineType: MedicineType,
    val hour: Int,
    val slotIndex: Int,
    val confirmed: Boolean
)

data class MedicationHistoryUiState(
    val profiles: List<UserProfile> = emptyList(),
    val selectedProfileId: String = "",
    val selectedDate: LocalDate = LocalDate.now().minusDays(1),
    val slots: List<MedicationHistorySlot> = emptyList(),
    val isLoading: Boolean = true,
    val hasMedicines: Boolean = true
) {
    val selectedProfile: UserProfile?
        get() = profiles.find { it.id == selectedProfileId }
}

class MedicationHistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val profileRepository = ProfileRepository(application)
    private val medicineRepository = MedicineRepository(application)
    private val doseTrackingRepository = DoseTrackingRepository(application)

    private val _selectedDate = MutableStateFlow(LocalDate.now().minusDays(1))
    private val _slots = MutableStateFlow<List<MedicationHistorySlot>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private val _hasMedicines = MutableStateFlow(true)

    val uiState: StateFlow<MedicationHistoryUiState> = combine(
        profileRepository.getProfiles(),
        profileRepository.getSelectedProfileId(),
        _selectedDate,
        _slots,
        _isLoading,
        _hasMedicines
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val profiles = values[0] as List<UserProfile>
        val selectedId = values[1] as String
        val date = values[2] as LocalDate
        @Suppress("UNCHECKED_CAST")
        val slots = values[3] as List<MedicationHistorySlot>
        val isLoading = values[4] as Boolean
        val hasMedicines = values[5] as Boolean
        MedicationHistoryUiState(
            profiles = profiles,
            selectedProfileId = selectedId,
            selectedDate = date,
            slots = slots,
            isLoading = isLoading,
            hasMedicines = hasMedicines
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MedicationHistoryUiState())

    init {
        viewModelScope.launch {
            combine(
                profileRepository.getProfiles(),
                profileRepository.getSelectedProfileId(),
                medicineRepository.getMedicines(),
                _selectedDate
            ) { profiles, selectedId, medicines, date ->
                Triple(profiles.find { it.id == selectedId }, medicines, date)
            }.collect { (profile, medicines, date) ->
                loadSlots(profile, medicines, date)
            }
        }
    }

    fun selectProfile(profileId: String) {
        viewModelScope.launch {
            profileRepository.selectProfile(profileId)
        }
    }

    fun navigateDate(forward: Boolean) {
        val current = _selectedDate.value
        val newDate = if (forward) current.plusDays(1) else current.minusDays(1)
        val yesterday = LocalDate.now().minusDays(1)
        val minDate = LocalDate.now().minusDays(90)
        if (newDate.isAfter(yesterday) || newDate.isBefore(minDate)) return
        _selectedDate.value = newDate
    }

    fun confirmDose(medicineId: String, slotIndex: Int) {
        val state = uiState.value
        val slot = state.slots.find { it.medicineId == medicineId && it.slotIndex == slotIndex } ?: return
        viewModelScope.launch {
            doseTrackingRepository.confirmDoseForDate(
                profileId = state.selectedProfileId,
                medicineId = medicineId,
                slotIndex = slotIndex,
                date = state.selectedDate,
                medicineName = slot.medicineName,
                dose = slot.dose,
                medicineType = slot.medicineType.name,
                reminderHour = slot.hour
            )
            loadSlots(state.selectedProfile, null, state.selectedDate)
        }
    }

    fun unconfirmDose(medicineId: String, slotIndex: Int) {
        val state = uiState.value
        val slot = state.slots.find { it.medicineId == medicineId && it.slotIndex == slotIndex } ?: return
        viewModelScope.launch {
            doseTrackingRepository.unconfirmDoseForDate(
                profileId = state.selectedProfileId,
                medicineId = medicineId,
                slotIndex = slotIndex,
                date = state.selectedDate,
                medicineName = slot.medicineName,
                dose = slot.dose,
                medicineType = slot.medicineType.name,
                reminderHour = slot.hour
            )
            loadSlots(state.selectedProfile, null, state.selectedDate)
        }
    }

    private suspend fun loadSlots(profile: UserProfile?, medicines: List<Medicine>?, date: LocalDate) {
        if (profile == null) {
            // Profile not yet loaded — stay in loading state
            return
        }
        if (profile.medicineAssignments.isEmpty()) {
            _hasMedicines.value = false
            _slots.value = emptyList()
            _isLoading.value = false
            return
        }

        _isLoading.value = true
        val allMedicines = medicines ?: medicineRepository.getMedicines().first()
        val history = doseTrackingRepository.getAllHistoryForDate(profile.id, date)
        val confirmedKeys = history.filter { it.confirmed }
            .map { it.medicineId to it.slotIndex }
            .toSet()

        val slots = mutableListOf<MedicationHistorySlot>()
        profile.medicineAssignments.forEach { assignment ->
            val medicine = allMedicines.find { it.id == assignment.medicineId } ?: return@forEach
            assignment.reminderHours.forEachIndexed { slotIndex, hour ->
                slots.add(
                    MedicationHistorySlot(
                        medicineId = assignment.medicineId,
                        medicineName = medicine.name,
                        dose = assignment.dose,
                        medicineType = medicine.type,
                        hour = hour,
                        slotIndex = slotIndex,
                        confirmed = (assignment.medicineId to slotIndex) in confirmedKeys
                    )
                )
            }
        }

        _hasMedicines.value = true
        _slots.value = slots.sortedBy { it.hour }
        _isLoading.value = false
    }
}
