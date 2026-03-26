package com.ryan.pollenwitan.widget

data class WidgetAllergenReading(
    val abbreviation: String,
    val value: String,
    val severityColor: Long
)

data class PollenWidgetData(
    val profileName: String,
    val locationName: String,
    val timestamp: String,
    val allergenReadings: List<WidgetAllergenReading>,
    val peakAllergenReadings: List<WidgetAllergenReading>,
    val aqiText: String,
    val aqiColor: Long,
    val medicationText: String,
    val hasMedication: Boolean,
    val hasData: Boolean
)
