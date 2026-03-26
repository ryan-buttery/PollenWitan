package com.ryan.pollenwitan.domain.calibration

import com.ryan.pollenwitan.domain.model.AllergenThreshold
import com.ryan.pollenwitan.domain.model.PollenType
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

data class CalibrationDataPoint(
    val date: LocalDate,
    val pollenConcentration: Double,
    val compositeSymptomScore: Double,
    val aqiConfounded: Boolean,
    val multiPollenWeight: Double,
    val medicationAdherence: Double
)

enum class Direction { LOWER, HIGHER }

enum class Confidence { LOW, MODERATE, HIGH }

enum class DataStatus {
    SUFFICIENT,
    INSUFFICIENT_ENTRIES,
    INSUFFICIENT_SYMPTOM_DAYS,
    INSUFFICIENT_CLEAN_DAYS,
    NO_DATA
}

data class ThresholdSuggestion(
    val pollenType: PollenType,
    val level: String,
    val currentValue: Double,
    val suggestedValue: Double,
    val direction: Direction,
    val confidence: Confidence,
    val dataPointCount: Int,
    val medicatedDayRatio: Double
)

data class CalibrationResult(
    val pollenType: PollenType,
    val suggestions: List<ThresholdSuggestion>,
    val dataStatus: DataStatus,
    val totalValidDays: Int
)

class ThresholdCalibrationEngine {

    companion object {
        private const val MIN_VALID_ENTRIES = 10
        private const val MIN_SYMPTOM_DAYS = 5
        private const val MIN_CLEAN_DAYS = 7
        private const val MIN_DATE_SPAN_DAYS = 14L
        private const val LOWER_THRESHOLD_DIFF = 0.20
        private const val HIGHER_THRESHOLD_DIFF = 0.40
        private const val AQI_CONFOUND_THRESHOLD = 60
    }

    fun calibrate(
        pollenType: PollenType,
        currentThreshold: AllergenThreshold,
        dataPoints: List<CalibrationDataPoint>
    ): CalibrationResult {
        if (dataPoints.isEmpty()) {
            return CalibrationResult(pollenType, emptyList(), DataStatus.NO_DATA, 0)
        }

        val totalValid = dataPoints.size
        val cleanDays = dataPoints.count { !it.aqiConfounded }
        val moderateDays = dataPoints.count { it.compositeSymptomScore >= 2.0 }

        val dateSpan = if (dataPoints.size >= 2) {
            val dates = dataPoints.map { it.date }.sorted()
            ChronoUnit.DAYS.between(dates.first(), dates.last())
        } else 0L

        val status = when {
            totalValid < MIN_VALID_ENTRIES -> DataStatus.INSUFFICIENT_ENTRIES
            moderateDays < MIN_SYMPTOM_DAYS -> DataStatus.INSUFFICIENT_SYMPTOM_DAYS
            cleanDays < MIN_CLEAN_DAYS -> DataStatus.INSUFFICIENT_CLEAN_DAYS
            dateSpan < MIN_DATE_SPAN_DAYS -> DataStatus.INSUFFICIENT_ENTRIES
            else -> DataStatus.SUFFICIENT
        }

        if (status != DataStatus.SUFFICIENT) {
            return CalibrationResult(pollenType, emptyList(), status, totalValid)
        }

        // Use only non-AQI-confounded days for percentile calculation
        val cleanPoints = dataPoints.filter { !it.aqiConfounded }

        val confidence = when {
            cleanDays >= 40 -> Confidence.HIGH
            cleanDays >= 20 -> Confidence.MODERATE
            else -> Confidence.LOW
        }

        val medicatedDayRatio = dataPoints.count { it.medicationAdherence >= 0.8 }
            .toDouble() / dataPoints.size

        val suggestions = mutableListOf<ThresholdSuggestion>()

        // Moderate onset: symptom score >= 2.0
        val moderateOnset = weightedPercentile(cleanPoints, 2.0, 0.25)
        if (moderateOnset != null) {
            val rounded = moderateOnset.roundToClean()
            if (shouldSuggest(rounded, currentThreshold.moderate)) {
                suggestions.add(
                    ThresholdSuggestion(
                        pollenType = pollenType,
                        level = "moderate",
                        currentValue = currentThreshold.moderate,
                        suggestedValue = rounded,
                        direction = if (rounded < currentThreshold.moderate) Direction.LOWER else Direction.HIGHER,
                        confidence = confidence,
                        dataPointCount = cleanDays,
                        medicatedDayRatio = medicatedDayRatio
                    )
                )
            }
        }

        // High onset: symptom score >= 3.5
        val highOnset = weightedPercentile(cleanPoints, 3.5, 0.25)
        if (highOnset != null) {
            val rounded = highOnset.roundToClean()
            if (shouldSuggest(rounded, currentThreshold.high)) {
                suggestions.add(
                    ThresholdSuggestion(
                        pollenType = pollenType,
                        level = "high",
                        currentValue = currentThreshold.high,
                        suggestedValue = rounded,
                        direction = if (rounded < currentThreshold.high) Direction.LOWER else Direction.HIGHER,
                        confidence = confidence,
                        dataPointCount = cleanDays,
                        medicatedDayRatio = medicatedDayRatio
                    )
                )
            }
        }

        // VeryHigh onset: symptom score >= 4.5
        val veryHighOnset = weightedPercentile(cleanPoints, 4.5, 0.25)
        val veryHighValue = if (veryHighOnset != null) {
            veryHighOnset.roundToClean()
        } else if (highOnset != null) {
            // Fallback: 2x the high onset
            (highOnset * 2.0).roundToClean()
        } else null

        if (veryHighValue != null && shouldSuggest(veryHighValue, currentThreshold.veryHigh)) {
            suggestions.add(
                ThresholdSuggestion(
                    pollenType = pollenType,
                    level = "veryHigh",
                    currentValue = currentThreshold.veryHigh,
                    suggestedValue = veryHighValue,
                    direction = if (veryHighValue < currentThreshold.veryHigh) Direction.LOWER else Direction.HIGHER,
                    confidence = if (veryHighOnset != null) confidence else Confidence.LOW,
                    dataPointCount = cleanDays,
                    medicatedDayRatio = medicatedDayRatio
                )
            )
        }

        // Validate ordering: moderate < high < veryHigh
        val validatedSuggestions = validateOrdering(suggestions, currentThreshold)

        return CalibrationResult(pollenType, validatedSuggestions, DataStatus.SUFFICIENT, totalValid)
    }

