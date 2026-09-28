package com.netlocker.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.netlocker.domain.model.NetworkAccessState
import com.netlocker.domain.model.RuleStatus
import com.netlocker.ui.theme.netLocker

/** Label + icon + colors for one access status. Always icon *and* text, never color alone. */
data class StatusStyle(
    val label: String,
    val icon: ImageVector,
    val color: Color,
    val container: Color,
)

@Composable
fun RuleStatus.style(): StatusStyle {
    val c = MaterialTheme.netLocker
    return when (this) {
        RuleStatus.ALLOWED -> StatusStyle("Allowed", Icons.Filled.CheckCircle, c.allowed, c.allowedContainer)
        RuleStatus.BLOCKED -> StatusStyle("Blocked", Icons.Filled.Block, c.blocked, c.blockedContainer)
        RuleStatus.WIFI_ONLY -> StatusStyle("Wi-Fi Only", Icons.Filled.Wifi, c.wifi, c.wifiContainer)
        RuleStatus.MOBILE_ONLY -> StatusStyle("Mobile Only", Icons.Filled.SignalCellularAlt, c.mobile, c.mobileContainer)
        RuleStatus.DISABLED -> StatusStyle("Disabled", Icons.Filled.PauseCircle, c.disabled, c.disabledContainer)
    }
}

@Composable
fun NetworkAccessState.style(): StatusStyle = when (this) {
    NetworkAccessState.ALLOWED -> RuleStatus.ALLOWED.style()
    NetworkAccessState.BLOCKED -> RuleStatus.BLOCKED.style()
    NetworkAccessState.WIFI_ONLY -> RuleStatus.WIFI_ONLY.style()
    NetworkAccessState.MOBILE_ONLY -> RuleStatus.MOBILE_ONLY.style()
}

/** Style for a single transport being allowed/blocked (used by the small Wi-Fi/Mobile pills). */
@Composable
fun allowedStyle(allowed: Boolean): StatusStyle =
    if (allowed) RuleStatus.ALLOWED.style() else RuleStatus.BLOCKED.style()
