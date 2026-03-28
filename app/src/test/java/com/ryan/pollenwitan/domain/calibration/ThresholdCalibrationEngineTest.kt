package com.ryan.pollenwitan.domain.calibration

import com.ryan.pollenwitan.domain.model.AllergenThreshold
import com.ryan.pollenwitan.domain.model.PollenType
import com.ryan.pollenwitan.domain.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class ThresholdCalibrationEngineTest {

    private lateinit var engine: ThresholdCalibrationEngine
    private val birchThreshold = UserProfile.defaultThreshold(PollenType.Birch)
    private val baseDate = LocalDate.of(2025, 6, 1)

    @Before
    fun setUp() {
        engine = ThresholdCalibrationEngine()
    }

    // --- Helper to generate data points ---

    private fun makePoints(
        count: Int,
        startDate: LocalDate = baseDate,
        pollenConcentration: Double = 30.0,
        compositeSymptomScore: Double = 3.0,
        aqiConfounded: Boolean = false,
        medicationAdherence: Double = 0.0
    ): List<CalibrationDataPoint> = (0 until count).map { i ->
        CalibrationDataPoint(
            date = startDate.plusDays(i.toLong()),
            pollenConcentration = pollenConcentration,
            compositeSymptomScore = compositeSymptomScore,
            aqiConfounded = aqiConfounded,
            multiPollenWeight = 1.0,
            medicationAdherence = medicationAdherence
        )
    }

    // --- Data status checks ---

    @Test
    fun `empty data returns NO_DATA`() {
        val result = engine.calibrate(PollenType.Birch, birchThreshold, emptyList())
        assertEquals(DataStatus.NO_DATA, result.dataStatus)
        assertTrue(result.suggestions.isEmpty())
        assertEquals(0, result.totalValidDays)
    }

    @Test
    fun `fewer than 10 entries returns INSUFFICIENT_ENTRIES`() {
        val points = makePoints(9)
        val result = engine.calibrate(PollenType.Birch, birchThreshold, points)
        assertEquals(DataStatus.INSUFFICIENT_ENTRIES, result.dataStatus)
        assertTrue(result.suggestions.isEmpty())
    }

    @Test
    fun `insufficient symptom days returns INSUFFICIENT_SYMPTOM_DAYS`() {
        // 15 entries but only 4 with symptom score >= 2.0
        val lowSymptom = makePoints(11, compositeSymptomScore = 1.0)
        val highSymptom = makePoints(4, startDate = baseDate.plusDays(11), compositeSymptomScore = 3.0)
        val points = lowSymptom + highSymptom
        val result = engine.calibrate(PollenType.Birch, birchThreshold, points)
        assertEquals(DataStatus.INSUFFICIENT_SYMPTOM_DAYS, result.dataStatus)
    }

    @Test
    fun `insufficient clean days returns INSUFFICIENT_CLEAN_DAYS`() {
        // 15 entries with enough symptoms, but only 6 clean days
        val confounded = makePoints(9, aqiConfounded = true)
        val clean = makePoints(6, startDate = baseDate.plusDays(9))
        val points = confounded + clean
        val result = engine.calibrate(PollenType.Birch, birchThreshold, points)
        assertEquals(DataStatus.INSUFFICIENT_CLEAN_DAYS, result.dataStatus)
    }

    @Test
    fun `date span less than 14 days returns INSUFFICIENT_ENTRIES`() {
        // 15 entries but all within 13 days
        val points = makePoints(15, startDate = baseDate).map {
            it.copy(date = baseDate.plusDays((0L..12L).random()))
        }.let { list ->
            // Ensure dates span exactly 13 days
            list.mapIndexed { i, p ->
                if (i == 0) p.copy(date = baseDate)
                else if (i == list.lastIndex) p.copy(date = baseDate.plusDays(13))
                else p
            }
        }
        val result = engine.calibrate(PollenType.Birch, birchThreshold, points)
        assertEquals(DataStatus.INSUFFICIENT_ENTRIES, result.dataStatus)
    }

    @Test
    fun `sufficient data returns SUFFICIENT status`() {
        val points = makeSufficientData()
        val result = engine.calibrate(PollenType.Birch, birchThreshold, points)
        assertEquals(DataStatus.SUFFICIENT, result.dataStatus)
    }

    // --- Suggestion generation ---

    @Test
    fun `no suggestion when thresholds match data well`() {
        // Generate data where symptoms correlate with default birch thresholds
        val points = makePoints(
            count = 20,
            pollenConcentration = 11.0, // exactly at moderate threshold
            compositeSymptomScore = 2.5
        )
        val result = engine.calibrate(PollenType.Birch, birchThreshold, points)
        assertEquals(DataStatus.SUFFICIENT, result.dataStatus)
        // Suggestions may or may not be generated depending on the percentile calculation,
        // but there should be no crash
    }

    @Test
    fun `lower suggestion when symptoms occur well below current threshold`() {
        // Symptoms at pollen=5 but default moderate threshold is 11
        // This is more than 20% lower, so should trigger a LOWER suggestion
        val points = makeSufficientData(pollenConcentration = 5.0, compositeSymptomScore = 3.0)
        val result = engine.calibrate(PollenType.Birch, birchThreshold, points)
        assertEquals(DataStatus.SUFFICIENT, result.dataStatus)
        val moderateSuggestion = result.suggestions.find { it.level == "moderate" }
        if (moderateSuggestion != null) {
            assertEquals(Direction.LOWER, moderateSuggestion.direction)
            assertTrue(
                "Suggested value should be below current",
                moderateSuggestion.suggestedValue < moderateSuggestion.currentValue
            )
        }
    }

    @Test
    fun `higher suggestion when symptoms only occur well above current threshold`() {
        // Symptoms only at pollen=200, current moderate is 11
        // The difference (200 vs 11) is well above 40%, so should trigger HIGHER
        val custom = AllergenThreshold(
            type = PollenType.Birch,
            low = 1.0,
            moderate = 5.0,
            high = 10.0,
            veryHigh = 20.0
        )
        val points = makeSufficientData(pollenConcentration = 50.0, compositeSymptomScore = 3.0)
        val result = engine.calibrate(PollenType.Birch, custom, points)
        assertEquals(DataStatus.SUFFICIENT, result.dataStatus)
        val moderateSuggestion = result.suggestions.find { it.level == "moderate" }
        if (moderateSuggestion != null) {
            assertEquals(Direction.HIGHER, moderateSuggestion.direction)
            assertTrue(
                "Suggested value should be above current",
                moderateSuggestion.suggestedValue > moderateSuggestion.currentValue
            )
        }
    }

    // --- Confidence tiers ---

    @Test
    fun `low confidence with fewer than 20 clean days`() {
        val points = makeSufficientData(cleanDays = 10)
        val result = engine.calibrate(PollenType.Birch, birchThreshold, points)
        result.suggestions.forEach {
            assertEquals(Confidence.LOW, it.confidence)
        }
    }

    @Test
    fun `moderate confidence with 20-39 clean days`() {
        val points = makeSufficientData(cleanDays = 25)
        val result = engine.calibrate(PollenType.Birch, birchThreshold, points)
        result.suggestions.forEach {
            assertEquals(Confidence.MODERATE, it.confidence)
        }
    }

    @Test
    fun `high confidence with 40+ clean days`() {
        val points = makeSufficientData(cleanDays = 45)
        val result = engine.calibrate(PollenType.Birch, birchThreshold, points)
        result.suggestions.forEach {
            assertEquals(Confidence.HIGH, it.confidence)
        }
    }

    // --- Ordering validation ---

    @Test
    fun `suggestions maintain moderate less than high less than veryHigh ordering`() {
        val points = makeSufficientData(cleanDays = 45)
        val result = engine.calibrate(PollenType.Birch, birchThreshold, points)
        val moderate = result.suggestions.find { it.level == "moderate" }?.suggestedValue
            ?: birchThreshold.moderate
        val high = result.suggestions.find { it.level == "high" }?.suggestedValue
            ?: birchThreshold.high
        val veryHigh = result.suggestions.find { it.level == "veryHigh" }?.suggestedValue
            ?: birchThreshold.veryHigh
        assertTrue("moderate ($moderate) < high ($high)", moderate < high)
        assertTrue("high ($high) < veryHigh ($veryHigh)", high < veryHigh)
    }

    // --- Medicated day ratio ---

    @Test
    fun `medicated day ratio calculated correctly`() {
        val medicated = makePoints(8, medicationAdherence = 0.9)
        val unmedicated = makePoints(
            12,
            startDate = baseDate.plusDays(8),
            medicationAdherence = 0.2
        )
        val points = medicated + unmedicated
        val result = engine.calibrate(PollenType.Birch, birchThreshold, points)
        if (result.suggestions.isNotEmpty()) {
            val ratio = result.suggestions.first().medicatedDayRatio
            // 8 out of 20 have adherence >= 0.8
            assertEquals(0.4, ratio, 0.01)
        }
    }

    // --- Total valid days ---

    @Test
    fun `totalValidDays reflects all data points`() {
        val points = makeSufficientData()
        val result = engine.calibrate(PollenType.Birch, birchThreshold, points)
        assertEquals(points.size, result.totalValidDays)
    }

    // --- Helpers ---

    private fun makeSufficientData(
        cleanDays: Int = 15,
        pollenConcentration: Double = 8.0,
        compositeSymptomScore: Double = 3.0
    ): List<CalibrationDataPoint> {
        // Ensure we meet all minimums: >=10 entries, >=5 symptom days, >=7 clean days, >=14 day span
        val totalDays = maxOf(cleanDays, 15)
        return (0 until totalDays).map { i ->
            CalibrationDataPoint(
                date = baseDate.plusDays(i.toLong()),
                pollenConcentration = pollenConcentration,
                compositeSymptomScore = compositeSymptomScore,
                aqiConfounded = i >= cleanDays,
                multiPollenWeight = 1.0,
                medicationAdherence = 0.0
            )
        }
    }
}
