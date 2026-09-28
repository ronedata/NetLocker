package com.netlocker

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.netlocker.network.VpnSupportChecker
import com.netlocker.ui.navigation.NetLockerApp
import com.netlocker.ui.permission.UnsupportedDeviceScreen
import com.netlocker.ui.theme.NetLockerBackground
import com.netlocker.ui.theme.NetLockerTheme
import com.netlocker.ui.theme.resolveDarkTheme
import com.netlocker.util.AppTheme
import com.netlocker.util.TextSize
import com.netlocker.util.ServiceLocator

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val vpnBlockedByPolicy = VpnSupportChecker.isVpnBlockedByDevicePolicy(this)

        setContent {
            val theme by ServiceLocator.preferencesManager.theme.collectAsState(initial = AppTheme.SYSTEM)
            val textSize by ServiceLocator.preferencesManager.textSize.collectAsState(initial = TextSize.DEFAULT)

            // The in-app text size replaces the phone's font-size setting (unless the user
            // picked "Follow system"); layout density is left exactly as the phone has it.
            val systemDensity = LocalDensity.current
            val density = remember(systemDensity, textSize) {
                textSize.scale?.let { Density(systemDensity.density, fontScale = it) } ?: systemDensity
            }

            CompositionLocalProvider(LocalDensity provides density) {
            NetLockerTheme(appTheme = theme) {
                // Keep the status/navigation bar icons legible against the chosen theme
                // (the theme setting can differ from the phone's own dark/light mode).
                val dark = resolveDarkTheme(theme)
                DisposableEffect(dark) {
                    val style = if (dark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                    onDispose { }
                }

                NetLockerBackground {
                    if (vpnBlockedByPolicy) UnsupportedDeviceScreen() else NetLockerApp()
                }
            }
            }
        }
    }
}
