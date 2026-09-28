package com.netlocker.data.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.netlocker.domain.model.AppCategory
import com.netlocker.domain.model.InstalledApp
import com.netlocker.domain.repository.InstalledAppRepository
import com.netlocker.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

/**
 * Lists installed apps via [PackageManager] — the ordinary, documented way to enumerate
 * apps (spec §4/§17's "network" list of APIs mentions UsageStatsManager too, but that's
 * for usage/foreground tracking, not app discovery; PackageManager is the correct and
 * sufficient tool for "what's installed", so that's the only one NetLocker asks
 * permission for — no unnecessary permissions for a feature not otherwise used).
 *
 * QUERY_ALL_PACKAGES (manifest) is required because NetLocker must see every app —
 * not just ones it declares an intent-based visibility filter for.
 *
 * ## Why a shared cache (found via on-device testing, not theoretical)
 * Scanning 500+ installed apps — including loading each one's icon — measurably takes
 * a couple of seconds on a real device. Naively re-running that scan for every
 * subscriber meant the App Details screen re-scanned everything from scratch even
 * though the Home screen had *just* finished the exact same scan moments earlier,
 * producing a multi-second blank screen. [rawApps] fixes this by caching the last scan
 * in a hot [MutableStateFlow] shared by every subscriber (Home, App Details, the
 * firewall's `RuleIndex`): a new subscriber gets the cached list immediately (StateFlow
 * replay), and the expensive scan itself only reruns when something actually changed
 * (a manual refresh, or a package install/update/removal).
 */
class InstalledAppRepositoryImpl(private val context: Context) : InstalledAppRepository {

    private val packageManager = context.packageManager
    private val manualRefreshTrigger = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Always holds *all* apps (system apps included) unfiltered — [observeInstalledApps]
     *  applies the `includeSystemApps` filter per-subscriber over this one shared scan. */
    private val rawApps = MutableStateFlow<List<InstalledApp>>(emptyList())

    private val _isLoaded = MutableStateFlow(false)

    /** Stays false until the first full scan finishes, so the UI can show a loading
     *  state instead of an empty list that looks like "no apps installed". */
    override val isLoaded: Flow<Boolean> = _isLoaded

    init {
        combine(manualRefreshTrigger, packageChangeEvents()) { _, _ -> Unit }
            .onEach {
                rawApps.value = queryInstalledApps()
                _isLoaded.value = true
            }
            .launchIn(repositoryScope)
    }

    override fun observeInstalledApps(includeSystemApps: Boolean): Flow<List<InstalledApp>> =
        rawApps.map { apps -> if (includeSystemApps) apps else apps.filterNot { it.isSystemApp } }

    override suspend fun refresh() {
        manualRefreshTrigger.emit(Unit)
    }

    @Suppress("DEPRECATION") // The typed GetInstalledApplicationsFlags overload needs API 33; minSdk here is 29.
    private suspend fun queryInstalledApps(): List<InstalledApp> =
        withContext(Dispatchers.IO) {
            val apps = packageManager.getInstalledApplications(0)
            apps
                .asSequence()
                .filter { it.packageName != context.packageName } // never lets the user firewall NetLocker itself
                .mapNotNull { runCatching { it.toInstalledApp() }.onFailure { e ->
                    Logger.w(TAG, "failed to read app info for ${it.packageName}", e)
                }.getOrNull() }
                .toList()
        }

    private fun ApplicationInfo.toInstalledApp(): InstalledApp = InstalledApp(
        packageName = packageName,
        uid = uid,
        label = packageManager.getApplicationLabel(this).toString(),
        versionName = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull(),
        isSystemApp = isSystemApp(),
        icon = runCatching { packageManager.getApplicationIcon(this) }.getOrNull(),
        category = appCategory(),
    )

    private fun ApplicationInfo.isSystemApp(): Boolean = (flags and ApplicationInfo.FLAG_SYSTEM) != 0

    /** Uses only what the app itself declares (its manifest category / isGame flag) —
     *  no guessing by name. Apps that declare nothing land in [AppCategory.OTHER]. */
    @Suppress("DEPRECATION") // FLAG_IS_GAME is deprecated but still set by apps that declare isGame.
    private fun ApplicationInfo.appCategory(): AppCategory = when {
        (flags and ApplicationInfo.FLAG_IS_GAME) != 0 || category == ApplicationInfo.CATEGORY_GAME -> AppCategory.GAME
        category == ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.SOCIAL
        else -> AppCategory.OTHER
    }

    /** Emits whenever an app is installed, updated or removed, so the list stays fresh
     *  without the user needing to manually refresh (spec §4/§22 auto refresh). */
    private fun packageChangeEvents(): Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                trySend(Unit)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        trySend(Unit) // initial value so combine() has something to start with
        awaitClose { context.unregisterReceiver(receiver) }
    }

    companion object {
        private const val TAG = "InstalledAppRepository"
    }
}
