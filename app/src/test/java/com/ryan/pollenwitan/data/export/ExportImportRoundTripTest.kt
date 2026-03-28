package com.ryan.pollenwitan.data.export

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Unit tests for data export/import round-trip integrity.
 *
 * These tests verify that [ExportData] survives a full JSON serialisation cycle
 * (encode → decode) without data loss, and that version validation, edge-case
 * handling, and forward-compatibility (unknown keys) work correctly.
 *
 * No Android dependencies — all tests exercise the serialisable models and
 * the same [Json] configurations used by [AppDataExporter] / [AppDataImporter].
 */
class ExportImportRoundTripTest {

    /** Matches AppDataExporter configuration. */
    private val exportJson = Json { prettyPrint = true; encodeDefaults = true }

    /** Matches AppDataImporter configuration. */
    private val importJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ── Fixtures ───────────────────────────────────────────────────────

    private fun fullExportData() = ExportData(
        version = 1,
        exportedAt = "2026-03-28T10:15:30Z",
        profiles = listOf(
            ExportProfile(
                id = "ryan",
                displayName = "Ryan",
                hasAsthma = true,
                trackedAllergens = mapOf(
                    "Birch" to ExportAllergenThreshold("Birch", 1.0, 11.0, 51.0, 201.0),
                    "Grass" to ExportAllergenThreshold("Grass", 1.0, 6.0, 31.0, 81.0)
                ),
                location = ExportProfileLocation(52.4064, 16.9252, "Poznań"),
                medicineAssignments = listOf(
                    ExportMedicineAssignment(
                        medicineId = "med-cetirizine",
                        dose = 1,
                        timesPerDay = 1,
                        reminderHours = listOf(8)
                    )
                ),
                trackedSymptoms = listOf(
                    ExportTrackedSymptom("Sneezing", "Sneezing", isDefault = true),
                    ExportTrackedSymptom("custom-1", "Headache", isDefault = false)
                )
            ),
            ExportProfile(
                id = "olga",
                displayName = "Olga",
                hasAsthma = false,
                trackedAllergens = mapOf(
                    "Alder" to ExportAllergenThreshold("Alder", 1.0, 11.0, 51.0, 101.0)
                ),
                location = null,
                medicineAssignments = emptyList(),
                trackedSymptoms = emptyList()
            )
        ),
        medicines = listOf(
            ExportMedicine("med-cetirizine", "Cetirizine", "Tablet"),
            ExportMedicine("med-nasal", "Avamys", "NasalSpray")
        ),
        doseHistory = listOf(
            ExportDoseEntry(
                profileId = "ryan",
                medicineId = "med-cetirizine",
                slotIndex = 0,
                date = "2026-03-28",
                confirmedAtMillis = 1711612800000L,
                confirmed = true,
                medicineName = "Cetirizine",
                dose = 1,
                medicineType = "Tablet",
                reminderHour = 8
            )
        ),
        symptomEntries = listOf(
            ExportSymptomEntry(
                profileId = "ryan",
                date = "2026-03-27",
                ratingsJson = """[{"symptomName":"Sneezing","severity":3}]""",
                loggedAtMillis = 1711526400000L,
                peakPollenJson = """{"Birch":45.5,"Grass":12.0}""",
                peakAqi = 42,
                peakPm25 = 8.3,
                peakPm10 = 15.7
            )
        ),
        locationSettings = ExportLocation(
            mode = "Manual",
            manualLatitude = 52.4064,
            manualLongitude = 16.9252,
            manualDisplayName = "Poznań",
            gpsLatitude = 52.41,
            gpsLongitude = 16.93,
            gpsDisplayName = "GPS Poznań"
        ),
        notificationPrefs = ExportNotificationPrefs(
            morningBriefingEnabled = true,
            morningBriefingHour = 7,
            thresholdAlertsEnabled = true,
            compoundRiskAlertsEnabled = true,
            preSeasonAlertsEnabled = false,
            symptomReminderEnabled = true,
            symptomReminderHour = 20
        )
    )

    // ── Full round-trip ────────────────────────────────────────────────

    @Test
    fun `full export data survives serialisation round-trip`() {
        val original = fullExportData()
        val jsonString = exportJson.encodeToString(original)
        val restored = importJson.decodeFromString<ExportData>(jsonString)

        assertEquals(original, restored)
    }

