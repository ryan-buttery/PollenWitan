package com.ryan.pollenwitan.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SeverityClassifierTest {

    // --- Pollen severity: zero and negative values ---

    @Test
    fun `zero pollen value returns None`() {
        PollenType.entries.forEach { type ->
            assertEquals(
                "Expected None for $type at 0.0",
                SeverityLevel.None,
                SeverityClassifier.pollenSeverity(type, 0.0)
            )
        }
    }

    @Test
    fun `negative pollen value returns None`() {
        PollenType.entries.forEach { type ->
            assertEquals(
                "Expected None for $type at -5.0",
                SeverityLevel.None,
                SeverityClassifier.pollenSeverity(type, -5.0)
            )
        }
    }

    // --- Birch (default: low=1, moderate=11, high=51, veryHigh=201) ---

    @Test
    fun `birch just above zero is Low`() {
        assertEquals(SeverityLevel.Low, SeverityClassifier.pollenSeverity(PollenType.Birch, 0.5))
    }

    @Test
    fun `birch just below moderate threshold is Low`() {
        assertEquals(SeverityLevel.Low, SeverityClassifier.pollenSeverity(PollenType.Birch, 10.9))
    }

    @Test
    fun `birch at moderate threshold is Moderate`() {
        assertEquals(SeverityLevel.Moderate, SeverityClassifier.pollenSeverity(PollenType.Birch, 11.0))
    }

    @Test
    fun `birch just below high threshold is Moderate`() {
        assertEquals(SeverityLevel.Moderate, SeverityClassifier.pollenSeverity(PollenType.Birch, 50.9))
    }

    @Test
    fun `birch at high threshold is High`() {
        assertEquals(SeverityLevel.High, SeverityClassifier.pollenSeverity(PollenType.Birch, 51.0))
    }

    @Test
    fun `birch just below veryHigh threshold is High`() {
        assertEquals(SeverityLevel.High, SeverityClassifier.pollenSeverity(PollenType.Birch, 200.9))
    }

    @Test
    fun `birch at veryHigh threshold is VeryHigh`() {
        assertEquals(SeverityLevel.VeryHigh, SeverityClassifier.pollenSeverity(PollenType.Birch, 201.0))
    }

    @Test
    fun `birch well above veryHigh is VeryHigh`() {
        assertEquals(SeverityLevel.VeryHigh, SeverityClassifier.pollenSeverity(PollenType.Birch, 500.0))
    }

    // --- Alder (default: low=1, moderate=11, high=51, veryHigh=101) ---

    @Test
    fun `alder just below veryHigh is High`() {
        assertEquals(SeverityLevel.High, SeverityClassifier.pollenSeverity(PollenType.Alder, 100.9))
    }

    @Test
    fun `alder at veryHigh threshold is VeryHigh`() {
        assertEquals(SeverityLevel.VeryHigh, SeverityClassifier.pollenSeverity(PollenType.Alder, 101.0))
    }

    // --- Grass (default: low=1, moderate=6, high=31, veryHigh=81) ---

    @Test
    fun `grass just below moderate is Low`() {
        assertEquals(SeverityLevel.Low, SeverityClassifier.pollenSeverity(PollenType.Grass, 5.9))
    }

    @Test
    fun `grass at moderate threshold is Moderate`() {
        assertEquals(SeverityLevel.Moderate, SeverityClassifier.pollenSeverity(PollenType.Grass, 6.0))
    }

    @Test
    fun `grass just below high is Moderate`() {
        assertEquals(SeverityLevel.Moderate, SeverityClassifier.pollenSeverity(PollenType.Grass, 30.9))
    }

    @Test
    fun `grass at high threshold is High`() {
        assertEquals(SeverityLevel.High, SeverityClassifier.pollenSeverity(PollenType.Grass, 31.0))
    }

    @Test
    fun `grass just below veryHigh is High`() {
        assertEquals(SeverityLevel.High, SeverityClassifier.pollenSeverity(PollenType.Grass, 80.9))
    }

    @Test
    fun `grass at veryHigh threshold is VeryHigh`() {
        assertEquals(SeverityLevel.VeryHigh, SeverityClassifier.pollenSeverity(PollenType.Grass, 81.0))
    }

    // --- Mugwort (default: low=1, moderate=11, high=51, veryHigh=101) ---

    @Test
    fun `mugwort boundary values match alder thresholds`() {
        assertEquals(SeverityLevel.Low, SeverityClassifier.pollenSeverity(PollenType.Mugwort, 10.9))
        assertEquals(SeverityLevel.Moderate, SeverityClassifier.pollenSeverity(PollenType.Mugwort, 11.0))
        assertEquals(SeverityLevel.High, SeverityClassifier.pollenSeverity(PollenType.Mugwort, 51.0))
        assertEquals(SeverityLevel.VeryHigh, SeverityClassifier.pollenSeverity(PollenType.Mugwort, 101.0))
    }

    // --- Ragweed (default: low=1, moderate=6, high=31, veryHigh=81) ---

    @Test
    fun `ragweed boundary values match grass thresholds`() {
        assertEquals(SeverityLevel.Low, SeverityClassifier.pollenSeverity(PollenType.Ragweed, 5.9))
        assertEquals(SeverityLevel.Moderate, SeverityClassifier.pollenSeverity(PollenType.Ragweed, 6.0))
        assertEquals(SeverityLevel.High, SeverityClassifier.pollenSeverity(PollenType.Ragweed, 31.0))
        assertEquals(SeverityLevel.VeryHigh, SeverityClassifier.pollenSeverity(PollenType.Ragweed, 81.0))
    }

    // --- Olive (default: low=1, moderate=11, high=51, veryHigh=201) ---

    @Test
    fun `olive boundary values match birch thresholds`() {
        assertEquals(SeverityLevel.Low, SeverityClassifier.pollenSeverity(PollenType.Olive, 10.9))
        assertEquals(SeverityLevel.Moderate, SeverityClassifier.pollenSeverity(PollenType.Olive, 11.0))
        assertEquals(SeverityLevel.High, SeverityClassifier.pollenSeverity(PollenType.Olive, 51.0))
        assertEquals(SeverityLevel.VeryHigh, SeverityClassifier.pollenSeverity(PollenType.Olive, 201.0))
    }

    // --- Custom thresholds ---

    @Test
    fun `custom threshold overrides default classification`() {
        val custom = AllergenThreshold(
            type = PollenType.Birch,
            low = 1.0,
            moderate = 5.0,
            high = 20.0,
            veryHigh = 50.0
        )
        // With defaults, 15.0 would be Moderate (11-51).
        // With custom, 15.0 is between 5 and 20, still Moderate.
        assertEquals(SeverityLevel.Moderate, SeverityClassifier.pollenSeverity(15.0, custom))
        // With defaults, 25.0 would be Moderate. With custom, it's High (20-50).
        assertEquals(SeverityLevel.High, SeverityClassifier.pollenSeverity(25.0, custom))
        // With defaults, 55.0 would be High. With custom, it's VeryHigh (>=50).
        assertEquals(SeverityLevel.VeryHigh, SeverityClassifier.pollenSeverity(55.0, custom))
    }

    @Test
    fun `custom threshold zero value still returns None`() {
        val custom = AllergenThreshold(
            type = PollenType.Grass,
            low = 1.0,
            moderate = 3.0,
            high = 10.0,
            veryHigh = 30.0
        )
        assertEquals(SeverityLevel.None, SeverityClassifier.pollenSeverity(0.0, custom))
    }

    // --- AQI severity ---

    @Test
    fun `aqi zero returns None`() {
        assertEquals(SeverityLevel.None, SeverityClassifier.aqiSeverity(0))
    }

    @Test
    fun `aqi negative returns None`() {
        assertEquals(SeverityLevel.None, SeverityClassifier.aqiSeverity(-10))
    }

    @Test
    fun `aqi 1 returns Low`() {
        assertEquals(SeverityLevel.Low, SeverityClassifier.aqiSeverity(1))
    }

    @Test
    fun `aqi 20 returns Low`() {
        assertEquals(SeverityLevel.Low, SeverityClassifier.aqiSeverity(20))
    }

    @Test
    fun `aqi 21 returns Low`() {
        assertEquals(SeverityLevel.Low, SeverityClassifier.aqiSeverity(21))
    }

    @Test
    fun `aqi 40 returns Low`() {
        assertEquals(SeverityLevel.Low, SeverityClassifier.aqiSeverity(40))
    }

    @Test
    fun `aqi 41 returns Moderate`() {
        assertEquals(SeverityLevel.Moderate, SeverityClassifier.aqiSeverity(41))
    }

    @Test
    fun `aqi 60 returns Moderate`() {
        assertEquals(SeverityLevel.Moderate, SeverityClassifier.aqiSeverity(60))
    }

    @Test
    fun `aqi 61 returns High`() {
        assertEquals(SeverityLevel.High, SeverityClassifier.aqiSeverity(61))
    }

    @Test
    fun `aqi 80 returns High`() {
        assertEquals(SeverityLevel.High, SeverityClassifier.aqiSeverity(80))
    }

    @Test
    fun `aqi 81 returns VeryHigh`() {
        assertEquals(SeverityLevel.VeryHigh, SeverityClassifier.aqiSeverity(81))
    }

    @Test
    fun `aqi 150 returns VeryHigh`() {
        assertEquals(SeverityLevel.VeryHigh, SeverityClassifier.aqiSeverity(150))
    }
}
