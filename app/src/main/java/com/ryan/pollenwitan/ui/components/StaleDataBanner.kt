package com.ryan.pollenwitan.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ryan.pollenwitan.R
import com.ryan.pollenwitan.ui.theme.SeverityColors

private const val STALE_THRESHOLD_MS = 6 * 60 * 60 * 1000L // 6 hours

@Composable
fun StaleDataBanner(fetchedAtMillis: Long, onRefresh: () -> Unit) {
    val ageMs = System.currentTimeMillis() - fetchedAtMillis
    if (ageMs < STALE_THRESHOLD_MS) return

    val ageText = formatAge(ageMs)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clickable(onClick = onRefresh),
        shape = RoundedCornerShape(8.dp),
        color = SeverityColors.Moderate.copy(alpha = 0.15f),
        contentColor = SeverityColors.Moderate
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.stale_data_banner, ageText),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

private fun formatAge(ms: Long): String {
    val hours = ms / (60 * 60 * 1000L)
    return if (hours >= 1) "${hours}h" else "${ms / (60 * 1000L)}m"
}
