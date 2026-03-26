package com.ryan.pollenwitan.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryan.pollenwitan.ui.theme.ForestTheme
import com.ryan.pollenwitan.ui.theme.SeverityColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class BarChartPoint(
    val date: LocalDate,
    val value: Float // 0.0 to 1.0
)

@Composable
fun TimelineBarChart(
    data: List<BarChartPoint>,
    selectedDate: LocalDate? = null,
    onDateTapped: (LocalDate) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = ForestTheme.current
    val textColor = colors.TextDim
    val gridColor = colors.TextDim.copy(alpha = 0.15f)
    val selectedColor = colors.Text.copy(alpha = 0.5f)
    val textMeasurer = rememberTextMeasurer()

    if (data.isEmpty()) return

    val sortedData = data.sortedBy { it.date }
    val dates = sortedData.map { it.date }

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .pointerInput(dates) {
                    detectTapGestures { offset ->
                        val leftMargin = 44.dp.toPx()
                        val rightMargin = 8.dp.toPx()
                        val chartWidth = size.width - leftMargin - rightMargin
                        if (dates.isEmpty() || offset.x < leftMargin) return@detectTapGestures
                        val barWidth = chartWidth / dates.size
                        val index = ((offset.x - leftMargin) / barWidth)
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

            // Grid lines at 0%, 50%, 100%
            listOf(0f, 0.5f, 1f).forEach { fraction ->
                val y = topMargin + chartHeight * (1f - fraction)
                drawLine(gridColor, Offset(leftMargin, y), Offset(size.width - rightMargin, y))
                drawText(
                    textMeasurer = textMeasurer,
                    text = "${(fraction * 100).toInt()}%",
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
            val barWidth = chartWidth / dates.size
            val gap = 2.dp.toPx()

            dates.forEachIndexed { index, date ->
                if (index % tickEvery == 0 || index == dates.lastIndex) {
                    val x = leftMargin + barWidth * index + barWidth / 2
                    drawText(
                        textMeasurer = textMeasurer,
                        text = date.format(DateTimeFormatter.ofPattern("d/M")),
                        topLeft = Offset(x - 12.dp.toPx(), size.height - bottomMargin + 4.dp.toPx()),
                        style = TextStyle(color = textColor, fontSize = 9.sp)
                    )
                }
            }

            // Draw bars
            sortedData.forEachIndexed { index, point ->
                val barX = leftMargin + barWidth * index + gap / 2
                val barW = barWidth - gap
                val barHeight = chartHeight * point.value.coerceIn(0f, 1f)
                val barY = topMargin + chartHeight - barHeight

                val barColor = lerp(SeverityColors.High, SeverityColors.Low, point.value)
                drawRect(
                    color = barColor,
                    topLeft = Offset(barX, barY),
                    size = Size(barW, barHeight)
                )

                // Selected date highlight
                if (point.date == selectedDate) {
                    drawRect(
                        color = selectedColor,
                        topLeft = Offset(barX, topMargin),
                        size = Size(barW, chartHeight),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
                        )
                    )
                }
            }
        }
    }
}
