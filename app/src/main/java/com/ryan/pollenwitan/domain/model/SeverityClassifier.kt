package com.ryan.pollenwitan.domain.model

object SeverityClassifier {

    fun pollenSeverity(type: PollenType, value: Double): SeverityLevel =
        pollenSeverity(value, UserProfile.defaultThreshold(type))

    fun pollenSeverity(value: Double, threshold: AllergenThreshold): SeverityLevel {
        if (value <= 0.0) return SeverityLevel.None
        return when {
            value < threshold.moderate -> SeverityLevel.Low
            value < threshold.high -> SeverityLevel.Moderate
            value < threshold.veryHigh -> SeverityLevel.High
            else -> SeverityLevel.VeryHigh
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
