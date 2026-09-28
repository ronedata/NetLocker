package com.netlocker.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.netlocker.util.AppTheme

private val DarkColors = darkColorScheme(
    primary = Color(0xFF2F7BFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF12306B),
    onPrimaryContainer = Color(0xFFDCE8FF),
    secondary = Color(0xFF22D3EE),
    secondaryContainer = Color(0xFF132A55),
    onSecondaryContainer = Color(0xFFDCE8FF),
    tertiary = Color(0xFF22D3EE),
    background = Color(0xFF07122B),
    onBackground = Color(0xFFEAF0FF),
    surface = Color(0xFF0B1A38),
    onSurface = Color(0xFFEAF0FF),
    surfaceVariant = Color(0xFF122447),
    onSurfaceVariant = Color(0xFF9FB0D3),
    outline = Color(0xFF2A4A85),
    outlineVariant = Color(0xFF1A3468),
    error = Color(0xFFFF5C6C),
    errorContainer = Color(0xFF3A1420),
    onErrorContainer = Color(0xFFFFD9DD),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F63E6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FF),
    onPrimaryContainer = Color(0xFF0B2A66),
    secondary = Color(0xFF0A8A9C),
    secondaryContainer = Color(0xFFE1ECFF),
    onSecondaryContainer = Color(0xFF0B2A66),
    tertiary = Color(0xFF0A8A9C),
    background = Color(0xFFF5F8FF),
    onBackground = Color(0xFF0B1730),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0B1730),
    surfaceVariant = Color(0xFFE6EDFB),
    onSurfaceVariant = Color(0xFF4A5A7E),
    outline = Color(0xFF8FA3CC),
    outlineVariant = Color(0xFFCFDCF5),
    error = Color(0xFFC4283A),
    errorContainer = Color(0xFFFFE4E7),
    onErrorContainer = Color(0xFF5A0F1A),
)

/** Resolves the Settings screen's System/Light/Dark choice to an actual dark/light flag. */
@Composable
fun resolveDarkTheme(appTheme: AppTheme): Boolean = when (appTheme) {
    AppTheme.SYSTEM -> isSystemInDarkTheme()
    AppTheme.LIGHT -> false
    AppTheme.DARK -> true
}

/**
 * NetLocker's Material 3 theme. Honors the Settings screen's System/Light/Dark choice.
 *
 * Android 12+ dynamic ("Material You") color is intentionally **not** used any more: the
 * app has its own navy/blue brand identity, and wallpaper-derived colors would replace
 * it on most phones.
 */
@Composable
fun NetLockerTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = resolveDarkTheme(appTheme)
    CompositionLocalProvider(
        LocalNetLockerColors provides if (dark) DarkNetLockerColors else LightNetLockerColors,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = NetLockerTypography,
            content = content,
        )
    }
}

/** Convenience accessor: `MaterialTheme.netLocker.allowed`. */
val MaterialTheme.netLocker: NetLockerColors
    @Composable get() = LocalNetLockerColors.current

/** The app-wide navy gradient behind every screen. */
@Composable
fun NetLockerBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = MaterialTheme.netLocker
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(colors.backgroundTop, colors.backgroundBottom))),
    ) {
        // Plain `Text` reads LocalContentColor, which is black unless a Surface sets it.
        // The gradient is drawn by a bare Box (not a Surface), so set it explicitly —
        // otherwise every heading sitting directly on the background is dark-on-navy.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            content()
        }
    }
}
