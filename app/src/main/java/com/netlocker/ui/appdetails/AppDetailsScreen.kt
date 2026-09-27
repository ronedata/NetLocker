package com.netlocker.ui.appdetails

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.netlocker.domain.model.NetworkAccessState
import com.netlocker.ui.components.StatusBadge
import com.netlocker.ui.components.ToggleRow
import com.netlocker.ui.components.accentColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailsScreen(
    packageName: String,
    onBack: () -> Unit,
    viewModel: AppDetailsViewModel = viewModel(factory = AppDetailsViewModel.factory(packageName)),
) {
    val appWithRule by viewModel.appWithRule.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(appWithRule?.app?.label ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val current = appWithRule
        if (current == null) {
            // Still loading (the installed-app scan can take a couple of seconds on a
            // device with many apps — see InstalledAppRepositoryImpl's caching note) or
            // the app was uninstalled mid-view. Either way, show a spinner rather than a
            // blank screen that reads as broken, and never a stale/incorrect state.
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val icon = current.app.icon
            if (icon != null) {
                androidx.compose.foundation.Image(
                    bitmap = icon.toBitmap().asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(20.dp)),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(current.app.label, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(
                current.app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            current.app.versionName?.let {
                Text("v$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Card(modifier = Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Network Access", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))

                    ToggleRow(
                        label = "Wi-Fi",
                        checked = current.rule.wifiAllowed,
                        onCheckedChange = { viewModel.setWifiAllowed(it, current.rule.mobileDataAllowed) },
                    )
                    ToggleRow(
                        label = "Mobile Data",
                        checked = current.rule.mobileDataAllowed,
                        onCheckedChange = { viewModel.setMobileDataAllowed(current.rule.wifiAllowed, it) },
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                    Text("Internet Access", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = current.rule.accessState.label(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = current.rule.accessState.accentColor(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    StatusBadge(state = current.rule.accessState)
                }
            }
        }
    }
}

private fun NetworkAccessState.label(): String = when (this) {
    NetworkAccessState.ALLOWED -> "Allowed"
    NetworkAccessState.WIFI_ONLY -> "Wi-Fi Only"
    NetworkAccessState.MOBILE_ONLY -> "Mobile Data Only"
    NetworkAccessState.BLOCKED -> "Blocked"
}
