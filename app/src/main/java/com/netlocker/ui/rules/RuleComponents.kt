package com.netlocker.ui.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.Block
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netlocker.domain.model.InstalledApp
import com.netlocker.domain.model.NetworkRule
import com.netlocker.domain.model.RuleStatus
import com.netlocker.domain.usecase.RuleWithApp
import com.netlocker.ui.components.AppIconImage
import com.netlocker.ui.components.CircleIconButton
import com.netlocker.ui.components.NetLockerCard
import com.netlocker.ui.components.NetLockerSearchField
import com.netlocker.ui.components.StatusPill
import com.netlocker.ui.components.allowedStyle
import com.netlocker.ui.components.style
import com.netlocker.ui.theme.netLocker

/**
 * One rule, kept compact so many fit on a screen. [firewallActive] drives the
 * "Enforced" / "Not enforced" line — it reflects the real VPN state, so a saved rule
 * is never presented as enforced while the firewall is off.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RuleCard(
    item: RuleWithApp,
    firewallActive: Boolean,
    onEdit: () -> Unit,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit,
    onOpenAppInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rule = item.rule
    val status = rule.status
    val style = status.style()
    val colors = MaterialTheme.netLocker
    var menuOpen by remember { mutableStateOf(false) }
    val disabled = status == RuleStatus.DISABLED

    NetLockerCard(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (disabled) 0.62f else 1f),
        borderColor = style.color.copy(alpha = 0.32f),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            AppIconImage(item.app?.icon, size = 48.dp, cornerRadius = 12.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.displayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(6.dp))
                    StatusPill(style.label, style.color, style.container, icon = style.icon)
                }
                Text(
                    rule.packageName,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                // FlowRow: with a large system font the two pills no longer fit side by
                // side, and clipping "Mobile Data: Blocked" would hide the very status shown.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TransportPill("Wi-Fi", Icons.Filled.Wifi, rule.wifiAllowed, disabled)
                    TransportPill("Mobile Data", Icons.Filled.SignalCellularAlt, rule.mobileDataAllowed, disabled)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EnforcementLabel(disabled = disabled, active = firewallActive, modifier = Modifier.weight(1f))
                    CircleIconButton(
                        icon = Icons.Filled.Edit,
                        contentDescription = "Edit rule for ${item.displayName}",
                        onClick = onEdit,
                        size = 36.dp,
                        tint = colors.wifi,
                    )
                    Box {
                        CircleIconButton(
                            icon = Icons.Filled.MoreVert,
                            contentDescription = "More options for ${item.displayName}",
                            onClick = { menuOpen = true },
                            size = 36.dp,
                        )
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Edit Rule") },
                                leadingIcon = { Icon(Icons.Filled.Edit, null) },
                                onClick = { menuOpen = false; onEdit() },
                            )
                            DropdownMenuItem(
                                text = { Text(if (rule.isEnabled) "Disable Rule" else "Enable Rule") },
                                leadingIcon = {
                                    Icon(if (rule.isEnabled) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle, null)
                                },
                                onClick = { menuOpen = false; onToggleEnabled() },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Rule", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { menuOpen = false; onDelete() },
                            )
                            DropdownMenuItem(
                                text = { Text("Open App Info") },
                                leadingIcon = { Icon(Icons.Filled.Info, null) },
                                onClick = { menuOpen = false; onOpenAppInfo() },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransportPill(name: String, icon: ImageVector, allowed: Boolean, ruleDisabled: Boolean) {
    val style = if (ruleDisabled) RuleStatus.DISABLED.style() else allowedStyle(allowed)
    val value = when {
        ruleDisabled -> "Default"
        allowed -> "Allowed"
        else -> "Blocked"
    }
    StatusPill("$name: $value", style.color, style.container, icon = icon)
}

@Composable
private fun EnforcementLabel(disabled: Boolean, active: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.netLocker
    val (text, icon, tint) = when {
        disabled -> Triple("Rule paused", Icons.Filled.PauseCircle, colors.disabled)
        active -> Triple("Enforced", Icons.Filled.CheckCircle, colors.allowed)
        else -> Triple("Not enforced", Icons.Filled.RadioButtonUnchecked, MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, fontSize = 12.sp, color = tint, fontWeight = FontWeight.Medium)
    }
}

/** The two switches shared by the edit dialog and the create-rule step. */
@Composable
fun RuleForm(
    wifiAllowed: Boolean,
    mobileDataAllowed: Boolean,
    onWifiChange: (Boolean) -> Unit,
    onMobileChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.netLocker
    val preview = NetworkRule("", wifiAllowed, mobileDataAllowed).status.style()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Network Access", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        FormSwitchRow(Icons.Filled.Wifi, colors.wifi, "Wi-Fi", wifiAllowed, onWifiChange)
        FormSwitchRow(Icons.Filled.SignalCellularAlt, colors.mobile, "Mobile Data", mobileDataAllowed, onMobileChange)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Rule status", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            StatusPill(preview.label, preview.color, preview.container, icon = preview.icon)
        }
    }
}

