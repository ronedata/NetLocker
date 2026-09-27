package com.netlocker.network.packet

/** Parsed UDP header (RFC 768) — 8 bytes: source port, dest port, length, checksum. */
class UdpHeader private constructor(
    val sourcePort: Int,
    val destinationPort: Int,
) {
    companion object {
        const val HEADER_LENGTH = 8

        fun parseOrNull(buffer: ByteArray, offset: Int, length: Int): UdpHeader? {
            if (length < HEADER_LENGTH) return null
            val sourcePort = ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)
            val destPort = ((buffer[offset + 2].toInt() and 0xFF) shl 8) or (buffer[offset + 3].toInt() and 0xFF)
            return UdpHeader(sourcePort, destPort)
        }

        /** Builds an 8-byte UDP header; checksum is computed by the caller (needs the IPv4 pseudo-header). */
        fun buildHeaderPlaceholder(sourcePort: Int, destinationPort: Int, payloadLength: Int): ByteArray {
            val header = ByteArray(HEADER_LENGTH)
            header[0] = (sourcePort shr 8).toByte()
            header[1] = (sourcePort and 0xFF).toByte()
            header[2] = (destinationPort shr 8).toByte()
            header[3] = (destinationPort and 0xFF).toByte()
            val length = HEADER_LENGTH + payloadLength
            header[4] = (length shr 8).toByte()
            header[5] = (length and 0xFF).toByte()
            header[6] = 0 // checksum filled in by caller after pseudo-header computation
            header[7] = 0
            return header
        }
    }
}
