package com.netlocker.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.netlocker.BuildConfig
import com.netlocker.ui.components.CircleIconButton
import com.netlocker.ui.components.NetLockerCard
import com.netlocker.ui.theme.netLocker
import com.netlocker.util.AppTheme

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val context = LocalContext.current

    val installPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.onReturnedFromInstallPermissionScreen() }

    LaunchedEffect(Unit) {
        viewModel.installIntentRequests.collect { intent -> context.startActivity(intent) }
    }

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
            CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack, size = 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                Text("Customize NetLocker to your needs", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp)) }
        }

        // Theme
        SettingsCard(icon = Icons.Filled.Palette, title = "Theme", subtitle = "Choose your preferred appearance") {
            Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTheme.entries.forEach { theme ->
                    ThemeOption(
                        theme = theme,
                        selected = uiState.theme == theme,
                        onSelect = { viewModel.setTheme(theme) },
                    )
                }
            }
        }

        SwitchCard(
            icon = Icons.Filled.Apps,
            title = "Show system apps",
            subtitle = "Include Android system apps in the app list",
            checked = uiState.showSystemApps,
            onCheckedChange = viewModel::setShowSystemApps,
        )
        SwitchCard(
            icon = Icons.Filled.Sync,
            title = "Auto refresh apps",
            subtitle = "Automatically refresh the installed apps list",
            checked = uiState.autoRefresh,
            onCheckedChange = viewModel::setAutoRefresh,
        )

        SettingsCard(
            icon = Icons.Filled.Notifications,
            title = "Minimal notification",
            subtitle = "Android requires a notification while the firewall runs. This makes it silent and " +
                "hidden on the lock screen. Android may still show a small status-bar icon for it — " +
                "you can hide that in Notification settings. The system's 🔑 VPN indicator can't be hidden by any app.",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Use minimal notification", modifier = Modifier.weight(1f).padding(end = 12.dp), fontWeight = FontWeight.SemiBold)
                    Switch(
                        checked = uiState.minimalNotification,
                        onCheckedChange = viewModel::setMinimalNotification,
                        colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary, checkedThumbColor = Color.White),
                    )
                }
                ClickRow(
                    icon = Icons.Filled.Settings,
                    title = "Notification settings",
                    subtitle = "Open Android's notification settings for NetLocker",
                    enabled = true,
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    },
                )
            }
        }

        SettingsCard(icon = Icons.Filled.VerifiedUser, title = "Network method", subtitle = "Detected automatically — VPN-based local firewall (no root required)")

        // About + updates
        SettingsCard(icon = Icons.Filled.Info, title = "About NetLocker", subtitle = "App information and updates") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow(icon = Icons.Filled.Lock, title = "Version", value = BuildConfig.VERSION_NAME)
                ClickRow(
                    icon = Icons.Filled.Update,
                    title = "Check for updates",
                    subtitle = "NetLocker isn't on Play Store — updates are checked against GitHub Releases",
                    enabled = updateState !is UpdateUiState.Checking && updateState !is UpdateUiState.Downloading,
                    onClick = viewModel::checkForUpdate,
                )
                UpdateStatus(
                    state = updateState,
                    onUpdate = viewModel::startUpdate,
                    onGrantPermission = { installPermissionLauncher.launch(viewModel.requestInstallPermissionIntent()) },
                )
            }
        }
        Spacer(Modifier.width(1.dp))
    }
}

@Composable
private fun SettingsCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    NetLockerCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content?.invoke(this)
        }
    }
}

@Composable
private fun SwitchCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    NetLockerCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary, checkedThumbColor = Color.White),
            )
        }
    }
}

@Composable
private fun IconBadge(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.primary) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp)) }
}

@Composable
private fun ThemeOption(theme: AppTheme, selected: Boolean, onSelect: () -> Unit) {
    val (icon, label, description) = when (theme) {
        AppTheme.SYSTEM -> Triple(Icons.Filled.PhoneAndroid, "System", "Use system default theme")
        AppTheme.LIGHT -> Triple(Icons.Filled.LightMode, "Light", "Use light theme")
        AppTheme.DARK -> Triple(Icons.Filled.DarkMode, "Dark", "Use dark theme")
    }
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.netLocker.cardBorder
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ClickRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
    highlight: Boolean = false,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(1.dp, if (highlight) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.netLocker.allowed, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Mirrors exactly what the GitHub check / download actually returned — never assumed. */
@Composable
private fun UpdateStatus(
    state: UpdateUiState,
    onUpdate: () -> Unit,
    onGrantPermission: () -> Unit,
) {
    when (state) {
        UpdateUiState.Idle -> Unit
        UpdateUiState.Checking -> StatusLine(busy = true, text = "Checking GitHub for a newer release…")
        UpdateUiState.Downloading -> StatusLine(busy = true, text = "Downloading update…")
        UpdateUiState.UpToDate -> StatusLine(text = "You're on the latest version.", color = MaterialTheme.netLocker.allowed)
        is UpdateUiState.Error -> StatusLine(icon = Icons.Filled.Error, text = state.message, color = MaterialTheme.colorScheme.error)
        is UpdateUiState.Available -> ClickRow(
            icon = Icons.Filled.Download,
            title = "Version ${state.update.versionName} is available.",
            subtitle = "Tap to download and install",
            enabled = true,
            onClick = onUpdate,
            highlight = true,
        )
        UpdateUiState.NeedsInstallPermission -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "NetLocker needs your permission to install an update it downloads itself " +
                    "(Android asks this of any app outside Play Store).",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ClickRow(
                icon = Icons.Filled.Lock,
                title = "Grant Permission",
                subtitle = "",
                enabled = true,
                onClick = onGrantPermission,
                highlight = true,
            )
        }
    }
}

@Composable
private fun StatusLine(
    text: String,
    busy: Boolean = false,
    icon: ImageVector? = null,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, color = color, fontSize = 14.sp)
    }
}
