package com.netlocker.network

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import com.netlocker.util.Logger
import java.net.InetSocketAddress

/**
 * Wraps [ConnectivityManager.getConnectionOwnerUid] — the one public API that lets an
 * app map a live TCP/UDP 4-tuple back to the UID that owns it. This is the load-bearing
 * API for the whole per-app enforcement model: without it (API < 29) NetLocker cannot
 * reliably attribute traffic to an app at all, since direct `/proc/net` inspection is
 * blocked for non-privileged apps since Android 11. This is exactly why minSdk is 29
 * — see build.gradle.kts and the README feasibility section.
 */
class ConnectionOwnerResolver(context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /** Returns the owning UID, or [Process.INVALID_UID] if it could not be determined. */
    fun resolveUid(protocol: Int, local: InetSocketAddress, remote: InetSocketAddress): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return Process.INVALID_UID
        return try {
            connectivityManager.getConnectionOwnerUid(protocol, local, remote)
        } catch (e: SecurityException) {
            Logger.d(TAG, "getConnectionOwnerUid denied: ${e.message}")
            Process.INVALID_UID
        } catch (e: Exception) {
            Logger.d(TAG, "getConnectionOwnerUid failed: ${e.message}")
            Process.INVALID_UID
        }
    }

    companion object {
        private const val TAG = "ConnectionOwnerResolver"
    }
}
