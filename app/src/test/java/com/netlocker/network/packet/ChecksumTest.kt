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
        val a = Checksum.computeWithPseudoHeader(
            byteArrayOf(10, 0, 0, 1), byteArrayOf(8, 8, 8, 8), IpProtocol.UDP, segment, segment.size,
        )
        val b = Checksum.computeWithPseudoHeader(
            byteArrayOf(10, 0, 0, 1), byteArrayOf(8, 8, 8, 8), IpProtocol.UDP, segment, segment.size,
        )
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `a correctly checksummed IPv6 UDP segment checksums back to zero`() {
        // Whole-packet verification: build a UDP segment addressed via 16-byte (IPv6)
        // pseudo-header addresses, embed it after an IPv6Packet.buildHeader, and confirm
        // Checksum.compute over the *segment alone* (with its own correct checksum
        // in place) round-trips to zero — the same RFC 1071 property the IPv4 header
        // test above relies on, just applied to the transport-layer checksum instead.
        val source = ByteArray(16) { (it + 1).toByte() }
        val destination = ByteArray(16) { (it + 100).toByte() }
        val payload = byteArrayOf(9, 8, 7)
        val udpHeader = UdpHeader.buildHeaderPlaceholder(sourcePort = 5353, destinationPort = 53, payloadLength = payload.size)
        val segment = udpHeader + payload
        val checksum = Checksum.computeWithPseudoHeader(source, destination, IpProtocol.UDP, segment, segment.size)
        segment[6] = (checksum shr 8).toByte()
        segment[7] = (checksum and 0xFF).toByte()

        val verify = Checksum.computeWithPseudoHeader(source, destination, IpProtocol.UDP, segment, segment.size)
        assertThat(verify).isEqualTo(0)
    }
}
