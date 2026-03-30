package com.ryan.pollenwitan.ui.screens

import com.ryan.pollenwitan.data.local.DoseHistoryEntity
import com.ryan.pollenwitan.domain.model.MedicineAssignment
import com.ryan.pollenwitan.domain.model.SymptomDiaryEntry
import com.ryan.pollenwitan.domain.model.SymptomRating
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SymptomTrendsViewModelTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ── expectedDosesPerDay ───────────────────────────────────────────

    @Test
    fun `empty assignments yield zero expected doses`() {
        assertEquals(0, SymptomTrendsLogic.expectedDosesPerDay(emptyList()))
    }

    @Test
    fun `single assignment counts reminder hours`() {
        val assignments = listOf(
            MedicineAssignment("med1", dose = 1, timesPerDay = 3, reminderHours = listOf(8, 14, 20))
        )
        assertEquals(3, SymptomTrendsLogic.expectedDosesPerDay(assignments))
    }

    @Test
    fun `multiple assignments sum all reminder hours`() {
        val assignments = listOf(
            MedicineAssignment("med1", dose = 1, timesPerDay = 2, reminderHours = listOf(8, 20)),
            MedicineAssignment("med2", dose = 2, timesPerDay = 1, reminderHours = listOf(9))
        )
        assertEquals(3, SymptomTrendsLogic.expectedDosesPerDay(assignments))
    }

    // ── buildSnapshots — date range ───────────────────────────────────

    @Test
    fun `week range produces 7 snapshots (day 0 through day 6)`() {
        val start = LocalDate.of(2025, 6, 1)
        val snapshots = SymptomTrendsLogic.buildSnapshots(
            emptyList(), emptyList(), 0, start, 7, json
        )
        assertEquals(7, snapshots.size)
        assertEquals(start, snapshots.first().date)
        assertEquals(start.plusDays(6), snapshots.last().date)
    }

    @Test
    fun `month range produces 30 snapshots`() {
        val start = LocalDate.of(2025, 6, 1)
        val snapshots = SymptomTrendsLogic.buildSnapshots(
            emptyList(), emptyList(), 0, start, 30, json
        )
        assertEquals(30, snapshots.size)
    }

    @Test
    fun `quarter range produces 90 snapshots`() {
        val start = LocalDate.of(2025, 3, 1)
        val snapshots = SymptomTrendsLogic.buildSnapshots(
            emptyList(), emptyList(), 0, start, 90, json
        )
        assertEquals(90, snapshots.size)
    }

    @Test
    fun `snapshots have consecutive dates`() {
        val start = LocalDate.of(2025, 6, 1)
        val snapshots = SymptomTrendsLogic.buildSnapshots(
            emptyList(), emptyList(), 0, start, 7, json
        )
        snapshots.forEachIndexed { i, snap ->
            assertEquals(start.plusDays(i.toLong()), snap.date)
        }
    }

    // ── buildSnapshots — empty data defaults ──────────────────────────

    @Test
    fun `day with no data has zero defaults`() {
        val start = LocalDate.of(2025, 6, 1)
        val snapshots = SymptomTrendsLogic.buildSnapshots(
            emptyList(), emptyList(), 2, start, 0, json
        )
        val snap = snapshots[0]
        assertTrue(snap.symptomRatings.isEmpty())
        assertTrue(snap.pollenLevels.isEmpty())
        assertEquals(0, snap.peakAqi)
        assertEquals(0.0, snap.peakPm25, 0.001)
        assertEquals(0.0, snap.peakPm10, 0.001)
        assertEquals(0, snap.dosesConfirmed)
        assertEquals(2, snap.dosesExpected)
    }

    // ── buildSnapshots — symptom diary mapping ────────────────────────

    @Test
    fun `diary entry maps symptom ratings to snapshot`() {
        val date = LocalDate.of(2025, 6, 5)
        val start = date
        val entry = SymptomDiaryEntry(
            profileId = "test",
            date = date,
            ratings = listOf(
                SymptomRating("s1", "Sneezing", 3),
                SymptomRating("s2", "ItchyWateryEyes", 1)
            ),
            loggedAtMillis = 0L,
            peakPollenJson = "{}",
            peakAqi = 42,
            peakPm25 = 12.5,
            peakPm10 = 25.0
        )

        val snapshots = SymptomTrendsLogic.buildSnapshots(
            listOf(entry), emptyList(), 0, start, 0, json
        )

        val snap = snapshots[0]
        assertEquals(3, snap.symptomRatings["Sneezing"])
        assertEquals(1, snap.symptomRatings["ItchyWateryEyes"])
        assertEquals(42, snap.peakAqi)
        assertEquals(12.5, snap.peakPm25, 0.001)
        assertEquals(25.0, snap.peakPm10, 0.001)
    }

    @Test
    fun `diary entry pollen JSON is parsed into pollenLevels`() {
        val date = LocalDate.of(2025, 6, 5)
        val pollenJson = """{"Birch": 45.2, "Grass": 12.0}"""
        val entry = SymptomDiaryEntry(
            profileId = "test",
            date = date,
            ratings = emptyList(),
            loggedAtMillis = 0L,
            peakPollenJson = pollenJson,
            peakAqi = 0,
            peakPm25 = 0.0,
            peakPm10 = 0.0
        )

        val snapshots = SymptomTrendsLogic.buildSnapshots(
            listOf(entry), emptyList(), 0, date, 0, json
        )

        assertEquals(45.2, snapshots[0].pollenLevels["Birch"]!!, 0.001)
        assertEquals(12.0, snapshots[0].pollenLevels["Grass"]!!, 0.001)
    }

    @Test
    fun `malformed pollen JSON produces empty pollenLevels`() {
        val date = LocalDate.of(2025, 6, 5)
        val entry = SymptomDiaryEntry(
            profileId = "test",
            date = date,
            ratings = emptyList(),
            loggedAtMillis = 0L,
            peakPollenJson = "not valid json",
            peakAqi = 0,
            peakPm25 = 0.0,
            peakPm10 = 0.0
        )

        val snapshots = SymptomTrendsLogic.buildSnapshots(
            listOf(entry), emptyList(), 0, date, 0, json
        )

        assertTrue(snapshots[0].pollenLevels.isEmpty())
    }

    // ── buildSnapshots — dose history ─────────────────────────────────

    @Test
    fun `dose history counts confirmed doses per day`() {
        val date = LocalDate.of(2025, 6, 5)
        val doses = listOf(
            makeDoseHistory("med1", 0, date.toString()),
            makeDoseHistory("med1", 1, date.toString()),
            makeDoseHistory("med2", 0, date.toString())
        )

        val snapshots = SymptomTrendsLogic.buildSnapshots(
            emptyList(), doses, 3, date, 0, json
        )

        assertEquals(3, snapshots[0].dosesConfirmed)
        assertEquals(3, snapshots[0].dosesExpected)
    }

    @Test
    fun `dose history on different days is attributed correctly`() {
        val start = LocalDate.of(2025, 6, 1)
        val day1Doses = listOf(makeDoseHistory("med1", 0, start.toString()))
        val day2Doses = listOf(
            makeDoseHistory("med1", 0, start.plusDays(1).toString()),
            makeDoseHistory("med1", 1, start.plusDays(1).toString())
        )

        val snapshots = SymptomTrendsLogic.buildSnapshots(
            emptyList(), day1Doses + day2Doses, 2, start, 1, json
        )

        assertEquals(1, snapshots[0].dosesConfirmed)
        assertEquals(2, snapshots[1].dosesConfirmed)
    }

    @Test
    fun `day with no dose history has zero confirmed`() {
        val start = LocalDate.of(2025, 6, 1)
        val doses = listOf(makeDoseHistory("med1", 0, start.plusDays(1).toString()))

        val snapshots = SymptomTrendsLogic.buildSnapshots(
            emptyList(), doses, 2, start, 1, json
        )

        assertEquals(0, snapshots[0].dosesConfirmed)
        assertEquals(1, snapshots[1].dosesConfirmed)
    }

    // ── buildSnapshots — mixed data ───────────────────────────────────

    @Test
    fun `snapshot combines diary and dose data for same day`() {
        val date = LocalDate.of(2025, 6, 5)
        val entry = SymptomDiaryEntry(
            profileId = "test",
            date = date,
            ratings = listOf(SymptomRating("s1", "Sneezing", 4)),
            loggedAtMillis = 0L,
            peakPollenJson = """{"Birch": 100.0}""",
            peakAqi = 65,
            peakPm25 = 18.0,
            peakPm10 = 30.0
        )
        val doses = listOf(makeDoseHistory("med1", 0, date.toString()))

        val snapshots = SymptomTrendsLogic.buildSnapshots(
            listOf(entry), doses, 2, date, 0, json
        )

        val snap = snapshots[0]
        assertEquals(4, snap.symptomRatings["Sneezing"])
        assertEquals(100.0, snap.pollenLevels["Birch"]!!, 0.001)
        assertEquals(65, snap.peakAqi)
        assertEquals(1, snap.dosesConfirmed)
        assertEquals(2, snap.dosesExpected)
    }

    @Test
    fun `diary entry outside range is ignored`() {
        val start = LocalDate.of(2025, 6, 5)
        val outsideEntry = SymptomDiaryEntry(
            profileId = "test",
            date = LocalDate.of(2025, 6, 1),
            ratings = listOf(SymptomRating("s1", "Sneezing", 5)),
            loggedAtMillis = 0L,
            peakPollenJson = "{}",
            peakAqi = 99,
            peakPm25 = 0.0,
            peakPm10 = 0.0
        )

        val snapshots = SymptomTrendsLogic.buildSnapshots(
            listOf(outsideEntry), emptyList(), 0, start, 0, json
        )

        assertTrue(snapshots[0].symptomRatings.isEmpty())
        assertEquals(0, snapshots[0].peakAqi)
    }

    // ── DaySnapshot defaults ──────────────────────────────────────────

    @Test
    fun `DaySnapshot default values are all zero or empty`() {
        val snap = DaySnapshot(date = LocalDate.of(2025, 1, 1))
        assertTrue(snap.symptomRatings.isEmpty())
        assertTrue(snap.pollenLevels.isEmpty())
        assertEquals(0, snap.peakAqi)
        assertEquals(0.0, snap.peakPm25, 0.001)
        assertEquals(0.0, snap.peakPm10, 0.001)
        assertEquals(0, snap.dosesConfirmed)
        assertEquals(0, snap.dosesExpected)
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private fun makeDoseHistory(
        medicineId: String,
        slotIndex: Int,
        date: String
    ) = DoseHistoryEntity(
        id = 0,
        profileId = "test",
        medicineId = medicineId,
        slotIndex = slotIndex,
        date = date,
        confirmedAtMillis = System.currentTimeMillis(),
        confirmed = true,
        medicineName = "Test Med",
        dose = 1,
        medicineType = "Tablet",
        reminderHour = 8
    )
}
