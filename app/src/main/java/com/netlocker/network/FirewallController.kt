package com.netlocker.network

import android.content.Intent
import com.netlocker.domain.model.FirewallStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * The app-facing handle onto the firewall — everything outside the `network` package
 * (ViewModels, use cases) talks to the VPN through this interface, never to
 * [NetLockerVpnService] directly. This keeps the "is it actually running, and did the
 * OS actually grant it" truth in one observable place (spec §21: never show a rule as
 * applied unless it really is).
 */
interface FirewallController {
    val status: StateFlow<FirewallStatus>

    /** Null if the system VPN permission is already granted; otherwise an Intent to
     *  launch for the system consent dialog (VpnService.prepare's contract). */
    fun vpnPermissionIntent(): Intent?

    /** Starts the foreground VPN service. Only meaningful once [vpnPermissionIntent]
     *  returns null (permission already granted) or the launched intent came back OK. */
    fun start()

    fun stop()

    /** Nudges a running firewall to re-evaluate one app's already-open connections
     *  immediately after its rule changed, instead of waiting for them to idle out. */
    suspend fun notifyRuleChanged(packageName: String)
}
