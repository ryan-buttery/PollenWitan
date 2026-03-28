package com.ryan.pollenwitan.data.repository

import com.ryan.pollenwitan.data.repository.DoseTrackingRepository.Keys
import com.ryan.pollenwitan.domain.model.DoseConfirmation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DoseTrackingRepositoryTest {

    // ── Key generation ──────────────────────────────────────────────────

    @Test
    fun `confirmed key follows expected format`() {
        val key = Keys.confirmed("profile-1", "med-abc", 0)
        assertEquals("confirmed_profile-1_med-abc_0", key)
    }

    @Test
    fun `confirmed key includes slot index`() {
        val key0 = Keys.confirmed("p1", "med1", 0)
        val key1 = Keys.confirmed("p1", "med1", 1)
        assertNotEquals(key0, key1)
        assertEquals("confirmed_p1_med1_0", key0)
        assertEquals("confirmed_p1_med1_1", key1)
    }

    @Test
    fun `confirmed key differentiates profiles`() {
        val keyA = Keys.confirmed("profile-a", "med1", 0)
        val keyB = Keys.confirmed("profile-b", "med1", 0)
        assertNotEquals(keyA, keyB)
    }

    @Test
    fun `confirmed key differentiates medicines`() {
        val keyA = Keys.confirmed("p1", "med-alpha", 0)
        val keyB = Keys.confirmed("p1", "med-beta", 0)
        assertNotEquals(keyA, keyB)
    }

    // ── Key parsing (parseConfirmation) ─────────────────────────────────

    @Test
    fun `parseConfirmation round-trips simple key`() {
        val profileId = "profile-1"
        val key = Keys.confirmed(profileId, "med-abc", 2)
        val result = Keys.parseConfirmation(profileId, key)

        assertNotNull(result)
        assertEquals(DoseConfirmation("med-abc", 2), result)
    }

    @Test
    fun `parseConfirmation round-trips slot zero`() {
        val profileId = "p1"
        val key = Keys.confirmed(profileId, "ibuprofen", 0)
        val result = Keys.parseConfirmation(profileId, key)

        assertNotNull(result)
        assertEquals(DoseConfirmation("ibuprofen", 0), result)
    }

    @Test
    fun `parseConfirmation handles medicine id with underscores`() {
        val profileId = "profile-1"
        val medicineId = "allergy_med_xl"
        val key = Keys.confirmed(profileId, medicineId, 1)
        // Key: "confirmed_profile-1_allergy_med_xl_1"
        val result = Keys.parseConfirmation(profileId, key)

        assertNotNull(result)
        assertEquals(medicineId, result!!.medicineId)
        assertEquals(1, result.slotIndex)
    }

    @Test
    fun `parseConfirmation handles single-char medicine id`() {
        val profileId = "p1"
        val key = Keys.confirmed(profileId, "x", 3)
        val result = Keys.parseConfirmation(profileId, key)

        assertNotNull(result)
        assertEquals(DoseConfirmation("x", 3), result)
    }

    @Test
    fun `parseConfirmation returns null for wrong profile prefix`() {
        val key = Keys.confirmed("profile-A", "med1", 0)
        val result = Keys.parseConfirmation("profile-B", key)
        assertNull(result)
    }

    @Test
    fun `parseConfirmation returns null for non-confirmed key`() {
        val result = Keys.parseConfirmation("p1", "tracking_date")
        assertNull(result)
    }

    @Test
    fun `parseConfirmation returns null for empty suffix after prefix`() {
        val result = Keys.parseConfirmation("p1", "confirmed_p1_")
        // After removing prefix "confirmed_p1_" we get "" -> split("_") = [""]
        // size < 2, so null
        assertNull(result)
    }

    @Test
    fun `parseConfirmation returns null when slot index is not a number`() {
        val result = Keys.parseConfirmation("p1", "confirmed_p1_med1_abc")
        assertNull(result)
    }

    @Test
    fun `parseConfirmation returns null for key with only medicine id no slot`() {
        // "confirmed_p1_med1" -> after prefix removal = "med1" -> split = ["med1"] -> size < 2
        val result = Keys.parseConfirmation("p1", "confirmed_p1_med1")
        assertNull(result)
    }

    // ── Date isolation ──────────────────────────────────────────────────

    @Test
    fun `tracking date key is constant`() {
        assertEquals("tracking_date", Keys.TRACKING_DATE)
    }

    @Test
    fun `different days produce different confirmed keys only when profile or medicine differs`() {
        // The key format is date-independent -- date isolation is handled
        // by the tracking_date guard in getConfirmations, not by the key itself.
        // Verify that the same profile+medicine+slot always produces the same key.
        val key1 = Keys.confirmed("p1", "med1", 0)
        val key2 = Keys.confirmed("p1", "med1", 0)
        assertEquals(key1, key2)
    }

    // ── Confirm / unconfirm round-trip via key symmetry ─────────────────

    @Test
    fun `confirm and unconfirm target the same key`() {
        // Both confirmDose and unconfirmDose use Keys.confirmed() to produce the
        // SharedPreferences key. Verify the key is deterministic.
        val profileId = "user-1"
        val medicineId = "cetirizine"
        val slotIndex = 1

        val confirmKey = Keys.confirmed(profileId, medicineId, slotIndex)
        val unconfirmKey = Keys.confirmed(profileId, medicineId, slotIndex)

        assertEquals(confirmKey, unconfirmKey)
    }

    @Test
    fun `generated key can be parsed back to original confirmation`() {
        val profileId = "user-42"
        val medicineId = "loratadine"
        val slotIndex = 2

        val key = Keys.confirmed(profileId, medicineId, slotIndex)
        val parsed = Keys.parseConfirmation(profileId, key)

        assertNotNull(parsed)
        assertEquals(medicineId, parsed!!.medicineId)
        assertEquals(slotIndex, parsed.slotIndex)
    }

    @Test
    fun `multiple slots for same medicine produce distinct parseable keys`() {
        val profileId = "p1"
        val medicineId = "nasal-spray"

        val confirmations = (0..3).map { slot ->
            val key = Keys.confirmed(profileId, medicineId, slot)
            Keys.parseConfirmation(profileId, key)!!
        }

        assertEquals(4, confirmations.toSet().size)
        confirmations.forEachIndexed { index, confirmation ->
            assertEquals(medicineId, confirmation.medicineId)
            assertEquals(index, confirmation.slotIndex)
        }
    }

    @Test
    fun `keys for different profiles do not cross-parse`() {
        val key = Keys.confirmed("alice", "med1", 0)

        val parsedByAlice = Keys.parseConfirmation("alice", key)
        val parsedByBob = Keys.parseConfirmation("bob", key)

        assertNotNull(parsedByAlice)
        assertNull(parsedByBob)
    }
}
