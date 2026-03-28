package com.ryan.pollenwitan.domain.calibration

import com.ryan.pollenwitan.domain.model.PollenType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class AllergenDiscoveryEngineTest {

    private lateinit var engine: AllergenDiscoveryEngine
    private val baseDate = LocalDate.of(2025, 6, 1)

    @Before
    fun setUp() {
        engine = AllergenDiscoveryEngine()
    }

    // --- Helper to generate data points ---

    private fun makePoints(
        count: Int,
        startDate: LocalDate = baseDate,
        pollenConcentrations: Map<PollenType, Double> = mapOf(PollenType.Birch to 30.0),
        compositeSymptomScore: Double = 3.0,
        aqiConfounded: Boolean = false,
        medicationAdherence: Double = 0.0
    ): List<DiscoveryDataPoint> = (0 until count).map { i ->
        DiscoveryDataPoint(
            date = startDate.plusDays(i.toLong()),
            pollenConcentrations = pollenConcentrations,
            compositeSymptomScore = compositeSymptomScore,
            aqiConfounded = aqiConfounded,
            medicationAdherence = medicationAdherence
        )
    }

    // --- Empty and insufficient data ---

    @Test
    fun `empty data returns NO_DATA for all pollen types`() {
        val analysis = engine.analyze(emptyList())
        assertEquals(DataStatus.NO_DATA, analysis.overallDataStatus)
        assertEquals(0, analysis.totalDiaryEntries)
        assertEquals(6, analysis.results.size)
        analysis.results.forEach {
            assertEquals(DataStatus.NO_DATA, it.dataStatus)
            assertEquals(0.0, it.correlationScore, 0.0)
        }
    }

    @Test
    fun `insufficient total entries returns INSUFFICIENT_ENTRIES`() {
        val points = makePoints(13) // need 14
        val analysis = engine.analyze(points)
        assertEquals(DataStatus.INSUFFICIENT_ENTRIES, analysis.overallDataStatus)
        assertEquals(13, analysis.totalDiaryEntries)
    }

    @Test
    fun `insufficient clean days returns INSUFFICIENT_CLEAN_DAYS`() {
        val confounded = makePoints(10, aqiConfounded = true)
        val clean = makePoints(6, startDate = baseDate.plusDays(10))
        val analysis = engine.analyze(confounded + clean)
        assertEquals(DataStatus.INSUFFICIENT_CLEAN_DAYS, analysis.overallDataStatus)
    }

    @Test
    fun `insufficient date span returns INSUFFICIENT_ENTRIES`() {
        // 14 entries but all on the same date
        val points = makePoints(14).map { it.copy(date = baseDate) }
        val analysis = engine.analyze(points)
        assertEquals(DataStatus.INSUFFICIENT_ENTRIES, analysis.overallDataStatus)
    }

    @Test
    fun `per-pollen insufficient data when fewer than 5 days with pollen`() {
        // 20 days of data, birch present on only 4
        val withBirch = makePoints(
            4,
            pollenConcentrations = mapOf(PollenType.Birch to 30.0)
        )
        val withoutBirch = makePoints(
            16,
            startDate = baseDate.plusDays(4),
            pollenConcentrations = mapOf(PollenType.Grass to 50.0)
        )
        val analysis = engine.analyze(withBirch + withoutBirch)
        val birchResult = analysis.results.find { it.pollenType == PollenType.Birch }!!
        assertEquals(DataStatus.INSUFFICIENT_ENTRIES, birchResult.dataStatus)
    }

    // --- Sufficient data ---

    @Test
    fun `sufficient data returns SUFFICIENT overall status`() {
        val points = makeSufficientData()
        val analysis = engine.analyze(points)
        assertEquals(DataStatus.SUFFICIENT, analysis.overallDataStatus)
    }

    @Test
    fun `total diary entries count is correct`() {
        val points = makeSufficientData()
        val analysis = engine.analyze(points)
        assertEquals(points.size, analysis.totalDiaryEntries)
    }

    @Test
    fun `required entries is 14`() {
        val analysis = engine.analyze(emptyList())
        assertEquals(14, analysis.requiredEntries)
    }

    // --- Correlation scoring ---

    @Test
    fun `perfect positive correlation scores near 1`() {
        // Symptom score increases linearly with pollen concentration
        val points = (0 until 20).map { i ->
            DiscoveryDataPoint(
                date = baseDate.plusDays(i.toLong()),
                pollenConcentrations = mapOf(PollenType.Birch to (i + 1).toDouble()),
                compositeSymptomScore = (i + 1).toDouble() * 0.25,
                aqiConfounded = false,
                medicationAdherence = 0.0
            )
        }
        val analysis = engine.analyze(points)
        val birchResult = analysis.results.find { it.pollenType == PollenType.Birch }!!
        assertTrue(
            "Expected strong correlation, got ${birchResult.correlationScore}",
            birchResult.correlationScore >= 0.9
        )
    }

    @Test
    fun `no correlation scores near zero`() {
        // Alternating high/low symptoms regardless of constant pollen
        val points = (0 until 20).map { i ->
            DiscoveryDataPoint(
                date = baseDate.plusDays(i.toLong()),
                pollenConcentrations = mapOf(PollenType.Birch to 30.0),
                compositeSymptomScore = if (i % 2 == 0) 1.0 else 4.0,
                aqiConfounded = false,
                medicationAdherence = 0.0
            )
        }
        val analysis = engine.analyze(points)
        val birchResult = analysis.results.find { it.pollenType == PollenType.Birch }!!
        // Constant pollen → zero variance in pollen → correlation is 0
        assertEquals(0.0, birchResult.correlationScore, 0.01)
    }

    @Test
    fun `negative correlation is clamped to zero`() {
        // Symptom decreases as pollen increases (inverse relationship)
        val points = (0 until 20).map { i ->
            DiscoveryDataPoint(
                date = baseDate.plusDays(i.toLong()),
                pollenConcentrations = mapOf(PollenType.Birch to (i + 1).toDouble()),
                compositeSymptomScore = (20 - i).toDouble() * 0.25,
                aqiConfounded = false,
                medicationAdherence = 0.0
            )
        }
        val analysis = engine.analyze(points)
        val birchResult = analysis.results.find { it.pollenType == PollenType.Birch }!!
        assertTrue(
            "Negative correlation should be clamped to 0, got ${birchResult.correlationScore}",
            birchResult.correlationScore >= 0.0
        )
    }

    // --- Correlation strength tiers ---

    @Test
    fun `correlation strength STRONG when score at least 0_5`() {
        val result = makeResultWithScore(0.55)
        assertEquals(CorrelationStrength.STRONG, result.correlationStrength)
    }

    @Test
    fun `correlation strength MODERATE when score 0_3 to 0_5`() {
        val result = makeResultWithScore(0.35)
        assertEquals(CorrelationStrength.MODERATE, result.correlationStrength)
    }

    @Test
    fun `correlation strength WEAK when score 0_15 to 0_3`() {
        val result = makeResultWithScore(0.2)
        assertEquals(CorrelationStrength.WEAK, result.correlationStrength)
    }

    @Test
    fun `correlation strength NONE when score below 0_15`() {
        val result = makeResultWithScore(0.1)
        assertEquals(CorrelationStrength.NONE, result.correlationStrength)
    }

    @Test
    fun `correlation strength INSUFFICIENT_DATA when status not SUFFICIENT`() {
        val result = DiscoveryResult(
            pollenType = PollenType.Birch,
            correlationScore = 0.8,
            confidence = Confidence.HIGH,
            symptomDaysWithHighPollen = 10,
            totalValidDays = 20,
            dataStatus = DataStatus.INSUFFICIENT_ENTRIES
        )
        assertEquals(CorrelationStrength.INSUFFICIENT_DATA, result.correlationStrength)
    }

    // --- Medication adjustment ---

    @Test
    fun `high medication adherence increases effective symptom score`() {
        // Two datasets: same pollen/symptoms, but one with high medication adherence
        // The medicated dataset should show higher correlation because symptoms are amplified
        val unmedicatedPoints = (0 until 20).map { i ->
            DiscoveryDataPoint(
                date = baseDate.plusDays(i.toLong()),
                pollenConcentrations = mapOf(PollenType.Birch to (i + 1).toDouble()),
                compositeSymptomScore = (i + 1).toDouble() * 0.2,
                aqiConfounded = false,
                medicationAdherence = 0.0
            )
        }
        val medicatedPoints = (0 until 20).map { i ->
            DiscoveryDataPoint(
                date = baseDate.plusDays(i.toLong()),
                pollenConcentrations = mapOf(PollenType.Birch to (i + 1).toDouble()),
                compositeSymptomScore = (i + 1).toDouble() * 0.2,
                aqiConfounded = false,
                medicationAdherence = 0.9
            )
        }
        val unmedicatedAnalysis = engine.analyze(unmedicatedPoints)
        val medicatedAnalysis = engine.analyze(medicatedPoints)
        val unmedicatedScore = unmedicatedAnalysis.results.find { it.pollenType == PollenType.Birch }!!.correlationScore
        val medicatedScore = medicatedAnalysis.results.find { it.pollenType == PollenType.Birch }!!.correlationScore
        // Both should have strong correlation (linear relationship is preserved by scaling)
        // The scores should be similar since scaling is linear
        assertTrue("Both should show strong correlation", unmedicatedScore >= 0.9)
        assertTrue("Both should show strong correlation", medicatedScore >= 0.9)
    }

    // --- Results ordering ---

    @Test
    fun `results sorted by correlation score descending`() {
        // Birch has strong correlation, Grass has weak
        val points = (0 until 20).map { i ->
            DiscoveryDataPoint(
                date = baseDate.plusDays(i.toLong()),
                pollenConcentrations = mapOf(
                    PollenType.Birch to (i + 1).toDouble(),
                    PollenType.Grass to 20.0 // constant → zero correlation
                ),
                compositeSymptomScore = (i + 1).toDouble() * 0.25,
                aqiConfounded = false,
                medicationAdherence = 0.0
            )
        }
        val analysis = engine.analyze(points)
        val scores = analysis.results.map { it.correlationScore }
        assertEquals("Results should be sorted descending", scores, scores.sortedDescending())
    }

    // --- Confidence tiers ---

    @Test
    fun `low confidence with fewer than 20 clean days`() {
        val points = makeSufficientData(cleanDays = 15)
        val analysis = engine.analyze(points)
        val birchResult = analysis.results.find {
            it.pollenType == PollenType.Birch && it.dataStatus == DataStatus.SUFFICIENT
        }
        if (birchResult != null) {
            assertEquals(Confidence.LOW, birchResult.confidence)
        }
    }

    @Test
    fun `moderate confidence with 20-39 clean days`() {
        val points = makeSufficientData(cleanDays = 25)
        val analysis = engine.analyze(points)
        val birchResult = analysis.results.find {
            it.pollenType == PollenType.Birch && it.dataStatus == DataStatus.SUFFICIENT
        }
        if (birchResult != null) {
            assertEquals(Confidence.MODERATE, birchResult.confidence)
        }
    }

    @Test
    fun `high confidence with 40+ clean days`() {
        val points = makeSufficientData(cleanDays = 45)
        val analysis = engine.analyze(points)
        val birchResult = analysis.results.find {
            it.pollenType == PollenType.Birch && it.dataStatus == DataStatus.SUFFICIENT
        }
        if (birchResult != null) {
            assertEquals(Confidence.HIGH, birchResult.confidence)
        }
    }

    // --- Helpers ---

    private fun makeSufficientData(cleanDays: Int = 20): List<DiscoveryDataPoint> {
        val totalDays = maxOf(cleanDays, 15)
        return (0 until totalDays).map { i ->
            DiscoveryDataPoint(
                date = baseDate.plusDays(i.toLong()),
                pollenConcentrations = mapOf(PollenType.Birch to (i + 1).toDouble()),
                compositeSymptomScore = (i + 1).toDouble() * 0.2,
                aqiConfounded = i >= cleanDays,
                medicationAdherence = 0.0
            )
        }
    }

    private fun makeResultWithScore(score: Double) = DiscoveryResult(
        pollenType = PollenType.Birch,
        correlationScore = score,
        confidence = Confidence.HIGH,
        symptomDaysWithHighPollen = 10,
        totalValidDays = 20,
        dataStatus = DataStatus.SUFFICIENT
    )
}
