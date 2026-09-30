package com.netlocker.network.relay

import com.netlocker.network.packet.Checksum
import com.netlocker.network.packet.IPv4Packet
import com.netlocker.network.packet.IPv6Packet
import com.netlocker.network.packet.IpProtocol
import com.netlocker.network.packet.TcpHeader
import com.netlocker.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * Terminates one TCP connection locally (acting as a fake server to the app on the
 * tun side) and relays its bytes over a real [Socket] to the actual destination.
 *
 * ## Scope and honesty note (read before touching sequence-number logic)
 * This is deliberately **not** a full TCP/IP stack. It implements just enough of
 * RFC 793 to carry ordinary request/response traffic reliably:
 *  - proper 3-way handshake, cumulative ACKs, FIN/RST teardown
 *  - a single outstanding unacknowledged segment at a time in the server->client
 *    direction ("stop-and-wait"), with a bounded timeout-retry
 *  - no SACK, no window scaling, no out-of-order reassembly buffer
 *
 * That means: correct behaviour for typical light/background app traffic, but
 * **reduced throughput on large transfers** compared to the device's real TCP stack,
 * and a connection that gives up (RST) after [MAX_RETRIES] unacknowledged retries
 * instead of hanging forever. This trade-off is intentional and is called out in the
 * README/feasibility report — it is the "best-effort" half of NetLocker's partial
 * (Wi-Fi-only / Mobile-only) restriction mode. Full block and full allow do not use
 * this class at all and have no such caveats.
 */
