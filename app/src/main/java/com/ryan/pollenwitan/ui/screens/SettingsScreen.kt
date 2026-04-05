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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.ryan.pollenwitan.ui.theme.localizedName
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryan.pollenwitan.data.repository.NotificationPrefs
import com.ryan.pollenwitan.domain.model.LocationMode
import com.ryan.pollenwitan.domain.model.Medicine
import com.ryan.pollenwitan.domain.model.MedicineType
import com.ryan.pollenwitan.BuildConfig
import com.ryan.pollenwitan.R
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var permissionDeniedPermanently by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showImportConfirm by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var showCsvProfilePicker by remember { mutableStateOf(false) }
    var showExportPasswordDialog by remember { mutableStateOf(false) }
    var pendingExportUri by remember { mutableStateOf<Uri?>(null) }
    var showImportPasswordDialog by remember { mutableStateOf(false) }

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

    // SAF launchers for export/import
    val exportJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            pendingExportUri = it
            showExportPasswordDialog = true
        }
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            pendingImportUri = it
            showImportConfirm = true
        }
    }

    var csvProfileId by remember { mutableStateOf<String?>(null) }
    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            val profileId = csvProfileId ?: return@let
            context.contentResolver.openOutputStream(it)?.let { stream ->
                viewModel.exportSymptomCsv(profileId, stream) { result ->
                    result.onSuccess {
                        statusMessage = context.getString(R.string.settings_csv_success)
                    }.onFailure { e ->
                        statusMessage = context.getString(R.string.settings_csv_error, e.message ?: "Unknown")
                    }
                }
            }
        }
    }

    // Helper to perform import with optional password
    fun performImport(uri: Uri, password: String? = null) {
        context.contentResolver.openInputStream(uri)?.let { stream ->
            viewModel.importAllData(stream, password) { result ->
                result.onSuccess { summary ->
                    statusMessage = context.getString(R.string.settings_import_success, summary)
                }.onFailure { e ->
                    if (password == null && e.message?.contains("encrypted") == true) {
                        // File is encrypted — prompt for password
                        showImportPasswordDialog = true
                    } else {
                        statusMessage = context.getString(R.string.settings_import_error, e.message ?: "Unknown")
                    }
                }
            }
        }
    }

    // Import confirmation dialog
    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = {
                showImportConfirm = false
                pendingImportUri = null
            },
            title = { Text(stringResource(R.string.settings_import_confirm_title)) },
            text = { Text(stringResource(R.string.settings_import_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showImportConfirm = false
                    pendingImportUri?.let { uri -> performImport(uri) }
                }) {
                    Text(stringResource(R.string.settings_import_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportConfirm = false
                    pendingImportUri = null
                }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // Export password dialog
    if (showExportPasswordDialog) {
        var password by remember { mutableStateOf("") }
        var confirmPassword by remember { mutableStateOf("") }
        val passwordsMatch = password.isNotEmpty() && password == confirmPassword

        AlertDialog(
            onDismissRequest = {
                showExportPasswordDialog = false
                pendingExportUri = null
            },
            title = { Text(stringResource(R.string.settings_export_password_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.settings_export_password_hint)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text(stringResource(R.string.settings_export_confirm_password_hint)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (confirmPassword.isNotEmpty() && password != confirmPassword) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.settings_export_passwords_mismatch),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.settings_export_plaintext_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExportPasswordDialog = false
                        pendingExportUri?.let { uri ->
                            context.contentResolver.openOutputStream(uri)?.let { stream ->
                                viewModel.exportAllData(stream, password) { result ->
                                    result.onSuccess {
                                        statusMessage = context.getString(R.string.settings_export_success)
                                    }.onFailure { e ->
                                        statusMessage = context.getString(R.string.settings_export_error, e.message ?: "Unknown")
                                    }
                                }
                            }
                        }
                        pendingExportUri = null
                    },
                    enabled = passwordsMatch
                ) {
                    Text(stringResource(R.string.settings_export_encrypt))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExportPasswordDialog = false
                    pendingExportUri?.let { uri ->
                        context.contentResolver.openOutputStream(uri)?.let { stream ->
                            viewModel.exportAllData(stream) { result ->
                                result.onSuccess {
                                    statusMessage = context.getString(R.string.settings_export_success)
                                }.onFailure { e ->
                                    statusMessage = context.getString(R.string.settings_export_error, e.message ?: "Unknown")
                                }
                            }
                        }
                    }
                    pendingExportUri = null
                }) {
                    Text(stringResource(R.string.settings_export_skip))
                }
            }
        )
    }

    // Import password dialog (shown when encrypted file detected)
    if (showImportPasswordDialog) {
        var importPassword by remember { mutableStateOf("") }
        var importPasswordError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = {
                showImportPasswordDialog = false
                pendingImportUri = null
            },
            title = { Text(stringResource(R.string.settings_import_password_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = importPassword,
                        onValueChange = {
                            importPassword = it
                            importPasswordError = null
                        },
                        label = { Text(stringResource(R.string.settings_import_password_hint)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = importPasswordError != null
                    )
                    importPasswordError?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingImportUri?.let { uri ->
                            context.contentResolver.openInputStream(uri)?.let { stream ->
                                viewModel.importAllData(stream, importPassword) { result ->
                                    result.onSuccess { summary ->
                                        showImportPasswordDialog = false
                                        pendingImportUri = null
                                        statusMessage = context.getString(R.string.settings_import_success, summary)
                                    }.onFailure { e ->
                                        if (e.message?.contains("password") == true || e.message?.contains("corrupted") == true) {
                                            importPasswordError = context.getString(R.string.settings_import_wrong_password)
                                        } else {
                                            showImportPasswordDialog = false
                                            pendingImportUri = null
                                            statusMessage = context.getString(R.string.settings_import_error, e.message ?: "Unknown")
                                        }
                                    }
                                }
                            }
                        }
                    },
                    enabled = importPassword.isNotEmpty()
                ) {
                    Text(stringResource(R.string.settings_import_decrypt))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportPasswordDialog = false
                    pendingImportUri = null
                }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // CSV profile picker dialog
    if (showCsvProfilePicker) {
        AlertDialog(
            onDismissRequest = { showCsvProfilePicker = false },
            title = { Text(stringResource(R.string.settings_select_profile)) },
            text = {
                Column {
                    uiState.profiles.forEach { profile ->
                        TextButton(
                            onClick = {
                                showCsvProfilePicker = false
                                csvProfileId = profile.id
                                val profileName = profile.displayName.replace(" ", "-").lowercase()
                                val date = java.time.LocalDate.now()
                                exportCsvLauncher.launch("pollenwitan-symptoms-$profileName-$date.csv")
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(profile.displayName, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCsvProfilePicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Medicines section
        MedicinesCard(
            medicines = uiState.medicines,
            onAddMedicine = viewModel::addMedicine,
            onUpdateMedicine = viewModel::updateMedicine,
            onDeleteMedicine = viewModel::deleteMedicine
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Language section
        LanguageCard()

        Spacer(modifier = Modifier.height(16.dp))

        // Location section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_location),
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
                    Text(stringResource(R.string.settings_manual), style = MaterialTheme.typography.bodyLarge)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = uiState.locationMode == LocationMode.Gps,
                        onClick = { viewModel.setLocationMode(LocationMode.Gps) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_gps), style = MaterialTheme.typography.bodyLarge)
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
                    text = stringResource(R.string.settings_notifications),
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
                        Text(stringResource(R.string.settings_grant_notification_permission))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                NotificationSettings(
                    prefs = uiState.notificationPrefs,
                    onMorningBriefingToggle = viewModel::setMorningBriefingEnabled,
                    onMorningHourChange = viewModel::setMorningBriefingHour,
                    onThresholdToggle = viewModel::setThresholdAlertsEnabled,
                    onCompoundRiskToggle = viewModel::setCompoundRiskAlertsEnabled,
                    onPreSeasonToggle = viewModel::setPreSeasonAlertsEnabled,
                    onSymptomReminderToggle = viewModel::setSymptomReminderEnabled,
                    onSymptomReminderHourChange = viewModel::setSymptomReminderHour,
                    onMissedDoseEscalationToggle = viewModel::setMissedDoseEscalationEnabled
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Widget section
        if (uiState.profiles.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.settings_widget_section),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.settings_widget_profile),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Default option
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = uiState.widgetProfileId.isEmpty(),
                            onClick = { viewModel.setWidgetProfile("") }
                        )
                        Text(
                            text = stringResource(R.string.settings_widget_profile_default),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }

                    // Per-profile options
                    uiState.profiles.forEach { profile ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.widgetProfileId == profile.id,
                                onClick = { viewModel.setWidgetProfile(profile.id) }
                            )
                            Text(
                                text = profile.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Data Management section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_data_management),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val date = java.time.LocalDate.now()
                        exportJsonLauncher.launch("pollenwitan-backup-$date.json")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_export_all))
                }
                Text(
                    text = stringResource(R.string.settings_export_all_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        importJsonLauncher.launch(arrayOf("application/json"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_import_data))
                }
                Text(
                    text = stringResource(R.string.settings_import_data_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        if (uiState.profiles.size == 1) {
                            csvProfileId = uiState.profiles.first().id
                            val profileName = uiState.profiles.first().displayName.replace(" ", "-").lowercase()
                            val date = java.time.LocalDate.now()
                            exportCsvLauncher.launch("pollenwitan-symptoms-$profileName-$date.csv")
                        } else if (uiState.profiles.size > 1) {
                            showCsvProfilePicker = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.profiles.isNotEmpty()
                ) {
                    Text(stringResource(R.string.settings_export_csv))
                }
                Text(
                    text = stringResource(R.string.settings_export_csv_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Status message
                statusMessage?.let { message ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (message.contains("failed", ignoreCase = true) || message.contains("nieudany", ignoreCase = true))
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // About section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.about_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.about_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.about_data_source),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.about_licence),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.about_source_code),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
    onCompoundRiskToggle: (Boolean) -> Unit,
    onPreSeasonToggle: (Boolean) -> Unit,
    onSymptomReminderToggle: (Boolean) -> Unit,
    onSymptomReminderHourChange: (Int) -> Unit,
    onMissedDoseEscalationToggle: (Boolean) -> Unit
) {
    // Morning briefing
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_morning_briefing), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.settings_morning_briefing_desc, String.format("%02d:00", prefs.morningBriefingHour)),
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
            Text(stringResource(R.string.settings_hour), style = MaterialTheme.typography.bodyMedium)
            listOf(6, 7, 8, 9).forEach { hour ->
                val isSelected = prefs.morningBriefingHour == hour
                if (isSelected) {
                    Button(
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Text("${hour}:00", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    OutlinedButton(
                        onClick = { onMorningHourChange(hour) },
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Text("${hour}:00", style = MaterialTheme.typography.bodySmall)
                    }
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
            Text(stringResource(R.string.settings_threshold_alerts), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.settings_threshold_alerts_desc),
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
            Text(stringResource(R.string.settings_compound_risk), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.settings_compound_risk_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = prefs.compoundRiskAlertsEnabled,
            onCheckedChange = onCompoundRiskToggle
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Pre-season medication alerts
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_pre_season_alerts), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.settings_pre_season_alerts_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = prefs.preSeasonAlertsEnabled,
            onCheckedChange = onPreSeasonToggle
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Symptom check-in reminder
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_symptom_reminder), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.settings_symptom_reminder_desc, String.format("%02d:00", prefs.symptomReminderHour)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = prefs.symptomReminderEnabled,
            onCheckedChange = onSymptomReminderToggle
        )
    }

    // Symptom reminder hour selector
    if (prefs.symptomReminderEnabled) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.settings_hour), style = MaterialTheme.typography.bodyMedium)
            listOf(18, 19, 20, 21).forEach { hour ->
                val isSelected = prefs.symptomReminderHour == hour
                if (isSelected) {
                    Button(
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Text("${hour}:00", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    OutlinedButton(
                        onClick = { onSymptomReminderHourChange(hour) },
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Text("${hour}:00", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    // Missed dose escalation
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_missed_dose_escalation), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.settings_missed_dose_escalation_desc, prefs.missedDoseWindowMinutes / 60),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = prefs.missedDoseEscalationEnabled,
            onCheckedChange = onMissedDoseEscalationToggle
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
        label = { Text(stringResource(R.string.settings_display_name)) },
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
            label = { Text(stringResource(R.string.settings_latitude)) },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        OutlinedTextField(
            value = lon,
            onValueChange = { lon = it },
            label = { Text(stringResource(R.string.settings_longitude)) },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    Button(
        onClick = { onSave(lat, lon, name) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.settings_save_location))
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
            Text(stringResource(R.string.settings_locating))
        } else {
            Text(stringResource(R.string.settings_locate_me))
        }
    }

    when (gpsStatus) {
        is GpsStatus.Success -> {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_location_found, gpsStatus.displayName),
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
            text = stringResource(R.string.settings_location_permission_denied),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedButton(onClick = onOpenSettings) {
            Text(stringResource(R.string.settings_open_app_settings))
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.settings_gps_auto_refresh),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun MedicinesCard(
    medicines: List<Medicine>,
    onAddMedicine: (String, MedicineType) -> Unit,
    onUpdateMedicine: (Medicine) -> Unit,
    onDeleteMedicine: (String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingMedicine by remember { mutableStateOf<Medicine?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.settings_medicines),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (medicines.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_no_medicines),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                medicines.forEachIndexed { index, medicine ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = medicine.name,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = medicine.type.localizedName(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { editingMedicine = medicine }) {
                            Text("\u270E", style = MaterialTheme.typography.bodyLarge)
                        }
                        IconButton(onClick = { onDeleteMedicine(medicine.id) }) {
                            Text("\u2715", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    if (index < medicines.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_add_medicine))
            }
        }
    }

    if (showAddDialog) {
        MedicineDialog(
            title = stringResource(R.string.settings_add_medicine),
            initialName = "",
            initialType = MedicineType.Tablet,
            onConfirm = { name, type ->
                onAddMedicine(name, type)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    editingMedicine?.let { medicine ->
        MedicineDialog(
            title = stringResource(R.string.settings_edit_medicine),
            initialName = medicine.name,
            initialType = medicine.type,
            onConfirm = { name, type ->
                onUpdateMedicine(medicine.copy(name = name.trim(), type = type))
                editingMedicine = null
            },
            onDismiss = { editingMedicine = null }
        )
    }
}

@Composable
private fun MedicineDialog(
    title: String,
    initialName: String,
    initialType: MedicineType,
    onConfirm: (String, MedicineType) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedType by remember { mutableStateOf(initialType) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.common_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.settings_type), style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                MedicineType.entries.forEach { type ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = selectedType == type,
                            onClick = { selectedType = type }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(type.localizedName(), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, selectedType) },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun LanguageCard() {
    val currentLocale = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    val isPolish = currentLocale.startsWith("pl")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.language_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!isPolish) {
                    Button(onClick = {}) {
                        Text(stringResource(R.string.language_english))
                    }
                } else {
                    OutlinedButton(onClick = {
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
                    }) {
                        Text(stringResource(R.string.language_english))
                    }
                }
                if (isPolish) {
                    Button(onClick = {}) {
                        Text(stringResource(R.string.language_polish))
                    }
                } else {
                    OutlinedButton(onClick = {
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("pl"))
                    }) {
                        Text(stringResource(R.string.language_polish))
                    }
                }
            }
        }
    }
}