    @Test
    fun `all profiles preserved in round-trip`() {
        val original = fullExportData()
        val jsonString = exportJson.encodeToString(original)
        val restored = importJson.decodeFromString<ExportData>(jsonString)

        assertEquals(2, restored.profiles.size)
        assertEquals("ryan", restored.profiles[0].id)
        assertEquals("olga", restored.profiles[1].id)
    }

    @Test
    fun `profile allergen thresholds preserved exactly`() {
        val original = fullExportData()
        val jsonString = exportJson.encodeToString(original)
        val restored = importJson.decodeFromString<ExportData>(jsonString)

        val birch = restored.profiles[0].trackedAllergens["Birch"]
        assertNotNull(birch)
        assertEquals(1.0, birch!!.low, 0.0)
        assertEquals(11.0, birch.moderate, 0.0)
        assertEquals(51.0, birch.high, 0.0)
        assertEquals(201.0, birch.veryHigh, 0.0)
    }

    @Test
    fun `medicine assignments preserved in round-trip`() {
        val original = fullExportData()
        val jsonString = exportJson.encodeToString(original)
        val restored = importJson.decodeFromString<ExportData>(jsonString)

        val assignments = restored.profiles[0].medicineAssignments
        assertEquals(1, assignments.size)
        assertEquals("med-cetirizine", assignments[0].medicineId)
        assertEquals(1, assignments[0].dose)
        assertEquals(1, assignments[0].timesPerDay)
        assertEquals(listOf(8), assignments[0].reminderHours)
    }

    @Test
    fun `tracked symptoms preserved in round-trip`() {
        val original = fullExportData()
        val jsonString = exportJson.encodeToString(original)
        val restored = importJson.decodeFromString<ExportData>(jsonString)

        val symptoms = restored.profiles[0].trackedSymptoms
        assertEquals(2, symptoms.size)
        assertEquals("Sneezing", symptoms[0].id)
        assertTrue(symptoms[0].isDefault)
        assertEquals("custom-1", symptoms[1].id)
        assertEquals("Headache", symptoms[1].displayName)
        assertTrue(!symptoms[1].isDefault)
    }

    @Test
    fun `dose history entries preserved in round-trip`() {
        val original = fullExportData()
        val jsonString = exportJson.encodeToString(original)
        val restored = importJson.decodeFromString<ExportData>(jsonString)

        assertEquals(1, restored.doseHistory.size)
        val entry = restored.doseHistory[0]
        assertEquals("ryan", entry.profileId)
        assertEquals("med-cetirizine", entry.medicineId)
        assertEquals(0, entry.slotIndex)
        assertEquals("2026-03-28", entry.date)
        assertEquals(1711612800000L, entry.confirmedAtMillis)
        assertTrue(entry.confirmed)
        assertEquals("Cetirizine", entry.medicineName)
        assertEquals(1, entry.dose)
        assertEquals("Tablet", entry.medicineType)
        assertEquals(8, entry.reminderHour)
    }

    @Test
    fun `symptom entries preserved in round-trip`() {
        val original = fullExportData()
        val jsonString = exportJson.encodeToString(original)
        val restored = importJson.decodeFromString<ExportData>(jsonString)

        assertEquals(1, restored.symptomEntries.size)
        val entry = restored.symptomEntries[0]
        assertEquals("ryan", entry.profileId)
        assertEquals("2026-03-27", entry.date)
        assertEquals("""[{"symptomName":"Sneezing","severity":3}]""", entry.ratingsJson)
        assertEquals(1711526400000L, entry.loggedAtMillis)
        assertEquals("""{"Birch":45.5,"Grass":12.0}""", entry.peakPollenJson)
        assertEquals(42, entry.peakAqi)
        assertEquals(8.3, entry.peakPm25, 0.001)
        assertEquals(15.7, entry.peakPm10, 0.001)
    }

    @Test
    fun `location settings preserved including GPS fields`() {
        val original = fullExportData()
        val jsonString = exportJson.encodeToString(original)
        val restored = importJson.decodeFromString<ExportData>(jsonString)

        val loc = restored.locationSettings
        assertEquals("Manual", loc.mode)
        assertEquals(52.4064, loc.manualLatitude, 0.0001)
        assertEquals(16.9252, loc.manualLongitude, 0.0001)
        assertEquals("Poznań", loc.manualDisplayName)
        assertEquals(52.41, loc.gpsLatitude!!, 0.001)
        assertEquals(16.93, loc.gpsLongitude!!, 0.001)
        assertEquals("GPS Poznań", loc.gpsDisplayName)
    }

