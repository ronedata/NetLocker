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
     * TCP/UDP checksum, computed over a pseudo-header (source, destination, protocol,
     * segment length) followed by the actual segment — RFC 793 / RFC 768 for IPv4, RFC
     * 8200 §8.1 for IPv6. One implementation covers both address families: ones'-
     * complement addition is order-independent, and IPv6's pseudo-header just sums more
     * address words (16 bytes vs 4) plus the same zero-padded protocol byte and segment
     * length — so summing whatever length [sourceAddress]/[destAddress] actually are is
     * correct for either family without needing to know which one it is.
     */
    fun computeWithPseudoHeader(
        sourceAddress: ByteArray,
        destAddress: ByteArray,
        protocol: Int,
        segment: ByteArray,
        segmentLength: Int,
    ): Int {
        var sum = 0
        sum += sumBigEndianWords(sourceAddress, sourceAddress.size)
        sum += sumBigEndianWords(destAddress, destAddress.size)
        sum += protocol and 0xFF
        sum += segmentLength
        sum += sumBigEndianWords(segment, segmentLength)
        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return sum.inv() and 0xFFFF
    }

    private fun sumBigEndianWords(data: ByteArray, length: Int): Int {
        var sum = 0
        var i = 0
        while (i < length - 1) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < length) {
            sum += (data[i].toInt() and 0xFF) shl 8
        }
        return sum
    }
}
