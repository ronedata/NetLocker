package com.netlocker.domain.repository

import com.netlocker.domain.model.InstalledApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Source of truth for "what apps are installed on this device". */
interface InstalledAppRepository {

    /**
     * Emits the current app list immediately, then again whenever an app is
     * installed/uninstalled/updated or [refresh] is called. [includeSystemApps]
     * filters out apps with [android.content.pm.ApplicationInfo.FLAG_SYSTEM] unless true.
     *
     * Until the first scan finishes this emits an empty list — use [isLoaded] to tell
     * "still scanning" apart from "genuinely no apps".
     */
    fun observeInstalledApps(includeSystemApps: Boolean): Flow<List<InstalledApp>>

    /** False until the first full scan of installed apps has completed. */
    val isLoaded: Flow<Boolean> get() = flowOf(true)

    /** Forces an immediate re-scan (e.g. pull-to-refresh, or Settings "Auto refresh"). */
    suspend fun refresh()
}
