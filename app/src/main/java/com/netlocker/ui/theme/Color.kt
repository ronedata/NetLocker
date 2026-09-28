package com.netlocker.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * NetLocker's brand palette (dark navy + electric blue, per the app's design mockups).
 * Material's ColorScheme carries the generic roles; [NetLockerColors] carries the
 * status colors (allowed / blocked / Wi-Fi-only / mobile-only / disabled) and card
 * chrome that Material has no role for.
 *
 * Status is never conveyed by color alone anywhere in the UI — every colored pill also
 * carries an icon and a text label (accessibility).
 */
@Immutable
data class NetLockerColors(
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val card: Color,
    val cardBorder: Color,
    val cardBorderStrong: Color,
    val navBar: Color,
    val allowed: Color,
    val allowedContainer: Color,
    val blocked: Color,
    val blockedContainer: Color,
    val wifi: Color,
    val wifiContainer: Color,
    val mobile: Color,
    val mobileContainer: Color,
    val disabled: Color,
    val disabledContainer: Color,
)

val DarkNetLockerColors = NetLockerColors(
    backgroundTop = Color(0xFF07122B),
    backgroundBottom = Color(0xFF040A18),
    card = Color(0xFF0B1A38),
    cardBorder = Color(0xFF1A3468),
    cardBorderStrong = Color(0xFF2A57B8),
    navBar = Color(0xFF081428),
    allowed = Color(0xFF3DDC84),
    allowedContainer = Color(0xFF0E2E24),
    blocked = Color(0xFFFF5C6C),
    blockedContainer = Color(0xFF3A1420),
    wifi = Color(0xFF3D9BFF),
    wifiContainer = Color(0xFF0F2A55),
    mobile = Color(0xFF2DE0B5),
    mobileContainer = Color(0xFF0C3030),
    disabled = Color(0xFF8A97B5),
    disabledContainer = Color(0xFF1B2743),
)

val LightNetLockerColors = NetLockerColors(
    backgroundTop = Color(0xFFF5F8FF),
    backgroundBottom = Color(0xFFE9F0FF),
    card = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFCFDCF5),
    cardBorderStrong = Color(0xFF8FB0EE),
    navBar = Color(0xFFFFFFFF),
    allowed = Color(0xFF0B8A4A),
    allowedContainer = Color(0xFFDDF6E8),
    blocked = Color(0xFFC4283A),
    blockedContainer = Color(0xFFFFE4E7),
    wifi = Color(0xFF1D5FD1),
    wifiContainer = Color(0xFFDDE8FF),
    mobile = Color(0xFF07857A),
    mobileContainer = Color(0xFFD7F5EF),
    disabled = Color(0xFF5C6885),
    disabledContainer = Color(0xFFE5E9F3),
)

val LocalNetLockerColors = staticCompositionLocalOf { DarkNetLockerColors }
