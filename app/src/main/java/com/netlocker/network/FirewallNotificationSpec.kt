package com.netlocker.network

import android.app.NotificationManager

/**
 * How the firewall's foreground-service notification is presented.
 *
 * Android requires *some* notification while a foreground service runs — that is what
 * keeps the OS from killing the firewall in the background — so it can be made small
 * and quiet but not removed. (The separate 🔑 "VPN active" indicator is drawn by the
 * system itself and cannot be hidden by any app.)
 *
 * A notification channel's importance can only be lowered by the *user* after it is
 * created — an app cannot change it — so "minimal" is a second channel rather than a
 * tweak to the first, and the service simply picks one at start.
 */
object FirewallNotificationSpec {
    const val CHANNEL_STANDARD = "netlocker_firewall_status"
    const val CHANNEL_MINIMAL = "netlocker_firewall_status_minimal"

    fun channelId(minimal: Boolean): String = if (minimal) CHANNEL_MINIMAL else CHANNEL_STANDARD

    /** IMPORTANCE_MIN is what we ask for (silent, collapsed, no lock-screen content). Android
     *  raises a foreground-service notification's channel to LOW regardless — verified on a
     *  Samsung device — so the status-bar icon may still appear; the user can hide it in
     *  the system's notification settings. */
    fun importance(minimal: Boolean): Int =
        if (minimal) NotificationManager.IMPORTANCE_MIN else NotificationManager.IMPORTANCE_LOW
}
