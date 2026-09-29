package com.netlocker.network

import android.net.Network
import android.os.ParcelFileDescriptor
import android.os.Process
import com.netlocker.domain.model.NetworkRule
import com.netlocker.domain.model.nowDayOfWeekIndex
import com.netlocker.domain.model.nowMinuteOfDay
import com.netlocker.network.packet.IpProtocol
import com.netlocker.network.packet.ParsedPacket
import com.netlocker.network.relay.SessionKey
import com.netlocker.network.relay.TcpNatSession
import com.netlocker.network.relay.TunWriter
import com.netlocker.network.relay.UdpNatSession
import com.netlocker.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * The decision core of NetLocker's enforcement layer.
 *
 * How a packet is handled depends entirely on which of three buckets its owning
 * app's [NetworkRule] falls into:
 *
 *  1. **Fully allowed** (Wi-Fi + Mobile Data both on) — these apps are excluded from
 *     the tunnel entirely via `Builder.addDisallowedApplication` in
 *     [NetLockerVpnService], so their packets never reach this class at all. Zero
 *     relay risk, zero performance cost — this is why "everything open" is the safe
 *     default (spec §6).
 *  2. **Fully blocked** (both off) — packets are read and then simply never
 *     forwarded anywhere. No relay needed; this path is as reliable as reading a
 *     boolean.
 *  3. **Partial** (exactly one of Wi-Fi/Mobile on) — the packet's owning transport
 *     must currently be up (per [TransportMonitor]) or it is blocked; if it is, the
 *     flow is hands off to [TcpNatSession] / [UdpNatSession] to actually relay it.
 *     See those classes for the documented scope of "best-effort".
 *
 * UID attribution (bucket lookup) itself can fail — the underlying
 * [ConnectionOwnerResolver] API can throw, return no answer, or the index simply
 * hasn't loaded yet. NetLocker fails **closed** in that case: an unattributable
 * packet inside the tunnel is dropped, never passed through unchecked.
 *
 * The single tun-reader thread ([readLoop]) never blocks on a session's own network
 * I/O: an existing session's packets are handed off via its non-suspending
 * `offer*` method (a channel under the hood), so one slow/stalled flow can never
 * stall reading (and therefore enforcing rules for) every other app.
 */
