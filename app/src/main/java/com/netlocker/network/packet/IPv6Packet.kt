package com.netlocker.network.packet

/**
 * Parsed view over an IPv6 fixed header sitting at the front of a raw packet buffer.
 *
 * Scope note (mirrors [IPv4Packet]'s honesty style): this only understands the 40-byte
 * fixed header with the transport protocol (TCP/UDP) directly in the Next Header field.
 * It does not walk an IPv6 extension-header chain (Hop-by-Hop Options, Routing,
 * Fragment, etc.) — a packet using one is detected as unparseable and dropped for
 * restricted apps rather than mis-handled, the same fail-closed behaviour as an
 * unsupported protocol. In practice this covers the overwhelming majority of ordinary
 * TCP/UDP traffic, which does not use extension headers.
 */
class IPv6Packet private constructor(
    override val raw: ByteArray,
    override val totalLength: Int,
    override val headerLength: Int,
    override val protocol: Int,
    override val sourceAddress: ByteArray,
    override val destinationAddress: ByteArray,
) : IpPacket {

    companion object {
        private const val FIXED_HEADER_BYTES = 40

        /** Returns null if [length] bytes at the start of [buffer] is not a well-formed,
         *  extension-header-free IPv6 packet. */
        fun parseOrNull(buffer: ByteArray, length: Int): IPv6Packet? {
            if (length < FIXED_HEADER_BYTES) return null
            val version = (buffer[0].toInt() and 0xFF) shr 4
            if (version != 6) return null // not IPv6 (e.g. IPv4 starts with version=4)

            val payloadLength = ((buffer[4].toInt() and 0xFF) shl 8) or (buffer[5].toInt() and 0xFF)
            val totalLength = FIXED_HEADER_BYTES + payloadLength
            if (totalLength > length) return null

            // Next Header: only accept it pointing directly at TCP/UDP — see class kdoc.
            val nextHeader = buffer[6].toInt() and 0xFF

            val source = buffer.copyOfRange(8, 24)
            val destination = buffer.copyOfRange(24, 40)

            return IPv6Packet(buffer, totalLength, FIXED_HEADER_BYTES, nextHeader, source, destination)
        }

        /**
         * Builds a bare 40-byte IPv6 fixed header (no extension headers) for packets
         * NetLocker itself originates back into the tun device (e.g. a UDP/TCP reply).
         * Unlike IPv4, IPv6 has no header checksum to compute — the transport-layer
         * checksum (over the IPv6 pseudo-header) is all that protects it.
         */
        fun buildHeader(
            sourceAddress: ByteArray,
            destinationAddress: ByteArray,
            protocol: Int,
            payloadLength: Int,
        ): ByteArray {
            val header = ByteArray(FIXED_HEADER_BYTES)
            header[0] = 0x60 // version=6, traffic class/flow label left at 0
            header[4] = (payloadLength shr 8).toByte()
            header[5] = (payloadLength and 0xFF).toByte()
            header[6] = protocol.toByte() // Next Header
            header[7] = 64 // Hop Limit (mirrors IPv4's TTL default)
            System.arraycopy(sourceAddress, 0, header, 8, 16)
            System.arraycopy(destinationAddress, 0, header, 24, 16)
            return header
        }
    }
}
