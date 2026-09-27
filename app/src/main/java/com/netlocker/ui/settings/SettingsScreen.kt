package com.netlocker.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.netlocker.BuildConfig
import com.netlocker.util.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionLabel("Theme")
            Column(modifier = Modifier.selectableGroup()) {
                AppTheme.entries.forEach { theme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = uiState.theme == theme,
                                onClick = { viewModel.setTheme(theme) },
                            )
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = uiState.theme == theme, onClick = { viewModel.setTheme(theme) })
                        Text(theme.displayName(), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            HorizontalDivider()

            ListItem(
                headlineContent = { Text("Show system apps") },
                trailingContent = {
                    Switch(checked = uiState.showSystemApps, onCheckedChange = viewModel::setShowSystemApps)
                },
            )
            ListItem(
                headlineContent = { Text("Auto refresh apps") },
                trailingContent = {
                    Switch(checked = uiState.autoRefresh, onCheckedChange = viewModel::setAutoRefresh)
                },
            )

            HorizontalDivider()

            ListItem(
                headlineContent = { Text("Network method") },
                supportingContent = { Text("Detected automatically — VPN-based local firewall (no root required)") },
            )

            HorizontalDivider()

            SectionLabel("About NetLocker")
            ListItem(
                headlineContent = { Text("Version") },
                supportingContent = { Text(BuildConfig.VERSION_NAME) },
            )

            ListItem(
                modifier = Modifier.clickable(
                    enabled = updateState !is UpdateUiState.Checking && updateState !is UpdateUiState.Downloading,
                    onClick = { viewModel.checkForUpdate() },
                ),
                headlineContent = { Text("Check for updates") },
                supportingContent = { Text("NetLocker isn't on Play Store — updates are checked against GitHub Releases") },
            )

            UpdateStatusContent(
                state = updateState,
                onUpdateClick = { viewModel.startUpdate() },
                onGrantPermissionClick = { installPermissionLauncher.launch(viewModel.requestInstallPermissionIntent()) },
            )
        }
    }
}

@Composable
private fun UpdateStatusContent(
    state: UpdateUiState,
    onUpdateClick: () -> Unit,
    onGrantPermissionClick: () -> Unit,
) {
    when (state) {
        UpdateUiState.Idle -> Unit
        UpdateUiState.Checking -> UpdateStatusRow {
            CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
            Text("Checking GitHub for a newer release…")
        }
        UpdateUiState.UpToDate -> UpdateStatusRow {
            Text("You're on the latest version.", color = MaterialTheme.colorScheme.primary)
        }
        is UpdateUiState.Available -> Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(
                "Version ${state.update.versionName} is available.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            if (state.update.releaseNotes.isNotBlank()) {
                Text(
                    state.update.releaseNotes,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
            }
            Button(onClick = onUpdateClick) { Text("Download & Install") }
        }
        UpdateUiState.NeedsInstallPermission -> Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(
                "NetLocker needs your permission to install an update it downloads itself " +
                    "(Android shows this for any app outside Play Store).",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Button(onClick = onGrantPermissionClick) { Text("Grant Permission") }
        }
        UpdateUiState.Downloading -> UpdateStatusRow {
            CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
            Text("Downloading update…")
        }
        is UpdateUiState.Error -> UpdateStatusRow {
            Text(state.message, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun UpdateStatusRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp),
    )
}

private fun AppTheme.displayName(): String = when (this) {
    AppTheme.SYSTEM -> "System"
    AppTheme.LIGHT -> "Light"
    AppTheme.DARK -> "Dark"
}
