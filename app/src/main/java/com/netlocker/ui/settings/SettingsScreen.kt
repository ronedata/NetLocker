package com.netlocker.ui.settings

import android.content.Intent
import android.os.Build
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
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
import com.netlocker.util.TextSize

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

        // Text size
        SettingsCard(icon = Icons.Filled.TextFields, title = "Text size", subtitle = "Make NetLocker's text smaller or larger") {
            Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextSize.entries.forEach { size ->
                    val (label, description) = when (size) {
                        TextSize.SMALL -> "Small" to "Compact text"
                        TextSize.DEFAULT -> "Default" to "Recommended"
                        TextSize.LARGE -> "Large" to "Bigger text"
                        TextSize.SYSTEM -> "Follow system" to "Use the phone's font size setting"
                    }
                    ChoiceOption(
                        label = label,
                        description = description,
                        selected = uiState.textSize == size,
                        onSelect = { viewModel.setTextSize(size) },
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

        // Keeping the firewall running: boot auto-start, Android's Always-on VPN, and the tile.
        SwitchCard(
            icon = Icons.Filled.PowerSettingsNew,
            title = "Start when phone turns on",
            subtitle = "Turn the firewall on automatically after a restart or after NetLocker updates. " +
                "Uses the VPN permission you already gave.",
            checked = uiState.autoStartOnBoot,
            onCheckedChange = viewModel::setAutoStartOnBoot,
        )
        SettingsCard(
            icon = Icons.Filled.VpnLock,
            title = "Always-on VPN",
            subtitle = "Android can keep a VPN app running for you. Pick NetLocker in Android's VPN settings " +
                "(its optional \"Block connections without VPN\" is stricter: no internet at all while the firewall is off).",
        ) {
            ClickRow(
                icon = Icons.Filled.Settings,
                title = "Open VPN settings",
                subtitle = "",
                enabled = true,
                onClick = { context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
            )
        }
        SettingsCard(
            icon = Icons.Filled.Tune,
            title = "Quick Settings tile",
            subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                "Turn the firewall on/off from the notification shade."
            } else {
                "Swipe down twice, tap the pencil (edit) icon, and drag the Firewall tile into your panel."
            },
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ClickRow(
                    icon = Icons.Filled.Add,
                    title = "Add tile",
                    subtitle = "",
                    enabled = true,
                    onClick = { requestAddTile(context) },
                )
            }
        }

        SwitchCard(
            icon = Icons.Filled.Schedule,
            title = "Schedule",
            subtitle = "Let apps be blocked during a time window you pick (e.g. overnight). Off by default " +
                "— turning it on adds a \"Schedule\" option to each app's rule.",
            checked = uiState.scheduleMasterEnabled,
            onCheckedChange = viewModel::setScheduleMasterEnabled,
        )

        SettingsCard(icon = Icons.Filled.VerifiedUser, title = "Network method", subtitle = "Detected automatically — VPN-based local firewall (no root required)")

        // About + updates
        SettingsCard(icon = Icons.Filled.Info, title = "About NetLocker", subtitle = "App information and updates") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow(icon = Icons.Filled.Lock, title = "Version", value = BuildConfig.VERSION_NAME)
                Button(
                    onClick = viewModel::checkForUpdate,
                    enabled = updateState !is UpdateUiState.Checking && updateState !is UpdateUiState.Downloading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Filled.Update, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Check for updates", fontWeight = FontWeight.SemiBold)
                }
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
private fun ChoiceOption(label: String, description: String, selected: Boolean, onSelect: () -> Unit) {
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
        UpdateUiState.Checking -> StatusLine(busy = true, text = "Checking for updates…")
        UpdateUiState.Downloading -> StatusLine(busy = true, text = "Downloading update…")
        UpdateUiState.UpToDate -> StatusLine(
            icon = Icons.Filled.CheckCircle,
            text = "No update available — you're on the latest version.",
            color = MaterialTheme.netLocker.allowed,
        )
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

/** Asks Android to add the Firewall tile to Quick Settings (Android 13+). The system shows
 *  its own confirmation; NetLocker can't add it silently. */
@androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun requestAddTile(context: android.content.Context) {
    val statusBar = context.getSystemService(android.app.StatusBarManager::class.java) ?: return
    statusBar.requestAddTileService(
        android.content.ComponentName(context, com.netlocker.tile.FirewallTileService::class.java),
        context.getString(com.netlocker.R.string.tile_label),
        android.graphics.drawable.Icon.createWithResource(context, com.netlocker.R.drawable.ic_tile_firewall),
        context.mainExecutor,
    ) { }
}
