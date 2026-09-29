package com.netlocker.ui.appdetails

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.netlocker.domain.model.NetworkAccessState
import com.netlocker.domain.model.connectionsBlockedTodayLabel
import com.netlocker.domain.model.dnsBlockedTodayLabel
import com.netlocker.ui.components.AppIconImage
import com.netlocker.ui.components.CircleIconButton
import com.netlocker.ui.components.NetLockerCard
import com.netlocker.ui.components.StatusPill
import com.netlocker.ui.components.rememberFirewallActions
import com.netlocker.ui.rules.ScheduleForm
import com.netlocker.ui.theme.netLocker
import com.netlocker.util.formatBytes

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
        verticalArrangement = Arrangement.spacedBy(10.dp),
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
                overflow = TextOverflow.Ellipsis,
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

        // Compact hero: icon beside the name, instead of a tall centred block.
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, colors.cardBorderStrong, RoundedCornerShape(16.dp))
                    .background(colors.card)
                    .padding(6.dp),
            ) { AppIconImage(current.app.icon, size = 48.dp, cornerRadius = 12.dp) }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(current.app.label, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    current.app.packageName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            current.app.versionName?.let {
                StatusPill("v$it", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
            }
        }

        // Rules membership: one tap adds the app to the Rules tab; once added it says so.
        if (current.hasRule) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.allowedContainer)
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = colors.allowed, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("In your Rules", color = colors.allowed, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = viewModel::resetToDefault) { Text("Remove", fontSize = 13.sp) }
            }
        } else {
            Button(
                onClick = { viewModel.addToRules(wifi, mobile) },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add to Rules", fontWeight = FontWeight.SemiBold)
            }
        }

        // Network Access — the one place this app's Wi-Fi/Mobile access is shown; a
        // separate "Internet Access" status card used to repeat the very same Allowed/
        // Blocked state again in a bigger box, which added length without adding anything.
        val state = rule.accessState
        NetLockerCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(Icons.Filled.Wifi, colors.wifi, "Network Access")
                AccessRow(
                    icon = Icons.Filled.Wifi,
                    tint = colors.wifi,
                    title = "Wi-Fi",
                    checked = wifi,
                    onCheckedChange = { viewModel.setWifiAllowed(it, mobile) },
                )
                AccessRow(
                    icon = Icons.Filled.SignalCellularAlt,
                    tint = colors.mobile,
                    title = "Mobile Data",
                    checked = mobile,
                    onCheckedChange = { viewModel.setMobileDataAllowed(wifi, it) },
                )
                if (!firewall.isActive && state != NetworkAccessState.ALLOWED) {
                    Text(
                        "The firewall is off, so this rule is saved but not enforced yet.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Schedule — only shown once the user has turned it on in Settings.
        val scheduleMasterEnabled by viewModel.scheduleMasterEnabled.collectAsState()
        if (scheduleMasterEnabled) {
            var scheduleEnabled by remember(rule.packageName) { mutableStateOf(rule.scheduleEnabled) }
            var scheduleStart by remember(rule.packageName) { mutableStateOf(rule.scheduleStartMinute) }
            var scheduleEnd by remember(rule.packageName) { mutableStateOf(rule.scheduleEndMinute) }
            var scheduleDays by remember(rule.packageName) { mutableStateOf(rule.scheduleDays) }
            NetLockerCard(modifier = Modifier.fillMaxWidth()) {
                ScheduleForm(
                    enabled = scheduleEnabled,
                    startMinute = scheduleStart,
                    endMinute = scheduleEnd,
                    days = scheduleDays,
                    onEnabledChange = {
                        scheduleEnabled = it
                        viewModel.setSchedule(it, scheduleStart, scheduleEnd, scheduleDays)
                    },
                    onStartChange = {
                        scheduleStart = it
                        viewModel.setSchedule(scheduleEnabled, it, scheduleEnd, scheduleDays)
                    },
                    onEndChange = {
                        scheduleEnd = it
                        viewModel.setSchedule(scheduleEnabled, scheduleStart, it, scheduleDays)
                    },
                    onDaysChange = {
                        scheduleDays = it
                        viewModel.setSchedule(scheduleEnabled, scheduleStart, scheduleEnd, it)
                    },
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        // Blocked attempts today — only meaningful for an app the rules actually restrict.
        val blocked by viewModel.blockedToday.collectAsState()
        if (!rule.isEffectivelyOpen) {
            val count = blocked?.count ?: 0
            val showDestinations by viewModel.showBlockedDestinations.collectAsState()
            var eventsExpanded by remember(rule.packageName) { mutableStateOf(false) }
            val events by viewModel.recentBlockedEvents.collectAsState()

            NetLockerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Block, contentDescription = null, tint = colors.blocked, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Blocked today", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    }
                    if (count == 0) {
                        Text(
                            if (!firewall.isActive) "The firewall is off, so nothing is being blocked." else "No connection attempts blocked yet today.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        val connectionCount = blocked?.connectionCount ?: 0
                        val dnsCount = blocked?.dnsCount ?: 0
                        if (connectionCount > 0) {
                            BlockedCountRow(Icons.Filled.Block, connectionsBlockedTodayLabel(connectionCount))
                        }
                        if (dnsCount > 0) {
                            BlockedCountRow(Icons.Filled.Search, dnsBlockedTodayLabel(dnsCount))
                        }
                        blocked?.lastBlockedAt?.let {
                            Text("Last at ${formatClock(it)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (showDestinations) {
                            TextButton(
                                onClick = { eventsExpanded = !eventsExpanded },
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Text(
                                    if (eventsExpanded) "Hide recent attempts" else "View recent attempts",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            if (eventsExpanded) {
                                if (events.isEmpty()) {
                                    Text(
                                        "No destinations logged yet.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        events.forEach { event ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                            ) {
                                                Text(
                                                    event.destination,
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f),
                                                )
                                                Text(
                                                    formatClock(event.atMillis),
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Today's usage (internet)
        val usage by viewModel.usage.collectAsState()
        var refreshKey by remember { mutableIntStateOf(0) }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refreshKey++ }
        LaunchedEffect(current.app.uid, refreshKey) { viewModel.loadUsage(current.app.uid) }

        NetLockerCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(Icons.Filled.DataUsage, colors.mobile, "Today's usage")
                when (val u = usage) {
                    UsageUiState.Loading -> Box(modifier = Modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                    UsageUiState.NeedsAccess -> {
                        Text(
                            "Allow \"Usage access\" in Android settings to see how much data this app used. " +
                                "Nothing leaves your phone.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { context.startActivity(viewModel.usageAccessSettingsIntent()) },
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        ) { Text("Allow usage access", fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
                    }
                    UsageUiState.Unavailable -> Text(
                        "Data usage couldn't be read on this device right now.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    is UsageUiState.Loaded -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            UsageCell(Icons.Filled.Wifi, colors.wifi, "Wi-Fi", formatBytes(u.usage.wifiBytes), Modifier.weight(1f))
                            UsageCell(Icons.Filled.SignalCellularAlt, colors.mobile, "Mobile", formatBytes(u.usage.mobileBytes), Modifier.weight(1f))
                        }
                        Text("Total today: ${formatBytes(u.usage.totalBytes)}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }

        // Battery — Android doesn't expose other apps' battery use to apps.
        NetLockerCard(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.BatteryStd, contentDescription = null, tint = colors.allowed, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Battery", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        "Android doesn't share other apps' battery use with apps.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(36.dp),
                ) { Text("Open", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, softWrap = false) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, tint: Color, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(30.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(10.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun BlockedCountRow(icon: ImageVector, label: String) {
    val colors = MaterialTheme.netLocker
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = colors.blocked, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun UsageCell(icon: ImageVector, tint: Color, title: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
        }
    }
}

@Composable
private fun AccessRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(start = 10.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { contentDescription = "$title access" },
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary, checkedThumbColor = Color.White),
        )
    }
}

private fun formatClock(epochMillis: Long): String =
    java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(epochMillis))
