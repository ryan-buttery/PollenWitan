package com.ryan.pollenwitan.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ryan.pollenwitan.R
import com.ryan.pollenwitan.data.location.GpsLocationProvider
import com.ryan.pollenwitan.data.repository.MedicineRepository
import com.ryan.pollenwitan.data.repository.ProfileRepository
import com.ryan.pollenwitan.domain.model.AllergenThreshold
import com.ryan.pollenwitan.domain.model.Medicine
import com.ryan.pollenwitan.domain.model.MedicineAssignment
import com.ryan.pollenwitan.domain.model.MedicineType
import com.ryan.pollenwitan.domain.model.PollenType
import com.ryan.pollenwitan.domain.model.ProfileLocation
import com.ryan.pollenwitan.domain.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

data class ProfileEditUiState(
    val isNewProfile: Boolean = true,
    val profileId: String = "",
    val displayName: String = "",
    val hasAsthma: Boolean = false,
    val trackedAllergens: Set<PollenType> = emptySet(),
    val thresholds: Map<PollenType, AllergenThreshold> = emptyMap(),
    val useCustomThresholds: Map<PollenType, Boolean> = emptyMap(),
    val useCustomLocation: Boolean = false,
    val locationLatitude: String = "",
    val locationLongitude: String = "",
    val locationDisplayName: String = "",
    val gpsStatus: GpsStatus = GpsStatus.Idle,
    val medicineAssignments: List<MedicineAssignmentUiState> = emptyList(),
    val availableMedicines: List<Medicine> = emptyList(),
    val isSaving: Boolean = false,
    val savedSuccessfully: Boolean = false,
    val validationError: String? = null
)

data class MedicineAssignmentUiState(
    val medicineId: String,
    val medicineName: String,
    val medicineType: MedicineType,
    val dose: String,
    val timesPerDay: String,
    val reminderHours: List<Int>
)

class ProfileEditViewModel(application: Application) : AndroidViewModel(application) {

    private val profileRepository = ProfileRepository(application)
    private val gpsLocationProvider = GpsLocationProvider(application)
    private val medicineRepository = MedicineRepository(application)

    private val _uiState = MutableStateFlow(ProfileEditUiState())
    val uiState: StateFlow<ProfileEditUiState> = _uiState.asStateFlow()

    fun loadProfile(profileId: String?) {
        viewModelScope.launch {
            val medicines = medicineRepository.getMedicines().first()
            if (profileId == null) {
                _uiState.value = ProfileEditUiState(
                    isNewProfile = true,
                    profileId = UUID.randomUUID().toString(),
                    availableMedicines = medicines
                )
                return@launch
            }
            val profiles = profileRepository.getProfiles().first()
            val profile = profiles.find { it.id == profileId } ?: return@launch
            val assignmentStates = profile.medicineAssignments.mapNotNull { assignment ->
                val medicine = medicines.find { it.id == assignment.medicineId } ?: return@mapNotNull null
                MedicineAssignmentUiState(
                    medicineId = assignment.medicineId,
                    medicineName = medicine.name,
                    medicineType = medicine.type,
                    dose = assignment.dose.toString(),
                    timesPerDay = assignment.timesPerDay.toString(),
                    reminderHours = assignment.reminderHours
                )
            }
            _uiState.value = ProfileEditUiState(
                isNewProfile = false,
                profileId = profile.id,
                displayName = profile.displayName,
                hasAsthma = profile.hasAsthma,
                trackedAllergens = profile.trackedAllergens.keys,
                thresholds = profile.trackedAllergens,
                useCustomThresholds = profile.trackedAllergens.mapValues { (type, threshold) ->
                    threshold != UserProfile.defaultThreshold(type)
                },
                useCustomLocation = profile.location != null,
                locationLatitude = profile.location?.latitude?.toString() ?: "",
                locationLongitude = profile.location?.longitude?.toString() ?: "",
                locationDisplayName = profile.location?.displayName ?: "",
                medicineAssignments = assignmentStates,
                availableMedicines = medicines
            )
        }
    }

    fun setDisplayName(name: String) {
        _uiState.value = _uiState.value.copy(displayName = name, validationError = null)
    }

    fun setHasAsthma(value: Boolean) {
        _uiState.value = _uiState.value.copy(hasAsthma = value)
    }

    fun toggleAllergen(type: PollenType) {
        val current = _uiState.value
        val newTracked = current.trackedAllergens.toMutableSet()
        val newThresholds = current.thresholds.toMutableMap()
        val newCustom = current.useCustomThresholds.toMutableMap()

        if (type in newTracked) {
            newTracked.remove(type)
            newThresholds.remove(type)
            newCustom.remove(type)
        } else {
            newTracked.add(type)
            newThresholds[type] = UserProfile.defaultThreshold(type)
            newCustom[type] = false
        }

        _uiState.value = current.copy(
            trackedAllergens = newTracked,
            thresholds = newThresholds,
            useCustomThresholds = newCustom,
            validationError = null
        )
    }

    fun setUseCustomThreshold(type: PollenType, custom: Boolean) {
        val current = _uiState.value
        val newCustom = current.useCustomThresholds.toMutableMap()
        val newThresholds = current.thresholds.toMutableMap()
        newCustom[type] = custom
        if (!custom) {
            newThresholds[type] = UserProfile.defaultThreshold(type)
        }
        _uiState.value = current.copy(
            useCustomThresholds = newCustom,
            thresholds = newThresholds
        )
    }

