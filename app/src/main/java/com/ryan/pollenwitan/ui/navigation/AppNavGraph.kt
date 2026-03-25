package com.ryan.pollenwitan.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ryan.pollenwitan.data.repository.ProfileRepository
import com.ryan.pollenwitan.ui.screens.CrossReactivityScreen
import com.ryan.pollenwitan.ui.screens.PollenCalendarScreen
import com.ryan.pollenwitan.ui.screens.DashboardScreen
import com.ryan.pollenwitan.ui.screens.ForecastScreen
import com.ryan.pollenwitan.ui.screens.OnboardingScreen
import com.ryan.pollenwitan.ui.screens.ProfileEditScreen
import com.ryan.pollenwitan.ui.screens.ProfileListScreen
import com.ryan.pollenwitan.ui.screens.SettingsScreen
import com.ryan.pollenwitan.R
import com.ryan.pollenwitan.ui.theme.ForestTheme
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch

private data class NavItem(
    val screen: Screen,
    @StringRes val labelRes: Int,
    val icon: ImageVector
)

private val navItems = listOf(
    NavItem(Screen.Dashboard, R.string.nav_dashboard, Icons.Filled.Dashboard),
    NavItem(Screen.Forecast, R.string.nav_forecast, Icons.Filled.CalendarMonth),
    NavItem(Screen.ProfileList, R.string.nav_profiles, Icons.Filled.Person),
    NavItem(Screen.CrossReactivity, R.string.nav_cross_reactivity, Icons.Filled.Link),
    NavItem(Screen.PollenCalendar, R.string.nav_pollen_calendar, Icons.Filled.EventNote),
    NavItem(Screen.Settings, R.string.nav_settings, Icons.Filled.Settings)
)

@Composable
fun AppNavGraph(
    isDarkTheme: Boolean = true,
    onToggleTheme: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val profileRepository = remember { ProfileRepository(context.applicationContext) }
    val profiles by profileRepository.getProfiles().collectAsStateWithLifecycle(initialValue = null)

    // Loading guard — wait for DataStore to initialize
    if (profiles == null) return

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val colors = ForestTheme.current

    // Redirect to onboarding if no profiles exist
    LaunchedEffect(profiles) {
        if (profiles != null && profiles!!.isEmpty()) {
            navController.navigate(Screen.Onboarding.route) {
                popUpTo(Screen.Dashboard.route) { inclusive = true }
            }
        }
    }

    // Determine current screen label for the top bar
    val currentRoute = currentDestination?.route
    val currentLabelRes = when {
        currentRoute == Screen.ProfileCreate.route -> R.string.nav_new_profile
        currentRoute?.startsWith("profiles/edit/") == true -> R.string.nav_edit_profile
        else -> navItems.find { item ->
            currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
        }?.labelRes ?: R.string.nav_dashboard
    }
    val currentLabel = stringResource(currentLabelRes)

    val isOnboarding = currentRoute == Screen.Onboarding.route

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !isOnboarding,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = colors.Mid,
                modifier = Modifier.width(280.dp),
            ) {
                // Drawer header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                ) {
                    Text(
                        text = stringResource(R.string.nav_app_name),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.Text,
                    )
                    Text(
                        text = stringResource(R.string.nav_app_subtitle),
                        fontSize = 14.sp,
                        color = colors.TextDim,
                    )
                }

                // Divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(1.dp)
                        .background(colors.TextDim.copy(alpha = 0.2f)),
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Theme toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onToggleTheme(!isDarkTheme) }
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(22.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isDarkTheme) "\u2600" else "\u263E",
                            fontSize = 20.sp,
                            color = colors.TextDim,
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = stringResource(if (isDarkTheme) R.string.theme_light else R.string.theme_dark),
                        fontSize = 16.sp,
                        color = colors.Text,
                    )
                }

                // Divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(1.dp)
                        .background(colors.TextDim.copy(alpha = 0.2f)),
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Navigation items
                navItems.forEach { item ->
                    val isSelected = currentDestination?.hierarchy?.any {
                        it.route == item.screen.route
                    } == true

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) colors.Selected else colors.Mid)
                            .clickable {
                                scope.launch { drawerState.close() }
                                navController.navigate(item.screen.route) {
                                    popUpTo(Screen.Dashboard.route) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = stringResource(item.labelRes),
                            tint = if (isSelected) colors.TextOnSelected else colors.TextDim,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = stringResource(item.labelRes),
                            fontSize = 16.sp,
                            color = if (isSelected) colors.TextOnSelected else colors.Text,
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            containerColor = colors.Dark,
            topBar = {
                if (!isOnboarding) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.Dark)
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = stringResource(R.string.nav_open_menu),
                                tint = colors.Text,
                            )
                        }
                        Text(
                            text = currentLabel,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.Text,
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Dashboard.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Screen.Onboarding.route) {
                    OnboardingScreen(
                        onFinished = {
                            navController.navigate(Screen.Dashboard.route) {
                                popUpTo(Screen.Onboarding.route) { inclusive = true }
                            }
                        }
                    )
                }
                composable(Screen.Dashboard.route) {
                    DashboardScreen()
                }
                composable(Screen.Forecast.route) { ForecastScreen() }
                composable(Screen.CrossReactivity.route) { CrossReactivityScreen() }
                composable(Screen.PollenCalendar.route) { PollenCalendarScreen() }
                composable(Screen.Settings.route) { SettingsScreen() }
                composable(Screen.ProfileList.route) {
                    ProfileListScreen(navController = navController)
                }
                composable(Screen.ProfileCreate.route) {
                    ProfileEditScreen(navController = navController, profileId = null)
                }
                composable(
                    Screen.ProfileEdit.route,
                    arguments = listOf(navArgument("profileId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val profileId = backStackEntry.arguments?.getString("profileId")
                    ProfileEditScreen(navController = navController, profileId = profileId)
                }
            }
        }
    }
}
