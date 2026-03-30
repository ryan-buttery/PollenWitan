package com.ryan.pollenwitan.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ryan.pollenwitan.data.export.AppDataExporter
import com.ryan.pollenwitan.data.export.AppDataImporter
import com.ryan.pollenwitan.data.export.CsvSymptomExporter
import com.ryan.pollenwitan.data.location.GpsLocationProvider
import com.ryan.pollenwitan.data.repository.LocationRepository
import com.ryan.pollenwitan.data.repository.MedicineRepository
import com.ryan.pollenwitan.data.repository.NotificationPrefs
import com.ryan.pollenwitan.data.repository.NotificationPrefsRepository
import com.ryan.pollenwitan.data.repository.ProfileRepository
import com.ryan.pollenwitan.domain.model.LocationMode
import com.ryan.pollenwitan.domain.model.Medicine
import com.ryan.pollenwitan.domain.model.MedicineType
import com.ryan.pollenwitan.domain.model.UserProfile
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val locationMode: LocationMode = LocationMode.Manual,
    val manualLatitude: String = "",
    val manualLongitude: String = "",
    val manualDisplayName: String = "",
    val gpsStatus: GpsStatus = GpsStatus.Idle,
    val notificationPrefs: NotificationPrefs = NotificationPrefs(),
    val medicines: List<Medicine> = emptyList(),
    val profiles: List<UserProfile> = emptyList()
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val locationRepository = LocationRepository(application)
    private val gpsLocationProvider = GpsLocationProvider(application)
    private val notificationPrefsRepository = NotificationPrefsRepository(application)
    private val medicineRepository = MedicineRepository(application)
    private val profileRepository = ProfileRepository(application)

    private val exporter = AppDataExporter(application)
    private val importer = AppDataImporter(application)
    private val csvExporter = CsvSymptomExporter(application)

    private val _gpsStatus = MutableStateFlow<GpsStatus>(GpsStatus.Idle)

    val uiState: StateFlow<SettingsUiState> = combine(
        locationRepository.getLocationMode(),
        locationRepository.getLocation(),
        _gpsStatus,
        notificationPrefsRepository.getPrefs(),
        medicineRepository.getMedicines(),
        profileRepository.getProfiles()
    ) { values ->
        val mode = values[0] as LocationMode
        val location = values[1] as com.ryan.pollenwitan.domain.model.AppLocation
        val gpsStatus = values[2] as GpsStatus
        val notifPrefs = values[3] as NotificationPrefs
        @Suppress("UNCHECKED_CAST")
        val medicines = values[4] as List<Medicine>
        @Suppress("UNCHECKED_CAST")
        val profiles = values[5] as List<UserProfile>
        SettingsUiState(
            locationMode = mode,
            manualLatitude = if (mode == LocationMode.Manual) location.latitude.toString() else "",
            manualLongitude = if (mode == LocationMode.Manual) location.longitude.toString() else "",
            manualDisplayName = if (mode == LocationMode.Manual) location.displayName else "",
            gpsStatus = gpsStatus,
            notificationPrefs = notifPrefs,
            medicines = medicines,
            profiles = profiles
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setLocationMode(mode: LocationMode) {
        viewModelScope.launch {
            locationRepository.setLocationMode(mode)
        }
    }

    fun saveManualLocation(latitude: String, longitude: String, displayName: String) {
        val lat = latitude.toDoubleOrNull() ?: return
        val lon = longitude.toDoubleOrNull() ?: return
        if (!ProfileEditLogic.isValidLatitude(lat) || !ProfileEditLogic.isValidLongitude(lon)) return
        val name = displayName.take(ProfileEditLogic.MAX_LOCATION_DISPLAY_NAME_LENGTH).trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            locationRepository.setManualLocation(lat, lon, name)
        }
    }

    fun requestGpsFix() {
        viewModelScope.launch {
            _gpsStatus.value = GpsStatus.Requesting
            val location = gpsLocationProvider.requestSingleUpdate()
            if (location != null) {
                locationRepository.updateGpsLocation(location.latitude, location.longitude, location.displayName)
                _gpsStatus.value = GpsStatus.Success(
                    "%.4f, %.4f".format(location.latitude, location.longitude)
                )
            } else {
                _gpsStatus.value = GpsStatus.Error("Could not get location. Check GPS is enabled.")
            }
        }
    }

    fun setMorningBriefingEnabled(enabled: Boolean) {
        viewModelScope.launch { notificationPrefsRepository.setMorningBriefingEnabled(enabled) }
    }

    fun setMorningBriefingHour(hour: Int) {
        viewModelScope.launch { notificationPrefsRepository.setMorningBriefingHour(hour) }
    }

    fun setThresholdAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch { notificationPrefsRepository.setThresholdAlertsEnabled(enabled) }
    }

    fun setCompoundRiskAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch { notificationPrefsRepository.setCompoundRiskAlertsEnabled(enabled) }
    }

    fun setPreSeasonAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch { notificationPrefsRepository.setPreSeasonAlertsEnabled(enabled) }
    }

    fun setSymptomReminderEnabled(enabled: Boolean) {
        viewModelScope.launch { notificationPrefsRepository.setSymptomReminderEnabled(enabled) }
    }

    fun setSymptomReminderHour(hour: Int) {
        viewModelScope.launch { notificationPrefsRepository.setSymptomReminderHour(hour) }
    }

    fun addMedicine(name: String, type: MedicineType) {
        val trimmed = name.take(ProfileEditLogic.MAX_MEDICINE_NAME_LENGTH).trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            medicineRepository.addMedicine(
                Medicine(id = UUID.randomUUID().toString(), name = trimmed, type = type)
            )
        }
    }

    fun updateMedicine(medicine: Medicine) {
        viewModelScope.launch { medicineRepository.updateMedicine(medicine) }
    }

    fun deleteMedicine(medicineId: String) {
        viewModelScope.launch { medicineRepository.deleteMedicine(medicineId) }
    }

    fun exportAllData(outputStream: OutputStream, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            try {
                exporter.export(outputStream)
                onResult(Result.success(Unit))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }

    fun importAllData(inputStream: InputStream, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            try {
                val summary = importer.import(inputStream)
                onResult(Result.success(summary))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }

    fun exportSymptomCsv(profileId: String, outputStream: OutputStream, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            try {
                csvExporter.export(profileId, outputStream)
                onResult(Result.success(Unit))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }
}