class FirewallEngine(
    private val tunFd: ParcelFileDescriptor,
    private val clientAddress: InetAddress,
    private val ruleIndex: RuleIndex,
    private val transportMonitor: TransportMonitor,
    private val connectionOwnerResolver: ConnectionOwnerResolver,
    private val protectSocket: (java.net.Socket) -> Boolean,
    private val protectDatagramSocket: (DatagramSocket) -> Boolean,
    /** Told about each flow the rules blocked: (packageName, flowKey). Only called when the
     *  owning app was identified — flows that can't be attributed aren't counted for anyone. */
    private val onBlocked: (packageName: String, flowKey: String) -> Unit = { _, _ -> },
    /** Snapshot of Settings' Schedule master switch, taken when this engine (and the
     *  tunnel's exclusion list) was last (re)built — see [NetLockerVpnService.isFullyOpen]
     *  and [NetworkRule.isWithinSchedule]. */
    private val scheduleMasterEnabled: Boolean = false,
) {
    private val udpSessions = ConcurrentHashMap<SessionKey, UdpNatSession>()
    private val tcpSessions = ConcurrentHashMap<SessionKey, TcpNatSession>()

    /** Owning uid of every currently-relayed session, so a rule change for one app can
     *  tear down just that app's open connections instead of waiting for them to idle
     *  out on their own — see [invalidateSessionsForUid]. */
    private val sessionOwners = ConcurrentHashMap<SessionKey, Int>()

    private lateinit var tunWriter: TunWriter
    private var readerThread: Thread? = null
    private var reaperJob: Job? = null

    @Volatile
    private var running = false

    fun start(scope: CoroutineScope) {
        val input = FileInputStream(tunFd.fileDescriptor)
        val output = FileOutputStream(tunFd.fileDescriptor)
        tunWriter = TunWriter(output)
        running = true

        readerThread = Thread({ readLoop(input, scope) }, "NetLocker-TunReader").apply { start() }
        reaperJob = scope.launch { reapIdleSessions() }
    }

    fun stop() {
        running = false
        reaperJob?.cancel()
        readerThread?.interrupt()
        udpSessions.values.forEach { it.close() }
        tcpSessions.values.forEach { it.forceClose() }
        udpSessions.clear()
        tcpSessions.clear()
        sessionOwners.clear()
    }

    /** Forces any already-open sessions belonging to [uid] closed, so a rule the user
     *  just changed takes effect immediately instead of only on the app's *next* new
     *  connection. This is what makes "Apply -> app loses internet now" true even for
     *  an app with an existing open connection at the moment the rule changes. */
    fun invalidateSessionsForUid(uid: Int) {
        sessionOwners.entries.filter { it.value == uid }.forEach { (key, _) ->
            udpSessions.remove(key)?.close()
            tcpSessions.remove(key)?.forceClose()
            sessionOwners.remove(key)
        }
    }

    private fun readLoop(input: FileInputStream, scope: CoroutineScope) {
        val buffer = ByteArray(MAX_PACKET_SIZE)
        while (running) {
            val length = try {
                input.read(buffer)
            } catch (e: Exception) {
                if (running) Logger.d(TAG, "tun read error: ${e.message}")
                break
            }
            if (length <= 0) continue

            val packetCopy = buffer.copyOf(length)
            when (val parsed = ParsedPacket.parse(packetCopy, length)) {
                is ParsedPacket.Tcp -> handleTcp(parsed, scope)
                is ParsedPacket.Udp -> handleUdp(parsed, scope)
                ParsedPacket.Unsupported -> Unit // dropped: unknown/unattributable protocol, fail closed
            }
        }
    }

    private fun handleUdp(packet: ParsedPacket.Udp, scope: CoroutineScope) {
        val key = SessionKey(
            protocol = IpProtocol.UDP,
            sourcePort = packet.udp.sourcePort,
            destinationAddress = packet.ip.destinationInetAddress().hostAddress ?: return,
            destinationPort = packet.udp.destinationPort,
        )

        val existing = udpSessions[key]
        if (existing != null) {
            existing.offerFromClient(packet.ip.raw, packet.payloadOffset, packet.payloadLength)
            return
        }

        val decision = decideForNewFlow(
            protocol = IpProtocol.UDP,
            sourcePort = packet.udp.sourcePort,
            destination = InetSocketAddress(packet.ip.destinationInetAddress(), packet.udp.destinationPort),
        ) ?: return // blocked or unattributable — drop, no session created

        val session = UdpNatSession(
            key = key,
            clientAddress = clientAddress,
            tunWriter = tunWriter,
            protect = protectDatagramSocket,
            bindToNetwork = { socket -> bindDatagramSocketToNetwork(socket, decision.network) },
            onIdle = { sessionOwners.remove(it); udpSessions.remove(it) },
        )
        if (session.start(scope)) {
            udpSessions[key] = session
            sessionOwners[key] = decision.uid
            session.offerFromClient(packet.ip.raw, packet.payloadOffset, packet.payloadLength)
        }
    }

    private fun handleTcp(packet: ParsedPacket.Tcp, scope: CoroutineScope) {
        val key = SessionKey(
            protocol = IpProtocol.TCP,
            sourcePort = packet.tcp.sourcePort,
            destinationAddress = packet.ip.destinationInetAddress().hostAddress ?: return,
            destinationPort = packet.tcp.destinationPort,
        )

        val existing = tcpSessions[key]
        if (existing != null) {
            existing.offerClientSegment(packet.tcp, packet.ip.raw, packet.payloadOffset, packet.payloadLength)
            return
        }

        if (!packet.tcp.isSyn) return // no session and not a new connection attempt — drop stray segment

        val decision = decideForNewFlow(
            protocol = IpProtocol.TCP,
            sourcePort = packet.tcp.sourcePort,
            destination = InetSocketAddress(packet.ip.destinationInetAddress(), packet.tcp.destinationPort),
        ) ?: return // blocked or unattributable — drop the SYN, app sees a connection timeout

        val session = TcpNatSession(
            key = key,
            clientAddress = clientAddress,
            tunWriter = tunWriter,
            protect = protectSocket,
            bindToNetwork = { socket -> bindSocketToNetwork(socket, decision.network) },
            onClosed = { sessionOwners.remove(it); tcpSessions.remove(it) },
        )
        tcpSessions[key] = session
        sessionOwners[key] = decision.uid
        session.beginHandshake(scope, packet.tcp.sequenceNumber)
    }

    /** Runs the actual per-app policy decision for a brand-new flow. Returns null if
     *  the flow must be blocked (unknown uid, no rule match, or required transport
     *  currently unavailable) — never guesses in the app's favour. */
    private fun decideForNewFlow(protocol: Int, sourcePort: Int, destination: InetSocketAddress): FlowDecision? {
        val local = InetSocketAddress(clientAddress, sourcePort)
        val uid = connectionOwnerResolver.resolveUid(protocol, local, destination)
        if (uid == Process.INVALID_UID) {
            Logger.d(TAG, "DECISION: uid unresolved for dest=$destination -> DROP")
            return null
        }

        if (!ruleIndex.isReady) return null // fail closed until we have a real snapshot

        val rule = ruleIndex.ruleForUid(uid)
        if (rule == null) {
            Logger.d(TAG, "DECISION: uid=$uid dest=$destination no rule -> DROP")
            return null
        }
        val snapshot = transportMonitor.snapshot.value

        // A scheduled block wins over everything else while its window is open — evaluated
        // fresh for every new flow, so this is always exactly correct for "now", unlike the
        // once-a-minute ScheduleEvaluator (which only exists to cut an *already-open*
        // session the instant a window starts; new flows never need that help).
        val withinSchedule = scheduleMasterEnabled && rule.isWithinSchedule(nowMinuteOfDay(), nowDayOfWeekIndex())

        val network = when {
            withinSchedule -> null
            // A fully-open, non-scheduled rule is excluded from the tunnel entirely via
            // addDisallowedApplication in NetLockerVpnService, so a packet should never
            // reach this branch. If it somehow does (e.g. an exclusion-list bug), fail
            // closed (drop) rather than risk silently widening access beyond what was
            // configured. (A *disabled* rule reads as effectively open too — it isn't
            // enforced. A rule with a *live schedule* is deliberately kept inside the
            // tunnel even while fully open outside its window — see isFullyOpen — so that
            // legitimate case falls through to the ordinary branches below instead.)
            rule.isEffectivelyOpen && !rule.scheduleEnabled -> null
            rule.effectiveWifiAllowed && snapshot.wifi != null -> snapshot.wifi
            rule.effectiveMobileDataAllowed && snapshot.cellular != null -> snapshot.cellular
            else -> null // required transport not currently up, or fully blocked
        }
        Logger.d(TAG, "DECISION: uid=$uid dest=$destination rule=$rule withinSchedule=$withinSchedule wifiNet=${snapshot.wifi != null} cellNet=${snapshot.cellular != null} -> ${if (network != null) "ALLOW via $network" else "DROP"}")
        if (network == null) {
            // Count only what the user's rules (or schedule) actually blocked — not the
            // fail-closed drop of an app that should have bypassed the tunnel. Same source
            // port + destination = the same attempt being retried (see AttemptDeduper).
            if (withinSchedule || !rule.isEffectivelyOpen) onBlocked(rule.packageName, "$protocol/$sourcePort/$destination")
            return null
        }

        return FlowDecision(uid, network)
    }

    private fun bindSocketToNetwork(socket: Socket, network: Network): Boolean =
        runCatching { network.bindSocket(socket) }.isSuccess

    private fun bindDatagramSocketToNetwork(socket: DatagramSocket, network: Network): Boolean =
        runCatching { network.bindSocket(socket) }.isSuccess

    private suspend fun reapIdleSessions() {
        while (running) {
            kotlinx.coroutines.delay(IDLE_SWEEP_INTERVAL_MS)
            val now = System.currentTimeMillis()

            udpSessions.filterValues { it.isIdle(now) }.keys.forEach { key ->
                udpSessions.remove(key)?.close()
                sessionOwners.remove(key)
            }
            tcpSessions.filterValues { it.isIdle(now, TCP_IDLE_TIMEOUT_MS) }.keys.forEach { key ->
                tcpSessions.remove(key)?.forceClose()
                sessionOwners.remove(key)
            }
        }
    }

    private data class FlowDecision(val uid: Int, val network: Network)

    companion object {
        private const val TAG = "FirewallEngine"
        private const val MAX_PACKET_SIZE = 32767
        private const val IDLE_SWEEP_INTERVAL_MS = 15_000L
        private const val TCP_IDLE_TIMEOUT_MS = 10 * 60_000L
    }
}