    @Test
    fun `notification preferences preserved in round-trip`() {
        val original = fullExportData()
        val jsonString = exportJson.encodeToString(original)
        val restored = importJson.decodeFromString<ExportData>(jsonString)

        val np = restored.notificationPrefs
        assertTrue(np.morningBriefingEnabled)
        assertEquals(7, np.morningBriefingHour)
        assertTrue(np.thresholdAlertsEnabled)
        assertTrue(np.compoundRiskAlertsEnabled)
        assertTrue(!np.preSeasonAlertsEnabled)
        assertTrue(np.symptomReminderEnabled)
        assertEquals(20, np.symptomReminderHour)
    }

    // ── Version validation ─────────────────────────────────────────────

    @Test
    fun `version 1 passes validation`() {
        val data = fullExportData()
        val jsonString = exportJson.encodeToString(data)
        val restored = importJson.decodeFromString<ExportData>(jsonString)

        // Mirrors the require() check in AppDataImporter.import()
        assertEquals(1, restored.version)
    }

    @Test
    fun `unsupported version is detectable`() {
        val data = fullExportData().copy(version = 2)
        val jsonString = exportJson.encodeToString(data)
        val restored = importJson.decodeFromString<ExportData>(jsonString)

        // Mirrors the require(data.version == 1) in AppDataImporter
        try {
            require(restored.version == 1) {
                "Unsupported export version: ${restored.version}"
            }
            fail("Expected IllegalArgumentException for version 2")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Unsupported export version: 2"))
        }
    }

    @Test
    fun `version 0 is rejected`() {
        val data = fullExportData().copy(version = 0)
        val jsonString = exportJson.encodeToString(data)
        val restored = importJson.decodeFromString<ExportData>(jsonString)

        assertTrue(restored.version != 1)
    }

    @Test
    fun `negative version is rejected`() {
        val data = fullExportData().copy(version = -1)
        val jsonString = exportJson.encodeToString(data)
        val restored = importJson.decodeFromString<ExportData>(jsonString)

        assertTrue(restored.version != 1)
    }

    // ── Order-dependent restoration ────────────────────────────────────

    @Test
    fun `medicines appear before profiles that reference them`() {
        // Verifies the export structure supports order-dependent restoration:
        // medicines must be imported before profiles that reference them via
        // medicineAssignments. The ExportData structure preserves list ordering.
        val data = fullExportData()
        val jsonString = exportJson.encodeToString(data)
        val restored = importJson.decodeFromString<ExportData>(jsonString)

        val medicineIds = restored.medicines.map { it.id }.toSet()
        for (profile in restored.profiles) {
            for (assignment in profile.medicineAssignments) {
                assertTrue(
                    "Profile '${profile.displayName}' references medicine '${assignment.medicineId}' " +
                            "which must exist in the medicines list",
                    medicineIds.contains(assignment.medicineId)
                )
            }
        }
    }

    @Test
    fun `dose history references valid profiles and medicines`() {
        val data = fullExportData()
        val jsonString = exportJson.encodeToString(data)
        val restored = importJson.decodeFromString<ExportData>(jsonString)

        val profileIds = restored.profiles.map { it.id }.toSet()
        val medicineIds = restored.medicines.map { it.id }.toSet()

        for (entry in restored.doseHistory) {
            assertTrue(
                "Dose entry references unknown profile '${entry.profileId}'",
                profileIds.contains(entry.profileId)
            )
            assertTrue(
                "Dose entry references unknown medicine '${entry.medicineId}'",
                medicineIds.contains(entry.medicineId)
            )
        }
    }

    @Test
    fun `symptom entries reference valid profiles`() {
        val data = fullExportData()
        val jsonString = exportJson.encodeToString(data)
        val restored = importJson.decodeFromString<ExportData>(jsonString)

        val profileIds = restored.profiles.map { it.id }.toSet()
        for (entry in restored.symptomEntries) {
            assertTrue(
                "Symptom entry references unknown profile '${entry.profileId}'",
                profileIds.contains(entry.profileId)
            )
        }
    }

    // ── Edge cases: empty collections ──────────────────────────────────

