package com.ryan.pollenwitan.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.ryan.pollenwitan.R
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

// Composable variant — use in UI
@Composable
fun SeverityLevel.toLabel(): String = when (this) {
    SeverityLevel.None -> stringResource(R.string.severity_none)
    SeverityLevel.Low -> stringResource(R.string.severity_low)
    SeverityLevel.Moderate -> stringResource(R.string.severity_moderate)
    SeverityLevel.High -> stringResource(R.string.severity_high)
    SeverityLevel.VeryHigh -> stringResource(R.string.severity_very_high)
}

// Context variant — use in workers/notifications
fun SeverityLevel.toLabel(context: Context): String = when (this) {
    SeverityLevel.None -> context.getString(R.string.severity_none)
    SeverityLevel.Low -> context.getString(R.string.severity_low)
    SeverityLevel.Moderate -> context.getString(R.string.severity_moderate)
    SeverityLevel.High -> context.getString(R.string.severity_high)
    SeverityLevel.VeryHigh -> context.getString(R.string.severity_very_high)
}
