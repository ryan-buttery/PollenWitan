package com.ryan.pollenwitan.widget

import android.content.Context
import com.ryan.pollenwitan.R
import com.ryan.pollenwitan.data.repository.AirQualityRepository
import com.ryan.pollenwitan.data.repository.LocationRepository
import com.ryan.pollenwitan.data.repository.ProfileRepository
import com.ryan.pollenwitan.domain.model.SeverityClassifier
import com.ryan.pollenwitan.domain.model.SeverityLevel
import com.ryan.pollenwitan.ui.theme.localizedAbbreviation
import com.ryan.pollenwitan.ui.theme.toLabel
import kotlinx.coroutines.flow.first
import java.time.format.DateTimeFormatter

object WidgetDataProvider {

    private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

    suspend fun fetch(context: Context): PollenWidgetData {
        val profileRepository = ProfileRepository(context)
        val locationRepository = LocationRepository(context)
        val airQualityRepository = AirQualityRepository(context)

        val profiles = profileRepository.getProfiles().first()
        if (profiles.isEmpty()) {
            return emptyData(context.getString(R.string.widget_no_profiles))
        }

        val selectedId = profileRepository.getSelectedProfileId().first()
        val profile = profiles.find { it.id == selectedId } ?: profiles.first()

        val location = profile.location?.let {
            Triple(it.latitude, it.longitude, it.displayName)
        } ?: run {
            val loc = locationRepository.getLocation().first()
            Triple(loc.latitude, loc.longitude, loc.displayName)
        }

        val conditions = airQualityRepository.getCurrentConditions(
            location.first, location.second
        ).getOrNull() ?: return emptyData(context.getString(R.string.widget_no_data))

        val readings = conditions.pollenReadings
            .filter { it.type in profile.trackedAllergens }
            .map { reading ->
                val threshold = profile.trackedAllergens[reading.type]!!
                val severity = SeverityClassifier.pollenSeverity(reading.value, threshold)
                WidgetAllergenReading(
                    abbreviation = reading.type.localizedAbbreviation(context),
                    value = String.format("%.0f", reading.value),
                    severityColor = severityToArgb(severity)
                )
            }

        val aqiSeverity = conditions.aqiSeverity
        val aqiText = context.getString(
            R.string.widget_aqi_format,
            conditions.europeanAqi,
            aqiSeverity.toLabel(context)
        )

        return PollenWidgetData(
            profileName = profile.displayName,
            locationName = location.third,
            timestamp = conditions.timestamp.format(TIME_FORMAT),
            allergenReadings = readings,
            aqiText = aqiText,
            aqiColor = severityToArgb(aqiSeverity),
            hasData = true
        )
    }

    private fun emptyData(message: String) = PollenWidgetData(
        profileName = "",
        locationName = "",
        timestamp = "",
        allergenReadings = emptyList(),
        aqiText = message,
        aqiColor = 0xFF9E9E9E,
        hasData = false
    )

    private fun severityToArgb(severity: SeverityLevel): Long = when (severity) {
        SeverityLevel.None -> 0xFF9E9E9E       // Grey
        SeverityLevel.Low -> 0xFF4CAF50         // Green
        SeverityLevel.Moderate -> 0xFFFFC107    // Amber
        SeverityLevel.High -> 0xFFF44336        // Red
        SeverityLevel.VeryHigh -> 0xFF9C27B0    // Purple
    }
}
