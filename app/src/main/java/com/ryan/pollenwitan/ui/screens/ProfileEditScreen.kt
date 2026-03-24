package com.ryan.pollenwitan.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.ryan.pollenwitan.domain.model.AllergenThreshold
import com.ryan.pollenwitan.domain.model.Medicine
import com.ryan.pollenwitan.domain.model.PollenType
import com.ryan.pollenwitan.domain.model.UserProfile

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileEditScreen(
    navController: NavController,
    profileId: String?,
    viewModel: ProfileEditViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(profileId) {
        viewModel.loadProfile(profileId)
    }

    LaunchedEffect(uiState.savedSuccessfully) {
        if (uiState.savedSuccessfully) {
            navController.popBackStack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = if (uiState.isNewProfile) "New Profile" else "Edit Profile",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Display name
        OutlinedTextField(
            value = uiState.displayName,
            onValueChange = viewModel::setDisplayName,
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Asthma toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Has asthma", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = uiState.hasAsthma,
                onCheckedChange = viewModel::setHasAsthma
            )
        }
        Spacer(modifier = Modifier.height(20.dp))

        // Location
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Custom location", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = uiState.useCustomLocation,
                onCheckedChange = viewModel::setUseCustomLocation
            )
        }
        if (uiState.useCustomLocation) {
            Spacer(modifier = Modifier.height(8.dp))
            LocationCard(
                latitude = uiState.locationLatitude,
                longitude = uiState.locationLongitude,
                displayName = uiState.locationDisplayName,
                gpsStatus = uiState.gpsStatus,
                onLatitudeChange = viewModel::setLocationLatitude,
                onLongitudeChange = viewModel::setLocationLongitude,
                onDisplayNameChange = viewModel::setLocationDisplayName,
                onRequestGps = viewModel::requestGpsFix
            )
        } else {
            Text(
                text = "Uses default location from Settings",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(20.dp))

        // Allergen picker
        Text(
            text = "Tracked Allergens",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PollenType.entries.forEach { type ->
                FilterChip(
                    selected = type in uiState.trackedAllergens,
                    onClick = { viewModel.toggleAllergen(type) },
                    label = { Text(type.displayName) }
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))

        // Threshold configuration per selected allergen
        uiState.trackedAllergens.forEach { type ->
            val threshold = uiState.thresholds[type] ?: return@forEach
            val isCustom = uiState.useCustomThresholds[type] ?: false

            ThresholdCard(
                type = type,
                threshold = threshold,
                isCustom = isCustom,
                onToggleCustom = { viewModel.setUseCustomThreshold(type, it) },
                onUpdateThreshold = { level, value -> viewModel.updateThreshold(type, level, value) }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Medicines section
        if (uiState.availableMedicines.isNotEmpty()) {
            Text(
                text = "Medicines",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            uiState.medicineAssignments.forEach { assignment ->
                MedicineAssignmentCard(
                    assignment = assignment,
                    onUpdateDose = { viewModel.updateAssignmentDose(assignment.medicineId, it) },
                    onUpdateTimesPerDay = { viewModel.updateAssignmentTimesPerDay(assignment.medicineId, it) },
                    onToggleHour = { viewModel.toggleReminderHour(assignment.medicineId, it) },
                    onRemove = { viewModel.removeMedicineAssignment(assignment.medicineId) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            val assignedIds = uiState.medicineAssignments.map { it.medicineId }.toSet()
            val unassigned = uiState.availableMedicines.filter { it.id !in assignedIds }
            if (unassigned.isNotEmpty()) {
                AddMedicineButton(
                    unassignedMedicines = unassigned,
                    onAdd = viewModel::addMedicineAssignment
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Validation error
        uiState.validationError?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Save button
        Button(
            onClick = viewModel::save,
            enabled = !uiState.isSaving,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(if (uiState.isNewProfile) "Create Profile" else "Save Changes")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ThresholdCard(
    type: PollenType,
    threshold: AllergenThreshold,
    isCustom: Boolean,
    onToggleCustom: (Boolean) -> Unit,
    onUpdateThreshold: (String, Double) -> Unit
) {
    val defaults = UserProfile.defaultThreshold(type)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = type.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Custom",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = isCustom,
                        onCheckedChange = onToggleCustom
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (isCustom) {
                ThresholdRow("Low", threshold.low) { onUpdateThreshold("low", it) }
                ThresholdRow("Moderate", threshold.moderate) { onUpdateThreshold("moderate", it) }
                ThresholdRow("High", threshold.high) { onUpdateThreshold("high", it) }
                ThresholdRow("Very High", threshold.veryHigh) { onUpdateThreshold("veryHigh", it) }
            } else {
                Text(
                    text = "Low: ${defaults.low.toInt()}  ·  Moderate: ${defaults.moderate.toInt()}  ·  High: ${defaults.high.toInt()}  ·  Very High: ${defaults.veryHigh.toInt()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ThresholdRow(
    label: String,
    value: Double,
    onValueChange: (Double) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(80.dp)
        )
        OutlinedTextField(
            value = if (value == 0.0) "" else value.toBigDecimal().stripTrailingZeros().toPlainString(),
            onValueChange = { text ->
                text.toDoubleOrNull()?.let { onValueChange(it) }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f),
            suffix = { Text("grains/m³", style = MaterialTheme.typography.bodySmall) }
        )
    }
}

@Composable
private fun LocationCard(
    latitude: String,
    longitude: String,
    displayName: String,
    gpsStatus: GpsStatus,
    onLatitudeChange: (String) -> Unit,
    onLongitudeChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onRequestGps: () -> Unit
) {
    var permissionDeniedPermanently by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        if (granted) {
            onRequestGps()
            permissionDeniedPermanently = false
        } else {
            permissionDeniedPermanently = true
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = displayName,
                onValueChange = onDisplayNameChange,
                label = { Text("Location name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = latitude,
                    onValueChange = onLatitudeChange,
                    label = { Text("Latitude") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = longitude,
                    onValueChange = onLongitudeChange,
                    label = { Text("Longitude") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            ) {
                Text("Locate Me")
            }

            when (gpsStatus) {
                is GpsStatus.Requesting -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Getting location...", style = MaterialTheme.typography.bodySmall)
                    }
                }
                is GpsStatus.Success -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Located: ${gpsStatus.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is GpsStatus.Error -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = gpsStatus.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                is GpsStatus.Idle -> {}
            }

            if (permissionDeniedPermanently) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Location permission denied. Enable it in app settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MedicineAssignmentCard(
    assignment: MedicineAssignmentUiState,
    onUpdateDose: (String) -> Unit,
    onUpdateTimesPerDay: (String) -> Unit,
    onToggleHour: (Int) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = assignment.medicineName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = assignment.medicineType.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = onRemove) {
                    Text("Remove")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = assignment.dose,
                    onValueChange = onUpdateDose,
                    label = { Text("Dose") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    suffix = { Text(assignment.medicineType.unitLabel, style = MaterialTheme.typography.bodySmall) }
                )
                OutlinedTextField(
                    value = assignment.timesPerDay,
                    onValueChange = onUpdateTimesPerDay,
                    label = { Text("Times/day") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Reminder hours", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                (6..22).forEach { hour ->
                    FilterChip(
                        selected = hour in assignment.reminderHours,
                        onClick = { onToggleHour(hour) },
                        label = { Text(String.format("%02d", hour)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AddMedicineButton(
    unassignedMedicines: List<Medicine>,
    onAdd: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = { showDialog = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Add Medicine")
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Add Medicine") },
            text = {
                Column {
                    unassignedMedicines.forEach { medicine ->
                        TextButton(
                            onClick = {
                                onAdd(medicine.id)
                                showDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${medicine.name} (${medicine.type.displayName})")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
