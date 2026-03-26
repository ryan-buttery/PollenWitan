package com.ryan.pollenwitan.ui.components.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ryan.pollenwitan.R
import com.ryan.pollenwitan.ui.theme.SeverityColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

data class DayDetail(
    val date: LocalDate,
    val symptomRatings: Map<String, Int>,
    val pollenLevels: Map<String, Double>,
    val peakAqi: Int,
    val peakPm25: Double,
    val peakPm10: Double,
    val dosesConfirmed: Int,
    val dosesExpected: Int
)

@Composable
fun DayDetailCard(
    detail: DayDetail,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header with date and close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        R.string.trends_day_detail_title,
                        detail.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }

            // Symptoms
            if (detail.symptomRatings.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.trends_symptoms_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                detail.symptomRatings.forEach { (name, severity) ->
                    Row(
                        modifier = Modifier.padding(vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(ratingSeverityColor(severity))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "$severity/5",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = ratingSeverityColor(severity)
                        )
                    }
                }
            }

            // Pollen
            if (detail.pollenLevels.any { it.value > 0 }) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.trends_pollen_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                detail.pollenLevels.filter { it.value > 0 }.forEach { (name, value) ->
                    Row(modifier = Modifier.padding(vertical = 1.dp)) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = String.format("%.0f", value),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // AQI & PM
            if (detail.peakAqi > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "AQI: ${detail.peakAqi}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "PM2.5: ${String.format("%.1f", detail.peakPm25)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "PM10: ${String.format("%.1f", detail.peakPm10)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Medication adherence
            if (detail.dosesExpected > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                val percent = if (detail.dosesExpected > 0) {
                    (detail.dosesConfirmed * 100) / detail.dosesExpected
                } else 0
                Text(
                    text = stringResource(R.string.trends_medication_title) + ": " +
                        stringResource(R.string.trends_adherence_percent, percent),
                    style = MaterialTheme.typography.bodySmall
                )
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
