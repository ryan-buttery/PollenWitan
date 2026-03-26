package com.ryan.pollenwitan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ryan.pollenwitan.R
import com.ryan.pollenwitan.domain.model.DefaultSymptom
import com.ryan.pollenwitan.domain.model.TrackedSymptom
import com.ryan.pollenwitan.ui.theme.SeverityColors
import com.ryan.pollenwitan.ui.theme.localizedName
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun SymptomCheckInScreen(
    dateString: String? = null,
    viewModel: SymptomCheckInViewModel = viewModel(),
    onSaved: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(dateString) {
        viewModel.setTargetDate(dateString)
    }

    val symptomPhrases = LocalContext.current.resources.getStringArray(R.array.reinforcement_symptom)
    val symptomPhrase = remember { symptomPhrases.random() }

    LaunchedEffect(uiState.savedSuccessfully) {
        if (uiState.savedSuccessfully) {
            kotlinx.coroutines.delay(2000)
            onSaved()
        }
    }

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (uiState.savedSuccessfully) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = symptomPhrase,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp)
            )
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
            text = if (uiState.isBackfill) stringResource(R.string.symptom_diary_backfill_title)
                   else stringResource(R.string.symptom_checkin_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = uiState.targetDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        uiState.trackedSymptoms.forEach { symptom ->
            val currentRating = uiState.ratings[symptom.id] ?: 0
            SymptomRatingRow(
                symptom = symptom,
                rating = currentRating,
                onRatingChange = { viewModel.setRating(symptom.id, it) }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = viewModel::save,
            enabled = !uiState.isSaving,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.common_save))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SymptomRatingRow(
    symptom: TrackedSymptom,
    rating: Int,
    onRatingChange: (Int) -> Unit
) {
    val displayName = if (symptom.isDefault) {
        DefaultSymptom.entries.find { it.name == symptom.id }?.localizedName() ?: symptom.displayName
    } else {
        symptom.displayName
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                (0..5).forEach { level ->
                    val isSelected = rating == level
                    val color = severityColor(level)
                    val label = severityLabel(level)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onRatingChange(level) }
                            .background(
                                if (isSelected) color.copy(alpha = 0.2f)
                                else Color.Transparent
                            )
                            .padding(vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) color else color.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = level.toString(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else color
                            )
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun severityLabel(level: Int): String = when (level) {
    0 -> stringResource(R.string.symptom_severity_0)
    1 -> stringResource(R.string.symptom_severity_1)
    2 -> stringResource(R.string.symptom_severity_2)
    3 -> stringResource(R.string.symptom_severity_3)
    4 -> stringResource(R.string.symptom_severity_4)
    5 -> stringResource(R.string.symptom_severity_5)
    else -> ""
}

private fun severityColor(level: Int): Color = when (level) {
    0 -> SeverityColors.None
    1 -> SeverityColors.Low
    2 -> SeverityColors.Low
    3 -> SeverityColors.Moderate
    4 -> SeverityColors.High
    5 -> SeverityColors.VeryHigh
    else -> SeverityColors.None
}
