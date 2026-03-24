package com.ryan.pollenwitan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

@Composable
fun PollenWitanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val forestColors = if (darkTheme) DarkForestColors else LightForestColors

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = forestColors.Accent,
            onPrimary = forestColors.TextOnSelected,
            primaryContainer = forestColors.Selected,
            onPrimaryContainer = forestColors.TextOnSelected,
            secondary = forestColors.Accent,
            onSecondary = forestColors.TextOnSelected,
            background = forestColors.Dark,
            onBackground = forestColors.Text,
            surface = forestColors.Mid,
            onSurface = forestColors.Text,
            surfaceVariant = forestColors.Light,
            onSurfaceVariant = forestColors.TextDim,
            error = Color(0xFFF44336),
            onError = Color.White,
        )
    } else {
        lightColorScheme(
            primary = forestColors.Accent,
            onPrimary = forestColors.TextOnSelected,
            primaryContainer = forestColors.Selected,
            onPrimaryContainer = forestColors.TextOnSelected,
            secondary = forestColors.Accent,
            onSecondary = forestColors.TextOnSelected,
            background = forestColors.Dark,
            onBackground = forestColors.Text,
            surface = forestColors.Mid,
            onSurface = forestColors.Text,
            surfaceVariant = forestColors.Light,
            onSurfaceVariant = forestColors.TextDim,
            error = Color(0xFFF44336),
            onError = Color.White,
        )
    }

    CompositionLocalProvider(LocalForestColors provides forestColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
