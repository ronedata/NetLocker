package com.netlocker.data.usage

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Process
import android.provider.Settings
import com.netlocker.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/** Bytes an app moved today, split by network type. */
data class DataUsage(val wifiBytes: Long, val mobileBytes: Long) {
    val totalBytes: Long get() = wifiBytes + mobileBytes
}

/**
 * Reads how much data an app used today from Android's own network statistics
 * ([NetworkStatsManager]) — the same numbers Settings → Connections → Data usage shows.
 *
 * This needs the special "Usage access" permission, which the user grants in system
 * Settings (an app can't grant it to itself). Without it [todayUsage] returns null and the
 * UI asks — it never invents numbers.
 */
class AppDataUsageReader(private val context: Context) {

    private val networkStatsManager: NetworkStatsManager =
        context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun usageAccessSettingsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Usage since local midnight, or null if usage access hasn't been granted / can't be read. */
    suspend fun todayUsage(uid: Int): DataUsage? = withContext(Dispatchers.IO) {
        if (!hasUsageAccess()) return@withContext null
        val end = System.currentTimeMillis()
        val start = startOfToday(end)
        try {
            DataUsage(
                wifiBytes = bytesFor(ConnectivityManager.TYPE_WIFI, uid, start, end),
                mobileBytes = bytesFor(ConnectivityManager.TYPE_MOBILE, uid, start, end),
            )
        } catch (e: Exception) {
            Logger.w(TAG, "could not read data usage for uid $uid", e)
            null
        }
    }

    private fun bytesFor(networkType: Int, uid: Int, start: Long, end: Long): Long {
        var total = 0L
        val stats = networkStatsManager.queryDetailsForUid(networkType, null, start, end, uid)
        stats.use {
            val bucket = NetworkStats.Bucket()
            while (it.hasNextBucket()) {
                it.getNextBucket(bucket)
                total += bucket.rxBytes + bucket.txBytes
            }
        }
        return total
    }

    /** Today's usage for every app at once (one query per network type instead of one per
     *  app), used to sort the app list by data used. Null if usage access hasn't been
     *  granted / can't be read — the caller must not invent numbers in that case. */
    suspend fun todayUsageForAllUids(): Map<Int, DataUsage>? = withContext(Dispatchers.IO) {
        if (!hasUsageAccess()) return@withContext null
        val end = System.currentTimeMillis()
        val start = startOfToday(end)
        try {
            val wifi = bytesPerUid(ConnectivityManager.TYPE_WIFI, start, end)
            val mobile = bytesPerUid(ConnectivityManager.TYPE_MOBILE, start, end)
            (wifi.keys + mobile.keys).associateWith { uid ->
                DataUsage(wifiBytes = wifi[uid] ?: 0L, mobileBytes = mobile[uid] ?: 0L)
            }
        } catch (e: Exception) {
            Logger.w(TAG, "could not read data usage for all apps", e)
            null
        }
    }

    private fun bytesPerUid(networkType: Int, start: Long, end: Long): Map<Int, Long> {
        val totals = mutableMapOf<Int, Long>()
        val stats = networkStatsManager.queryDetails(networkType, null, start, end)
        stats.use {
            val bucket = NetworkStats.Bucket()
            while (it.hasNextBucket()) {
                it.getNextBucket(bucket)
                totals[bucket.uid] = (totals[bucket.uid] ?: 0L) + bucket.rxBytes + bucket.txBytes
            }
        }
        return totals
    }

    private fun startOfToday(now: Long): Long = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private companion object {
        const val TAG = "AppDataUsageReader"
    }
}
