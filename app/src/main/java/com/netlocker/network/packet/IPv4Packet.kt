package com.netlocker.network.packet

import java.net.InetAddress

/** Transport protocol numbers relevant to enforcement (IANA assigned numbers). */
object IpProtocol {
    const val ICMP = 1
    const val TCP = 6
    const val UDP = 17
}

/**
 * Parsed view over an IPv4 header sitting at the front of a raw packet buffer.
 *
 * Scope note (see README "Known limitations"): NetLocker's relay path only
 * understands IPv4. IPv6-only carriers (common with some operators' 464XLAT / VoLTE
 * data setups) will not have their mobile-data traffic attributed or relayed by this
 * engine. Packets that are not IPv4 are detected and dropped for restricted apps
 * rather than silently mis-handled.
 */
class IPv4Packet private constructor(
    val raw: ByteArray,
    val totalLength: Int,
    val headerLength: Int,
    val protocol: Int,
    val sourceAddress: ByteArray,
    val destinationAddress: ByteArray,
) {
    val payloadOffset: Int get() = headerLength
    val payloadLength: Int get() = totalLength - headerLength

    fun sourceInetAddress(): InetAddress = InetAddress.getByAddress(sourceAddress)
    fun destinationInetAddress(): InetAddress = InetAddress.getByAddress(destinationAddress)

    companion object {
        private const val MIN_HEADER_BYTES = 20

        /** Returns null if [length] bytes at the start of [buffer] is not a well-formed IPv4 header. */
        fun parseOrNull(buffer: ByteArray, length: Int): IPv4Packet? {
            if (length < MIN_HEADER_BYTES) return null
            val versionAndIhl = buffer[0].toInt() and 0xFF
            val version = versionAndIhl shr 4
            if (version != 4) return null // not IPv4 (e.g. IPv6 starts with version=6)

            val ihl = versionAndIhl and 0x0F
            val headerLength = ihl * 4
            if (headerLength < MIN_HEADER_BYTES || headerLength > length) return null

            val totalLength = ((buffer[2].toInt() and 0xFF) shl 8) or (buffer[3].toInt() and 0xFF)
            if (totalLength > length || totalLength < headerLength) return null

            val protocol = buffer[9].toInt() and 0xFF
            val source = buffer.copyOfRange(12, 16)
            val destination = buffer.copyOfRange(16, 20)

            return IPv4Packet(buffer, totalLength, headerLength, protocol, source, destination)
        }

        /**
         * Builds a complete IPv4 header (no options) with a correct checksum, for packets
         * NetLocker itself originates back into the tun device (e.g. a UDP/TCP reply).
         */
        fun buildHeader(
            sourceAddress: ByteArray,
            destinationAddress: ByteArray,
            protocol: Int,
            payloadLength: Int,
            identification: Int,
        ): ByteArray {
            val header = ByteArray(20)
            header[0] = 0x45 // version=4, IHL=5 (20 bytes, no options)
            header[1] = 0
            val totalLength = 20 + payloadLength
            header[2] = (totalLength shr 8).toByte()
            header[3] = (totalLength and 0xFF).toByte()
            header[4] = (identification shr 8).toByte()
            header[5] = (identification and 0xFF).toByte()
            header[6] = 0x40.toByte() // Don't Fragment
            header[7] = 0
            header[8] = 64 // TTL
            header[9] = protocol.toByte()
            header[10] = 0 // checksum placeholder
            header[11] = 0
            System.arraycopy(sourceAddress, 0, header, 12, 4)
            System.arraycopy(destinationAddress, 0, header, 16, 4)

            val checksum = Checksum.compute(header, 0, 20)
            header[10] = (checksum shr 8).toByte()
            header[11] = (checksum and 0xFF).toByte()
            return header
        }
    }
}
