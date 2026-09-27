package com.netlocker.network.packet

/**
 * Internet checksum (RFC 1071) used by IPv4, TCP and UDP headers. NetLocker has to
 * compute this itself because packets it hands back to the tun device are ones it
 * assembled (or mutated) in user space — the kernel will not fix up a bad checksum
 * for us, it will just silently drop the packet.
 */
object Checksum {

    /** Plain one's-complement checksum over [data] in the given range. */
    fun compute(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        val end = offset + length
        while (i < end - 1) {
            val word = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < end) {
            sum += (data[i].toInt() and 0xFF) shl 8
        }
        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return sum.inv() and 0xFFFF
    }

    /**
     * TCP/UDP checksum, which is computed over the IPv4 "pseudo-header" (source dest,
     * protocol, segment length) followed by the actual segment — RFC 793 / RFC 768.
     */
    fun computeWithIpv4PseudoHeader(
        sourceAddress: ByteArray,
        destAddress: ByteArray,
        protocol: Int,
        segment: ByteArray,
        segmentLength: Int,
    ): Int {
        var sum = 0
        sum += ((sourceAddress[0].toInt() and 0xFF) shl 8) or (sourceAddress[1].toInt() and 0xFF)
        sum += ((sourceAddress[2].toInt() and 0xFF) shl 8) or (sourceAddress[3].toInt() and 0xFF)
        sum += ((destAddress[0].toInt() and 0xFF) shl 8) or (destAddress[1].toInt() and 0xFF)
        sum += ((destAddress[2].toInt() and 0xFF) shl 8) or (destAddress[3].toInt() and 0xFF)
        sum += protocol and 0xFF
        sum += segmentLength

        var i = 0
        while (i < segmentLength - 1) {
            val word = ((segment[i].toInt() and 0xFF) shl 8) or (segment[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < segmentLength) {
            sum += (segment[i].toInt() and 0xFF) shl 8
        }
        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return sum.inv() and 0xFFFF
    }
}
