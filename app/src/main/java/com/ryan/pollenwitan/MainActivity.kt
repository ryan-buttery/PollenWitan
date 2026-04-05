package com.ryan.pollenwitan

import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import com.ryan.pollenwitan.data.repository.ThemePrefsRepository
import com.ryan.pollenwitan.data.security.DatabaseEncryption
import com.ryan.pollenwitan.ui.navigation.AppNavGraph
import com.ryan.pollenwitan.ui.navigation.Screen
import com.ryan.pollenwitan.ui.theme.ForestTheme
import com.ryan.pollenwitan.ui.theme.PollenWitanTheme
import com.ryan.pollenwitan.worker.NotificationHelper
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private var navigateTo by mutableStateOf<String?>(null)

    private fun validatedRoute(intent: Intent?): String? {
        val route = intent?.getStringExtra(NotificationHelper.EXTRA_NAVIGATE_TO) ?: return null
        return route.takeIf { it in VALID_NOTIFICATION_ROUTES }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySystemBars(dark = true)

        navigateTo = validatedRoute(intent)

        val themePrefsRepository = ThemePrefsRepository(this)

        setContent {
            val isDark by themePrefsRepository.isDarkTheme().collectAsState(initial = true)

            LaunchedEffect(isDark) {
                applySystemBars(isDark)
            }

            PollenWitanTheme(darkTheme = isDark) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ForestTheme.current.Dark)
                ) {
                    AppNavGraph(
                        isDarkTheme = isDark,
                        onToggleTheme = { newValue ->
                            lifecycleScope.launch { themePrefsRepository.setDarkTheme(newValue) }
                        },
                        initialRoute = navigateTo
                    )

                    // One-shot warning when the encrypted DB had to be deleted
                    var showDbResetWarning by remember {
                        mutableStateOf(DatabaseEncryption.dbWasReset)
                    }
                    if (showDbResetWarning) {
                        AlertDialog(
                            onDismissRequest = { showDbResetWarning = false },
                            title = { Text(stringResource(R.string.db_reset_title)) },
                            text = { Text(stringResource(R.string.db_reset_message)) },
                            confirmButton = {
                                TextButton(onClick = { showDbResetWarning = false }) {
                                    Text(stringResource(R.string.common_ok))
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        navigateTo = validatedRoute(intent)
    }

    companion object {
        private val VALID_NOTIFICATION_ROUTES = setOf(
            Screen.Dashboard.route,
            Screen.Forecast.route,
            Screen.Settings.route,
            Screen.ProfileList.route,
            Screen.SymptomCheckIn.createRoute(),
            Screen.SymptomDiary.route,
            Screen.SymptomTrends.route,
            Screen.MedicationHistory.route
        )
    }

    private fun applySystemBars(dark: Boolean) {
        val color = if (dark) {
            android.graphics.Color.parseColor("#0A1F0A")
        } else {
            android.graphics.Color.parseColor("#F5F0E8")
        }

        if (dark) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(color),
                navigationBarStyle = SystemBarStyle.dark(color),
            )
        } else {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.light(color, color),
                navigationBarStyle = SystemBarStyle.light(color, color),
            )
        }
    }
}
