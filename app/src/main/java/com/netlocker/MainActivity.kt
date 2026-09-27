package com.netlocker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.netlocker.network.VpnSupportChecker
import com.netlocker.ui.appdetails.AppDetailsScreen
import com.netlocker.ui.home.HomeScreen
import com.netlocker.ui.permission.UnsupportedDeviceScreen
import com.netlocker.ui.settings.SettingsScreen
import com.netlocker.ui.theme.NetLockerTheme
import com.netlocker.util.ServiceLocator

private object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val APP_DETAILS = "app_details/{packageName}"
    fun appDetails(packageName: String) = "app_details/$packageName"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val vpnBlockedByPolicy = VpnSupportChecker.isVpnBlockedByDevicePolicy(this)

        setContent {
            val theme by ServiceLocator.preferencesManager.theme.collectAsState(initial = com.netlocker.util.AppTheme.SYSTEM)

            NetLockerTheme(appTheme = theme) {
                if (vpnBlockedByPolicy) {
                    UnsupportedDeviceScreen()
                } else {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = Routes.HOME) {
                        composable(Routes.HOME) {
                            HomeScreen(
                                onOpenAppDetails = { packageName -> navController.navigate(Routes.appDetails(packageName)) },
                                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                            )
                        }
                        composable(Routes.SETTINGS) {
                            SettingsScreen(onBack = { navController.popBackStack() })
                        }
                        composable(
                            route = Routes.APP_DETAILS,
                            arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
                        ) { backStackEntry ->
                            val packageName = backStackEntry.arguments?.getString("packageName").orEmpty()
                            AppDetailsScreen(packageName = packageName, onBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}
