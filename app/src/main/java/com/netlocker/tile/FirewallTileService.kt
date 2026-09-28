package com.netlocker.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.netlocker.MainActivity
import com.netlocker.R
import com.netlocker.domain.model.FirewallStatus
import com.netlocker.util.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Quick Settings tile: turns the firewall on/off from the notification shade.
 *
 * It only ever *reflects* the real firewall status (never assumes it worked). If the system
 * VPN permission hasn't been granted yet, a tap opens NetLocker instead, because that consent
 * dialog can only be shown by an activity.
 */
class FirewallTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var statusJob: Job? = null

    override fun onStartListening() {
        statusJob?.cancel()
        statusJob = scope.launch {
            ServiceLocator.firewallController.status.collect { render(it) }
        }
    }

    override fun onStopListening() {
        statusJob?.cancel()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        val controller = ServiceLocator.firewallController
        when {
            controller.status.value == FirewallStatus.Active -> controller.stop()
            controller.vpnPermissionIntent() == null -> controller.start()
            else -> openApp()
        }
    }

    private fun render(status: FirewallStatus) {
        val tile = qsTile ?: return
        val active = status == FirewallStatus.Active
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        tile.subtitle = getString(if (active) R.string.tile_on else R.string.tile_off)
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
