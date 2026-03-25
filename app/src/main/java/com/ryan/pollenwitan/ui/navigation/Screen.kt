package com.ryan.pollenwitan.ui.navigation

sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")
    data object Forecast : Screen("forecast")
    data object Settings : Screen("settings")
    data object ProfileList : Screen("profiles")
    data object ProfileCreate : Screen("profiles/create")
    data object ProfileEdit : Screen("profiles/edit/{profileId}") {
        fun createRoute(profileId: String) = "profiles/edit/$profileId"
    }
    data object CrossReactivity : Screen("cross-reactivity")
    data object PollenCalendar : Screen("pollen-calendar")
    data object Onboarding : Screen("onboarding")
}
