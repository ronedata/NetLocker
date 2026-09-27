package com.netlocker.network.packet

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChecksumTest {

    @Test
    fun `a correctly checksummed IPv4 header checksums back to zero`() {
        val header = IPv4Packet.buildHeader(
            sourceAddress = byteArrayOf(10, 111, 222.toByte(), 1),
            destinationAddress = byteArrayOf(8, 8, 8, 8),
            protocol = IpProtocol.UDP,
            payloadLength = 8,
            identification = 42,
        )
        // RFC 1071 property: checksumming a buffer that already contains its own
        // correct checksum yields zero.
        assertThat(Checksum.compute(header, 0, header.size)).isEqualTo(0)
    }

    @Test
    fun `pseudo-header checksum is stable for identical input`() {
        val segment = byteArrayOf(0, 53, 0, 53, 0, 8, 0, 0)
        val a = Checksum.computeWithIpv4PseudoHeader(
            byteArrayOf(10, 0, 0, 1), byteArrayOf(8, 8, 8, 8), IpProtocol.UDP, segment, segment.size,
        )
        val b = Checksum.computeWithIpv4PseudoHeader(
            byteArrayOf(10, 0, 0, 1), byteArrayOf(8, 8, 8, 8), IpProtocol.UDP, segment, segment.size,
        )
        assertThat(a).isEqualTo(b)
    }
}
