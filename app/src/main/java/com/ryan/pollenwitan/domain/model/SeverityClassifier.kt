package com.ryan.pollenwitan.domain.model

object SeverityClassifier {

    fun pollenSeverity(type: PollenType, value: Double): SeverityLevel {
        if (value <= 0.0) return SeverityLevel.None
        return when (type) {
            PollenType.Birch -> when {
                value <= 10 -> SeverityLevel.Low
                value <= 50 -> SeverityLevel.Moderate
                value <= 200 -> SeverityLevel.High
                else -> SeverityLevel.VeryHigh
            }
            PollenType.Alder -> when {
                value <= 10 -> SeverityLevel.Low
                value <= 50 -> SeverityLevel.Moderate
                value <= 100 -> SeverityLevel.High
                else -> SeverityLevel.VeryHigh
            }
            PollenType.Grass -> when {
                value <= 5 -> SeverityLevel.Low
                value <= 30 -> SeverityLevel.Moderate
                value <= 80 -> SeverityLevel.High
                else -> SeverityLevel.VeryHigh
            }
        }
    }

    fun aqiSeverity(aqi: Int): SeverityLevel = when {
        aqi <= 0 -> SeverityLevel.None
        aqi <= 20 -> SeverityLevel.Low       // Good
        aqi <= 40 -> SeverityLevel.Low        // Fair
        aqi <= 60 -> SeverityLevel.Moderate   // Moderate
        aqi <= 80 -> SeverityLevel.High       // Poor
        else -> SeverityLevel.VeryHigh        // Very Poor
    }
}
