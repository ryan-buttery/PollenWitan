package com.ryan.pollenwitan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryan.pollenwitan.domain.model.CurrentConditions
import com.ryan.pollenwitan.domain.model.PollenReading
import com.ryan.pollenwitan.domain.model.SeverityClassifier
import com.ryan.pollenwitan.domain.model.UserProfile
import com.ryan.pollenwitan.ui.components.ProfileSwitcher
import com.ryan.pollenwitan.ui.theme.toColor
import com.ryan.pollenwitan.ui.theme.toLabel
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.format.DateTimeFormatter

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val weather = uiState.weatherState) {
        is WeatherState.Loading -> LoadingContent()
        is WeatherState.Success -> DashboardContent(
            conditions = weather.conditions,
            profiles = uiState.profiles,
            selectedProfile = uiState.selectedProfile,
            locationDisplayName = uiState.locationDisplayName,
            onSelectProfile = viewModel::selectProfile,
            onRefresh = viewModel::refresh
        )
        is WeatherState.Error -> ErrorContent(
            message = weather.message,
            onRetry = viewModel::refresh
        )
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Something went wrong",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
        )
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
private fun DashboardContent(
    conditions: CurrentConditions,
    profiles: List<UserProfile>,
    selectedProfile: UserProfile?,
    locationDisplayName: String,
    onSelectProfile: (String) -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = locationDisplayName.ifEmpty { "Loading..." },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = conditions.timestamp.format(DateTimeFormatter.ofPattern("EEEE d MMMM, HH:mm")),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Profile switcher
        ProfileSwitcher(
            profiles = profiles,
            selectedProfileId = selectedProfile?.id,
            onSelectProfile = onSelectProfile
        )
        if (profiles.size > 1) {
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Pollen card -- profile-aware
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (selectedProfile != null) "Pollen — ${selectedProfile.displayName}" else "Pollen",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (selectedProfile != null) {
                    // Show tracked allergens with profile-specific thresholds
                    val trackedReadings = conditions.pollenReadings.filter { reading ->
                        reading.type in selectedProfile.trackedAllergens
                    }
                    if (trackedReadings.isEmpty()) {
                        Text(
                            text = "No tracked allergens",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        trackedReadings.forEach { reading ->
                            val threshold = selectedProfile.trackedAllergens[reading.type]!!
                            val personalSeverity = SeverityClassifier.pollenSeverity(reading.value, threshold)
                            PollenRow(reading.copy(severity = personalSeverity))
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    // Show untracked allergens dimmed
                    val untrackedReadings = conditions.pollenReadings.filter { reading ->
                        reading.type !in selectedProfile.trackedAllergens
                    }
                    if (untrackedReadings.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Other",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        untrackedReadings.forEach { reading ->
                            PollenRow(reading, dimmed = true)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                } else {
                    // No profile selected -- show all with default thresholds
                    conditions.pollenReadings.forEach { reading ->
                        PollenRow(reading)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Air quality card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Air Quality",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))

                // European AQI
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(conditions.aqiSeverity.toColor())
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "European AQI",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${conditions.europeanAqi}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = conditions.aqiSeverity.toColor()
                    )
                }
                Text(
                    text = conditions.aqiSeverity.toLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = conditions.aqiSeverity.toColor(),
                    modifier = Modifier.align(Alignment.End)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // PM2.5
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("PM2.5", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = String.format("%.1f \u00B5g/m\u00B3", conditions.pm25),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))

                // PM10
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("PM10", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = String.format("%.1f \u00B5g/m\u00B3", conditions.pm10),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Refresh button
        Button(
            onClick = onRefresh,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Refresh")
        }
    }
}

@Composable
private fun PollenRow(reading: PollenReading, dimmed: Boolean = false) {
    val alpha = if (dimmed) 0.5f else 1f
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(reading.severity.toColor().copy(alpha = alpha))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = reading.type.displayName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = String.format("%.0f", reading.value),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = reading.severity.toLabel(),
            style = MaterialTheme.typography.bodySmall,
            color = reading.severity.toColor().copy(alpha = alpha)
        )
    }
}
