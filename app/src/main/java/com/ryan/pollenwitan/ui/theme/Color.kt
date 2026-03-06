package com.ryan.pollenwitan.ui.theme

import androidx.compose.ui.graphics.Color
import com.ryan.pollenwitan.domain.model.SeverityLevel

// Severity colours for allergen levels — consistent across light/dark themes
object SeverityColors {
    val None = Color(0xFF9E9E9E)       // Grey
    val Low = Color(0xFF4CAF50)        // Green
    val Moderate = Color(0xFFFFC107)   // Amber
    val High = Color(0xFFF44336)       // Red
    val VeryHigh = Color(0xFF9C27B0)   // Purple
}

// AQI colours
object AqiColors {
    val Good = Color(0xFF4CAF50)
    val Fair = Color(0xFFFFC107)
    val Moderate = Color(0xFFFF9800)
    val Poor = Color(0xFFF44336)
    val VeryPoor = Color(0xFF7B1FA2)
}

fun SeverityLevel.toColor(): Color = when (this) {
    SeverityLevel.None -> SeverityColors.None
    SeverityLevel.Low -> SeverityColors.Low
    SeverityLevel.Moderate -> SeverityColors.Moderate
    SeverityLevel.High -> SeverityColors.High
    SeverityLevel.VeryHigh -> SeverityColors.VeryHigh
}

fun SeverityLevel.toLabel(): String = when (this) {
    SeverityLevel.None -> "None"
    SeverityLevel.Low -> "Low"
    SeverityLevel.Moderate -> "Moderate"
    SeverityLevel.High -> "High"
    SeverityLevel.VeryHigh -> "Very High"
}
