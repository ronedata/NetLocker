package com.netlocker.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.netlocker.MainActivity
import com.netlocker.R
import com.netlocker.domain.model.FirewallStatus
import com.netlocker.domain.model.NetworkRule
import com.netlocker.domain.repository.InstalledAppRepository
import com.netlocker.domain.repository.NetworkRuleRepository
import com.netlocker.util.Logger
import com.netlocker.util.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress

/**
 * The local firewall tunnel. See FirewallEngine for the actual per-app decision logic —
 * this class's job is Android plumbing: building the tun interface with the right
 * exclusion list, running it as a foreground service, and rebuilding the tunnel when a
 * rule change means an app must move in/out of that exclusion list.
 */
class NetLockerVpnService : VpnService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var engine: FirewallEngine? = null
    private var tunFd: ParcelFileDescriptor? = null

    private lateinit var installedAppRepository: InstalledAppRepository
    private lateinit var networkRuleRepository: NetworkRuleRepository
    private lateinit var ruleIndex: RuleIndex
    private lateinit var transportMonitor: TransportMonitor
    private lateinit var connectionOwnerResolver: ConnectionOwnerResolver
    private lateinit var blockedTracker: BlockedAttemptTracker
    private lateinit var scheduleEvaluator: ScheduleEvaluator

    /** Package names currently excluded from the tunnel (i.e. fully-allowed at the
     *  time the tunnel was last (re)established) — see [reconfigureAndEstablish]. */
    private var excludedPackages: Set<String> = emptySet()
    private var restartJob: Job? = null

    /** Which notification style is currently posted (see [FirewallNotificationSpec]). */
    @Volatile private var minimalNotification = false
    private var notificationPrefJob: Job? = null
    private var scheduleMasterPrefJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        installedAppRepository = ServiceLocator.installedAppRepository
        networkRuleRepository = ServiceLocator.networkRuleRepository
        ruleIndex = ServiceLocator.ruleIndex
        transportMonitor = ServiceLocator.transportMonitor
        connectionOwnerResolver = ConnectionOwnerResolver(this)
        blockedTracker = BlockedAttemptTracker(
            ServiceLocator.blockedStatsRepository,
            ServiceLocator.blockedEventsRepository,
            ServiceLocator.preferencesManager.showBlockedDestinations,
        )
        scheduleEvaluator = ScheduleEvaluator(
            ServiceLocator.preferencesManager.scheduleMasterEnabled,
            networkRuleRepository,
            ::onScheduleWindowEntered,
        )
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelfFirewall()
                return START_NOT_STICKY
            }
            else -> startFirewall()
        }
        return START_STICKY
    }

    private fun startFirewall() {
        if (tunFd != null) return // already running

        try {
            minimalNotification = readMinimalPreference()
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(minimalNotification),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } catch (e: Exception) {
            Logger.e(TAG, "startForeground failed", e)
            _status.value = FirewallStatus.Error(e.message ?: "Could not start the foreground firewall service")
            stopSelf()
            return
        }

        ruleIndex.start(serviceScope)
        transportMonitor.start()
        blockedTracker.start(serviceScope)
        scheduleEvaluator.start(serviceScope)
        watchNotificationPreference()
        watchScheduleMasterPreference()

        serviceScope.launch {
            reconfigureAndEstablish()
        }
    }

    /** Rebuilds the Builder (fresh exclusion list) and calls establish() again. Calling
     *  establish() while a previous tun is active atomically swaps it — Android closes
     *  the old interface for us, which is the standard, documented way to change a
     *  live VpnService's routing/exclusion configuration. */
    private suspend fun reconfigureAndEstablish() {
        val rules = networkRuleRepository.observeRules().first()
        val allApps = installedAppRepository.observeInstalledApps(includeSystemApps = true).first()
        val scheduleMasterEnabled = ServiceLocator.preferencesManager.scheduleMasterEnabled.first()

        val fullyOpenPackages = allApps
            .map { it.packageName }
            .filter { pkg -> isFullyOpen(rules[pkg], scheduleMasterEnabled) }
            .toSet() +
            // NetLocker never appears in its own app list (so it can't firewall itself), which
            // also means it isn't in `allApps` — without this its own traffic (e.g. the update
            // check) would be routed through the tunnel it is running.
            packageName

        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .addAddress(TUNNEL_ADDRESS, TUNNEL_PREFIX_LENGTH)
            .addRoute("0.0.0.0", 0)
            .addDnsServer(FALLBACK_DNS)
            .setMtu(TUNNEL_MTU)
            // Deliberately left in blocking mode (the default): FirewallEngine reads the
            // tun fd on its own dedicated thread with a plain blocking read() loop, which
            // is simpler and correct here — non-blocking mode would just busy-spin it.

        for (pkg in fullyOpenPackages) {
            runCatching { builder.addDisallowedApplication(pkg) }
                .onFailure { Logger.w(TAG, "could not exclude $pkg from tunnel", it) }
        }

        val newFd = try {
            builder.establish()
        } catch (e: Exception) {
            Logger.e(TAG, "establish() failed", e)
            _status.value = FirewallStatus.Error(e.message ?: "VPN interface could not be created")
            return
        }

        if (newFd == null) {
            // establish() returns null if VPN permission was revoked out from under us.
            _status.value = FirewallStatus.PermissionRequired
            stopSelfFirewall()
            return
        }

        engine?.stop()
        tunFd?.close()

        tunFd = newFd
        excludedPackages = fullyOpenPackages
        engine = FirewallEngine(
            tunFd = newFd,
            clientAddress = InetAddress.getByName(TUNNEL_ADDRESS),
            ruleIndex = ruleIndex,
            transportMonitor = transportMonitor,
            connectionOwnerResolver = connectionOwnerResolver,
            protectSocket = ::protect,
            protectDatagramSocket = ::protect,
            onBlocked = blockedTracker::onBlocked,
            scheduleMasterEnabled = scheduleMasterEnabled,
        ).also { it.start(serviceScope) }

        _status.value = FirewallStatus.Active
    }

    /** Called (via [ServiceLocator]'s FirewallController) whenever a rule changes while
     *  the firewall is running. */
    fun onRuleChanged(packageName: String, uid: Int) {
        engine?.invalidateSessionsForUid(uid)

        // If this rule change flips the app's fully-open membership, the exclusion
        // list Builder was configured with is now stale and the tunnel must be rebuilt.
        serviceScope.launch {
            val rule = networkRuleRepository.getRuleOnce(packageName)
            val scheduleMasterEnabled = ServiceLocator.preferencesManager.scheduleMasterEnabled.first()
            val shouldBeExcluded = isFullyOpen(rule, scheduleMasterEnabled)
            val wasExcluded = packageName in excludedPackages
            if (shouldBeExcluded != wasExcluded) {
                restartJob?.cancel()
                restartJob = launch { reconfigureAndEstablish() }
            }
        }
    }

    /** Whether an app can be excluded from the tunnel entirely (spec: the zero-overhead
     *  path). An app with a live schedule must always stay inside the tunnel — even
     *  outside its block window — because there is no other hook to start enforcing the
     *  moment that window opens; see [ScheduleEvaluator]. */
    private fun isFullyOpen(rule: NetworkRule?, scheduleMasterEnabled: Boolean): Boolean {
        val hasLiveSchedule = scheduleMasterEnabled && rule?.scheduleEnabled == true
        return (rule == null || rule.isEffectivelyOpen) && !hasLiveSchedule
    }

    /** [ScheduleEvaluator] callback: an app's scheduled block window just started, so any
     *  connection it already has open must be cut immediately rather than left running
     *  until it happens to close on its own — the same immediacy a normal rule edit gets. */
    private fun onScheduleWindowEntered(packageName: String) {
        val uid = runCatching { packageManager.getApplicationInfo(packageName, 0).uid }
            .getOrDefault(Process.INVALID_UID)
        if (uid != Process.INVALID_UID) engine?.invalidateSessionsForUid(uid)
    }

    /** Rebuilds the tunnel's exclusion list when the Schedule master switch itself flips —
     *  that can move several apps in/out of the tunnel at once, unlike a single rule edit. */
    private fun watchScheduleMasterPreference() {
        scheduleMasterPrefJob?.cancel()
        scheduleMasterPrefJob = serviceScope.launch {
            ServiceLocator.preferencesManager.scheduleMasterEnabled.drop(1).collect {
                reconfigureAndEstablish()
            }
        }
    }

    private fun stopSelfFirewall() {
        engine?.stop()
        engine = null
        tunFd?.close()
        tunFd = null
        transportMonitor.stop()
        scheduleEvaluator.stop()
        saveBlockedCounts()
        excludedPackages = emptySet()
        _status.value = FirewallStatus.Stopped
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        // The user turned off NetLocker's VPN from system Settings directly.
        _status.value = FirewallStatus.PermissionRequired
        stopSelfFirewall()
        super.onRevoke()
    }

    /** Writes any not-yet-saved blocked counts. Runs on its own short-lived scope because
     *  [serviceScope] is cancelled when the service goes away. */
    private fun saveBlockedCounts() {
        CoroutineScope(Dispatchers.IO).launch { blockedTracker.stop() }
    }

    override fun onDestroy() {
        engine?.stop()
        tunFd?.close()
        transportMonitor.stop()
        scheduleEvaluator.stop()
        saveBlockedCounts()
        if (instance === this) instance = null
        serviceScope.cancel()
        super.onDestroy()
    }

    /** Reads the "Minimal notification" setting. Bounded: the notification must be posted
     *  within seconds of startForegroundService(), and a preference read is normally a
     *  few milliseconds — if it ever stalls, fall back to the standard notification. */
    private fun readMinimalPreference(): Boolean = runBlocking {
        withTimeoutOrNull(PREFERENCE_READ_TIMEOUT_MS) {
            ServiceLocator.preferencesManager.minimalNotification.first()
        } ?: false
    }

    private fun buildNotification(minimal: Boolean): Notification {
        val channelId = ensureNotificationChannel(minimal)
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.vpn_notification_title))
            .setOngoing(true)
            .setContentIntent(contentIntent)
        return if (minimal) {
            // Smallest presentation we can request: silent, no timestamp, hidden on the
            // lock screen. It cannot be removed — Android requires it while the firewall
            // runs — and Android may still show its status-bar icon (see FirewallNotificationSpec).
            builder
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setSilent(true)
                .setShowWhen(false)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .build()
        } else {
            builder.setContentText(getString(R.string.vpn_notification_text)).build()
        }
    }

    private fun ensureNotificationChannel(minimal: Boolean): String {
        val channelId = FirewallNotificationSpec.channelId(minimal)
        val channel = NotificationChannel(
            channelId,
            getString(if (minimal) R.string.vpn_notification_channel_name_minimal else R.string.vpn_notification_channel_name),
            FirewallNotificationSpec.importance(minimal),
        ).apply {
            if (minimal) {
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return channelId
    }

    /** Re-posts the running notification when the user flips "Minimal notification" — so
     *  the change applies immediately, without restarting the firewall. */
    private fun watchNotificationPreference() {
        notificationPrefJob?.cancel()
        notificationPrefJob = serviceScope.launch {
            ServiceLocator.preferencesManager.minimalNotification.collect { minimal ->
                if (minimal != minimalNotification) {
                    minimalNotification = minimal
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, buildNotification(minimal))
                }
            }
        }
    }

    companion object {
        private const val TAG = "NetLockerVpnService"
        const val ACTION_STOP = "com.netlocker.action.STOP_FIREWALL"
        private const val NOTIFICATION_ID = 1
        private const val PREFERENCE_READ_TIMEOUT_MS = 1_500L

        // Arbitrary, unlikely-to-collide private subnet for the tun interface itself.
        private const val TUNNEL_ADDRESS = "10.111.222.1"
        private const val TUNNEL_PREFIX_LENGTH = 24
        private const val TUNNEL_MTU = 1500
        private const val FALLBACK_DNS = "8.8.8.8"

        @Volatile private var instance: NetLockerVpnService? = null

        private val _status = MutableStateFlow<FirewallStatus>(FirewallStatus.Stopped)
        val status: StateFlow<FirewallStatus> = _status.asStateFlow()

        /** Best-effort in-process hook used by FirewallControllerImpl; a no-op if the
         *  service isn't currently running (rule was saved but nothing enforces it yet,
         *  which is accurately reflected by [status] being Stopped/PermissionRequired). */
        fun notifyRuleChanged(packageName: String, uid: Int) {
            instance?.onRuleChanged(packageName, uid)
        }
    }
}