    fun updateThreshold(type: PollenType, level: String, value: Double) {
        val current = _uiState.value
        val existing = current.thresholds[type] ?: return
        val updated = when (level) {
            "low" -> existing.copy(low = value)
            "moderate" -> existing.copy(moderate = value)
            "high" -> existing.copy(high = value)
            "veryHigh" -> existing.copy(veryHigh = value)
            else -> return
        }
        val newThresholds = current.thresholds.toMutableMap()
        newThresholds[type] = updated
        _uiState.value = current.copy(thresholds = newThresholds)
    }

    fun setUseCustomLocation(enabled: Boolean) {
        _uiState.value = if (enabled) {
            _uiState.value.copy(useCustomLocation = true)
        } else {
            _uiState.value.copy(
                useCustomLocation = false,
                locationLatitude = "",
                locationLongitude = "",
                locationDisplayName = "",
                gpsStatus = GpsStatus.Idle
            )
        }
    }

    fun setLocationLatitude(value: String) {
        _uiState.value = _uiState.value.copy(locationLatitude = value)
    }

    fun setLocationLongitude(value: String) {
        _uiState.value = _uiState.value.copy(locationLongitude = value)
    }

    fun setLocationDisplayName(value: String) {
        _uiState.value = _uiState.value.copy(locationDisplayName = value)
    }

    fun addMedicineAssignment(medicineId: String) {
        val current = _uiState.value
        val medicine = current.availableMedicines.find { it.id == medicineId } ?: return
        if (current.medicineAssignments.any { it.medicineId == medicineId }) return
        val newAssignment = MedicineAssignmentUiState(
            medicineId = medicineId,
            medicineName = medicine.name,
            medicineType = medicine.type,
            dose = "1",
            timesPerDay = "1",
            reminderHours = listOf(8)
        )
        _uiState.value = current.copy(
            medicineAssignments = current.medicineAssignments + newAssignment
        )
    }

    fun removeMedicineAssignment(medicineId: String) {
        val current = _uiState.value
        _uiState.value = current.copy(
            medicineAssignments = current.medicineAssignments.filter { it.medicineId != medicineId }
        )
    }

    fun updateAssignmentDose(medicineId: String, dose: String) {
        updateAssignment(medicineId) { it.copy(dose = dose) }
    }

    fun updateAssignmentTimesPerDay(medicineId: String, times: String) {
        updateAssignment(medicineId) { it.copy(timesPerDay = times) }
    }

    fun toggleReminderHour(medicineId: String, hour: Int) {
        updateAssignment(medicineId) { assignment ->
            val hours = assignment.reminderHours.toMutableList()
            if (hour in hours) hours.remove(hour) else hours.add(hour)
            assignment.copy(reminderHours = hours.sorted())
        }
    }

    private fun updateAssignment(medicineId: String, transform: (MedicineAssignmentUiState) -> MedicineAssignmentUiState) {
        val current = _uiState.value
        _uiState.value = current.copy(
            medicineAssignments = current.medicineAssignments.map {
                if (it.medicineId == medicineId) transform(it) else it
            }
        )
    }

    fun requestGpsFix() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(gpsStatus = GpsStatus.Requesting)
            val location = gpsLocationProvider.requestSingleUpdate()
            if (location != null) {
                _uiState.value = _uiState.value.copy(
                    locationLatitude = location.latitude.toString(),
                    locationLongitude = location.longitude.toString(),
                    locationDisplayName = location.displayName,
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

    fun save() {
        val state = _uiState.value
        if (state.displayName.isBlank()) {
            _uiState.value = state.copy(validationError = getApplication<Application>().getString(R.string.validation_name_empty))
            return
        }
        if (state.trackedAllergens.isEmpty()) {
            _uiState.value = state.copy(validationError = getApplication<Application>().getString(R.string.validation_select_allergen))
            return
        }

        val location = if (state.useCustomLocation) {
            val lat = state.locationLatitude.toDoubleOrNull()
            val lon = state.locationLongitude.toDoubleOrNull()
            if (lat != null && lon != null && state.locationDisplayName.isNotBlank()) {
                ProfileLocation(lat, lon, state.locationDisplayName.trim())
            } else null
        } else null

        val medicineAssignments = state.medicineAssignments.mapNotNull { assignment ->
            val dose = assignment.dose.toIntOrNull() ?: return@mapNotNull null
            val timesPerDay = assignment.timesPerDay.toIntOrNull() ?: return@mapNotNull null
            if (dose <= 0 || timesPerDay <= 0) return@mapNotNull null
            MedicineAssignment(
                medicineId = assignment.medicineId,
                dose = dose,
                timesPerDay = timesPerDay,
                reminderHours = assignment.reminderHours
            )
        }

        val profile = UserProfile(
            id = state.profileId,
            displayName = state.displayName.trim(),
            trackedAllergens = state.thresholds.filterKeys { it in state.trackedAllergens },
            hasAsthma = state.hasAsthma,
            location = location,
            medicineAssignments = medicineAssignments
        )

        _uiState.value = state.copy(isSaving = true, validationError = null)
        viewModelScope.launch {
            if (state.isNewProfile) {
                profileRepository.addProfile(profile)
            } else {
                profileRepository.updateProfile(profile)
            }
            _uiState.value = _uiState.value.copy(isSaving = false, savedSuccessfully = true)
        }
    }
}
