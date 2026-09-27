package com.netlocker.network.packet

/**
 * Parsed TCP header (RFC 793). Sequence/ack numbers are kept as [Long] even though the
 * wire format is 32-bit, because Kotlin's Int is signed and TCP sequence arithmetic
 * needs unsigned comparisons — see network/relay/TcpNatSession.kt.
 */
class TcpHeader private constructor(
    val sourcePort: Int,
    val destinationPort: Int,
    val sequenceNumber: Long,
    val ackNumber: Long,
    val headerLength: Int,
    val flags: Int,
    val window: Int,
) {
    val isSyn get() = flags and FLAG_SYN != 0
    val isAck get() = flags and FLAG_ACK != 0
    val isFin get() = flags and FLAG_FIN != 0
    val isRst get() = flags and FLAG_RST != 0
    val isPsh get() = flags and FLAG_PSH != 0

    companion object {
        const val FLAG_FIN = 0x01
        const val FLAG_SYN = 0x02
        const val FLAG_RST = 0x04
        const val FLAG_PSH = 0x08
        const val FLAG_ACK = 0x10

        private const val MIN_HEADER_BYTES = 20

        fun parseOrNull(buffer: ByteArray, offset: Int, length: Int): TcpHeader? {
            if (length < MIN_HEADER_BYTES) return null
            val sourcePort = u16(buffer, offset)
            val destPort = u16(buffer, offset + 2)
            val seq = u32(buffer, offset + 4)
            val ack = u32(buffer, offset + 8)
            val dataOffsetByte = buffer[offset + 12].toInt() and 0xFF
            val headerLength = (dataOffsetByte shr 4) * 4
            if (headerLength < MIN_HEADER_BYTES || headerLength > length) return null
            val flags = buffer[offset + 13].toInt() and 0x3F
            val window = u16(buffer, offset + 14)
            return TcpHeader(sourcePort, destPort, seq, ack, headerLength, flags, window)
        }

        /** Builds a bare 20-byte TCP header (no options) with the checksum field left zeroed. */
        fun buildHeaderPlaceholder(
            sourcePort: Int,
            destinationPort: Int,
            sequenceNumber: Long,
            ackNumber: Long,
            flags: Int,
            window: Int,
        ): ByteArray {
            val header = ByteArray(MIN_HEADER_BYTES)
            header[0] = (sourcePort shr 8).toByte()
            header[1] = (sourcePort and 0xFF).toByte()
            header[2] = (destinationPort shr 8).toByte()
            header[3] = (destinationPort and 0xFF).toByte()
            putU32(header, 4, sequenceNumber)
            putU32(header, 8, ackNumber)
            header[12] = (5 shl 4).toByte() // data offset = 5 words (20 bytes), no options
            header[13] = (flags and 0x3F).toByte()
            header[14] = (window shr 8).toByte()
            header[15] = (window and 0xFF).toByte()
            header[16] = 0 // checksum placeholder
            header[17] = 0
            header[18] = 0 // urgent pointer
            header[19] = 0
            return header
        }

        private fun u16(buffer: ByteArray, offset: Int): Int =
            ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)

        private fun u32(buffer: ByteArray, offset: Int): Long {
            var value = 0L
            for (i in 0 until 4) {
                value = (value shl 8) or (buffer[offset + i].toLong() and 0xFF)
            }
            return value
        }

        private fun putU32(buffer: ByteArray, offset: Int, value: Long) {
            buffer[offset] = (value shr 24).toByte()
            buffer[offset + 1] = (value shr 16).toByte()
            buffer[offset + 2] = (value shr 8).toByte()
            buffer[offset + 3] = value.toByte()
        }
    }
}
