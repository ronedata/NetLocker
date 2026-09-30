package com.netlocker.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.netlocker.domain.model.AppWithRule
import com.netlocker.ui.components.AppIconImage
import com.netlocker.ui.components.AppListLoading
import com.netlocker.ui.components.CircleIconButton
import com.netlocker.ui.components.EmptyState
import com.netlocker.ui.components.FirewallStatusBanner
import com.netlocker.ui.components.NetLockerCard
import com.netlocker.ui.components.NetLockerChip
import com.netlocker.ui.components.NetLockerSearchField
import com.netlocker.ui.components.NetLockerWordmark
import com.netlocker.ui.components.NetworkToggle
import com.netlocker.ui.components.StatusPill
import com.netlocker.ui.components.allowedStyle
import com.netlocker.ui.components.rememberFirewallActions
import com.netlocker.ui.theme.netLocker

@Composable
fun AppsScreen(
    onOpenAppDetails: (String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: AppsViewModel = viewModel(factory = AppsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsState()
    val needsUsageAccess by viewModel.needsUsageAccessPrompt.collectAsState()
    val firewall = rememberFirewallActions()
    val context = LocalContext.current
    var overflowOpen by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumeCheckUsageAccess() }

    if (needsUsageAccess) {
        AlertDialog(
            onDismissRequest = viewModel::cancelUsageSort,
            title = { Text("Usage access needed") },
            text = {
                Text(
                    "Allow \"Usage access\" in Android settings to sort apps by data used today. " +
                        "Nothing leaves your phone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    context.startActivity(viewModel.usageAccessSettingsIntent())
                    viewModel.dismissUsageAccessPrompt()
                }) { Text("Open settings") }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelUsageSort) { Text("Cancel") } },
        )
    }

    // Header, firewall banner, category chips and search stay fixed at the top; only the
    // app list below them scrolls.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        AppsHeader(
            onSettings = onOpenSettings,
            overflow = {
                Box {
                    CircleIconButton(Icons.Filled.MoreVert, "More options", onClick = { overflowOpen = true })
                    DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Refresh apps") },
                            onClick = { overflowOpen = false; viewModel.refreshApps() },
                        )
                    }
                }
            },
        )

        Column(
            modifier = Modifier.padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FirewallStatusBanner(firewall)

            CategoryChips(
                selected = state.category,
                counts = state.counts,
                // Counts would read "0" while the first scan is still running — hide them.
                showCounts = !state.isLoading,
                onSelect = viewModel::onCategorySelected,
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NetLockerSearchField(
                    query = state.query,
                    onQueryChange = viewModel::onQueryChange,
                    placeholder = "Search apps...",
                    modifier = Modifier.weight(1f),
                )
                Box {
                    CircleIconButton(Icons.Filled.Tune, "Sort apps", onClick = { sortOpen = true }, size = 52.dp)
                    DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (state.sort == AppSort.NAME) "✓ Name (A–Z)" else "Name (A–Z)") },
                            onClick = { sortOpen = false; viewModel.onSortSelected(AppSort.NAME) },
                        )
                        DropdownMenuItem(
                            text = { Text(if (state.sort == AppSort.NAME_DESC) "✓ Name (Z–A)" else "Name (Z–A)") },
                            onClick = { sortOpen = false; viewModel.onSortSelected(AppSort.NAME_DESC) },
                        )
                        DropdownMenuItem(
                            text = { Text(if (state.sort == AppSort.RESTRICTED_FIRST) "✓ Restricted first" else "Restricted first") },
                            onClick = { sortOpen = false; viewModel.onSortSelected(AppSort.RESTRICTED_FIRST) },
                        )
                        DropdownMenuItem(
                            text = { Text(if (state.sort == AppSort.DATA_USAGE_DESC) "✓ Most data used" else "Most data used") },
                            onClick = { sortOpen = false; viewModel.onSortSelected(AppSort.DATA_USAGE_DESC) },
                        )
                        DropdownMenuItem(
                            text = { Text(if (state.sort == AppSort.DATA_USAGE_ASC) "✓ Least data used" else "Least data used") },
                            onClick = { sortOpen = false; viewModel.onSortSelected(AppSort.DATA_USAGE_ASC) },
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 10.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                state.isLoading -> item(key = "loading") { AppListLoading() }
                state.apps.isEmpty() -> item(key = "empty") {
                    EmptyState(
                        icon = Icons.Filled.Search,
                        title = "No apps found",
                        message = "Try a different search or category.",
                    )
                }
                else -> items(state.apps, key = { it.app.packageName }) { item ->
                    AppRow(
                        item = item,
                        onClick = { onOpenAppDetails(item.app.packageName) },
                        onWifiToggle = { viewModel.setWifiAllowed(item.app.packageName, it, item.rule.effectiveMobileDataAllowed) },
                        onMobileToggle = { viewModel.setMobileDataAllowed(item.app.packageName, item.rule.effectiveWifiAllowed, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppsHeader(
    onSettings: () -> Unit,
    overflow: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Shield, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            NetLockerWordmark()
            Text(
                "Control internet access for your apps",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CircleIconButton(Icons.Filled.Settings, "Open settings", onClick = onSettings)
        overflow()
    }
}

@Composable
private fun CategoryChips(
    selected: AppCategoryFilter,
    counts: Map<AppCategoryFilter, Int>,
    showCounts: Boolean,
    onSelect: (AppCategoryFilter) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val chips = listOf(
            Triple(AppCategoryFilter.ALL, "All", Icons.Filled.Apps),
            Triple(AppCategoryFilter.GAMES, "Games", Icons.Filled.SportsEsports),
            Triple(AppCategoryFilter.SOCIAL, "Social", Icons.Filled.Groups),
            Triple(AppCategoryFilter.SYSTEM, "System", Icons.Filled.Android),
        )
        chips.forEach { (filter, label, icon) ->
            NetLockerChip(
                label = label,
                icon = icon,
                count = if (showCounts) counts[filter] ?: 0 else null,
                selected = selected == filter,
                onClick = { onSelect(filter) },
            )
        }
    }
}

@Composable
private fun AppRow(
    item: AppWithRule,
    onClick: () -> Unit,
    onWifiToggle: (Boolean) -> Unit,
    onMobileToggle: (Boolean) -> Unit,
) {
    val app = item.app
    val rule = item.rule
    val wifi = rule.effectiveWifiAllowed
    val mobile = rule.effectiveMobileDataAllowed
    val colors = MaterialTheme.netLocker

    NetLockerCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        // Two rows so the layout survives large system font sizes (this was tuned on a
        // phone set to 130% font scale): identity + switches on top, status pills below
        // with the full card width available.
        Column(modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIconImage(app.icon, size = 44.dp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(app.label, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        app.packageName,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                NetworkToggle(
                    icon = Icons.Filled.Wifi,
                    iconTint = colors.wifi,
                    checked = wifi,
                    onCheckedChange = onWifiToggle,
                    contentDescription = "Wi-Fi access for ${app.label}",
                )
                NetworkToggle(
                    icon = Icons.Filled.SignalCellularAlt,
                    iconTint = colors.mobile,
                    checked = mobile,
                    onCheckedChange = onMobileToggle,
                    contentDescription = "Mobile data access for ${app.label}",
                )
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Row(
                modifier = Modifier.padding(start = 54.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                allowedStyle(wifi).let { StatusPill("Wi-Fi: ${if (wifi) "On" else "Off"}", it.color, it.container) }
                allowedStyle(mobile).let { StatusPill("Mobile: ${if (mobile) "On" else "Off"}", it.color, it.container) }
            }
        }
    }
}
