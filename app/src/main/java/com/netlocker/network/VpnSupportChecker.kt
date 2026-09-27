package com.netlocker.network

import android.content.Context
import android.os.UserManager

/**
 * Detects the one legitimate, documented way a *supported* Android/Samsung device can
 * still make VpnService unusable: an enterprise/MDM policy (common on Knox-managed
 * Samsung work profiles) that sets [UserManager.DISALLOW_CONFIG_VPN]. This is not a
 * version/OEM guess — it's the exact restriction Android defines for this purpose, so
 * NetLocker can tell the user the real reason instead of a generic failure (spec §15).
 */
object VpnSupportChecker {
    fun isVpnBlockedByDevicePolicy(context: Context): Boolean {
        val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager ?: return false
        return runCatching { userManager.hasUserRestriction(UserManager.DISALLOW_CONFIG_VPN) }.getOrDefault(false)
    }
}