    @Test
    fun `empty profiles list round-trips`() {
        val data = fullExportData().copy(profiles = emptyList())
        val jsonString = exportJson.encodeToString(data)
        val restored = importJson.decodeFromString<ExportData>(jsonString)

        assertTrue(restored.profiles.isEmpty())
    }

    @Test
    fun `empty medicines list round-trips`() {
        val data = fullExportData().copy(medicines = emptyList())
        val jsonString = exportJson.encodeToString(data)
        val restored = importJson.decodeFromString<ExportData>(jsonString)

        assertTrue(restored.medicines.isEmpty())
    }

    @Test
    fun `empty dose history round-trips`() {
        val data = fullExportData().copy(doseHistory = emptyList())
        val jsonString = exportJson.encodeToString(data)
        val restored = importJson.decodeFromString<ExportData>(jsonString)

        assertTrue(restored.doseHistory.isEmpty())
    }

    @Test
    fun `empty symptom entries round-trips`() {
        val data = fullExportData().copy(symptomEntries = emptyList())
        val jsonString = exportJson.encodeToString(data)
        val restored = importJson.decodeFromString<ExportData>(jsonString)

        assertTrue(restored.symptomEntries.isEmpty())
    }

    @Test
    fun `minimal export data with all empty collections round-trips`() {
        val minimal = ExportData(
            version = 1,
            exportedAt = "2026-01-01T00:00:00Z",
            profiles = emptyList(),
            medicines = emptyList(),
            doseHistory = emptyList(),
            symptomEntries = emptyList(),
            locationSettings = ExportLocation(
                mode = "Manual",
                manualLatitude = 0.0,
                manualLongitude = 0.0,
                manualDisplayName = ""
            ),
            notificationPrefs = ExportNotificationPrefs(
                morningBriefingEnabled = false,
                morningBriefingHour = 6,
                thresholdAlertsEnabled = false,
                compoundRiskAlertsEnabled = false,
                preSeasonAlertsEnabled = false,
                symptomReminderEnabled = false,
                symptomReminderHour = 20
            )
        )
        val jsonString = exportJson.encodeToString(minimal)
        val restored = importJson.decodeFromString<ExportData>(jsonString)

        assertEquals(minimal, restored)
    }

    // ── Edge cases: null optional fields ───────────────────────────────

    @Test
    fun `profile with null location round-trips`() {
        val profile = ExportProfile(
            id = "test",
            displayName = "Test",
            hasAsthma = false,
            trackedAllergens = emptyMap(),
            location = null
        )
        val data = fullExportData().copy(profiles = listOf(profile))
        val jsonString = exportJson.encodeToString(data)
        val restored = importJson.decodeFromString<ExportData>(jsonString)

        assertNull(restored.profiles[0].location)
    }

    @Test
    fun `location settings with null GPS fields round-trips`() {
        val loc = ExportLocation(
            mode = "Manual",
            manualLatitude = 52.0,
            manualLongitude = 17.0,
            manualDisplayName = "Test",
            gpsLatitude = null,
            gpsLongitude = null,
            gpsDisplayName = null
        )
        val data = fullExportData().copy(locationSettings = loc)
        val jsonString = exportJson.encodeToString(data)
        val restored = importJson.decodeFromString<ExportData>(jsonString)

        assertNull(restored.locationSettings.gpsLatitude)
        assertNull(restored.locationSettings.gpsLongitude)
        assertNull(restored.locationSettings.gpsDisplayName)
    }

    // ── Forward compatibility ──────────────────────────────────────────

    @Test
    fun `unknown JSON keys are ignored on import`() {
        // Simulates a newer exporter adding fields that an older importer doesn't know about
        val data = fullExportData()
        val jsonString = exportJson.encodeToString(data)

        // Inject an unknown top-level key
        val modified = jsonString.replaceFirst(
            "\"version\"",
            "\"futureField\": \"some-value\",\n    \"version\""
        )

        val restored = importJson.decodeFromString<ExportData>(modified)
        assertEquals(data, restored)
    }

    @Test
    fun `unknown nested key in profile is ignored on import`() {
        val data = fullExportData()
        val jsonString = exportJson.encodeToString(data)

        // Inject an unknown key inside the first profile object
        val modified = jsonString.replaceFirst(
            "\"hasAsthma\"",
            "\"unknownProfileField\": true,\n            \"hasAsthma\""
        )

        val restored = importJson.decodeFromString<ExportData>(modified)
        assertEquals(data.profiles.size, restored.profiles.size)
        assertEquals(data.profiles[0].id, restored.profiles[0].id)
    }

