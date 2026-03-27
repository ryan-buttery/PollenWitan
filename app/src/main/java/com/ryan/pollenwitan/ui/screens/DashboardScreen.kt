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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryan.pollenwitan.R
import com.ryan.pollenwitan.domain.model.CurrentConditions
import com.ryan.pollenwitan.domain.model.PollenReading
import com.ryan.pollenwitan.domain.model.SeverityClassifier
import com.ryan.pollenwitan.domain.model.SymptomDiaryEntry
import com.ryan.pollenwitan.domain.model.UserProfile
import com.ryan.pollenwitan.ui.components.ProfileSwitcher
import com.ryan.pollenwitan.ui.theme.localizedName
import com.ryan.pollenwitan.ui.theme.localizedUnitLabel
import com.ryan.pollenwitan.ui.theme.toColor
import com.ryan.pollenwitan.ui.theme.toLabel
import java.time.LocalTime
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.format.DateTimeFormatter

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel(),
    onNavigateToCheckIn: () -> Unit = {},
    onNavigateToDiscovery: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val weather = uiState.weatherState) {
        is WeatherState.Loading -> LoadingContent()
        is WeatherState.Success -> DashboardContent(
            conditions = weather.conditions,
            profiles = uiState.profiles,
            selectedProfile = uiState.selectedProfile,
            locationDisplayName = uiState.locationDisplayName,
            medicineSlots = uiState.medicineSlots,
            todaySymptomEntry = uiState.todaySymptomEntry,
            onSelectProfile = viewModel::selectProfile,
            onRefresh = viewModel::refresh,
            onConfirmDose = viewModel::confirmDose,
            onUnconfirmDose = viewModel::unconfirmDose,
            onNavigateToCheckIn = onNavigateToCheckIn,
            onNavigateToDiscovery = onNavigateToDiscovery
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
            text = stringResource(R.string.common_error_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
        )
        Button(onClick = onRetry) {
            Text(stringResource(R.string.common_retry))
        }
    }
}

@Composable
private fun DashboardContent(
    conditions: CurrentConditions,
    profiles: List<UserProfile>,
    selectedProfile: UserProfile?,
    locationDisplayName: String,
    medicineSlots: List<MedicineSlot>,
    todaySymptomEntry: SymptomDiaryEntry?,
    onSelectProfile: (String) -> Unit,
    onRefresh: () -> Unit,
    onConfirmDose: (String, Int) -> Unit,
    onUnconfirmDose: (String, Int) -> Unit,
    onNavigateToCheckIn: () -> Unit,
    onNavigateToDiscovery: (String) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = selectedProfile?.displayName ?: locationDisplayName.ifEmpty { stringResource(R.string.common_loading) },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        if (selectedProfile != null && locationDisplayName.isNotEmpty()) {
            Text(
                text = locationDisplayName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = stringResource(R.string.dashboard_current_conditions, conditions.timestamp.format(DateTimeFormatter.ofPattern("EEEE d MMMM, HH:mm"))),
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
                    text = if (selectedProfile != null) stringResource(R.string.dashboard_pollen_title_profile, selectedProfile.displayName) else stringResource(R.string.dashboard_pollen_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.dashboard_pollen_units),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (selectedProfile != null && selectedProfile.discoveryMode) {
                    // Discovery mode: show all pollens dimmed with default thresholds
                    conditions.pollenReadings.forEach { reading ->
                        val defaultThreshold = UserProfile.defaultThreshold(reading.type)
                        val severity = SeverityClassifier.pollenSeverity(reading.value, defaultThreshold)
                        PollenRow(reading.copy(severity = severity), dimmed = true)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                } else if (selectedProfile != null) {
                    // Show tracked allergens with profile-specific thresholds
                    val trackedReadings = conditions.pollenReadings.filter { reading ->
                        reading.type in selectedProfile.trackedAllergens
                    }
                    if (trackedReadings.isEmpty()) {
                        Text(
                            text = stringResource(R.string.dashboard_no_tracked_allergens),
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
                            text = stringResource(R.string.common_other),
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

        // Discovery mode banner
        if (selectedProfile != null && selectedProfile.discoveryMode) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.discovery_banner_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.discovery_banner_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onNavigateToDiscovery(selectedProfile.id) }
                    ) {
                        Text(stringResource(R.string.discovery_view_analysis))
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
                    text = stringResource(R.string.dashboard_air_quality),
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
                        text = stringResource(R.string.dashboard_european_aqi),
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

        // Medicines card
        if (medicineSlots.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.dashboard_medicines),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    medicineSlots.forEach { slot ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = slot.confirmed,
                                onCheckedChange = { checked ->
                                    if (checked) onConfirmDose(slot.medicineId, slot.slotIndex)
                                    else onUnconfirmDose(slot.medicineId, slot.slotIndex)
                                }
                            )
                            Text(
                                text = String.format("%02d:00", slot.hour),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.width(52.dp)
                            )
                            Text(
                                text = slot.medicineName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = stringResource(R.string.dashboard_dose_format, slot.dose, slot.medicineType.localizedUnitLabel()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val allConfirmed = medicineSlots.isNotEmpty() && medicineSlots.all { it.confirmed }
                    val medPhrases = LocalContext.current.resources.getStringArray(R.array.reinforcement_medication)
                    val medPhrase = remember { medPhrases.random() }
                    AnimatedVisibility(
                        visible = allConfirmed,
                        enter = fadeIn() + slideInVertically { it }
                    ) {
                        Text(
                            text = medPhrase,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            }
        }

        // Symptom check-in card
        if (selectedProfile != null) {
            val isEvening = LocalTime.now().hour >= 17
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.symptom_checkin_card),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.symptom_checkin_subtitle_evening),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (todaySymptomEntry != null) {
                        val avgSeverity = todaySymptomEntry.ratings
                            .map { it.severity }
                            .average()
                        Text(
                            text = stringResource(R.string.symptom_logged_summary, todaySymptomEntry.ratings.size),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.symptom_avg_severity, avgSeverity),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onNavigateToCheckIn) {
                            Text(stringResource(R.string.symptom_checkin_button_update))
                        }
                    } else if (isEvening) {
                        Text(
                            text = stringResource(R.string.symptom_checkin_prompt_evening),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onNavigateToCheckIn) {
                            Text(stringResource(R.string.symptom_checkin_button_evening))
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.symptom_checkin_prompt_early),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Refresh button
        Button(
            onClick = onRefresh,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(stringResource(R.string.common_refresh))
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
            text = reading.type.localizedName(),
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
