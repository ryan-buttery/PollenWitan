package com.ryan.pollenwitan.ui.screens

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ryan.pollenwitan.R

@Composable
fun UsefulInfoScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.info_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.info_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        InfoSection(
            titleRes = R.string.info_allergies_title,
            contentRes = R.string.info_allergies_body
        )

        InfoSection(
            titleRes = R.string.info_specificity_title,
            contentRes = R.string.info_specificity_body,
            footnoteRes = R.string.info_specificity_cross_reactivity_link
        )

        InfoSection(
            titleRes = R.string.info_medication_title,
            contentRes = R.string.info_medication_body
        )

        InfoSection(
            titleRes = R.string.info_features_title,
            contentRes = R.string.info_features_body
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun InfoSection(
    @StringRes titleRes: Int,
    @StringRes contentRes: Int,
    @StringRes footnoteRes: Int? = null
) {
    var expanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(contentRes),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (footnoteRes != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(footnoteRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
