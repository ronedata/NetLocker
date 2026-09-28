package com.netlocker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.netlocker.ui.theme.netLocker
import androidx.compose.material3.MaterialTheme

/**
 * A network-type icon followed by a compact switch, sized for a list row. The visible
 * switch is scaled down, but its layout box stays 48dp tall so the touch target keeps
 * the accessibility minimum.
 */
@Composable
fun NetworkToggle(
    icon: ImageVector,
    iconTint: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        Box(modifier = Modifier.size(width = 46.dp, height = 48.dp), contentAlignment = Alignment.Center) {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier
                    .scale(0.78f)
                    .semantics { this.contentDescription = contentDescription },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedThumbColor = Color.White,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedBorderColor = MaterialTheme.netLocker.cardBorderStrong,
                ),
            )
        }
    }
}
