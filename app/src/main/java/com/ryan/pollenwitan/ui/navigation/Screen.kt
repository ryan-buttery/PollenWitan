package com.ryan.pollenwitan.ui.navigation

sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")
    data object Forecast : Screen("forecast")
    data object Settings : Screen("settings")
}
