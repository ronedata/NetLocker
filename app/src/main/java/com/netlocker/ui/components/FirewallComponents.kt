package com.netlocker.ui.components

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.netlocker.domain.model.FirewallStatus
import com.netlocker.ui.theme.netLocker
import com.netlocker.util.ServiceLocator

/** The current real firewall state plus the actions to change it — see [rememberFirewallActions]. */
@Stable
class FirewallActions(
    val status: FirewallStatus,
    val enable: () -> Unit,
    val disable: () -> Unit,
) {
    /** True only when the VPN tunnel is genuinely up — never assumed from saved rules. */
    val isActive: Boolean get() = status == FirewallStatus.Active
}

/**
 * Shared "turn the firewall on/off" logic for every screen that shows the firewall
 * banner. Enabling asks for the Android 13+ notification permission first (a real
 * system dialog — never requested silently), then for the system VPN consent dialog.
 * Declining the notification permission doesn't block the firewall; it just runs
 * without a status notification.
 */
@Composable
fun rememberFirewallActions(): FirewallActions {
    val controller = ServiceLocator.firewallController
    val status by controller.status.collectAsState()

    val vpnConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) controller.start()
    }

    val launchVpnConsent = {
        val intent = controller.vpnPermissionIntent()
        if (intent != null) vpnConsentLauncher.launch(intent) else controller.start()
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { launchVpnConsent() }

    return FirewallActions(
        status = status,
        enable = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                launchVpnConsent()
            }
        },
        disable = { controller.stop() },
    )
}

/**
 * One-row firewall status banner. Reflects the *real* state of the enforcement layer:
 * "active" only when the VPN is up; otherwise it says plainly that rules are saved but
 * not enforced (spec: never show fake success).
 */
@Composable
fun FirewallStatusBanner(
    firewall: FirewallActions,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.netLocker
    val banner = when (val status = firewall.status) {
        FirewallStatus.Active -> BannerContent(
            icon = Icons.Filled.Shield,
            tint = colors.allowed,
            title = "NetLocker firewall is active",
            subtitle = "Your network rules are being enforced.",
            action = "Disable",
            onAction = firewall.disable,
        )
        FirewallStatus.Stopped -> BannerContent(
            icon = Icons.Filled.Lock,
            tint = MaterialTheme.colorScheme.primary,
            title = "NetLocker firewall is off",
            subtitle = "Rules are saved but not enforced yet.",
            action = "Enable",
            onAction = firewall.enable,
        )
        FirewallStatus.PermissionRequired -> BannerContent(
            icon = Icons.Filled.Lock,
            tint = MaterialTheme.colorScheme.primary,
            title = "NetLocker needs additional access",
            subtitle = "Grant VPN permission to enforce your rules.",
            action = "Grant",
            onAction = firewall.enable,
        )
        is FirewallStatus.Error -> BannerContent(
            icon = Icons.Filled.ErrorOutline,
            tint = colors.blocked,
            title = "Could not apply network restriction",
            subtitle = status.reason,
            action = "Retry",
            onAction = firewall.enable,
        )
    }

    val accent = MaterialTheme.colorScheme.primary
    val bannerShape = RoundedCornerShape(20.dp)
    // Glowing-outline card: a translucent navy fill, a bright accent border, and the
    // "Locker" half of the brand name picked out in the accent colour.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(bannerShape)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
            .border(1.5.dp, accent.copy(alpha = 0.85f), bannerShape)
            .padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Shield, contentDescription = null, tint = banner.tint, modifier = Modifier.size(38.dp))
            if (banner.icon == Icons.Filled.Lock || banner.icon == Icons.Filled.Shield) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            } else {
                Icon(banner.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        // Always exactly one line each, on every screen: the text shrinks to fit rather
        // than wrapping, even when the system font size is enlarged.
        Column(modifier = Modifier.weight(1f)) {
            SingleLineFitText(
                brandHighlighted(banner.title, accent),
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            SingleLineFitText(
                AnnotatedString(banner.subtitle),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        val actionColors = if (firewall.isActive) {
            listOf(colors.wifiContainer, colors.wifiContainer)
        } else {
            listOf(accent, accent.copy(red = accent.red * 0.7f, green = accent.green * 0.85f))
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(actionColors))
                .clickable(role = Role.Button, onClick = banner.onAction)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val content = if (firewall.isActive) colors.wifi else Color.White
            Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text(banner.action, color = content, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false)
        }
    }
}

/** One line, never wrapped: if the text is wider than its slot the font size is stepped
 *  down (to a floor) until it fits, so a large system font can't push the banner onto
 *  several lines. Below the floor it ellipsizes rather than clipping silently. */
@Composable
private fun SingleLineFitText(
    text: AnnotatedString,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    color: Color = Color.Unspecified,
) {
    var scale by remember(text, fontSize) { mutableFloatStateOf(1f) }
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize * scale,
        fontWeight = fontWeight,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { if (it.hasVisualOverflow && scale > MIN_FIT_SCALE) scale = maxOf(MIN_FIT_SCALE, scale * 0.92f) },
    )
}

private const val MIN_FIT_SCALE = 0.5f

/** Colours the "Locker" in a leading "NetLocker" with [accent], as in the brand wordmark. */
private fun brandHighlighted(title: String, accent: Color): AnnotatedString = buildAnnotatedString {
    val brand = "NetLocker"
    if (title.startsWith(brand)) {
        append("Net")
        withStyle(SpanStyle(color = accent)) { append("Locker") }
        append(title.removePrefix(brand))
    } else {
        append(title)
    }
}

private data class BannerContent(
    val icon: ImageVector,
    val tint: Color,
    val title: String,
    val subtitle: String,
    val action: String,
    val onAction: () -> Unit,
)
