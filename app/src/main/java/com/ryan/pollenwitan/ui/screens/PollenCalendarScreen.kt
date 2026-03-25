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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.ryan.pollenwitan.domain.model.PollenSeasonCalendar
import com.ryan.pollenwitan.domain.model.PollenType
import com.ryan.pollenwitan.ui.theme.SeverityColors
import com.ryan.pollenwitan.ui.theme.localizedName
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun PollenCalendarScreen() {
    val today = LocalDate.now()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.calendar_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.calendar_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Sort by season start date
        val sortedEntries = PollenSeasonCalendar.seasonStart.entries.sortedBy { it.value }

        sortedEntries.forEach { (pollenType, monthDay) ->
            val seasonStartDate = monthDay.atYear(today.year)
            val preSeasonDate = seasonStartDate.minusWeeks(4)
            val isActive = !today.isBefore(seasonStartDate)
            val isPreSeason = today in preSeasonDate..seasonStartDate.minusDays(1)
            val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status indicator
                    val dotColor = when {
                        isActive -> SeverityColors.High
                        isPreSeason -> SeverityColors.Moderate
                        else -> SeverityColors.None
                    }
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = pollenType.localizedName(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(
                                R.string.calendar_season_start,
                                seasonStartDate.format(dateFormatter)
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(
                                R.string.calendar_pre_season_from,
                                preSeasonDate.format(dateFormatter)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (isPreSeason) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.calendar_pre_season_now),
                                style = MaterialTheme.typography.labelMedium,
                                color = SeverityColors.Moderate,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else if (isActive) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.calendar_in_season),
                                style = MaterialTheme.typography.labelMedium,
                                color = SeverityColors.High,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Legend
        Text(
            text = stringResource(R.string.calendar_legend_title),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        LegendRow(color = SeverityColors.High, label = stringResource(R.string.calendar_legend_in_season))
        LegendRow(color = SeverityColors.Moderate, label = stringResource(R.string.calendar_legend_pre_season))
        LegendRow(color = SeverityColors.None, label = stringResource(R.string.calendar_legend_off_season))

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.calendar_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun LegendRow(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
