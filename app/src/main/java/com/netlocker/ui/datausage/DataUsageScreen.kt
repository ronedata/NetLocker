package com.netlocker.ui.datausage

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.netlocker.ui.components.AppIconImage
import com.netlocker.ui.components.CircleIconButton
import com.netlocker.ui.components.EmptyState
import com.netlocker.ui.components.NetLockerCard
import com.netlocker.ui.theme.netLocker
import com.netlocker.util.formatBytes

@Composable
fun DataUsageScreen(
    onBack: () -> Unit,
    viewModel: DataUsageViewModel = viewModel(factory = DataUsageViewModel.Factory),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Data usage today", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Every app that used Wi-Fi or Mobile Data today, most first",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when (val s = state) {
            DataUsageUiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            DataUsageUiState.NeedsAccess -> NetLockerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Allow \"Usage access\" in Android settings to see how much data each app used. " +
                            "Nothing leaves your phone.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { context.startActivity(viewModel.usageAccessSettingsIntent()) }) {
                        Text("Allow usage access", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            DataUsageUiState.Unavailable -> NetLockerCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Data usage couldn't be read on this device right now.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is DataUsageUiState.Loaded -> {
                if (s.rows.isEmpty()) {
                    EmptyState(
                        icon = Icons.Filled.DataUsage,
                        title = "No usage yet today",
                        message = "Once an app uses Wi-Fi or Mobile Data today, it'll show up here.",
                    )
                } else {
                    TotalsCard(totalWifiBytes = s.totalWifiBytes, totalMobileBytes = s.totalMobileBytes)
                    Spacer(Modifier.height(10.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(s.rows, key = { it.app.packageName }) { row -> AppUsageCard(row) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TotalsCard(totalWifiBytes: Long, totalMobileBytes: Long) {
    NetLockerCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Total across your apps today", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TotalCell(Icons.Filled.Wifi, MaterialTheme.netLocker.wifi, "Wi-Fi", formatBytes(totalWifiBytes), Modifier.weight(1f))
                TotalCell(Icons.Filled.SignalCellularAlt, MaterialTheme.netLocker.mobile, "Mobile", formatBytes(totalMobileBytes), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TotalCell(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color, label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.width(18.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun AppUsageCard(row: AppUsageRow) {
    NetLockerCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIconImage(row.app.icon, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(row.app.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Wi-Fi ${formatBytes(row.usage.wifiBytes)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Mobile ${formatBytes(row.usage.mobileBytes)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(formatBytes(row.usage.totalBytes), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}
