package com.ryan.pollenwitan.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.ryan.pollenwitan.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ryan.pollenwitan.domain.model.PollenType
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.ryan.pollenwitan.ui.theme.ForestTheme
import com.ryan.pollenwitan.ui.theme.localizedName

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = ForestTheme.current

    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete && uiState.currentStep == OnboardingStep.Done) {
            // Don't auto-navigate — let user tap "Go to Dashboard"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Step indicator
        StepIndicator(
            currentStep = uiState.currentStep,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        )

        // Content area
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            when (uiState.currentStep) {
                OnboardingStep.Welcome -> WelcomeStep()
                OnboardingStep.Location -> LocationStep(
                    uiState = uiState,
                    viewModel = viewModel
                )
                OnboardingStep.Profile -> ProfileStep(
                    uiState = uiState,
                    viewModel = viewModel
                )
                OnboardingStep.Done -> DoneStep(
                    uiState = uiState,
                    onGoToDashboard = onFinished
                )
            }
        }

        // Bottom navigation (not shown on Done step)
        if (uiState.currentStep != OnboardingStep.Done) {
            Spacer(modifier = Modifier.height(16.dp))
            BottomNavButtons(
                currentStep = uiState.currentStep,
                isSaving = uiState.isSaving,
                onNext = viewModel::nextStep,
                onBack = viewModel::previousStep
            )
        }
    }
}

@Composable
private fun StepIndicator(
    currentStep: OnboardingStep,
    modifier: Modifier = Modifier
) {
    val colors = ForestTheme.current
    val steps = OnboardingStep.entries

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, step ->
            val color = when {
                step == currentStep -> colors.Accent
                step.ordinal < currentStep.ordinal -> colors.TextDim
                else -> colors.TextDim.copy(alpha = 0.3f)
            }
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            if (index < steps.lastIndex) {
                Spacer(modifier = Modifier.width(12.dp))
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    val currentLocale = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    val isPolish = currentLocale.startsWith("pl")

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Language selector
        Text(
            text = stringResource(R.string.language_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
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

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LocationStep(
    uiState: OnboardingUiState,
    viewModel: OnboardingViewModel
) {
    var permissionDenied by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        if (granted) {
            viewModel.requestGpsFix()
            permissionDenied = false
        } else {
            permissionDenied = true
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.onboarding_location_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_location_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))

        // Default option
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            RadioButton(
                selected = uiState.locationChoice == LocationChoice.Default,
                onClick = { viewModel.setLocationChoice(LocationChoice.Default) }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(stringResource(R.string.onboarding_location_default), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.onboarding_location_default_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // GPS option
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            RadioButton(
                selected = uiState.locationChoice == LocationChoice.Gps,
                onClick = { viewModel.setLocationChoice(LocationChoice.Gps) }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.onboarding_location_gps), style = MaterialTheme.typography.bodyLarge)
        }

        if (uiState.locationChoice == LocationChoice.Gps) {
            Column(modifier = Modifier.padding(start = 48.dp)) {
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
                    Text(stringResource(R.string.settings_locate_me))
                }
                when (uiState.gpsStatus) {
                    is GpsStatus.Requesting -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_getting_location), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    is GpsStatus.Success -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.settings_located, uiState.gpsStatus.displayName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    is GpsStatus.Error -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.gpsStatus.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    is GpsStatus.Idle -> {}
                }
                if (permissionDenied) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.onboarding_location_permission_denied),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Manual option
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            RadioButton(
                selected = uiState.locationChoice == LocationChoice.Manual,
                onClick = { viewModel.setLocationChoice(LocationChoice.Manual) }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.onboarding_location_manual), style = MaterialTheme.typography.bodyLarge)
        }

        if (uiState.locationChoice == LocationChoice.Manual) {
            Column(modifier = Modifier.padding(start = 48.dp)) {
                OutlinedTextField(
                    value = uiState.manualDisplayName,
                    onValueChange = viewModel::setManualDisplayName,
                    label = { Text(stringResource(R.string.onboarding_location_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.manualLatitude,
                        onValueChange = viewModel::setManualLatitude,
                        label = { Text(stringResource(R.string.settings_latitude)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.manualLongitude,
                        onValueChange = viewModel::setManualLongitude,
                        label = { Text(stringResource(R.string.settings_longitude)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileStep(
    uiState: OnboardingUiState,
    viewModel: OnboardingViewModel
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.onboarding_profile_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_profile_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = uiState.profileName,
            onValueChange = viewModel::setProfileName,
            label = { Text(stringResource(R.string.common_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.onboarding_has_asthma), style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = uiState.hasAsthma,
                onCheckedChange = viewModel::setHasAsthma
            )
        }
        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.onboarding_tracked_allergens),
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
                    selected = type in uiState.selectedAllergens,
                    onClick = { viewModel.toggleAllergen(type) },
                    label = { Text(type.localizedName()) }
                )
            }
        }

        uiState.validationError?.let { error ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun DoneStep(
    uiState: OnboardingUiState,
    onGoToDashboard: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = stringResource(R.string.onboarding_done_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_done_profile_created, uiState.profileName),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_done_tracking, uiState.selectedAllergens.map { it.localizedName() }.joinToString(", ")),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (uiState.hasAsthma) {
            Text(
                text = stringResource(R.string.onboarding_done_asthma_enabled),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.onboarding_done_medicine_tip),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onGoToDashboard) {
            Text(stringResource(R.string.onboarding_go_to_dashboard))
        }
    }
}

@Composable
private fun BottomNavButtons(
    currentStep: OnboardingStep,
    isSaving: Boolean,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (currentStep != OnboardingStep.Welcome) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.common_back))
            }
        } else {
            Spacer(modifier = Modifier.width(1.dp))
        }

        Button(
            onClick = onNext,
            enabled = !isSaving
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.onboarding_saving))
            } else {
                Text(
                    when (currentStep) {
                        OnboardingStep.Welcome -> stringResource(R.string.onboarding_get_started)
                        OnboardingStep.Location -> stringResource(R.string.common_next)
                        OnboardingStep.Profile -> stringResource(R.string.onboarding_finish)
                        OnboardingStep.Done -> ""
                    }
                )
            }
        }
    }
}
