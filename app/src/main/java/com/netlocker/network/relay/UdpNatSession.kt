package com.netlocker.network.relay

import com.netlocker.network.packet.Checksum
import com.netlocker.network.packet.IPv4Packet
import com.netlocker.network.packet.IpProtocol
import com.netlocker.network.packet.UdpHeader
import com.netlocker.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Relays one UDP "flow" (identified by [key]) between the app's virtual connection
 * (as seen on the tun device) and a real [DatagramSocket] on the device's actual
 * network. UDP is connectionless so "session" here just means "long enough to relay
 * a request/response and clean up on idle" — there is no handshake to get wrong,
 * which is why this is far simpler (and far more trustworthy) than [TcpNatSession].
 */
class UdpNatSession(
    private val key: SessionKey,
    private val clientAddress: InetAddress,
    private val tunWriter: TunWriter,
    private val protect: (DatagramSocket) -> Boolean,
    private val bindToNetwork: (DatagramSocket) -> Boolean,
    private val onIdle: (SessionKey) -> Unit,
    private val idleTimeoutMillis: Long = 60_000L,
) {
    private val socket = DatagramSocket(null)
    private val destinationAddress = InetAddress.getByName(key.destinationAddress)

    @Volatile
    private var lastActivity = System.currentTimeMillis()
    private var pumpJob: Job? = null
    private var sendPumpJob: Job? = null

    /** Hand-off from [FirewallEngine]'s tun-reader thread, which must never block on
     *  this session's own socket I/O — see the matching note on TcpNatSession. UDP has
     *  no ordering guarantee to begin with, but funnelling sends through one consumer
     *  still keeps this session's socket access single-threaded and simple. */
    private val outboundFromClient = Channel<Datagram>(Channel.UNLIMITED)
    private data class Datagram(val data: ByteArray, val offset: Int, val length: Int)

    /** Opens and binds the real socket. Returns false if the required transport
     *  (the Network [bindToNetwork] targets) isn't currently up — the caller must
     *  treat that as "blocked", not silently fall back to the wrong transport. */
    fun start(scope: CoroutineScope): Boolean {
        socket.reuseAddress = true
        if (!protect(socket)) {
            Logger.d(TAG, "protect() failed for $key")
            socket.close()
            return false
        }
        if (!bindToNetwork(socket)) {
            Logger.d(TAG, "required transport unavailable for $key")
            socket.close()
            return false
        }
        pumpJob = scope.launch(Dispatchers.IO) { pumpInbound() }
        sendPumpJob = scope.launch(Dispatchers.IO) {
            for (datagram in outboundFromClient) sendNow(datagram.data, datagram.offset, datagram.length)
        }
        return true
    }

    /** Non-suspending hand-off from the tun-reader thread. */
    fun offerFromClient(data: ByteArray, offset: Int, length: Int) {
        outboundFromClient.trySend(Datagram(data, offset, length))
    }

    private fun sendNow(data: ByteArray, offset: Int, length: Int) {
        lastActivity = System.currentTimeMillis()
        runCatching {
            socket.send(DatagramPacket(data, offset, length, destinationAddress, key.destinationPort))
        }
    }

    fun isIdle(now: Long): Boolean = now - lastActivity > idleTimeoutMillis

    fun close() {
        pumpJob?.cancel()
        sendPumpJob?.cancel()
        outboundFromClient.close()
        runCatching { socket.close() }
    }

    private suspend fun pumpInbound() {
        val buffer = ByteArray(MAX_UDP_PAYLOAD)
        socket.soTimeout = SOCKET_POLL_TIMEOUT_MS
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(packet)
            } catch (_: SocketTimeoutException) {
                if (isIdle(System.currentTimeMillis())) {
                    onIdle(key)
                    return
                }
                continue
            } catch (e: IOException) {
                onIdle(key)
                return
            }
            lastActivity = System.currentTimeMillis()
            val reply = buildReplyPacket(packet.data, packet.offset, packet.length)
            tunWriter.write(reply, reply.size)
        }
    }

    /** Wraps a datagram received from the real destination back into an IPv4/UDP
     *  packet addressed to the app, as if it came directly from [key.destinationAddress]. */
    private fun buildReplyPacket(data: ByteArray, offset: Int, length: Int): ByteArray {
        val sourceAddrBytes = destinationAddress.address
        val destAddrBytes = clientAddress.address

        val udpHeader = UdpHeader.buildHeaderPlaceholder(
            sourcePort = key.destinationPort,
            destinationPort = key.sourcePort,
            payloadLength = length,
        )
        val segment = ByteArray(udpHeader.size + length)
        System.arraycopy(udpHeader, 0, segment, 0, udpHeader.size)
        System.arraycopy(data, offset, segment, udpHeader.size, length)

        val udpChecksum = Checksum.computeWithIpv4PseudoHeader(
            sourceAddress = sourceAddrBytes,
            destAddress = destAddrBytes,
            protocol = IpProtocol.UDP,
            segment = segment,
            segmentLength = segment.size,
        )
        segment[6] = (udpChecksum shr 8).toByte()
        segment[7] = (udpChecksum and 0xFF).toByte()

        val ipHeader = IPv4Packet.buildHeader(
            sourceAddress = sourceAddrBytes,
            destinationAddress = destAddrBytes,
            protocol = IpProtocol.UDP,
            payloadLength = segment.size,
            identification = idCounter.incrementAndGet() and 0xFFFF,
        )

        return ipHeader + segment
    }

    companion object {
        private const val TAG = "UdpNatSession"
        private const val MAX_UDP_PAYLOAD = 65507
        private const val SOCKET_POLL_TIMEOUT_MS = 5_000
        private val idCounter = AtomicInteger(0)
    }
}
