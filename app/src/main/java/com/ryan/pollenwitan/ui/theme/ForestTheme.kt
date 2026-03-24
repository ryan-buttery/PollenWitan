package com.ryan.pollenwitan.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class ForestColors(
    val Dark: Color,
    val Mid: Color,
    val Light: Color,
    val Selected: Color,
    val TextOnSelected: Color,
    val Text: Color,
    val TextDim: Color,
    val Accent: Color,
)

val DarkForestColors = ForestColors(
    Dark = Color(0xFF0A1F0A),
    Mid = Color(0xFF1A3A1A),
    Light = Color(0xFF2D5A2D),
    Selected = Color(0xFF3D7A3D),
    TextOnSelected = Color(0xFFFFFFFF),
    Text = Color(0xFFE0F0E0),
    TextDim = Color(0xFFA0C8A0),
    Accent = Color(0xFF4A8A4A),
)

val LightForestColors = ForestColors(
    Dark = Color(0xFFF5F0E8),
    Mid = Color(0xFFEDE8DC),
    Light = Color(0xFFE0D9CC),
    Selected = Color(0xFF2D5A3D),
    TextOnSelected = Color(0xFFFFFFFF),
    Text = Color(0xFF1A1A1A),
    TextDim = Color(0xFF666666),
    Accent = Color(0xFF3B6B4A),
)

val LocalForestColors = staticCompositionLocalOf { DarkForestColors }

object ForestTheme {
    val current: ForestColors
        @Composable get() = LocalForestColors.current
}
