package com.netlocker.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.netlocker.ui.components.AppListItem
import com.netlocker.ui.components.AppSearchBar
import com.netlocker.ui.components.FirewallStatusBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenAppDetails: (String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsState()

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.startFirewall()
        }
    }

    fun launchVpnConsent() {
        val intent = viewModel.vpnPermissionIntent()
        if (intent != null) vpnPermissionLauncher.launch(intent) else viewModel.startFirewall()
    }

    // Android 13+ requires explicit, user-visible consent to show the "firewall active"
    // notification (spec §14: never request permissions silently). This is a real system
    // dialog, not a silent grant — declining it still lets the firewall run, just without
    // a persistent status notification.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { launchVpnConsent() }

    fun requestFirewallStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            launchVpnConsent()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("NetLocker") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            FirewallStatusBanner(
                status = uiState.firewallStatus,
                onEnableClick = { requestFirewallStart() },
                onDisableClick = { viewModel.stopFirewall() },
            )

            AppSearchBar(query = uiState.searchQuery, onQueryChange = viewModel::onSearchQueryChange)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.apps, key = { it.app.packageName }) { appWithRule ->
                    AppListItem(
                        appWithRule = appWithRule,
                        onClick = { onOpenAppDetails(appWithRule.app.packageName) },
                        onWifiToggle = { allowed ->
                            viewModel.setWifiAllowed(appWithRule.app.packageName, allowed, appWithRule.rule.mobileDataAllowed)
                        },
                        onMobileDataToggle = { allowed ->
                            viewModel.setMobileDataAllowed(appWithRule.app.packageName, appWithRule.rule.wifiAllowed, allowed)
                        },
                    )
                }
            }
        }
    }
}