    // ── Malformed JSON ─────────────────────────────────────────────────

    @Test(expected = kotlinx.serialization.SerializationException::class)
    fun `completely invalid JSON throws SerializationException`() {
        importJson.decodeFromString<ExportData>("this is not json")
    }

    @Test(expected = kotlinx.serialization.SerializationException::class)
    fun `empty string throws SerializationException`() {
        importJson.decodeFromString<ExportData>("")
    }

    @Test(expected = kotlinx.serialization.SerializationException::class)
    fun `missing required field throws SerializationException`() {
        // ExportData requires exportedAt, profiles, medicines, etc.
        val incomplete = """{"version": 1}"""
        importJson.decodeFromString<ExportData>(incomplete)
    }

    // ── Profile with all six allergen types ─────────────────────────────

    @Test
    fun `profile with all six pollen types round-trips`() {
        val allAllergens = mapOf(
            "Birch" to ExportAllergenThreshold("Birch", 1.0, 11.0, 51.0, 201.0),
            "Alder" to ExportAllergenThreshold("Alder", 1.0, 11.0, 51.0, 101.0),
            "Grass" to ExportAllergenThreshold("Grass", 1.0, 6.0, 31.0, 81.0),
            "Mugwort" to ExportAllergenThreshold("Mugwort", 1.0, 11.0, 51.0, 101.0),
            "Ragweed" to ExportAllergenThreshold("Ragweed", 1.0, 6.0, 31.0, 81.0),
            "Olive" to ExportAllergenThreshold("Olive", 1.0, 11.0, 51.0, 201.0)
        )
        val profile = ExportProfile(
            id = "all-allergens",
            displayName = "All Allergens",
            hasAsthma = true,
            trackedAllergens = allAllergens
        )
        val data = fullExportData().copy(profiles = listOf(profile))
        val jsonString = exportJson.encodeToString(data)
        val restored = importJson.decodeFromString<ExportData>(jsonString)

        assertEquals(6, restored.profiles[0].trackedAllergens.size)
        for ((typeName, threshold) in allAllergens) {
            val restoredThreshold = restored.profiles[0].trackedAllergens[typeName]
            assertNotNull("Missing allergen $typeName after round-trip", restoredThreshold)
            assertEquals(threshold, restoredThreshold)
        }
    }

    // ── Multiple medicine reminder hours ────────────────────────────────

    @Test
    fun `medicine assignment with multiple reminder hours round-trips`() {
        val assignment = ExportMedicineAssignment(
            medicineId = "med-test",
            dose = 2,
            timesPerDay = 3,
            reminderHours = listOf(8, 14, 20)
        )
        val profile = ExportProfile(
            id = "multi-dose",
            displayName = "Multi-Dose",
            hasAsthma = false,
            trackedAllergens = emptyMap(),
            medicineAssignments = listOf(assignment)
        )
        val data = fullExportData().copy(profiles = listOf(profile))
        val jsonString = exportJson.encodeToString(data)
        val restored = importJson.decodeFromString<ExportData>(jsonString)

        assertEquals(listOf(8, 14, 20), restored.profiles[0].medicineAssignments[0].reminderHours)
    }

    // ── Large dataset stability ─────────────────────────────────────────

    @Test
    fun `many profiles and entries round-trip correctly`() {
        val profiles = (1..10).map { i ->
            ExportProfile(
                id = "profile-$i",
                displayName = "User $i",
                hasAsthma = i % 2 == 0,
                trackedAllergens = mapOf(
                    "Birch" to ExportAllergenThreshold("Birch", 1.0, 11.0, 51.0, 201.0)
                )
            )
        }
        val doseEntries = (1..50).map { i ->
            ExportDoseEntry(
                profileId = "profile-${(i % 10) + 1}",
                medicineId = "med-1",
                slotIndex = 0,
                date = "2026-03-%02d".format(i % 28 + 1),
                confirmedAtMillis = 1711612800000L + i * 86400000L,
                confirmed = true,
                medicineName = "Med",
                dose = 1,
                medicineType = "Tablet",
                reminderHour = 8
            )
        }
        val symptomEntries = (1..30).map { i ->
            ExportSymptomEntry(
                profileId = "profile-${(i % 10) + 1}",
                date = "2026-03-%02d".format(i % 28 + 1),
                ratingsJson = "[]",
                loggedAtMillis = 1711612800000L + i * 86400000L,
                peakPollenJson = "{}",
                peakAqi = 30 + i,
                peakPm25 = 5.0 + i,
                peakPm10 = 10.0 + i
            )
        }

        val data = fullExportData().copy(
            profiles = profiles,
            doseHistory = doseEntries,
            symptomEntries = symptomEntries
        )
        val jsonString = exportJson.encodeToString(data)
        val restored = importJson.decodeFromString<ExportData>(jsonString)

        assertEquals(10, restored.profiles.size)
        assertEquals(50, restored.doseHistory.size)
        assertEquals(30, restored.symptomEntries.size)
        assertEquals(data, restored)
    }