@Composable
private fun FormSwitchRow(
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
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                if (checked) "Allowed" else "Blocked",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { contentDescription = "$title access" },
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary, checkedThumbColor = Color.White),
        )
    }
}

@Composable
fun EditRuleDialog(
    item: RuleWithApp,
    onDismiss: () -> Unit,
    onSave: (wifiAllowed: Boolean, mobileDataAllowed: Boolean) -> Unit,
) {
    var wifi by remember(item.rule.packageName) { mutableStateOf(item.rule.wifiAllowed) }
    var mobile by remember(item.rule.packageName) { mutableStateOf(item.rule.mobileDataAllowed) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.netLocker.card,
        title = { Text("Edit Rule", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                AppHeaderRow(item.app, item.displayName, item.rule.packageName)
                RuleForm(wifi, mobile, { wifi = it }, { mobile = it })
            }
        },
        confirmButton = { Button(onClick = { onSave(wifi, mobile) }) { Text("Save Rule") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun DeleteRuleDialog(item: RuleWithApp, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.netLocker.card,
        title = { Text("Delete Rule?", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "Remove the custom network rule for ${item.displayName}? " +
                    "The app goes back to its default network access. This only removes NetLocker's rule — the app itself is not affected.",
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text("Delete", color = Color.White) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AppHeaderRow(app: InstalledApp?, name: String, packageName: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppIconImage(app?.icon, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(packageName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * "Add Rule" flow in one bottom sheet: pick an installed app (only apps without a rule
 * are offered), then choose its Wi-Fi/Mobile Data access and save.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRuleSheet(
    apps: List<InstalledApp>,
    onDismiss: () -> Unit,
    onSave: (packageName: String, wifiAllowed: Boolean, mobileDataAllowed: Boolean) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selected by remember { mutableStateOf<InstalledApp?>(null) }
    var query by remember { mutableStateOf("") }
    var wifi by remember { mutableStateOf(true) }
    var mobile by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.netLocker.card,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val app = selected
            if (app == null) {
                Text("Select App", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                NetLockerSearchField(query, { query = it }, "Search apps...", Modifier.fillMaxWidth())
                val filtered = remember(apps, query) {
                    apps.filter {
                        query.isBlank() ||
                            it.label.contains(query.trim(), ignoreCase = true) ||
                            it.packageName.contains(query.trim(), ignoreCase = true)
                    }
                }
                if (filtered.isEmpty()) {
                    Text(
                        if (apps.isEmpty()) "Every installed app already has a rule." else "No matching apps.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                } else {
                    LazyColumn(modifier = Modifier.height(420.dp)) {
                        items(filtered, key = { it.packageName }) { candidate ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { selected = candidate }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    AppHeaderRow(candidate, candidate.label, candidate.packageName)
                                }
                                // One tap: save a rule that turns Wi-Fi AND Mobile Data off for this
                                // app. The sheet stays open (the app drops out of the list once it
                                // has a rule) so several apps can be blocked in a row.
                                OutlinedButton(
                                    onClick = { onSave(candidate.packageName, false, false) },
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .semantics { contentDescription = "Block ${candidate.label}" },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.netLocker.blocked),
                                ) {
                                    Icon(Icons.Filled.Block, contentDescription = null, tint = MaterialTheme.netLocker.blocked, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Block", color = MaterialTheme.netLocker.blocked, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }
                }
            } else {
                Text("Create Rule", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                AppHeaderRow(app, app.label, app.packageName)
                RuleForm(wifi, mobile, { wifi = it }, { mobile = it })
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                    OutlinedButton(onClick = { selected = null }, modifier = Modifier.weight(1f)) { Text("Back") }
                    Button(onClick = { onSave(app.packageName, wifi, mobile); onDismiss() }, modifier = Modifier.weight(1f)) {
                        Text("Save Rule")
                    }
                }
            }
        }
    }
}
