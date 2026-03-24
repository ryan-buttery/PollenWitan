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
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ryan.pollenwitan.ui.screens.DashboardScreen
import com.ryan.pollenwitan.ui.screens.ForecastScreen
import com.ryan.pollenwitan.ui.screens.SettingsScreen
import com.ryan.pollenwitan.ui.theme.ForestTheme
import kotlinx.coroutines.launch

private data class NavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector
)

private val navItems = listOf(
    NavItem(Screen.Dashboard, "Dashboard", Icons.Filled.Dashboard),
    NavItem(Screen.Forecast, "Forecast", Icons.Filled.CalendarMonth),
    NavItem(Screen.Settings, "Settings", Icons.Filled.Settings)
)

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val colors = ForestTheme.current

    // Determine current screen label for the top bar
    val currentLabel = navItems.find { item ->
        currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
    }?.label ?: "Dashboard"

    ModalNavigationDrawer(
        drawerState = drawerState,
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
                        text = "PollenWitan",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.Text,
                    )
                    Text(
                        text = "Pollen & Air Quality Forecast",
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
                            contentDescription = item.label,
                            tint = if (isSelected) colors.TextOnSelected else colors.TextDim,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = item.label,
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
                            contentDescription = "Open menu",
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
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Dashboard.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Screen.Dashboard.route) { DashboardScreen() }
                composable(Screen.Forecast.route) { ForecastScreen() }
                composable(Screen.Settings.route) { SettingsScreen() }
            }
        }
    }
}
