package com.netlocker.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.netlocker.R
import com.netlocker.domain.model.FirewallStatus

/**
 * The "why isn't this working" banner (spec §14 permission handling, §21 no-fake-success
 * error reporting). Never silently hidden when enforcement isn't actually active — a
 * saved rule with no running firewall is truthfully shown as not-yet-enforced.
 */
@Composable
fun FirewallStatusBanner(status: FirewallStatus, onEnableClick: () -> Unit, onDisableClick: () -> Unit) {
    when (status) {
        FirewallStatus.Active -> InfoBanner(
            title = "NetLocker firewall is active",
            body = "Your Wi-Fi/Mobile Data rules are being enforced.",
            buttonLabel = "Disable",
            onClick = onDisableClick,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        )
        FirewallStatus.Stopped -> InfoBanner(
            title = "NetLocker firewall is off",
            body = "Rules are saved but not enforced yet. Turn on the firewall to start applying them.",
            buttonLabel = "Enable",
            onClick = onEnableClick,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        )
        FirewallStatus.PermissionRequired -> InfoBanner(
            title = stringResource(R.string.permission_rationale_title),
            body = stringResource(R.string.permission_rationale_body),
            buttonLabel = stringResource(R.string.grant_permission),
            onClick = onEnableClick,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        )
        is FirewallStatus.Error -> InfoBanner(
            title = "Could not apply network restriction.",
            body = "Reason:\n${status.reason}",
            buttonLabel = "Retry",
            onClick = onEnableClick,
            containerColor = MaterialTheme.colorScheme.errorContainer,
        )
    }
}

@Composable
private fun InfoBanner(
    title: String,
    body: String,
    buttonLabel: String,
    onClick: () -> Unit,
    containerColor: androidx.compose.ui.graphics.Color,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            Button(onClick = onClick) { Text(buttonLabel) }
        }
    }
}
