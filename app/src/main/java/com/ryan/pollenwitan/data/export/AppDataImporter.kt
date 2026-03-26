package com.ryan.pollenwitan.data.export

import android.content.Context
import com.ryan.pollenwitan.data.local.AppDatabase
import com.ryan.pollenwitan.data.local.DoseHistoryEntity
import com.ryan.pollenwitan.data.local.SymptomEntryEntity
import com.ryan.pollenwitan.data.repository.LocationRepository
import com.ryan.pollenwitan.data.repository.MedicineRepository
import com.ryan.pollenwitan.data.repository.NotificationPrefsRepository
import com.ryan.pollenwitan.data.repository.ProfileRepository
import com.ryan.pollenwitan.domain.model.AllergenThreshold
import com.ryan.pollenwitan.domain.model.LocationMode
import com.ryan.pollenwitan.domain.model.Medicine
import com.ryan.pollenwitan.domain.model.MedicineAssignment
import com.ryan.pollenwitan.domain.model.MedicineType
import com.ryan.pollenwitan.domain.model.PollenType
import com.ryan.pollenwitan.domain.model.ProfileLocation
import com.ryan.pollenwitan.domain.model.TrackedSymptom
import com.ryan.pollenwitan.domain.model.UserProfile
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.io.InputStream

/**
 * Reads JSON from an [InputStream], validates, and replaces all app data.
 * Import is destructive — existing data is cleared before restoring.
 */
class AppDataImporter(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * @return a summary string (e.g. "Imported 2 profiles, 3 medicines, 45 dose entries, 12 symptom entries")
     * @throws IllegalArgumentException if the JSON is invalid or version is unsupported
     */
    suspend fun import(inputStream: InputStream): String {
        val text = inputStream.bufferedReader().use { it.readText() }
        val data = json.decodeFromString<ExportData>(text)

        require(data.version == 1) {
            "Unsupported export version: ${data.version}"
        }

        // Clear existing data
        val profileRepo = ProfileRepository(context)
        val medicineRepo = MedicineRepository(context)
        val locationRepo = LocationRepository(context)
        val notifRepo = NotificationPrefsRepository(context)
        val db = AppDatabase.getInstance(context)

        // Delete existing profiles
        val existingProfiles = profileRepo.getProfiles().first()
        for (profile in existingProfiles) {
            profileRepo.deleteProfile(profile.id)
        }

        // Delete existing medicines
        val existingMedicines = medicineRepo.getMedicines().first()
        for (medicine in existingMedicines) {
            medicineRepo.deleteMedicine(medicine.id)
        }

        // Clear Room tables
        db.doseHistoryDao().deleteAll()
        db.symptomEntryDao().deleteAll()

        // Restore medicines first (profiles reference them)
        for (med in data.medicines) {
            val type = MedicineType.entries.find { it.name == med.type } ?: MedicineType.Other
            medicineRepo.addMedicine(Medicine(id = med.id, name = med.name, type = type))
        }

        // Restore profiles
        for (exportProfile in data.profiles) {
            profileRepo.addProfile(exportProfile.toDomain())
        }

        // Restore dose history
        if (data.doseHistory.isNotEmpty()) {
            db.doseHistoryDao().insertAll(
                data.doseHistory.map { entry ->
                    DoseHistoryEntity(
                        profileId = entry.profileId,
                        medicineId = entry.medicineId,
                        slotIndex = entry.slotIndex,
                        date = entry.date,
                        confirmedAtMillis = entry.confirmedAtMillis,
                        confirmed = entry.confirmed,
                        medicineName = entry.medicineName,
                        dose = entry.dose,
                        medicineType = entry.medicineType,
                        reminderHour = entry.reminderHour
                    )
                }
            )
        }

        // Restore symptom entries
        if (data.symptomEntries.isNotEmpty()) {
            db.symptomEntryDao().insertAll(
                data.symptomEntries.map { entry ->
                    SymptomEntryEntity(
                        profileId = entry.profileId,
                        date = entry.date,
                        ratingsJson = entry.ratingsJson,
                        loggedAtMillis = entry.loggedAtMillis,
                        peakPollenJson = entry.peakPollenJson,
                        peakAqi = entry.peakAqi,
                        peakPm25 = entry.peakPm25,
                        peakPm10 = entry.peakPm10
                    )
                }
            )
        }

        // Restore location settings
        val loc = data.locationSettings
        val mode = try {
            LocationMode.valueOf(loc.mode)
        } catch (_: Exception) {
            LocationMode.Manual
        }
        locationRepo.setLocationMode(mode)
        locationRepo.setManualLocation(loc.manualLatitude, loc.manualLongitude, loc.manualDisplayName)
        if (loc.gpsLatitude != null && loc.gpsLongitude != null) {
            locationRepo.updateGpsLocation(
                loc.gpsLatitude, loc.gpsLongitude, loc.gpsDisplayName ?: "GPS Location"
            )
        }

        // Restore notification prefs
        val np = data.notificationPrefs
        notifRepo.setMorningBriefingEnabled(np.morningBriefingEnabled)
        notifRepo.setMorningBriefingHour(np.morningBriefingHour)
        notifRepo.setThresholdAlertsEnabled(np.thresholdAlertsEnabled)
        notifRepo.setCompoundRiskAlertsEnabled(np.compoundRiskAlertsEnabled)
        notifRepo.setPreSeasonAlertsEnabled(np.preSeasonAlertsEnabled)
        notifRepo.setSymptomReminderEnabled(np.symptomReminderEnabled)
        notifRepo.setSymptomReminderHour(np.symptomReminderHour)

        return buildString {
            append("Imported ${data.profiles.size} profiles")
            append(", ${data.medicines.size} medicines")
            append(", ${data.doseHistory.size} dose entries")
            append(", ${data.symptomEntries.size} symptom entries")
        }
    }

    private fun ExportProfile.toDomain() = UserProfile(
        id = id,
        displayName = displayName,
        hasAsthma = hasAsthma,
        trackedAllergens = trackedAllergens.mapNotNull { (typeName, threshold) ->
            val type = PollenType.entries.find { it.name == typeName } ?: return@mapNotNull null
            type to AllergenThreshold(
                type = type,
                low = threshold.low,
                moderate = threshold.moderate,
                high = threshold.high,
                veryHigh = threshold.veryHigh
            )
        }.toMap(),
        location = location?.let {
            ProfileLocation(it.latitude, it.longitude, it.displayName)
        },
        medicineAssignments = medicineAssignments.map { assignment ->
            MedicineAssignment(
                medicineId = assignment.medicineId,
                dose = assignment.dose,
                timesPerDay = assignment.timesPerDay,
                reminderHours = assignment.reminderHours
            )
        },
        trackedSymptoms = trackedSymptoms.map { symptom ->
            TrackedSymptom(
                id = symptom.id,
                displayName = symptom.displayName,
                isDefault = symptom.isDefault
            )
        }
    )
}