class TcpNatSession(
    private val key: SessionKey,
    private val clientAddress: InetAddress,
    private val tunWriter: TunWriter,
    private val protect: (Socket) -> Boolean,
    private val bindToNetwork: (Socket) -> Boolean,
    private val onClosed: (SessionKey) -> Unit,
) {
    enum class State { CONNECTING, SYN_ACK_SENT, ESTABLISHED, CLOSING, CLOSED }

    @Volatile var state: State = State.CONNECTING
        private set

    private var clientIsn = 0L
    private var serverIsn = 0L
    private var rcvNext = 0L
    private var sendNext = 0L
    private var sendUna = 0L

    private val socket = Socket()
    private var inboundPumpJob: Job? = null
    private val closed = AtomicBoolean(false)

    /**
     * Segments arriving from the client after the initial SYN come from
     * [FirewallEngine]'s single tun-reader thread, which must never block on this
     * session's own I/O (that would stall every *other* app's traffic too). So the
     * reader thread only ever calls the non-suspending [offerClientSegment]; the
     * actual (suspending, potentially slow) handling runs on this session's own
     * consumer coroutine, one segment at a time and strictly in arrival order —
     * important since TCP's seq/ack bookkeeping is not safe to process out of order.
     */
    private val inboundFromClient = Channel<ClientSegment>(Channel.UNLIMITED)
    private data class ClientSegment(val tcp: TcpHeader, val payload: ByteArray, val offset: Int, val length: Int)

    @Volatile
    var lastActivity: Long = System.currentTimeMillis()
        private set

    /** Called once, when the engine sees the initial SYN that opens this session. */
    fun beginHandshake(scope: CoroutineScope, clientSequenceNumber: Long) {
        clientIsn = clientSequenceNumber
        rcvNext = clientSequenceNumber + 1
        serverIsn = Random.nextInt().toLong() and 0xFFFFFFFFL
        scope.launch(Dispatchers.IO) { connectAndHandshake(scope) }
        scope.launch(Dispatchers.Default) {
            for (segment in inboundFromClient) {
                handleClientSegment(segment.tcp, segment.payload, segment.offset, segment.length)
            }
        }
    }

    /** Non-suspending hand-off from the tun-reader thread — see [inboundFromClient]. */
    fun offerClientSegment(tcp: TcpHeader, payload: ByteArray, payloadOffset: Int, payloadLength: Int) {
        inboundFromClient.trySend(ClientSegment(tcp, payload, payloadOffset, payloadLength))
    }

    private suspend fun connectAndHandshake(scope: CoroutineScope) {
        if (!protect(socket) || !bindToNetwork(socket)) {
            Logger.d(TAG, "cannot establish real socket for $key (protect/bind failed)")
            sendReset()
            finish()
            return
        }
        try {
            socket.connect(InetSocketAddress(InetAddress.getByName(key.destinationAddress), key.destinationPort), CONNECT_TIMEOUT_MS)
        } catch (e: IOException) {
            Logger.d(TAG, "connect failed for $key: ${e.message}")
            sendReset()
            finish()
            return
        }
        sendNext = serverIsn + 1
        sendUna = sendNext
        sendControlSegment(seq = serverIsn, ack = rcvNext, flags = TcpHeader.FLAG_SYN or TcpHeader.FLAG_ACK)
        state = State.SYN_ACK_SENT
        lastActivity = System.currentTimeMillis()
        inboundPumpJob = scope.launch(Dispatchers.IO) { pumpInboundFromSocket() }
    }

    /** Processes one client->tun segment, strictly in the order [offerClientSegment] received them. */
    private suspend fun handleClientSegment(tcp: TcpHeader, payload: ByteArray, payloadOffset: Int, payloadLength: Int) {
        lastActivity = System.currentTimeMillis()

        if (tcp.isRst) {
            forceClose()
            return
        }
        if (state == State.SYN_ACK_SENT && tcp.isAck) {
            state = State.ESTABLISHED
        }
        if (tcp.isAck && tcp.ackNumber > sendUna) {
            sendUna = tcp.ackNumber
        }

        if (payloadLength > 0 && state == State.ESTABLISHED) {
            if (tcp.sequenceNumber == rcvNext) {
                val writeOk = withContext(Dispatchers.IO) {
                    runCatching { socket.getOutputStream().write(payload, payloadOffset, payloadLength) }.isSuccess
                }
                if (!writeOk) {
                    forceClose()
                    return
                }
                rcvNext += payloadLength
            }
            // Whether in-order or a retransmit/duplicate, re-ack our current expectation —
            // this is what tells a duplicate-sending client "you can stop retransmitting".
            sendControlSegment(seq = sendNext, ack = rcvNext, flags = TcpHeader.FLAG_ACK)
        }

        if (tcp.isFin) {
            rcvNext += 1
            sendControlSegment(seq = sendNext, ack = rcvNext, flags = TcpHeader.FLAG_ACK)
            runCatching { socket.shutdownOutput() }
            if (state != State.CLOSED) state = State.CLOSING
        }
    }

    private suspend fun pumpInboundFromSocket() {
        val buffer = ByteArray(MSS)
        val input = try {
            socket.getInputStream()
        } catch (e: IOException) {
            forceClose()
            return
        }
        while (state == State.ESTABLISHED || state == State.SYN_ACK_SENT) {
            val read = try {
                input.read(buffer)
            } catch (e: IOException) {
                -1
            }
            if (read < 0) {
                sendControlSegment(seq = sendNext, ack = rcvNext, flags = TcpHeader.FLAG_FIN or TcpHeader.FLAG_ACK)
                sendNext += 1
                state = State.CLOSING
                return
            }
            if (read == 0) continue
            if (!sendDataSegmentAndWaitForAck(buffer, read)) {
                forceClose()
                return
            }
        }
    }

    /** Stop-and-wait send: one in-flight segment, bounded retries — see class kdoc. */
    private suspend fun sendDataSegmentAndWaitForAck(data: ByteArray, length: Int): Boolean {
        val segmentSeq = sendNext
        repeat(MAX_RETRIES) {
            sendSegment(seq = segmentSeq, ack = rcvNext, flags = TcpHeader.FLAG_PSH or TcpHeader.FLAG_ACK, payload = data, payloadLength = length)
            val deadline = System.currentTimeMillis() + RETRANSMIT_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                if (sendUna > segmentSeq) {
                    sendNext = segmentSeq + length
                    return true
                }
                delay(POLL_INTERVAL_MS)
            }
        }
        return false
    }

    private suspend fun sendReset() {
        sendControlSegment(seq = 0, ack = clientIsn + 1, flags = TcpHeader.FLAG_RST or TcpHeader.FLAG_ACK)
    }

    private suspend fun sendControlSegment(seq: Long, ack: Long, flags: Int) =
        sendSegment(seq, ack, flags, payload = ByteArray(0), payloadLength = 0)

    private suspend fun sendSegment(seq: Long, ack: Long, flags: Int, payload: ByteArray, payloadLength: Int) {
        val destAddrBytes = clientAddress.address
        val sourceAddrBytes = InetAddress.getByName(key.destinationAddress).address

        val tcpHeader = TcpHeader.buildHeaderPlaceholder(
            sourcePort = key.destinationPort,
            destinationPort = key.sourcePort,
            sequenceNumber = seq,
            ackNumber = ack,
            flags = flags,
            window = ADVERTISED_WINDOW,
        )
        val segment = ByteArray(tcpHeader.size + payloadLength)
        System.arraycopy(tcpHeader, 0, segment, 0, tcpHeader.size)
        if (payloadLength > 0) System.arraycopy(payload, 0, segment, tcpHeader.size, payloadLength)

        val checksum = Checksum.computeWithPseudoHeader(
            sourceAddress = sourceAddrBytes,
            destAddress = destAddrBytes,
            protocol = IpProtocol.TCP,
            segment = segment,
            segmentLength = segment.size,
        )
        segment[16] = (checksum shr 8).toByte()
        segment[17] = (checksum and 0xFF).toByte()

        val ipHeader = if (destAddrBytes.size == 16) {
            IPv6Packet.buildHeader(
                sourceAddress = sourceAddrBytes,
                destinationAddress = destAddrBytes,
                protocol = IpProtocol.TCP,
                payloadLength = segment.size,
            )
        } else {
            IPv4Packet.buildHeader(
                sourceAddress = sourceAddrBytes,
                destinationAddress = destAddrBytes,
                protocol = IpProtocol.TCP,
                payloadLength = segment.size,
                identification = idCounter.incrementAndGet() and 0xFFFF,
            )
        }

        tunWriter.write(ipHeader + segment, ipHeader.size + segment.size)
    }

    fun isIdle(now: Long, timeoutMillis: Long): Boolean = now - lastActivity > timeoutMillis

    fun forceClose() {
        state = State.CLOSED
        finish()
    }

    private fun finish() {
        if (closed.compareAndSet(false, true)) {
            inboundPumpJob?.cancel()
            inboundFromClient.close()
            runCatching { socket.close() }
            onClosed(key)
        }
    }

    companion object {
        private const val TAG = "TcpNatSession"
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val RETRANSMIT_TIMEOUT_MS = 1_500L
        private const val POLL_INTERVAL_MS = 40L
        private const val MAX_RETRIES = 4
        private const val MSS = 1400
        private const val ADVERTISED_WINDOW = 65535
        private val idCounter = AtomicInteger(0)
    }
}