    private fun weightedPercentile(
        points: List<CalibrationDataPoint>,
        minSymptomScore: Double,
        percentile: Double
    ): Double? {
        val qualifying = points.filter { it.compositeSymptomScore >= minSymptomScore }
        if (qualifying.size < 3) return null

        // Build weighted concentration list
        val weighted = mutableListOf<Pair<Double, Double>>() // concentration, weight
        qualifying.forEach { point ->
            weighted.add(point.pollenConcentration to point.multiPollenWeight)
        }
        weighted.sortBy { it.first }

        val totalWeight = weighted.sumOf { it.second }
        val targetWeight = totalWeight * percentile
        var accumulatedWeight = 0.0

        for ((concentration, weight) in weighted) {
            accumulatedWeight += weight
            if (accumulatedWeight >= targetWeight) {
                return concentration
            }
        }
        return weighted.firstOrNull()?.first
    }

    private fun shouldSuggest(suggested: Double, current: Double): Boolean {
        if (suggested <= 0 || current <= 0) return false
        val diff = abs(suggested - current) / current
        return if (suggested < current) {
            diff >= LOWER_THRESHOLD_DIFF
        } else {
            diff >= HIGHER_THRESHOLD_DIFF
        }
    }

    private fun validateOrdering(
        suggestions: List<ThresholdSuggestion>,
        currentThreshold: AllergenThreshold
    ): List<ThresholdSuggestion> {
        // Determine effective values (suggested if present, else current)
        val moderateSuggestion = suggestions.find { it.level == "moderate" }
        val highSuggestion = suggestions.find { it.level == "high" }
        val veryHighSuggestion = suggestions.find { it.level == "veryHigh" }

        var effectiveModerate = moderateSuggestion?.suggestedValue ?: currentThreshold.moderate
        var effectiveHigh = highSuggestion?.suggestedValue ?: currentThreshold.high
        var effectiveVeryHigh = veryHighSuggestion?.suggestedValue ?: currentThreshold.veryHigh

        // Ensure ordering: moderate < high < veryHigh
        if (effectiveHigh <= effectiveModerate) {
            effectiveHigh = effectiveModerate + 1.0
        }
        if (effectiveVeryHigh <= effectiveHigh) {
            effectiveVeryHigh = effectiveHigh + 1.0
        }

        return suggestions.map { suggestion ->
            when (suggestion.level) {
                "moderate" -> suggestion.copy(suggestedValue = effectiveModerate)
                "high" -> suggestion.copy(suggestedValue = effectiveHigh)
                "veryHigh" -> suggestion.copy(suggestedValue = effectiveVeryHigh)
                else -> suggestion
            }
        }
    }

    private fun Double.roundToClean(): Double {
        val rounded = this.roundToInt().toDouble()
        return if (rounded < 1.0) 1.0 else rounded
    }
}
