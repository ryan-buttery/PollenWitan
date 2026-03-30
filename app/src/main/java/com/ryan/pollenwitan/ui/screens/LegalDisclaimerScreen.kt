package com.ryan.pollenwitan.ui.screens

import android.app.Activity
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ryan.pollenwitan.R
import com.ryan.pollenwitan.data.repository.LegalPrefsRepository
import com.ryan.pollenwitan.ui.theme.ForestTheme
import kotlinx.coroutines.launch

@Composable
fun LegalDisclaimerScreen(onAccepted: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { LegalPrefsRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val colors = ForestTheme.current
    val listState = rememberLazyListState()

    val isAtBottom by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisible != null && lastVisible.index == listState.layoutInfo.totalItemsCount - 1
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.legal_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.legal_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            item { DisclaimerSection(R.string.legal_not_medical_advice_title, R.string.legal_not_medical_advice_body) }
            item { DisclaimerSection(R.string.legal_data_accuracy_title, R.string.legal_data_accuracy_body) }
            item { DisclaimerSection(R.string.legal_liability_title, R.string.legal_liability_body) }
            item { DisclaimerSection(R.string.legal_privacy_title, R.string.legal_privacy_body) }
            item { DisclaimerSection(R.string.legal_attribution_title, R.string.legal_attribution_body) }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        if (!isAtBottom) {
            Text(
                text = stringResource(R.string.legal_scroll_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.TextDim,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(
                onClick = { (context as? Activity)?.finishAffinity() }
            ) {
                Text(stringResource(R.string.legal_disagree))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                enabled = isAtBottom,
                onClick = {
                    scope.launch {
                        repository.acceptDisclaimer()
                        onAccepted()
                    }
                }
            ) {
                Text(stringResource(R.string.legal_agree))
            }
        }
    }
}

@Composable
private fun DisclaimerSection(
    @StringRes titleRes: Int,
    @StringRes bodyRes: Int
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
