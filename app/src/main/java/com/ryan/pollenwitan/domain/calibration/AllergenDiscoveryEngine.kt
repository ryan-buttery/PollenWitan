package com.ryan.pollenwitan.domain.calibration

import com.ryan.pollenwitan.domain.model.PollenType
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.sqrt

data class DiscoveryDataPoint(
    val date: LocalDate,
    val pollenConcentrations: Map<PollenType, Double>,
    val compositeSymptomScore: Double,
    val aqiConfounded: Boolean,
    val medicationAdherence: Double
)

data class DiscoveryResult(
    val pollenType: PollenType,
    val correlationScore: Double,
    val confidence: Confidence,
    val symptomDaysWithHighPollen: Int,
    val totalValidDays: Int,
    val dataStatus: DataStatus
) {
    val correlationStrength: CorrelationStrength
        get() = when {
            dataStatus != DataStatus.SUFFICIENT -> CorrelationStrength.INSUFFICIENT_DATA
            correlationScore >= 0.5 -> CorrelationStrength.STRONG
            correlationScore >= 0.3 -> CorrelationStrength.MODERATE
            correlationScore >= 0.15 -> CorrelationStrength.WEAK
            else -> CorrelationStrength.NONE
        }
}

enum class CorrelationStrength { STRONG, MODERATE, WEAK, NONE, INSUFFICIENT_DATA }

data class DiscoveryAnalysis(
    val results: List<DiscoveryResult>,
    val totalDiaryEntries: Int,
    val requiredEntries: Int,
    val overallDataStatus: DataStatus
)

class AllergenDiscoveryEngine {

    companion object {
        private const val MIN_VALID_ENTRIES = 14
        private const val MIN_CLEAN_DAYS = 7
        private const val MIN_DATE_SPAN_DAYS = 14L
        private const val MIN_POLLEN_DAYS = 5
        private const val AQI_CONFOUND_THRESHOLD = 60
    }

    fun analyze(dataPoints: List<DiscoveryDataPoint>): DiscoveryAnalysis {
        if (dataPoints.isEmpty()) {
            return DiscoveryAnalysis(
                results = PollenType.entries.map { noDataResult(it) },
                totalDiaryEntries = 0,
                requiredEntries = MIN_VALID_ENTRIES,
                overallDataStatus = DataStatus.NO_DATA
            )
        }

        val cleanDays = dataPoints.count { !it.aqiConfounded }
        val dateSpan = if (dataPoints.size >= 2) {
            val dates = dataPoints.map { it.date }.sorted()
            ChronoUnit.DAYS.between(dates.first(), dates.last())
        } else 0L

        val overallStatus = when {
            dataPoints.size < MIN_VALID_ENTRIES -> DataStatus.INSUFFICIENT_ENTRIES
            cleanDays < MIN_CLEAN_DAYS -> DataStatus.INSUFFICIENT_CLEAN_DAYS
            dateSpan < MIN_DATE_SPAN_DAYS -> DataStatus.INSUFFICIENT_ENTRIES
            else -> DataStatus.SUFFICIENT
        }

        val results = PollenType.entries.map { pollenType ->
            analyzePollenType(pollenType, dataPoints, cleanDays)
        }.sortedByDescending { it.correlationScore }

        return DiscoveryAnalysis(
            results = results,
            totalDiaryEntries = dataPoints.size,
            requiredEntries = MIN_VALID_ENTRIES,
            overallDataStatus = overallStatus
        )
    }

    private fun analyzePollenType(
        pollenType: PollenType,
        allPoints: List<DiscoveryDataPoint>,
        totalCleanDays: Int
    ): DiscoveryResult {
        // Filter to non-AQI-confounded days with pollen data for this type
        val relevantPoints = allPoints.filter { point ->
            !point.aqiConfounded && (point.pollenConcentrations[pollenType] ?: 0.0) > 0.0
        }

        if (relevantPoints.size < MIN_POLLEN_DAYS) {
            return DiscoveryResult(
                pollenType = pollenType,
                correlationScore = 0.0,
                confidence = Confidence.LOW,
                symptomDaysWithHighPollen = 0,
                totalValidDays = relevantPoints.size,
                dataStatus = if (relevantPoints.isEmpty()) DataStatus.NO_DATA
                else DataStatus.INSUFFICIENT_ENTRIES
            )
        }

        // Compute Pearson correlation between pollen concentration and symptom score
        val concentrations = relevantPoints.map { it.pollenConcentrations[pollenType] ?: 0.0 }
        val symptoms = relevantPoints.map { adjustedSymptomScore(it) }

        val correlation = pearsonCorrelation(concentrations, symptoms)

        // Count days where both pollen was elevated and symptoms were notable
        val symptomDaysWithHighPollen = relevantPoints.count { point ->
            val concentration = point.pollenConcentrations[pollenType] ?: 0.0
            val medianConcentration = concentrations.sorted()[concentrations.size / 2]
            concentration >= medianConcentration && adjustedSymptomScore(point) >= 2.0
        }

        val confidence = when {
            totalCleanDays >= 40 -> Confidence.HIGH
            totalCleanDays >= 20 -> Confidence.MODERATE
            else -> Confidence.LOW
        }

        return DiscoveryResult(
            pollenType = pollenType,
            correlationScore = correlation.coerceAtLeast(0.0),
            confidence = confidence,
            symptomDaysWithHighPollen = symptomDaysWithHighPollen,
            totalValidDays = relevantPoints.size,
            dataStatus = DataStatus.SUFFICIENT
        )
    }

    private fun adjustedSymptomScore(point: DiscoveryDataPoint): Double {
        // Reduce symptom weight when medication adherence is high,
        // since medication may be masking true symptom severity
        val medicationFactor = if (point.medicationAdherence >= 0.8) 1.3 else 1.0
        return point.compositeSymptomScore * medicationFactor
    }

    private fun pearsonCorrelation(x: List<Double>, y: List<Double>): Double {
        if (x.size != y.size || x.size < 3) return 0.0

        val n = x.size
        val meanX = x.average()
        val meanY = y.average()

        var sumXY = 0.0
        var sumX2 = 0.0
        var sumY2 = 0.0

        for (i in 0 until n) {
            val dx = x[i] - meanX
            val dy = y[i] - meanY
            sumXY += dx * dy
            sumX2 += dx * dx
            sumY2 += dy * dy
        }

        val denominator = sqrt(sumX2 * sumY2)
        if (denominator == 0.0) return 0.0

        return sumXY / denominator
    }

    private fun noDataResult(pollenType: PollenType) = DiscoveryResult(
        pollenType = pollenType,
        correlationScore = 0.0,
        confidence = Confidence.LOW,
        symptomDaysWithHighPollen = 0,
        totalValidDays = 0,
        dataStatus = DataStatus.NO_DATA
    )
}
