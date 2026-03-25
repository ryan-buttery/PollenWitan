package com.ryan.pollenwitan.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryan.pollenwitan.domain.model.ForecastDay
import com.ryan.pollenwitan.domain.model.HourlyReading
import com.ryan.pollenwitan.domain.model.PeriodSummary
import com.ryan.pollenwitan.domain.model.PollenReading
import com.ryan.pollenwitan.domain.model.PollenType
import com.ryan.pollenwitan.domain.model.SeverityClassifier
import com.ryan.pollenwitan.domain.model.SeverityLevel
import com.ryan.pollenwitan.domain.model.UserProfile
import com.ryan.pollenwitan.ui.components.ProfileSwitcher
import com.ryan.pollenwitan.ui.theme.localizedAbbreviation
import com.ryan.pollenwitan.ui.theme.localizedName
import com.ryan.pollenwitan.ui.theme.toColor
import com.ryan.pollenwitan.ui.theme.toLabel
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ryan.pollenwitan.R
import java.time.format.DateTimeFormatter

@Composable
fun ForecastScreen(viewModel: ForecastViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val forecast = uiState.forecastState) {
        is ForecastState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is ForecastState.Error -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.common_error_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = forecast.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                )
                Button(onClick = viewModel::refresh) { Text(stringResource(R.string.common_retry)) }
            }
        }
        is ForecastState.Success -> ForecastContent(
            days = forecast.days,
            profiles = uiState.profiles,
            selectedProfile = uiState.selectedProfile,
            locationDisplayName = uiState.locationDisplayName,
            expandedDayIndex = uiState.expandedDayIndex,
            onSelectProfile = viewModel::selectProfile,
            onToggleDay = viewModel::toggleDay,
            onRefresh = viewModel::refresh
        )
    }
}

@Composable
private fun ForecastContent(
    days: List<ForecastDay>,
    profiles: List<UserProfile>,
    selectedProfile: UserProfile?,
    locationDisplayName: String,
    expandedDayIndex: Int?,
    onSelectProfile: (String) -> Unit,
    onToggleDay: (Int) -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = locationDisplayName.ifEmpty { stringResource(R.string.common_loading) },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.forecast_4_day),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        ProfileSwitcher(
            profiles = profiles,
            selectedProfileId = selectedProfile?.id,
            onSelectProfile = onSelectProfile
        )
        if (profiles.size > 1) {
            Spacer(modifier = Modifier.height(16.dp))
        }

        var legendExpanded by remember { mutableStateOf(false) }
        Text(
            text = if (legendExpanded) stringResource(R.string.forecast_hide_key) else stringResource(R.string.forecast_show_key),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { legendExpanded = !legendExpanded }
        )
        AnimatedVisibility(visible = legendExpanded) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                SeverityLegend()
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        days.forEachIndexed { index, day ->
            ForecastDayCard(
                day = day,
                selectedProfile = selectedProfile,
                isExpanded = expandedDayIndex == index,
                onToggle = { onToggleDay(index) }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onRefresh,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(stringResource(R.string.common_refresh))
        }
    }
}

@Composable
private fun ForecastDayCard(
    day: ForecastDay,
    selectedProfile: UserProfile?,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val dayFormatter = DateTimeFormatter.ofPattern("EEEE")
    val dateFormatter = DateTimeFormatter.ofPattern("d MMMM")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Day header with peak severity dots
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = day.date.format(dayFormatter),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = day.date.format(dateFormatter),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Peak severity dots for tracked allergens
                Text(
                    text = stringResource(R.string.forecast_peak),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                PeakSeverityDots(day.peakPollenReadings, selectedProfile)

                Spacer(modifier = Modifier.width(8.dp))

                // Peak AQI
                Text(
                    text = stringResource(R.string.forecast_aqi_label, day.peakAqi, day.peakAqiSeverity.toLabel()),
                    style = MaterialTheme.typography.bodySmall,
                    color = day.peakAqiSeverity.toColor()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Column headers for pollen types
            PollenColumnHeaders(selectedProfile)

            Spacer(modifier = Modifier.height(4.dp))

            // Period rows
            day.periods.forEach { period ->
                PeriodRow(period, selectedProfile)
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Expanded hourly breakdown
            AnimatedVisibility(visible = isExpanded) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = stringResource(R.string.forecast_hourly_header),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    day.hourlyReadings.forEach { reading ->
                        HourlyRow(reading, selectedProfile)
                    }
                }
            }
        }
    }
}

@Composable
private fun PollenColumnHeaders(selectedProfile: UserProfile?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(80.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PollenType.entries.forEach { type ->
                val isTracked = selectedProfile?.trackedAllergens?.containsKey(type) != false
                val alpha = if (isTracked) 0.7f else 0.3f
                Text(
                    text = type.localizedAbbreviation(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    modifier = Modifier.width(18.dp)
                )
            }
        }
    }
}

@Composable
private fun PeakSeverityDots(
    peakReadings: List<PollenReading>,
    selectedProfile: UserProfile?
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        peakReadings.forEach { reading ->
            val isTracked = selectedProfile?.trackedAllergens?.containsKey(reading.type) != false
            val severity = if (selectedProfile != null) {
                val threshold = selectedProfile.trackedAllergens[reading.type]
                if (threshold != null) SeverityClassifier.pollenSeverity(reading.value, threshold)
                else reading.severity
            } else {
                reading.severity
            }
            val alpha = if (isTracked) 1f else 0.4f
            Box(
                modifier = Modifier
                    .width(18.dp)
                    .height(10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(severity.toColor().copy(alpha = alpha))
                )
            }
        }
    }
}

@Composable
private fun PeriodRow(
    period: PeriodSummary,
    selectedProfile: UserProfile?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.width(80.dp)) {
            Text(
                text = period.period.localizedName(),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "%02d–%02d".format(
                    period.period.hourRange.first,
                    period.period.hourRange.last + 1
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Severity dots for each pollen type
        PeakSeverityDots(period.peakPollenReadings, selectedProfile)

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = stringResource(R.string.forecast_aqi_label, period.avgAqi, period.aqiSeverity.toLabel()),
            style = MaterialTheme.typography.bodySmall,
            color = period.aqiSeverity.toColor()
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeverityLegend() {
    Column {
        // Severity colour key
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SeverityLevel.entries.forEach { level ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(level.toColor())
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = level.toLabel(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Abbreviation key
        Text(
            text = PollenType.entries.map { stringResource(R.string.forecast_abbreviation_format, it.localizedAbbreviation(), it.localizedName()) }.joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Units note
        Text(
            text = stringResource(R.string.forecast_legend_units),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun HourlyRow(
    reading: HourlyReading,
    selectedProfile: UserProfile?
) {
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = reading.hour.format(timeFormatter),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp)
        )

        // Pollen values
        reading.pollenReadings.forEach { pollen ->
            val isTracked = selectedProfile?.trackedAllergens?.containsKey(pollen.type) != false
            val severity = if (selectedProfile != null) {
                val threshold = selectedProfile.trackedAllergens[pollen.type]
                if (threshold != null) SeverityClassifier.pollenSeverity(pollen.value, threshold)
                else pollen.severity
            } else {
                pollen.severity
            }
            val alpha = if (isTracked) 1f else 0.4f

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(severity.toColor().copy(alpha = alpha))
            )
            Text(
                text = String.format("%.0f", pollen.value),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                modifier = Modifier.width(28.dp).padding(start = 2.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // AQI
        Text(
            text = "${reading.europeanAqi}",
            style = MaterialTheme.typography.labelSmall,
            color = reading.aqiSeverity.toColor()
        )
    }
}
