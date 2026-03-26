package com.ryan.pollenwitan.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ryan.pollenwitan.R
import com.ryan.pollenwitan.domain.calibration.CalibrationResult
import com.ryan.pollenwitan.domain.calibration.Confidence
import com.ryan.pollenwitan.domain.calibration.DataStatus
import com.ryan.pollenwitan.domain.calibration.Direction
import com.ryan.pollenwitan.domain.calibration.ThresholdSuggestion
import com.ryan.pollenwitan.domain.model.PollenType
import com.ryan.pollenwitan.ui.theme.localizedName

@Composable
fun ThresholdCalibrationScreen(
    profileId: String,
    viewModel: ThresholdCalibrationViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(profileId) {
        viewModel.loadProfile(profileId)
    }

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.calibration_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = uiState.profileName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Text(
                text = stringResource(R.string.calibration_description),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (uiState.results.isEmpty()) {
            Text(
                text = stringResource(R.string.calibration_no_allergens),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val allMatched = uiState.results.all { result ->
                result.dataStatus == DataStatus.SUFFICIENT && result.suggestions.isEmpty()
            } && uiState.results.any { it.dataStatus == DataStatus.SUFFICIENT }

            if (allMatched && !uiState.hasAnySuggestions) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Text(
                        text = stringResource(R.string.calibration_no_suggestions),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            uiState.results.forEach { result ->
                AllergenCalibrationSection(
                    result = result,
                    dismissedSuggestions = uiState.dismissedSuggestions,
                    acceptedSuggestions = uiState.acceptedSuggestions,
                    onAccept = { level -> viewModel.acceptSuggestion(result.pollenType, level) },
                    onDismiss = { level -> viewModel.dismissSuggestion(result.pollenType, level) }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun AllergenCalibrationSection(
    result: CalibrationResult,
    dismissedSuggestions: Set<Pair<PollenType, String>>,
    acceptedSuggestions: Set<Pair<PollenType, String>>,
    onAccept: (String) -> Unit,
    onDismiss: (String) -> Unit
) {
    Text(
        text = result.pollenType.localizedName(),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(modifier = Modifier.height(8.dp))

    when (result.dataStatus) {
        DataStatus.NO_DATA -> {
            InsufficientDataCard(
                stringResource(R.string.calibration_no_data, result.pollenType.localizedName())
            )
        }
        DataStatus.INSUFFICIENT_ENTRIES -> {
            InsufficientDataCard(
                stringResource(R.string.calibration_insufficient_entries, result.pollenType.localizedName())
            )
        }
        DataStatus.INSUFFICIENT_SYMPTOM_DAYS -> {
            InsufficientDataCard(
                stringResource(R.string.calibration_insufficient_symptom_days, result.pollenType.localizedName())
            )
        }
        DataStatus.INSUFFICIENT_CLEAN_DAYS -> {
            InsufficientDataCard(
                stringResource(R.string.calibration_insufficient_clean_days)
            )
        }
        DataStatus.SUFFICIENT -> {
            if (result.suggestions.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = stringResource(R.string.calibration_thresholds_match, result.pollenType.localizedName()),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                result.suggestions.forEach { suggestion ->
                    val key = result.pollenType to suggestion.level
                    when {
                        key in acceptedSuggestions -> {
                            AcceptedCard(suggestion)
                        }
                        key in dismissedSuggestions -> { /* hidden */ }
                        else -> {
                            SuggestionCard(
                                suggestion = suggestion,
                                onAccept = { onAccept(suggestion.level) },
                                onDismiss = { onDismiss(suggestion.level) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun InsufficientDataCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun SuggestionCard(
    suggestion: ThresholdSuggestion,
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    val levelLabel = levelDisplayName(suggestion.level)
    val pollenName = suggestion.pollenType.localizedName()

    val explanationText = if (suggestion.direction == Direction.LOWER) {
        stringResource(
            R.string.calibration_suggestion_lower,
            levelLabel.lowercase(),
            pollenName,
            suggestion.suggestedValue.toInt(),
            suggestion.currentValue.toInt()
        )
    } else {
        stringResource(
            R.string.calibration_suggestion_higher,
            pollenName,
            levelLabel.lowercase(),
            suggestion.currentValue.toInt(),
            suggestion.suggestedValue.toInt()
        )
    }

    val confidenceText = stringResource(R.string.calibration_based_on_days, suggestion.dataPointCount)
    val confidenceLabel = when (suggestion.confidence) {
        Confidence.LOW -> stringResource(R.string.calibration_confidence_low)
        Confidence.MODERATE -> stringResource(R.string.calibration_confidence_moderate)
        Confidence.HIGH -> stringResource(R.string.calibration_confidence_high)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (suggestion.direction == Direction.LOWER)
                MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "$levelLabel ${stringResource(R.string.calibration_threshold_label)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = explanationText,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$confidenceText ($confidenceLabel)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (suggestion.medicatedDayRatio > 0.5) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.calibration_medication_caveat),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.calibration_apply))
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.calibration_dismiss))
                }
            }
        }
    }
}

@Composable
private fun AcceptedCard(suggestion: ThresholdSuggestion) {
    val levelLabel = levelDisplayName(suggestion.level)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = stringResource(
                    R.string.calibration_applied_message,
                    levelLabel.lowercase(),
                    suggestion.suggestedValue.toInt()
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun levelDisplayName(level: String): String = when (level) {
    "moderate" -> stringResource(R.string.severity_moderate)
    "high" -> stringResource(R.string.severity_high)
    "veryHigh" -> stringResource(R.string.severity_very_high)
    else -> level
}
