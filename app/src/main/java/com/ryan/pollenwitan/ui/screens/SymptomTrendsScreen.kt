package com.ryan.pollenwitan.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ryan.pollenwitan.R
import com.ryan.pollenwitan.ui.components.ProfileSwitcher
import com.ryan.pollenwitan.ui.components.charts.BarChartPoint
import com.ryan.pollenwitan.ui.components.charts.DayDetail
import com.ryan.pollenwitan.ui.components.charts.DayDetailCard
import com.ryan.pollenwitan.ui.components.charts.LineChartPoint
import com.ryan.pollenwitan.ui.components.charts.TimelineBarChart
import com.ryan.pollenwitan.ui.components.charts.TimelineLineChart
import com.ryan.pollenwitan.ui.theme.AqiColors
import com.ryan.pollenwitan.ui.theme.ForestTheme

private val SymptomSeriesColors = listOf(
    Color(0xFF2196F3), // blue
    Color(0xFFFF9800), // orange
    Color(0xFF009688), // teal
    Color(0xFFE91E63), // pink
    Color(0xFF795548), // brown
    Color(0xFF607D8B)  // blue-grey
)

private val PollenTypeColors = mapOf(
    "Birch" to Color(0xFFFFC107),   // amber
    "Alder" to Color(0xFF795548),   // brown
    "Grass" to Color(0xFF4CAF50),   // green
    "Mugwort" to Color(0xFF9C27B0), // purple
    "Ragweed" to Color(0xFFFF9800), // orange
    "Olive" to Color(0xFF009688)    // teal
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SymptomTrendsScreen(
    viewModel: SymptomTrendsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = ForestTheme.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.trends_title),
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
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Date range selector
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TrendRange.entries.forEach { range ->
                FilterChip(
                    selected = uiState.range == range,
                    onClick = { viewModel.selectRange(range) },
                    label = { Text(stringResource(range.labelRes)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val hasData = uiState.snapshots.any { it.symptomRatings.isNotEmpty() }

        if (!hasData) {
            Text(
                text = stringResource(R.string.trends_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        } else {
            val selectedDate = uiState.selectedDay?.date

            // Symptom severity chart
            ChartSection(title = stringResource(R.string.trends_symptoms_title)) {
                val symptomColors = uiState.symptomNames.mapIndexed { index, name ->
                    name to SymptomSeriesColors[index % SymptomSeriesColors.size]
                }.toMap()

                val symptomData = uiState.snapshots
                    .filter { it.symptomRatings.isNotEmpty() }
                    .map { snapshot ->
                        LineChartPoint(
                            date = snapshot.date,
                            values = snapshot.symptomRatings.mapValues { it.value.toFloat() }
                        )
                    }

                TimelineLineChart(
                    data = symptomData,
                    seriesColors = symptomColors,
                    yRange = 0f..5f,
                    yTickCount = 5,
                    selectedDate = selectedDate,
                    onDateTapped = { viewModel.selectDay(it) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Peak pollen chart
            val pollenSnapshots = uiState.snapshots.filter { it.pollenLevels.isNotEmpty() }
            if (pollenSnapshots.isNotEmpty()) {
                ChartSection(title = stringResource(R.string.trends_pollen_title)) {
                    val pollenColors = uiState.allergenNames.associateWith { name ->
                        PollenTypeColors[name] ?: Color(0xFF607D8B)
                    }

                    val maxPollen = pollenSnapshots
                        .flatMap { it.pollenLevels.values }
                        .maxOrNull()?.toFloat()?.coerceAtLeast(10f) ?: 10f

                    val pollenData = pollenSnapshots.map { snapshot ->
                        LineChartPoint(
                            date = snapshot.date,
                            values = snapshot.pollenLevels.mapValues { it.value.toFloat() }
                        )
                    }

                    TimelineLineChart(
                        data = pollenData,
                        seriesColors = pollenColors,
                        yRange = 0f..maxPollen,
                        yTickCount = 5,
                        selectedDate = selectedDate,
                        onDateTapped = { viewModel.selectDay(it) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // AQI chart
            val aqiSnapshots = uiState.snapshots.filter { it.peakAqi > 0 }
            if (aqiSnapshots.isNotEmpty()) {
                ChartSection(title = stringResource(R.string.trends_aqi_title)) {
                    val maxAqi = aqiSnapshots
                        .maxOf { it.peakAqi }.toFloat().coerceAtLeast(50f)

                    val aqiData = aqiSnapshots.map { snapshot ->
                        LineChartPoint(
                            date = snapshot.date,
                            values = buildMap {
                                put("AQI", snapshot.peakAqi.toFloat())
                                if (snapshot.peakPm25 > 0) {
                                    put("PM2.5", snapshot.peakPm25.toFloat())
                                }
                            }
                        )
                    }

                    val maxPm25 = aqiSnapshots
                        .maxOfOrNull { it.peakPm25 }?.toFloat() ?: 0f
                    val yMax = maxOf(maxAqi, maxPm25).coerceAtLeast(50f)

                    TimelineLineChart(
                        data = aqiData,
                        seriesColors = mapOf(
                            "AQI" to AqiColors.Moderate,
                            "PM2.5" to colors.TextDim
                        ),
                        yRange = 0f..yMax,
                        yTickCount = 5,
                        selectedDate = selectedDate,
                        onDateTapped = { viewModel.selectDay(it) },
                        dashedSeries = setOf("PM2.5")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Medication adherence chart
            val hasAdherence = uiState.snapshots.any { it.dosesExpected > 0 }
            if (hasAdherence) {
                ChartSection(title = stringResource(R.string.trends_medication_title)) {
                    val adherenceData = uiState.snapshots
                        .filter { it.dosesExpected > 0 }
                        .map { snapshot ->
                            BarChartPoint(
                                date = snapshot.date,
                                value = snapshot.dosesConfirmed.toFloat() / snapshot.dosesExpected
                            )
                        }

                    TimelineBarChart(
                        data = adherenceData,
                        selectedDate = selectedDate,
                        onDateTapped = { viewModel.selectDay(it) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Day detail card
            uiState.selectedDay?.let { day ->
                DayDetailCard(
                    detail = DayDetail(
                        date = day.date,
                        symptomRatings = day.symptomRatings,
                        pollenLevels = day.pollenLevels,
                        peakAqi = day.peakAqi,
                        peakPm25 = day.peakPm25,
                        peakPm10 = day.peakPm10,
                        dosesConfirmed = day.dosesConfirmed,
                        dosesExpected = day.dosesExpected
                    ),
                    onDismiss = { viewModel.selectDay(null) }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Hint
            if (uiState.selectedDay == null) {
                Text(
                    text = stringResource(R.string.trends_tap_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
private fun ChartSection(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}
