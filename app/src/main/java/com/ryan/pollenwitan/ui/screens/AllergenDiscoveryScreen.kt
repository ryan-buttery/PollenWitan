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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ryan.pollenwitan.R
import com.ryan.pollenwitan.domain.calibration.Confidence
import com.ryan.pollenwitan.domain.calibration.CorrelationStrength
import com.ryan.pollenwitan.domain.calibration.DataStatus
import com.ryan.pollenwitan.domain.calibration.DiscoveryResult
import com.ryan.pollenwitan.domain.model.PollenType
import com.ryan.pollenwitan.ui.theme.SeverityColors
import com.ryan.pollenwitan.ui.theme.localizedName

@Composable
fun AllergenDiscoveryScreen(
    profileId: String,
    viewModel: AllergenDiscoveryViewModel = viewModel()
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
            text = stringResource(R.string.discovery_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = uiState.profileName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Medical disclaimer
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.discovery_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val analysis = uiState.analysis
        if (analysis == null) return@Column

        // Data progress
        if (analysis.overallDataStatus != DataStatus.SUFFICIENT) {
            DataProgressCard(
                totalEntries = analysis.totalDiaryEntries,
                requiredEntries = analysis.requiredEntries
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Description
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Text(
                text = stringResource(R.string.discovery_description),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (analysis.overallDataStatus == DataStatus.SUFFICIENT) {
            Text(
                text = stringResource(R.string.discovery_results_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))

            analysis.results.forEach { result ->
                val isAdded = result.pollenType in uiState.addedAllergens
                val isDismissed = result.pollenType in uiState.dismissedAllergens

                if (!isDismissed) {
                    DiscoveryResultCard(
                        result = result,
                        isAdded = isAdded,
                        onAdd = { viewModel.addAllergen(result.pollenType) },
                        onDismiss = { viewModel.dismissAllergen(result.pollenType) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            if (uiState.addedAllergens.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.exitDiscoveryMode() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.discovery_exit_mode))
                }
            }
        } else {
            Text(
                text = stringResource(R.string.discovery_insufficient_data),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun DataProgressCard(totalEntries: Int, requiredEntries: Int) {
    val progress = (totalEntries.toFloat() / requiredEntries).coerceIn(0f, 1f)
    val remaining = (requiredEntries - totalEntries).coerceAtLeast(0)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.discovery_data_progress, totalEntries, requiredEntries),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
            if (remaining > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.discovery_entries_remaining, remaining),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DiscoveryResultCard(
    result: DiscoveryResult,
    isAdded: Boolean,
    onAdd: () -> Unit,
    onDismiss: () -> Unit
) {
    val containerColor = when (result.correlationStrength) {
        CorrelationStrength.STRONG -> MaterialTheme.colorScheme.primaryContainer
        CorrelationStrength.MODERATE -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = result.pollenType.localizedName(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                CorrelationBadge(result.correlationStrength)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Correlation bar
            if (result.dataStatus == DataStatus.SUFFICIENT) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.discovery_correlation_score),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(80.dp)
                    )
                    LinearProgressIndicator(
                        progress = { result.correlationScore.toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = correlationColor(result.correlationStrength),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "%.0f%%".format(result.correlationScore * 100),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = stringResource(
                        R.string.discovery_symptom_days,
                        result.symptomDaysWithHighPollen,
                        result.totalValidDays
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val confidenceLabel = when (result.confidence) {
                    Confidence.LOW -> stringResource(R.string.calibration_confidence_low)
                    Confidence.MODERATE -> stringResource(R.string.calibration_confidence_moderate)
                    Confidence.HIGH -> stringResource(R.string.calibration_confidence_high)
                }
                Text(
                    text = stringResource(R.string.discovery_confidence, confidenceLabel),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Action buttons for strong/moderate results
            if (!isAdded && (result.correlationStrength == CorrelationStrength.STRONG ||
                        result.correlationStrength == CorrelationStrength.MODERATE)
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onAdd,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.discovery_add_allergen))
                    }
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.calibration_dismiss))
                    }
                }
            }

            if (isAdded) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.discovery_allergen_added),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun CorrelationBadge(strength: CorrelationStrength) {
    val (text, color) = when (strength) {
        CorrelationStrength.STRONG -> stringResource(R.string.discovery_strong) to SeverityColors.High
        CorrelationStrength.MODERATE -> stringResource(R.string.discovery_moderate) to SeverityColors.Moderate
        CorrelationStrength.WEAK -> stringResource(R.string.discovery_weak) to SeverityColors.Low
        CorrelationStrength.NONE -> stringResource(R.string.discovery_none) to SeverityColors.None
        CorrelationStrength.INSUFFICIENT_DATA -> stringResource(R.string.common_loading) to SeverityColors.None
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun correlationColor(strength: CorrelationStrength) = when (strength) {
    CorrelationStrength.STRONG -> SeverityColors.High
    CorrelationStrength.MODERATE -> SeverityColors.Moderate
    CorrelationStrength.WEAK -> SeverityColors.Low
    else -> SeverityColors.None
}
