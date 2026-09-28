package com.netlocker.ui.rules

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.netlocker.domain.model.RuleFilter
import com.netlocker.domain.model.activeRulesLabel
import com.netlocker.domain.usecase.RuleWithApp
import com.netlocker.ui.components.AppListLoading
import com.netlocker.ui.components.EmptyState
import com.netlocker.ui.components.FirewallStatusBanner
import com.netlocker.ui.components.NetLockerChip
import com.netlocker.ui.components.NetLockerSearchField
import com.netlocker.ui.components.StatusPill
import com.netlocker.ui.components.rememberFirewallActions
import com.netlocker.ui.theme.netLocker

@Composable
fun RulesScreen(
    viewModel: RulesViewModel = viewModel(factory = RulesViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsState()
    val firewall = rememberFirewallActions()
    val context = LocalContext.current

    var editing by remember { mutableStateOf<RuleWithApp?>(null) }
    var deleting by remember { mutableStateOf<RuleWithApp?>(null) }
    var adding by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Rules", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(10.dp))
            StatusPill(
                text = activeRulesLabel(state.activeCount),
                color = MaterialTheme.colorScheme.primary,
                container = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
            )
            Spacer(Modifier.weight(1f))
            Button(onClick = { adding = true }, shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Add Rule", fontWeight = FontWeight.SemiBold)
            }
        }

        // Only the title row stays fixed. Everything else is part of one list so that,
        // especially with a large system font, the banner/filters/search scroll away and
        // the rules get the whole screen instead of the bottom half.
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "firewall-banner") { FirewallStatusBanner(firewall) }

            if (!state.isLoading && state.totalCount > 0) {
                item(key = "filters") { FilterRow(state.filter, state.counts, viewModel::onFilterSelected) }
                item(key = "search") {
                    NetLockerSearchField(
                        query = state.query,
                        onQueryChange = viewModel::onQueryChange,
                        placeholder = "Search rules...",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            when {
                state.isLoading -> item(key = "loading") { AppListLoading(message = "Loading your rules…", rows = 4) }
                state.totalCount == 0 -> item(key = "empty-all") {
                    EmptyState(
                        icon = Icons.Filled.Shield,
                        title = "No rules yet",
                        message = "You haven't created any network rules.",
                        actionLabel = "+ Add Rule",
                        onAction = { adding = true },
                    )
                }
                state.rules.isEmpty() -> item(key = "empty-filter") {
                    val (title, message) = emptyFilterText(state.filter, state.query)
                    EmptyState(icon = Icons.Filled.Search, title = title, message = message)
                }
                else -> items(state.rules, key = { it.rule.packageName }) { item ->
                    RuleCard(
                        item = item,
                        firewallActive = firewall.isActive,
                        onEdit = { editing = item },
                        onToggleEnabled = { viewModel.setEnabled(item.rule.packageName, !item.rule.isEnabled) },
                        onDelete = { deleting = item },
                        onOpenAppInfo = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", item.rule.packageName, null))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                    )
                }
            }
        }
    }

    editing?.let { item ->
        EditRuleDialog(
            item = item,
            onDismiss = { editing = null },
            onSave = { wifi, mobile ->
                viewModel.saveRule(item.rule.packageName, wifi, mobile)
                editing = null
            },
        )
    }
    deleting?.let { item ->
        DeleteRuleDialog(
            item = item,
            onDismiss = { deleting = null },
            onConfirm = {
                viewModel.deleteRule(item.rule.packageName)
                deleting = null
            },
        )
    }
    if (adding) {
        AddRuleSheet(
            apps = state.addableApps,
            onDismiss = { adding = false },
            onSave = viewModel::saveRule,
        )
    }
}

@Composable
private fun FilterRow(
    selected: RuleFilter,
    counts: com.netlocker.domain.model.RuleCounts,
    onSelect: (RuleFilter) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val chips = listOf(
            Triple(RuleFilter.ALL, "All", Icons.Filled.Apps),
            Triple(RuleFilter.BLOCKED, "Blocked", Icons.Filled.Block),
            Triple(RuleFilter.WIFI_ONLY, "Wi-Fi Only", Icons.Filled.Wifi),
            Triple(RuleFilter.MOBILE_ONLY, "Mobile Only", Icons.Filled.SignalCellularAlt),
            Triple(RuleFilter.ALLOWED, "Allowed", Icons.Filled.CheckCircle),
        )
        chips.forEach { (filter, label, icon) ->
            NetLockerChip(
                label = label,
                icon = icon,
                count = counts.of(filter),
                selected = selected == filter,
                onClick = { onSelect(filter) },
            )
        }
    }
}

private fun emptyFilterText(filter: RuleFilter, query: String): Pair<String, String> = when {
    query.isNotBlank() -> "No matching rules" to "No rule matches \"${query.trim()}\"."
    else -> when (filter) {
        RuleFilter.BLOCKED -> "No blocked apps" to "There are no apps blocked by NetLocker."
        RuleFilter.WIFI_ONLY -> "No Wi-Fi only apps" to "No app is restricted to Wi-Fi only."
        RuleFilter.MOBILE_ONLY -> "No mobile only apps" to "No app is restricted to mobile data only."
        RuleFilter.ALLOWED -> "No allowed rules" to "No app has an explicit \"allowed\" rule."
        RuleFilter.ALL -> "No rules yet" to "You haven't created any network rules."
    }
}
