package com.ryan.pollenwitan.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryan.pollenwitan.data.repository.NotificationPrefs
import com.ryan.pollenwitan.domain.model.LocationMode
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var permissionDeniedPermanently by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        if (granted) {
            viewModel.requestGpsFix()
            permissionDeniedPermanently = false
        } else {
            permissionDeniedPermanently = true
        }
    }

    // Request POST_NOTIFICATIONS on Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Location section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Location",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = uiState.locationMode == LocationMode.Manual,
                        onClick = { viewModel.setLocationMode(LocationMode.Manual) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Manual", style = MaterialTheme.typography.bodyLarge)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = uiState.locationMode == LocationMode.Gps,
                        onClick = { viewModel.setLocationMode(LocationMode.Gps) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("GPS", style = MaterialTheme.typography.bodyLarge)
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (uiState.locationMode == LocationMode.Manual) {
                    ManualLocationFields(
                        latitude = uiState.manualLatitude,
                        longitude = uiState.manualLongitude,
                        displayName = uiState.manualDisplayName,
                        onSave = viewModel::saveManualLocation
                    )
                } else {
                    GpsLocationSection(
                        gpsStatus = uiState.gpsStatus,
                        permissionDeniedPermanently = permissionDeniedPermanently,
                        onLocateMe = {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        onOpenSettings = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                            )
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Notifications section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Notifications",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Prompt for notification permission on Android 13+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    OutlinedButton(
                        onClick = {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Grant Notification Permission")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                NotificationSettings(
                    prefs = uiState.notificationPrefs,
                    onMorningBriefingToggle = viewModel::setMorningBriefingEnabled,
                    onMorningHourChange = viewModel::setMorningBriefingHour,
                    onThresholdToggle = viewModel::setThresholdAlertsEnabled,
                    onCompoundRiskToggle = viewModel::setCompoundRiskAlertsEnabled
                )
            }
        }
    }
}

@Composable
private fun NotificationSettings(
    prefs: NotificationPrefs,
    onMorningBriefingToggle: (Boolean) -> Unit,
    onMorningHourChange: (Int) -> Unit,
    onThresholdToggle: (Boolean) -> Unit,
    onCompoundRiskToggle: (Boolean) -> Unit
) {
    // Morning briefing
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Morning briefing", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Daily summary at ${String.format("%02d:00", prefs.morningBriefingHour)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = prefs.morningBriefingEnabled,
            onCheckedChange = onMorningBriefingToggle
        )
    }

    // Briefing hour selector
    if (prefs.morningBriefingEnabled) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Hour:", style = MaterialTheme.typography.bodyMedium)
            listOf(6, 7, 8, 9).forEach { hour ->
                OutlinedButton(
                    onClick = { onMorningHourChange(hour) },
                    enabled = prefs.morningBriefingHour != hour,
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Text("${hour}:00", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Threshold alerts
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Threshold alerts", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Alert when pollen reaches High or Very High",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = prefs.thresholdAlertsEnabled,
            onCheckedChange = onThresholdToggle
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Compound risk alerts
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Compound risk alerts", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Pollen + poor air quality (asthma profiles)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = prefs.compoundRiskAlertsEnabled,
            onCheckedChange = onCompoundRiskToggle
        )
    }
}

@Composable
private fun ManualLocationFields(
    latitude: String,
    longitude: String,
    displayName: String,
    onSave: (String, String, String) -> Unit
) {
    var lat by remember(latitude) { mutableStateOf(latitude) }
    var lon by remember(longitude) { mutableStateOf(longitude) }
    var name by remember(displayName) { mutableStateOf(displayName) }

    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Display name") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = lat,
            onValueChange = { lat = it },
            label = { Text("Latitude") },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        OutlinedTextField(
            value = lon,
            onValueChange = { lon = it },
            label = { Text("Longitude") },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    Button(
        onClick = { onSave(lat, lon, name) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Save Location")
    }
}

@Composable
private fun GpsLocationSection(
    gpsStatus: GpsStatus,
    permissionDeniedPermanently: Boolean,
    onLocateMe: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Button(
        onClick = onLocateMe,
        enabled = gpsStatus !is GpsStatus.Requesting,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (gpsStatus is GpsStatus.Requesting) {
            CircularProgressIndicator(
                modifier = Modifier.height(20.dp).width(20.dp),
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Locating...")
        } else {
            Text("Locate Me")
        }
    }

    when (gpsStatus) {
        is GpsStatus.Success -> {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Location: ${gpsStatus.displayName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        is GpsStatus.Error -> {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = gpsStatus.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
        else -> {}
    }

    if (permissionDeniedPermanently) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Location permission denied. Grant it in app settings to use GPS.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedButton(onClick = onOpenSettings) {
            Text("Open App Settings")
        }
    }
}
