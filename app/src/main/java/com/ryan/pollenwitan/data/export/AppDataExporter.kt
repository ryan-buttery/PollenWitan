package com.ryan.pollenwitan.data.export

import android.content.Context
import com.ryan.pollenwitan.data.local.AppDatabase
import com.ryan.pollenwitan.data.repository.LocationRepository
import com.ryan.pollenwitan.data.repository.MedicineRepository
import com.ryan.pollenwitan.data.repository.NotificationPrefsRepository
import com.ryan.pollenwitan.data.repository.ProfileRepository
import com.ryan.pollenwitan.domain.model.UserProfile
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.OutputStream
import java.time.Instant

/**
 * Assembles all app data into [ExportData] and writes JSON to an [OutputStream].
 */
class AppDataExporter(private val context: Context) {

    private val json = Json { prettyPrint = true; encodeDefaults = true }

    suspend fun export(outputStream: OutputStream) {
        val profileRepo = ProfileRepository(context)
        val medicineRepo = MedicineRepository(context)
        val locationRepo = LocationRepository(context)
        val notifRepo = NotificationPrefsRepository(context)
        val db = AppDatabase.getInstance(context)

        val profiles = profileRepo.getProfiles().first()
        val medicines = medicineRepo.getMedicines().first()
        val location = locationRepo.getLocation().first()
        val locationMode = locationRepo.getLocationMode().first()
        val notifPrefs = notifRepo.getPrefs().first()
        val doseHistory = db.doseHistoryDao().getAll()
        val symptomEntries = db.symptomEntryDao().getAll()

        val exportData = ExportData(
            version = 1,
            exportedAt = Instant.now().toString(),
            profiles = profiles.map { it.toExport() },
            medicines = medicines.map { med ->
                ExportMedicine(
                    id = med.id,
                    name = med.name,
                    type = med.type.name
                )
            },
            doseHistory = doseHistory.map { entity ->
                ExportDoseEntry(
                    profileId = entity.profileId,
                    medicineId = entity.medicineId,
                    slotIndex = entity.slotIndex,
                    date = entity.date,
                    confirmedAtMillis = entity.confirmedAtMillis,
                    confirmed = entity.confirmed,
                    medicineName = entity.medicineName,
                    dose = entity.dose,
                    medicineType = entity.medicineType,
                    reminderHour = entity.reminderHour
                )
            },
            symptomEntries = symptomEntries.map { entity ->
                ExportSymptomEntry(
                    profileId = entity.profileId,
                    date = entity.date,
                    ratingsJson = entity.ratingsJson,
                    loggedAtMillis = entity.loggedAtMillis,
                    peakPollenJson = entity.peakPollenJson,
                    peakAqi = entity.peakAqi,
                    peakPm25 = entity.peakPm25,
                    peakPm10 = entity.peakPm10
                )
            },
            locationSettings = ExportLocation(
                mode = locationMode.name,
                manualLatitude = location.latitude,
                manualLongitude = location.longitude,
                manualDisplayName = location.displayName
            ),
            notificationPrefs = ExportNotificationPrefs(
                morningBriefingEnabled = notifPrefs.morningBriefingEnabled,
                morningBriefingHour = notifPrefs.morningBriefingHour,
                thresholdAlertsEnabled = notifPrefs.thresholdAlertsEnabled,
                compoundRiskAlertsEnabled = notifPrefs.compoundRiskAlertsEnabled,
                preSeasonAlertsEnabled = notifPrefs.preSeasonAlertsEnabled,
                symptomReminderEnabled = notifPrefs.symptomReminderEnabled,
                symptomReminderHour = notifPrefs.symptomReminderHour
            )
        )

        outputStream.bufferedWriter().use { writer ->
            writer.write(json.encodeToString(exportData))
        }
    }

    private fun UserProfile.toExport() = ExportProfile(
        id = id,
        displayName = displayName,
        hasAsthma = hasAsthma,
        trackedAllergens = trackedAllergens.map { (type, threshold) ->
            type.name to ExportAllergenThreshold(
                type = type.name,
                low = threshold.low,
                moderate = threshold.moderate,
                high = threshold.high,
                veryHigh = threshold.veryHigh
            )
        }.toMap(),
        location = location?.let {
            ExportProfileLocation(it.latitude, it.longitude, it.displayName)
        },
        medicineAssignments = medicineAssignments.map { assignment ->
            ExportMedicineAssignment(
                medicineId = assignment.medicineId,
                dose = assignment.dose,
                timesPerDay = assignment.timesPerDay,
                reminderHours = assignment.reminderHours
            )
        },
        trackedSymptoms = trackedSymptoms.map { symptom ->
            ExportTrackedSymptom(
                id = symptom.id,
                displayName = symptom.displayName,
                isDefault = symptom.isDefault
            )
        }
    )
}
