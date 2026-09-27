package com.netlocker.network

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Process
import androidx.core.content.ContextCompat
import com.netlocker.domain.model.FirewallStatus
import com.netlocker.util.Logger
import kotlinx.coroutines.flow.StateFlow

class FirewallControllerImpl(private val context: Context) : FirewallController {

    override val status: StateFlow<FirewallStatus> = NetLockerVpnService.status

    override fun vpnPermissionIntent(): Intent? = VpnService.prepare(context)

    override fun start() {
        ContextCompat.startForegroundService(context, Intent(context, NetLockerVpnService::class.java))
    }

    override fun stop() {
        context.startService(
            Intent(context, NetLockerVpnService::class.java).setAction(NetLockerVpnService.ACTION_STOP),
        )
    }

    override suspend fun notifyRuleChanged(packageName: String) {
        val uid = runCatching {
            context.packageManager.getApplicationInfo(packageName, 0).uid
        }.getOrDefault(Process.INVALID_UID)

        if (uid == Process.INVALID_UID) {
            Logger.w("FirewallController", "could not resolve uid for $packageName; rule saved but not re-pushed to a live session")
            return
        }
        NetLockerVpnService.notifyRuleChanged(packageName, uid)
    }
}