    // ── GPS location mode ───────────────────────────────────────────────

    @Test
    fun `GPS location mode round-trips`() {
        val loc = ExportLocation(
            mode = "Gps",
            manualLatitude = 52.4064,
            manualLongitude = 16.9252,
            manualDisplayName = "Poznań",
            gpsLatitude = 52.41,
            gpsLongitude = 16.93,
            gpsDisplayName = "GPS Location"
        )
        val data = fullExportData().copy(locationSettings = loc)
        val jsonString = exportJson.encodeToString(data)
        val restored = importJson.decodeFromString<ExportData>(jsonString)

        assertEquals("Gps", restored.locationSettings.mode)
    }

    // ── Import summary format ───────────────────────────────────────────

    @Test
    fun `import summary string matches expected format`() {
        // Mirrors the buildString block in AppDataImporter.import()
        val data = fullExportData()
        val summary = buildString {
            append("Imported ${data.profiles.size} profiles")
            append(", ${data.medicines.size} medicines")
            append(", ${data.doseHistory.size} dose entries")
            append(", ${data.symptomEntries.size} symptom entries")
        }

        assertEquals("Imported 2 profiles, 2 medicines, 1 dose entries, 1 symptom entries", summary)
    }

    @Test
    fun `import summary for empty data`() {
        val data = fullExportData().copy(
            profiles = emptyList(),
            medicines = emptyList(),
            doseHistory = emptyList(),
            symptomEntries = emptyList()
        )
        val summary = buildString {
            append("Imported ${data.profiles.size} profiles")
            append(", ${data.medicines.size} medicines")
            append(", ${data.doseHistory.size} dose entries")
            append(", ${data.symptomEntries.size} symptom entries")
        }

        assertEquals("Imported 0 profiles, 0 medicines, 0 dose entries, 0 symptom entries", summary)
    }

    // ── Encoding defaults ───────────────────────────────────────────────

    @Test
    fun `default values are encoded and survive round-trip`() {
        // ExportProfile has defaults for location (null), medicineAssignments (empty),
        // trackedSymptoms (empty). With encodeDefaults=true these should appear in JSON.
        val profile = ExportProfile(
            id = "defaults-test",
            displayName = "Defaults",
            hasAsthma = false,
            trackedAllergens = emptyMap()
        )
        val data = fullExportData().copy(profiles = listOf(profile))
        val jsonString = exportJson.encodeToString(data)

        // Verify default fields are present in the JSON output
        assertTrue(jsonString.contains("\"medicineAssignments\""))
        assertTrue(jsonString.contains("\"trackedSymptoms\""))

        val restored = importJson.decodeFromString<ExportData>(jsonString)
        assertEquals(profile, restored.profiles[0])
    }

    // ── Cross-configuration compatibility ───────────────────────────────

    @Test
    fun `export config JSON can be read by import config JSON`() {
        // Export uses prettyPrint + encodeDefaults; import uses ignoreUnknownKeys + encodeDefaults.
        // Verify the output of one is readable by the other.
        val original = fullExportData()
        val exported = exportJson.encodeToString(original)
        val imported = importJson.decodeFromString<ExportData>(exported)

        assertEquals(original, imported)
    }

    @Test
    fun `compact JSON without pretty print can be imported`() {
        val compactJson = Json { encodeDefaults = true }
        val original = fullExportData()
        val compact = compactJson.encodeToString(original)

        // Import config (ignoreUnknownKeys) should handle compact JSON fine
        val restored = importJson.decodeFromString<ExportData>(compact)
        assertEquals(original, restored)
    }
}
