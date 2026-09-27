package com.netlocker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.netlocker.domain.model.NetworkAccessState
import com.netlocker.ui.theme.StatusAllowedGreen
import com.netlocker.ui.theme.StatusBlockedRed
import com.netlocker.ui.theme.StatusPartialAmber

/** Renders the Wi-Fi ✓/✕ and Mobile Data ✓/✕ pair described in spec §13, plus an
 *  overall-state color so the list is scannable without reading the text. */
@Composable
fun StatusBadge(state: NetworkAccessState, modifier: Modifier = Modifier) {
    val (wifiOk, mobileOk) = when (state) {
        NetworkAccessState.ALLOWED -> true to true
        NetworkAccessState.WIFI_ONLY -> true to false
        NetworkAccessState.MOBILE_ONLY -> false to true
        NetworkAccessState.BLOCKED -> false to false
    }
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatusChip(label = "Wi-Fi", ok = wifiOk)
        StatusChip(label = "Mobile Data", ok = mobileOk)
    }
}

@Composable
private fun StatusChip(label: String, ok: Boolean) {
    val color = if (ok) StatusAllowedGreen else StatusBlockedRed
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = if (ok) Icons.Filled.Check else Icons.Filled.Close,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp),
        )
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Maps a [NetworkAccessState] to the accent color used for its badge/card border. */
fun NetworkAccessState.accentColor() = when (this) {
    NetworkAccessState.ALLOWED -> StatusAllowedGreen
    NetworkAccessState.WIFI_ONLY, NetworkAccessState.MOBILE_ONLY -> StatusPartialAmber
    NetworkAccessState.BLOCKED -> StatusBlockedRed
}
