package com.netlocker.ui.appdetails

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.netlocker.domain.model.NetworkAccessState
import com.netlocker.ui.components.AppIconImage
import com.netlocker.ui.components.CircleIconButton
import com.netlocker.ui.components.NetLockerCard
import com.netlocker.ui.components.StatusPill
import com.netlocker.ui.components.allowedStyle
import com.netlocker.ui.components.rememberFirewallActions
import com.netlocker.ui.components.style
import com.netlocker.ui.theme.netLocker

@Composable
fun AppDetailsScreen(
    packageName: String,
    onBack: () -> Unit,
    viewModel: AppDetailsViewModel = viewModel(factory = AppDetailsViewModel.factory(packageName)),
) {
    val appWithRule by viewModel.appWithRule.collectAsState()
    val firewall = rememberFirewallActions()
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val current = appWithRule

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
            Spacer(Modifier.width(8.dp))
            Text(
                current?.app?.label ?: "",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Box {
                CircleIconButton(Icons.Filled.MoreVert, "More options", onClick = { menuOpen = true })
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Reset to default") },
                        leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                        onClick = { menuOpen = false; viewModel.resetToDefault() },
                    )
                    DropdownMenuItem(
                        text = { Text("Open App Info") },
                        leadingIcon = { Icon(Icons.Filled.Info, null) },
                        onClick = {
                            menuOpen = false
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                    )
                }
            }
        }

        if (current == null) {
            // Still loading (the installed-app scan can take a couple of seconds) or the
            // app was uninstalled while open — a spinner, never a blank or stale screen.
            Box(modifier = Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        val rule = current.rule
        val wifi = rule.effectiveWifiAllowed
        val mobile = rule.effectiveMobileDataAllowed
        val colors = MaterialTheme.netLocker

        // Hero
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(26.dp))
                    .border(1.dp, colors.cardBorderStrong, RoundedCornerShape(26.dp))
                    .background(colors.card)
                    .padding(10.dp),
            ) { AppIconImage(current.app.icon, size = 88.dp, cornerRadius = 20.dp) }
            Spacer(Modifier.height(12.dp))
            Text(current.app.label, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Text(current.app.packageName, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            current.app.versionName?.let {
                Spacer(Modifier.height(8.dp))
                StatusPill("v$it", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
            }
        }

        // Network Access
        NetLockerCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(Icons.Filled.Wifi, colors.wifi, "Network Access", "Control this app's network connectivity.")
                AccessRow(
                    icon = Icons.Filled.Wifi,
                    tint = colors.wifi,
                    title = "Wi-Fi",
                    subtitle = "Allow this app to access internet via Wi-Fi.",
                    checked = wifi,
                    onCheckedChange = { viewModel.setWifiAllowed(it, mobile) },
                )
                AccessRow(
                    icon = Icons.Filled.SignalCellularAlt,
                    tint = colors.mobile,
                    title = "Mobile Data",
                    subtitle = "Allow this app to access internet via mobile data.",
                    checked = mobile,
                    onCheckedChange = { viewModel.setMobileDataAllowed(wifi, it) },
                )
            }
        }

        // Internet Access status
        val state = rule.accessState
        val style = state.style()
        NetLockerCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(Icons.Filled.Language, colors.wifi, "Internet Access", "Current network access status for this app.")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(style.container)
                        .border(1.dp, style.color.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(style.icon, contentDescription = null, tint = style.color, modifier = Modifier.size(38.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(style.label, color = style.color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text(state.description(), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        allowedStyle(wifi).let {
                            StatusPill("Wi-Fi · ${if (wifi) "Allowed" else "Blocked"}", it.color, it.container, icon = Icons.Filled.Wifi)
                        }
                        allowedStyle(mobile).let {
                            StatusPill("Mobile Data · ${if (mobile) "Allowed" else "Blocked"}", it.color, it.container, icon = Icons.Filled.SignalCellularAlt)
                        }
                    }
                }
                if (!firewall.isActive && state != NetworkAccessState.ALLOWED) {
                    Text(
                        "The firewall is off, so this rule is saved but not enforced yet.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun NetworkAccessState.description(): String = when (this) {
    NetworkAccessState.ALLOWED -> "This app can access the internet."
    NetworkAccessState.BLOCKED -> "This app has no internet access."
    NetworkAccessState.WIFI_ONLY -> "This app can only use Wi-Fi."
    NetworkAccessState.MOBILE_ONLY -> "This app can only use mobile data."
}

@Composable
private fun SectionHeader(icon: ImageVector, tint: Color, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(42.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AccessRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(1.dp, MaterialTheme.netLocker.cardBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(46.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { contentDescription = "$title access" },
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary, checkedThumbColor = Color.White),
        )
    }
}
