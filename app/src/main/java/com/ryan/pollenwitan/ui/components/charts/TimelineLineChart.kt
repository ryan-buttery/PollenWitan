package com.ryan.pollenwitan.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryan.pollenwitan.ui.theme.ForestTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class LineChartPoint(
    val date: LocalDate,
    val values: Map<String, Float>
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimelineLineChart(
    data: List<LineChartPoint>,
    seriesColors: Map<String, Color>,
    yRange: ClosedFloatingPointRange<Float>,
    yTickCount: Int = 6,
    yLabel: String = "",
    selectedDate: LocalDate? = null,
    onDateTapped: (LocalDate) -> Unit = {},
    modifier: Modifier = Modifier,
    dashedSeries: Set<String> = emptySet()
) {
    val colors = ForestTheme.current
    val textMeasurer = rememberTextMeasurer()
    val textColor = colors.TextDim
    val gridColor = colors.TextDim.copy(alpha = 0.15f)
    val selectedColor = colors.Text.copy(alpha = 0.5f)

    if (data.isEmpty()) return

    val sortedData = data.sortedBy { it.date }
    val dates = sortedData.map { it.date }

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .pointerInput(dates) {
                    detectTapGestures { offset ->
                        val leftMargin = 44.dp.toPx()
                        val rightMargin = 8.dp.toPx()
                        val chartWidth = size.width - leftMargin - rightMargin
                        if (dates.size < 2 || offset.x < leftMargin) return@detectTapGestures
                        val x = offset.x - leftMargin
                        val index = ((x / chartWidth) * (dates.size - 1))
                            .toInt()
                            .coerceIn(0, dates.lastIndex)
                        onDateTapped(dates[index])
                    }
                }
        ) {
            val leftMargin = 44.dp.toPx()
            val rightMargin = 8.dp.toPx()
            val topMargin = 8.dp.toPx()
            val bottomMargin = 24.dp.toPx()
            val chartWidth = size.width - leftMargin - rightMargin
            val chartHeight = size.height - topMargin - bottomMargin

            val yMin = yRange.start
            val yMax = yRange.endInclusive

            // Grid lines + Y-axis labels
            for (i in 0..yTickCount) {
                val yVal = yMin + (yMax - yMin) * i / yTickCount
                val y = topMargin + chartHeight * (1f - (yVal - yMin) / (yMax - yMin))
                drawLine(gridColor, Offset(leftMargin, y), Offset(size.width - rightMargin, y))
                drawText(
                    textMeasurer = textMeasurer,
                    text = if (yMax <= 5f) "${yVal.toInt()}" else String.format("%.0f", yVal),
                    topLeft = Offset(0f, y - 6.dp.toPx()),
                    style = TextStyle(color = textColor, fontSize = 10.sp)
                )
            }

            // X-axis date labels
            val tickEvery = when {
                dates.size <= 10 -> 1
                dates.size <= 35 -> 5
                else -> 15
            }
            dates.forEachIndexed { index, date ->
                if (index % tickEvery == 0 || index == dates.lastIndex) {
                    val x = leftMargin + if (dates.size > 1) chartWidth * index / (dates.size - 1) else chartWidth / 2
                    val label = date.format(DateTimeFormatter.ofPattern("d/M"))
                    drawText(
                        textMeasurer = textMeasurer,
                        text = label,
                        topLeft = Offset(x - 12.dp.toPx(), size.height - bottomMargin + 4.dp.toPx()),
                        style = TextStyle(color = textColor, fontSize = 9.sp)
                    )
                }
            }

            // Draw series
            seriesColors.forEach { (seriesName, color) ->
                val points = sortedData.mapIndexedNotNull { index, point ->
                    val value = point.values[seriesName] ?: return@mapIndexedNotNull null
                    val x = leftMargin + if (dates.size > 1) chartWidth * index / (dates.size - 1) else chartWidth / 2
                    val y = topMargin + chartHeight * (1f - (value - yMin) / (yMax - yMin))
                    Offset(x, y)
                }
                if (points.size < 2) {
                    points.forEach { pt ->
                        drawCircle(color, radius = 3.dp.toPx(), center = pt)
                    }
                    return@forEach
                }

                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        lineTo(points[i].x, points[i].y)
                    }
                }

                val isDashed = seriesName in dashedSeries
                val stroke = if (isDashed) {
                    Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
                    )
                } else {
                    Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                }
                drawPath(path, color, style = stroke)

                points.forEach { pt ->
                    drawCircle(color, radius = 3.dp.toPx(), center = pt)
                }
            }

            // Selected date indicator
            if (selectedDate != null) {
                val index = dates.indexOf(selectedDate)
                if (index >= 0) {
                    val x = leftMargin + if (dates.size > 1) chartWidth * index / (dates.size - 1) else chartWidth / 2
                    drawLine(
                        selectedColor,
                        Offset(x, topMargin),
                        Offset(x, topMargin + chartHeight),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                    )
                }
            }
        }

        // Legend
        if (seriesColors.size > 1) {
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 44.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                seriesColors.forEach { (name, color) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Canvas(modifier = Modifier.size(8.dp)) {
                            drawCircle(color)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.TextDim
                        )
                    }
                }
            }
        }
    }
}
