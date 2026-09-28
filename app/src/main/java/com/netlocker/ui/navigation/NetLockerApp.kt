package com.netlocker.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.netlocker.ui.apps.AppsScreen
import com.netlocker.ui.appdetails.AppDetailsScreen
import com.netlocker.ui.rules.RulesScreen
import com.netlocker.ui.settings.SettingsScreen
import com.netlocker.ui.theme.netLocker

private object Routes {
    const val APPS = "apps"
    const val RULES = "rules"
    const val SETTINGS = "settings"
    const val APP_DETAILS = "app_details/{packageName}"
    fun appDetails(packageName: String) = "app_details/$packageName"
}

private data class TopLevelDestination(val route: String, val label: String, val icon: ImageVector)

private val topLevelDestinations = listOf(
    TopLevelDestination(Routes.APPS, "Apps", Icons.Filled.Apps),
    TopLevelDestination(Routes.RULES, "Rules", Icons.Filled.Description),
    TopLevelDestination(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)

/** App shell: bottom navigation (Apps / Rules / Settings) plus the per-app details screen. */
@Composable
fun NetLockerApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = topLevelDestinations.any { it.route == currentRoute }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            if (showBottomBar) {
                NetLockerBottomBar(currentRoute = currentRoute, onNavigate = navController::navigateTopLevel)
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.APPS,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.APPS) {
                AppsScreen(
                    onOpenAppDetails = { navController.navigate(Routes.appDetails(it)) },
                    onOpenSettings = { navController.navigateTopLevel(Routes.SETTINGS) },
                )
            }
            composable(Routes.RULES) {
                RulesScreen()
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { navController.navigateTopLevel(Routes.APPS) })
            }
            composable(
                route = Routes.APP_DETAILS,
                arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
            ) { entry ->
                AppDetailsScreen(
                    packageName = entry.arguments?.getString("packageName").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun NetLockerBottomBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.netLocker.navBar, tonalElevation = androidx.compose.ui.unit.Dp.Hairline) {
        topLevelDestinations.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = { onNavigate(destination.route) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(destination.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
