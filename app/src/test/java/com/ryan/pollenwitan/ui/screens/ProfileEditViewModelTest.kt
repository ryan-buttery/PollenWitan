package com.ryan.pollenwitan.ui.screens

import com.ryan.pollenwitan.domain.model.AllergenThreshold
import com.ryan.pollenwitan.domain.model.MedicineType
import com.ryan.pollenwitan.domain.model.PollenType
import com.ryan.pollenwitan.domain.model.TrackedSymptom
import com.ryan.pollenwitan.domain.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileEditViewModelTest {

    // ── Validation ────────────────────────────────────────────────────

    @Test
    fun `empty name is rejected`() {
        val state = makeState(displayName = "")
        val result = ProfileEditLogic.validate(state)
        assertTrue(result is ProfileEditLogic.ValidationResult.Invalid)
        assertEquals(
            ProfileEditLogic.ValidationReason.NameEmpty,
            (result as ProfileEditLogic.ValidationResult.Invalid).reason
        )
    }

    @Test
    fun `blank name is rejected`() {
        val state = makeState(displayName = "   ")
        val result = ProfileEditLogic.validate(state)
        assertTrue(result is ProfileEditLogic.ValidationResult.Invalid)
        assertEquals(
            ProfileEditLogic.ValidationReason.NameEmpty,
            (result as ProfileEditLogic.ValidationResult.Invalid).reason
        )
    }

    @Test
    fun `no allergens and no discovery mode is rejected`() {
        val state = makeState(
            displayName = "Ryan",
            trackedAllergens = emptySet(),
            discoveryMode = false
        )
        val result = ProfileEditLogic.validate(state)
        assertTrue(result is ProfileEditLogic.ValidationResult.Invalid)
        assertEquals(
            ProfileEditLogic.ValidationReason.NoAllergenOrDiscovery,
            (result as ProfileEditLogic.ValidationResult.Invalid).reason
        )
    }

    @Test
    fun `discovery mode without allergens is valid`() {
        val state = makeState(
            displayName = "Ryan",
            trackedAllergens = emptySet(),
            discoveryMode = true
        )
        val result = ProfileEditLogic.validate(state)
        assertTrue(result is ProfileEditLogic.ValidationResult.Valid)
    }

    @Test
    fun `at least one allergen without discovery mode is valid`() {
        val state = makeState(
            displayName = "Ryan",
            trackedAllergens = setOf(PollenType.Birch),
            discoveryMode = false
        )
        val result = ProfileEditLogic.validate(state)
        assertTrue(result is ProfileEditLogic.ValidationResult.Valid)
    }

    @Test
    fun `allergens and discovery mode together is valid`() {
        val state = makeState(
            displayName = "Ryan",
            trackedAllergens = setOf(PollenType.Birch),
            discoveryMode = true
        )
        val result = ProfileEditLogic.validate(state)
        assertTrue(result is ProfileEditLogic.ValidationResult.Valid)
    }

    // ── Default threshold generation ──────────────────────────────────

    @Test
    fun `default thresholds match documented values for all pollen types`() {
        val birch = UserProfile.defaultThreshold(PollenType.Birch)
        assertEquals(1.0, birch.low, 0.001)
        assertEquals(11.0, birch.moderate, 0.001)
        assertEquals(51.0, birch.high, 0.001)
        assertEquals(201.0, birch.veryHigh, 0.001)

        val grass = UserProfile.defaultThreshold(PollenType.Grass)
        assertEquals(1.0, grass.low, 0.001)
        assertEquals(6.0, grass.moderate, 0.001)
        assertEquals(31.0, grass.high, 0.001)
        assertEquals(81.0, grass.veryHigh, 0.001)

        val alder = UserProfile.defaultThreshold(PollenType.Alder)
        assertEquals(1.0, alder.low, 0.001)
        assertEquals(11.0, alder.moderate, 0.001)
        assertEquals(51.0, alder.high, 0.001)
        assertEquals(101.0, alder.veryHigh, 0.001)
    }

    @Test
    fun `default thresholds preserve pollen type`() {
        PollenType.entries.forEach { type ->
            val threshold = UserProfile.defaultThreshold(type)
            assertEquals(type, threshold.type)
        }
    }

    // ── Location resolution ───────────────────────────────────────────

    @Test
    fun `custom location disabled returns null`() {
        val state = makeState(useCustomLocation = false)
        assertNull(ProfileEditLogic.resolveLocation(state))
    }

    @Test
    fun `valid custom location is resolved`() {
        val state = makeState(
            useCustomLocation = true,
            locationLatitude = "50.06",
            locationLongitude = "19.94",
            locationDisplayName = "Krakow"
        )
        val loc = ProfileEditLogic.resolveLocation(state)!!
        assertEquals(50.06, loc.latitude, 0.001)
        assertEquals(19.94, loc.longitude, 0.001)
        assertEquals("Krakow", loc.displayName)
    }

    @Test
    fun `non-numeric latitude returns null`() {
        val state = makeState(
            useCustomLocation = true,
            locationLatitude = "abc",
            locationLongitude = "19.94",
            locationDisplayName = "Krakow"
        )
        assertNull(ProfileEditLogic.resolveLocation(state))
    }

    @Test
    fun `non-numeric longitude returns null`() {
        val state = makeState(
            useCustomLocation = true,
            locationLatitude = "50.06",
            locationLongitude = "xyz",
            locationDisplayName = "Krakow"
        )
        assertNull(ProfileEditLogic.resolveLocation(state))
    }

    @Test
    fun `blank display name returns null`() {
        val state = makeState(
            useCustomLocation = true,
            locationLatitude = "50.06",
            locationLongitude = "19.94",
            locationDisplayName = "  "
        )
        assertNull(ProfileEditLogic.resolveLocation(state))
    }

    @Test
    fun `location display name is trimmed`() {
        val state = makeState(
            useCustomLocation = true,
            locationLatitude = "50.06",
            locationLongitude = "19.94",
            locationDisplayName = "  Krakow  "
        )
        assertEquals("Krakow", ProfileEditLogic.resolveLocation(state)!!.displayName)
    }

    // ── Medicine assignment serialisation ─────────────────────────────

    @Test
    fun `valid assignment is converted`() {
        val assignments = listOf(makeAssignmentUi("med1", dose = "2", timesPerDay = "3"))
        val result = ProfileEditLogic.buildMedicineAssignments(assignments)
        assertEquals(1, result.size)
        assertEquals("med1", result[0].medicineId)
        assertEquals(2, result[0].dose)
        assertEquals(3, result[0].timesPerDay)
    }

    @Test
    fun `non-numeric dose is dropped`() {
        val assignments = listOf(makeAssignmentUi("med1", dose = "abc", timesPerDay = "1"))
        val result = ProfileEditLogic.buildMedicineAssignments(assignments)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `non-numeric times per day is dropped`() {
        val assignments = listOf(makeAssignmentUi("med1", dose = "1", timesPerDay = "abc"))
        val result = ProfileEditLogic.buildMedicineAssignments(assignments)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `zero dose is dropped`() {
        val assignments = listOf(makeAssignmentUi("med1", dose = "0", timesPerDay = "1"))
        val result = ProfileEditLogic.buildMedicineAssignments(assignments)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `negative dose is dropped`() {
        val assignments = listOf(makeAssignmentUi("med1", dose = "-1", timesPerDay = "1"))
        val result = ProfileEditLogic.buildMedicineAssignments(assignments)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `zero times per day is dropped`() {
        val assignments = listOf(makeAssignmentUi("med1", dose = "1", timesPerDay = "0"))
        val result = ProfileEditLogic.buildMedicineAssignments(assignments)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `mixed valid and invalid assignments filters correctly`() {
        val assignments = listOf(
            makeAssignmentUi("med1", dose = "2", timesPerDay = "1"),
            makeAssignmentUi("med2", dose = "abc", timesPerDay = "1"),
            makeAssignmentUi("med3", dose = "1", timesPerDay = "2")
        )
        val result = ProfileEditLogic.buildMedicineAssignments(assignments)
        assertEquals(2, result.size)
        assertEquals("med1", result[0].medicineId)
        assertEquals("med3", result[1].medicineId)
    }

    @Test
    fun `validation rejects more reminder hours than times per day`() {
        val state = makeState(
            medicineAssignments = listOf(
                makeAssignmentUi("med1", dose = "1", timesPerDay = "2", reminderHours = listOf(8, 14, 20))
            )
        )
        val result = ProfileEditLogic.validate(state)
        assertTrue(result is ProfileEditLogic.ValidationResult.Invalid)
        assertEquals(
            ProfileEditLogic.ValidationReason.TooManyReminderHours,
            (result as ProfileEditLogic.ValidationResult.Invalid).reason
        )
    }

    @Test
    fun `validation accepts equal reminder hours and times per day`() {
        val state = makeState(
            medicineAssignments = listOf(
                makeAssignmentUi("med1", dose = "1", timesPerDay = "3", reminderHours = listOf(8, 14, 20))
            )
        )
        assertTrue(ProfileEditLogic.validate(state) is ProfileEditLogic.ValidationResult.Valid)
    }

    @Test
    fun `validation accepts fewer reminder hours than times per day`() {
        val state = makeState(
            medicineAssignments = listOf(
                makeAssignmentUi("med1", dose = "1", timesPerDay = "3", reminderHours = listOf(8))
            )
        )
        assertTrue(ProfileEditLogic.validate(state) is ProfileEditLogic.ValidationResult.Valid)
    }

    @Test
    fun `validation rejects out-of-range reminder hour`() {
        val state = makeState(
            medicineAssignments = listOf(
                makeAssignmentUi("med1", dose = "1", timesPerDay = "1", reminderHours = listOf(24))
            )
        )
        val result = ProfileEditLogic.validate(state)
        assertTrue(result is ProfileEditLogic.ValidationResult.Invalid)
        assertEquals(
            ProfileEditLogic.ValidationReason.InvalidReminderHour,
            (result as ProfileEditLogic.ValidationResult.Invalid).reason
        )
    }

    @Test
    fun `validation accepts midnight reminder hour`() {
        val state = makeState(
            medicineAssignments = listOf(
                makeAssignmentUi("med1", dose = "1", timesPerDay = "1", reminderHours = listOf(0))
            )
        )
        assertTrue(ProfileEditLogic.validate(state) is ProfileEditLogic.ValidationResult.Valid)
    }

    @Test
    fun `reminder hours are preserved in assignment`() {
        val assignments = listOf(
            makeAssignmentUi("med1", dose = "1", timesPerDay = "3", reminderHours = listOf(7, 13, 21))
        )
        val result = ProfileEditLogic.buildMedicineAssignments(assignments)
        assertEquals(listOf(7, 13, 21), result[0].reminderHours)
    }

    // ── buildProfile ──────────────────────────────────────────────────

    @Test
    fun `buildProfile trims display name`() {
        val state = makeState(displayName = "  Ryan  ")
        val profile = ProfileEditLogic.buildProfile(state)
        assertEquals("Ryan", profile.displayName)
    }

    @Test
    fun `buildProfile only includes tracked allergens in thresholds`() {
        val birchThreshold = UserProfile.defaultThreshold(PollenType.Birch)
        val grassThreshold = UserProfile.defaultThreshold(PollenType.Grass)
        val state = makeState(
            trackedAllergens = setOf(PollenType.Birch),
            thresholds = mapOf(
                PollenType.Birch to birchThreshold,
                PollenType.Grass to grassThreshold
            )
        )
        val profile = ProfileEditLogic.buildProfile(state)
        assertEquals(1, profile.trackedAllergens.size)
        assertTrue(PollenType.Birch in profile.trackedAllergens)
        assertFalse(PollenType.Grass in profile.trackedAllergens)
    }

    @Test
    fun `buildProfile passes through hasAsthma flag`() {
        val state = makeState(hasAsthma = true)
        assertTrue(ProfileEditLogic.buildProfile(state).hasAsthma)
        val state2 = makeState(hasAsthma = false)
        assertFalse(ProfileEditLogic.buildProfile(state2).hasAsthma)
    }

    @Test
    fun `buildProfile passes through discoveryMode`() {
        val state = makeState(discoveryMode = true, trackedAllergens = emptySet())
        assertTrue(ProfileEditLogic.buildProfile(state).discoveryMode)
    }

    @Test
    fun `buildProfile preserves tracked symptoms`() {
        val symptoms = listOf(
            TrackedSymptom("Sneezing", "Sneezing", true),
            TrackedSymptom("custom1", "My Symptom", false)
        )
        val state = makeState(trackedSymptoms = symptoms)
        val profile = ProfileEditLogic.buildProfile(state)
        assertEquals(2, profile.trackedSymptoms.size)
        assertEquals("My Symptom", profile.trackedSymptoms[1].displayName)
    }

    @Test
    fun `buildProfile sets profile ID from state`() {
        val state = makeState(profileId = "test-123")
        assertEquals("test-123", ProfileEditLogic.buildProfile(state).id)
    }

    @Test
    fun `buildProfile without custom location sets null`() {
        val state = makeState(useCustomLocation = false)
        assertNull(ProfileEditLogic.buildProfile(state).location)
    }

    @Test
    fun `buildProfile with custom location sets ProfileLocation`() {
        val state = makeState(
            useCustomLocation = true,
            locationLatitude = "50.06",
            locationLongitude = "19.94",
            locationDisplayName = "Krakow"
        )
        val loc = ProfileEditLogic.buildProfile(state).location!!
        assertEquals(50.06, loc.latitude, 0.001)
        assertEquals(19.94, loc.longitude, 0.001)
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private fun makeState(
        profileId: String = "test-id",
        displayName: String = "Test",
        hasAsthma: Boolean = false,
        discoveryMode: Boolean = false,
        trackedAllergens: Set<PollenType> = setOf(PollenType.Birch),
        thresholds: Map<PollenType, AllergenThreshold> = trackedAllergens.associateWith {
            UserProfile.defaultThreshold(it)
        },
        useCustomLocation: Boolean = false,
        locationLatitude: String = "",
        locationLongitude: String = "",
        locationDisplayName: String = "",
        medicineAssignments: List<MedicineAssignmentUiState> = emptyList(),
        trackedSymptoms: List<TrackedSymptom> = UserProfile.defaultSymptoms()
    ) = ProfileEditUiState(
        profileId = profileId,
        displayName = displayName,
        hasAsthma = hasAsthma,
        discoveryMode = discoveryMode,
        trackedAllergens = trackedAllergens,
        thresholds = thresholds,
        useCustomLocation = useCustomLocation,
        locationLatitude = locationLatitude,
        locationLongitude = locationLongitude,
        locationDisplayName = locationDisplayName,
        medicineAssignments = medicineAssignments,
        trackedSymptoms = trackedSymptoms
    )

    private fun makeAssignmentUi(
        medicineId: String,
        dose: String,
        timesPerDay: String,
        reminderHours: List<Int> = listOf(8)
    ) = MedicineAssignmentUiState(
        medicineId = medicineId,
        medicineName = "Test Med",
        medicineType = MedicineType.Tablet,
        dose = dose,
        timesPerDay = timesPerDay,
        reminderHours = reminderHours
    )
}
