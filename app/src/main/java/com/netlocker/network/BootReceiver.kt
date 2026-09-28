package com.netlocker.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.netlocker.util.Logger
import com.netlocker.util.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Restarts the firewall after a reboot or after NetLocker itself was updated (an update
 * stops the running service) — but only if the user turned on "Start when phone turns on",
 * and only if the system VPN permission is still granted. Otherwise it does nothing: it
 * never shows a consent dialog on its own.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!ServiceLocator.preferencesManager.autoStartOnBoot.first()) return@launch
                val controller = ServiceLocator.firewallController
                if (controller.vpnPermissionIntent() == null) {
                    controller.start()
                } else {
                    Logger.w(TAG, "auto-start skipped: VPN permission is no longer granted")
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
