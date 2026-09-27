package com.netlocker.ui.theme

import androidx.compose.ui.graphics.Color

// Static fallback palette (used pre-Android 12 or when dynamic color is unavailable).
// A restrained green/slate pairing — evokes "shield/firewall" without being loud.
val GreenPrimaryLight = Color(0xFF2E6B44)
val GreenPrimaryDark = Color(0xFF8FD9A8)

val SurfaceLight = Color(0xFFFBFDF8)
val SurfaceDark = Color(0xFF10140F)

val OnSurfaceLight = Color(0xFF1A1C19)
val OnSurfaceDark = Color(0xFFE2E3DD)

val ErrorLight = Color(0xFFBA1A1A)
val ErrorDark = Color(0xFFFFB4AB)

val OutlineLight = Color(0xFF72796F)
val OutlineDark = Color(0xFF8C938A)

// Status accents used by StatusBadge (spec §13) — kept separate from the theme's
// primary/error roles so a "Wi-Fi only" badge doesn't read as an app error state.
val StatusAllowedGreen = Color(0xFF2E7D32)
val StatusPartialAmber = Color(0xFFB8860B)
val StatusBlockedRed = Color(0xFFC62828)
