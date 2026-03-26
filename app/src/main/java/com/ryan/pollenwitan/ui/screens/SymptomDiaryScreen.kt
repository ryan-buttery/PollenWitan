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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.ryan.pollenwitan.domain.model.SymptomDiaryEntry
import com.ryan.pollenwitan.ui.components.ProfileSwitcher
import com.ryan.pollenwitan.ui.theme.SeverityColors
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun SymptomDiaryScreen(
    viewModel: SymptomDiaryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.symptom_diary_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Profile switcher
        ProfileSwitcher(
            profiles = uiState.profiles,
            selectedProfileId = uiState.selectedProfile?.id,
            onSelectProfile = viewModel::selectProfile
        )
        if (uiState.profiles.size > 1) {
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Date range navigation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.navigateRange(forward = false) }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null
                )
            }
            Text(
                text = "${uiState.rangeStart.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))} – ${uiState.rangeEnd.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))}",
                style = MaterialTheme.typography.bodyMedium
            )
            IconButton(onClick = { viewModel.navigateRange(forward = true) }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.entries.isEmpty()) {
            Text(
                text = stringResource(R.string.symptom_no_entries),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        } else {
            uiState.entries.sortedByDescending { it.date }.forEach { entry ->
                DiaryEntryCard(entry)
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun DiaryEntryCard(entry: SymptomDiaryEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = entry.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Symptom ratings
            entry.ratings.forEach { rating ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(ratingSeverityColor(rating.severity))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = rating.symptomName,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${rating.severity}/5",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = ratingSeverityColor(rating.severity)
                    )
                }
            }

            // Environmental snapshot
            if (entry.peakAqi > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "AQI: ${entry.peakAqi}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "PM2.5: ${String.format("%.1f", entry.peakPm25)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "PM10: ${String.format("%.1f", entry.peakPm10)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun ratingSeverityColor(level: Int) = when (level) {
    0 -> SeverityColors.None
    1 -> SeverityColors.Low
    2 -> SeverityColors.Low
    3 -> SeverityColors.Moderate
    4 -> SeverityColors.High
    5 -> SeverityColors.VeryHigh
    else -> SeverityColors.None
}
