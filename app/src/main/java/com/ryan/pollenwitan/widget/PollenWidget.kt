package com.ryan.pollenwitan.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ryan.pollenwitan.MainActivity
import com.ryan.pollenwitan.R

class PollenWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetDataProvider.fetch(context)

        provideContent {
            WidgetContent(data, context)
        }
    }
}

private val TextColor = ColorProvider(Color(0xFFE0F0E0.toInt()))
private val DimTextColor = ColorProvider(Color(0xFFA0C8A0.toInt()))
private val BgColor = ColorProvider(Color(0xFF0A1F0A.toInt()))

@androidx.glance.GlanceComposable
@androidx.compose.runtime.Composable
private fun WidgetContent(data: PollenWidgetData, context: Context) {
    val launchIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(16.dp)
            .background(BgColor)
            .clickable(actionStartActivity(launchIntent))
            .padding(12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (!data.hasData) {
            Text(
                text = data.aqiText,
                style = TextStyle(color = DimTextColor, fontSize = 14.sp)
            )
        } else {
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                // Header: profile name · location, refresh, timestamp
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${data.profileName} \u00B7 ${data.locationName}",
                        style = TextStyle(
                            color = TextColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Box(
                        modifier = GlanceModifier
                            .size(24.dp)
                            .cornerRadius(12.dp)
                            .clickable(actionRunCallback<WidgetRefreshAction>()),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "\u27F3",
                            style = TextStyle(color = DimTextColor, fontSize = 14.sp)
                        )
                    }
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Text(
                        text = data.timestamp,
                        style = TextStyle(color = DimTextColor, fontSize = 12.sp)
                    )
                }

                Spacer(modifier = GlanceModifier.height(6.dp))

                // Current allergen readings
                AllergenGrid(data.allergenReadings)

                // Peak allergen readings
                if (data.peakAllergenReadings.isNotEmpty()) {
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = context.getString(R.string.widget_peak),
                            style = TextStyle(
                                color = DimTextColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        Spacer(modifier = GlanceModifier.width(6.dp))
                    }
                    AllergenGrid(data.peakAllergenReadings)
                }

                Spacer(modifier = GlanceModifier.height(4.dp))

                // Footer: AQI + medication status
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = data.aqiText,
                        style = TextStyle(
                            color = ColorProvider(Color(data.aqiColor.toInt())),
                            fontSize = 12.sp
                        )
                    )
                    if (data.hasMedication) {
                        Spacer(modifier = GlanceModifier.defaultWeight())
                        Text(
                            text = data.medicationText,
                            style = TextStyle(color = DimTextColor, fontSize = 12.sp)
                        )
                    }
                }
            }
        }
    }
}

@androidx.glance.GlanceComposable
@androidx.compose.runtime.Composable
private fun AllergenGrid(readings: List<WidgetAllergenReading>) {
    val rows = readings.chunked(3)
    rows.forEach { rowItems ->
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            rowItems.forEach { reading ->
                Row(
                    modifier = GlanceModifier.width(95.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = GlanceModifier
                            .size(8.dp)
                            .cornerRadius(4.dp)
                            .background(ColorProvider(Color(reading.severityColor.toInt())))
                    ) {}
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Text(
                        text = "${reading.abbreviation} ${reading.value}",
                        style = TextStyle(color = TextColor, fontSize = 12.sp),
                        maxLines = 1
                    )
                }
            }
        }
        Spacer(modifier = GlanceModifier.height(2.dp))
    }
}
