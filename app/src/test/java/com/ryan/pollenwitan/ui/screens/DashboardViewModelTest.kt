package com.ryan.pollenwitan.ui.screens

import com.ryan.pollenwitan.data.repository.ProfileRepository
import com.ryan.pollenwitan.domain.model.AllergenThreshold
import com.ryan.pollenwitan.domain.model.AppLocation
import com.ryan.pollenwitan.domain.model.DoseConfirmation
import com.ryan.pollenwitan.domain.model.Medicine
import com.ryan.pollenwitan.domain.model.MedicineAssignment
import com.ryan.pollenwitan.domain.model.MedicineType
import com.ryan.pollenwitan.domain.model.PollenType
import com.ryan.pollenwitan.domain.model.ProfileLocation
import com.ryan.pollenwitan.domain.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardViewModelTest {

    // ── buildMedicineSlots ────────────────────────────────────────────

    @Test
    fun `empty assignments produce empty slots`() {
        val profile = makeProfile(assignments = emptyList())
        val slots = DashboardLogic.buildMedicineSlots(profile, emptyList(), emptySet())
        assertTrue(slots.isEmpty())
    }

    @Test
    fun `single assignment with one reminder produces one slot`() {
        val profile = makeProfile(
            assignments = listOf(
                MedicineAssignment("med1", dose = 1, timesPerDay = 1, reminderHours = listOf(8))
            )
        )
        val medicines = listOf(Medicine("med1", "Cetirizine", MedicineType.Tablet))

        val slots = DashboardLogic.buildMedicineSlots(profile, medicines, emptySet())

        assertEquals(1, slots.size)
        val slot = slots[0]
        assertEquals("med1", slot.medicineId)
        assertEquals("Cetirizine", slot.medicineName)
        assertEquals(1, slot.dose)
        assertEquals(MedicineType.Tablet, slot.medicineType)
        assertEquals(8, slot.hour)
        assertEquals(0, slot.slotIndex)
        assertFalse(slot.confirmed)
    }

    @Test
    fun `multiple reminder hours produce multiple slots`() {
        val profile = makeProfile(
            assignments = listOf(
                MedicineAssignment("med1", dose = 2, timesPerDay = 3, reminderHours = listOf(8, 14, 20))
            )
        )
        val medicines = listOf(Medicine("med1", "Drops", MedicineType.Eyedrops))

        val slots = DashboardLogic.buildMedicineSlots(profile, medicines, emptySet())

        assertEquals(3, slots.size)
        assertEquals(listOf(0, 1, 2), slots.map { it.slotIndex })
        assertEquals(listOf(8, 14, 20), slots.map { it.hour })
    }

    @Test
    fun `slots are sorted by hour across medicines`() {
        val profile = makeProfile(
            assignments = listOf(
                MedicineAssignment("med1", dose = 1, timesPerDay = 1, reminderHours = listOf(20)),
                MedicineAssignment("med2", dose = 1, timesPerDay = 1, reminderHours = listOf(8))
            )
        )
        val medicines = listOf(
            Medicine("med1", "Evening Med", MedicineType.Tablet),
            Medicine("med2", "Morning Med", MedicineType.Tablet)
        )

        val slots = DashboardLogic.buildMedicineSlots(profile, medicines, emptySet())

        assertEquals(2, slots.size)
        assertEquals("med2", slots[0].medicineId)
        assertEquals("med1", slots[1].medicineId)
    }

    @Test
    fun `confirmed dose is marked as confirmed`() {
        val profile = makeProfile(
            assignments = listOf(
                MedicineAssignment("med1", dose = 1, timesPerDay = 2, reminderHours = listOf(8, 20))
            )
        )
        val medicines = listOf(Medicine("med1", "Cetirizine", MedicineType.Tablet))
        val confirmations = setOf(DoseConfirmation("med1", 0))

        val slots = DashboardLogic.buildMedicineSlots(profile, medicines, confirmations)

        assertEquals(2, slots.size)
        assertTrue("First slot should be confirmed", slots.find { it.slotIndex == 0 }!!.confirmed)
        assertFalse("Second slot should not be confirmed", slots.find { it.slotIndex == 1 }!!.confirmed)
    }

    @Test
    fun `all slots confirmed when all confirmations present`() {
        val profile = makeProfile(
            assignments = listOf(
                MedicineAssignment("med1", dose = 1, timesPerDay = 2, reminderHours = listOf(8, 20))
            )
        )
        val medicines = listOf(Medicine("med1", "Cetirizine", MedicineType.Tablet))
        val confirmations = setOf(
            DoseConfirmation("med1", 0),
            DoseConfirmation("med1", 1)
        )

        val slots = DashboardLogic.buildMedicineSlots(profile, medicines, confirmations)

        assertTrue(slots.all { it.confirmed })
    }

    @Test
    fun `unknown medicine ID is skipped`() {
        val profile = makeProfile(
            assignments = listOf(
                MedicineAssignment("med1", dose = 1, timesPerDay = 1, reminderHours = listOf(8)),
                MedicineAssignment("deleted", dose = 1, timesPerDay = 1, reminderHours = listOf(9))
            )
        )
        val medicines = listOf(Medicine("med1", "Cetirizine", MedicineType.Tablet))

        val slots = DashboardLogic.buildMedicineSlots(profile, medicines, emptySet())

        assertEquals(1, slots.size)
        assertEquals("med1", slots[0].medicineId)
    }

    @Test
    fun `confirmation for wrong medicine does not affect slots`() {
        val profile = makeProfile(
            assignments = listOf(
                MedicineAssignment("med1", dose = 1, timesPerDay = 1, reminderHours = listOf(8))
            )
        )
        val medicines = listOf(Medicine("med1", "Cetirizine", MedicineType.Tablet))
        val confirmations = setOf(DoseConfirmation("other_med", 0))

        val slots = DashboardLogic.buildMedicineSlots(profile, medicines, confirmations)

        assertFalse(slots[0].confirmed)
    }

    @Test
    fun `multiple medicines produce interleaved sorted slots`() {
        val profile = makeProfile(
            assignments = listOf(
                MedicineAssignment("tablet", dose = 1, timesPerDay = 1, reminderHours = listOf(8)),
                MedicineAssignment("spray", dose = 2, timesPerDay = 2, reminderHours = listOf(7, 19))
            )
        )
        val medicines = listOf(
            Medicine("tablet", "Cetirizine", MedicineType.Tablet),
            Medicine("spray", "Nasonex", MedicineType.NasalSpray)
        )

        val slots = DashboardLogic.buildMedicineSlots(profile, medicines, emptySet())

        assertEquals(3, slots.size)
        assertEquals(listOf(7, 8, 19), slots.map { it.hour })
        assertEquals(listOf("spray", "tablet", "spray"), slots.map { it.medicineId })
    }

    // ── DashboardUiState computed properties ──────────────────────────

    @Test
    fun `allDosesConfirmed is false when no slots`() {
        val state = DashboardUiState(medicineSlots = emptyList())
        assertFalse(state.allDosesConfirmed)
    }

    @Test
    fun `allDosesConfirmed is true when all slots confirmed`() {
        val slots = listOf(
            makeSlot("med1", 0, confirmed = true),
            makeSlot("med1", 1, confirmed = true)
        )
        val state = DashboardUiState(medicineSlots = slots)
        assertTrue(state.allDosesConfirmed)
    }

    @Test
    fun `allDosesConfirmed is false when any slot unconfirmed`() {
        val slots = listOf(
            makeSlot("med1", 0, confirmed = true),
            makeSlot("med1", 1, confirmed = false)
        )
        val state = DashboardUiState(medicineSlots = slots)
        assertFalse(state.allDosesConfirmed)
    }

    @Test
    fun `selectedProfile returns matching profile`() {
        val profiles = listOf(
            makeProfile(id = "a", name = "Alice"),
            makeProfile(id = "b", name = "Bob")
        )
        val state = DashboardUiState(profiles = profiles, selectedProfileId = "b")
        assertEquals("Bob", state.selectedProfile?.displayName)
    }

    @Test
    fun `selectedProfile returns null when no match`() {
        val profiles = listOf(makeProfile(id = "a", name = "Alice"))
        val state = DashboardUiState(profiles = profiles, selectedProfileId = "missing")
        assertNull(state.selectedProfile)
    }

    // ── Location resolution ───────────────────────────────────────────

    @Test
    fun `resolveLocation returns global when profile is null`() {
        val global = AppLocation(52.41, 16.93, "Poznan")
        val resolved = ProfileRepository.resolveLocation(null, global)
        assertEquals(global, resolved)
    }

    @Test
    fun `resolveLocation returns global when profile has no override`() {
        val global = AppLocation(52.41, 16.93, "Poznan")
        val profile = makeProfile(location = null)
        val resolved = ProfileRepository.resolveLocation(profile, global)
        assertEquals(global, resolved)
    }

    @Test
    fun `resolveLocation returns profile override when set`() {
        val global = AppLocation(52.41, 16.93, "Poznan")
        val override = ProfileLocation(50.06, 19.94, "Krakow")
        val profile = makeProfile(location = override)
        val resolved = ProfileRepository.resolveLocation(profile, global)
        assertEquals(50.06, resolved.latitude, 0.001)
        assertEquals(19.94, resolved.longitude, 0.001)
        assertEquals("Krakow", resolved.displayName)
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private fun makeProfile(
        id: String = "test",
        name: String = "Test",
        assignments: List<MedicineAssignment> = emptyList(),
        location: ProfileLocation? = null
    ) = UserProfile(
        id = id,
        displayName = name,
        trackedAllergens = mapOf(
            PollenType.Birch to AllergenThreshold(PollenType.Birch, 1.0, 11.0, 51.0, 201.0)
        ),
        hasAsthma = false,
        location = location,
        medicineAssignments = assignments
    )

    private fun makeSlot(
        medicineId: String,
        slotIndex: Int,
        confirmed: Boolean,
        hour: Int = 8
    ) = MedicineSlot(
        medicineId = medicineId,
        medicineName = "Test Med",
        dose = 1,
        medicineType = MedicineType.Tablet,
        hour = hour,
        slotIndex = slotIndex,
        confirmed = confirmed
    )
}
