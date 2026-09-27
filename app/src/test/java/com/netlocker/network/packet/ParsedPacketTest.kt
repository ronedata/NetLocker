package com.netlocker.network.packet

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ParsedPacketTest {

    @Test
    fun `builds and parses a UDP packet round-trip`() {
        val payload = byteArrayOf(1, 2, 3, 4, 5)
        val source = byteArrayOf(10, 111, 222.toByte(), 1)
        val destination = byteArrayOf(8, 8, 8, 8)

        val udpHeader = UdpHeader.buildHeaderPlaceholder(sourcePort = 5000, destinationPort = 53, payloadLength = payload.size)
        val segment = udpHeader + payload
        val checksum = Checksum.computeWithIpv4PseudoHeader(source, destination, IpProtocol.UDP, segment, segment.size)
        segment[6] = (checksum shr 8).toByte()
        segment[7] = (checksum and 0xFF).toByte()

        val ipHeader = IPv4Packet.buildHeader(source, destination, IpProtocol.UDP, segment.size, identification = 1)
        val fullPacket = ipHeader + segment

        val parsed = ParsedPacket.parse(fullPacket, fullPacket.size)
        check(parsed is ParsedPacket.Udp)
        assertThat(parsed.udp.sourcePort).isEqualTo(5000)
        assertThat(parsed.udp.destinationPort).isEqualTo(53)
        assertThat(parsed.payloadLength).isEqualTo(payload.size)
        assertThat(parsed.ip.sourceAddress).isEqualTo(source)
        assertThat(parsed.ip.destinationAddress).isEqualTo(destination)
        assertThat(fullPacket.copyOfRange(parsed.payloadOffset, parsed.payloadOffset + parsed.payloadLength)).isEqualTo(payload)
    }

    @Test
    fun `builds and parses a TCP SYN packet round-trip`() {
        val source = byteArrayOf(10, 111, 222.toByte(), 1)
        val destination = byteArrayOf(93, 184.toByte(), 216.toByte(), 34)

        val tcpHeader = TcpHeader.buildHeaderPlaceholder(
            sourcePort = 40000,
            destinationPort = 443,
            sequenceNumber = 1000L,
            ackNumber = 0L,
            flags = TcpHeader.FLAG_SYN,
            window = 65535,
        )
        val checksum = Checksum.computeWithIpv4PseudoHeader(source, destination, IpProtocol.TCP, tcpHeader, tcpHeader.size)
        tcpHeader[16] = (checksum shr 8).toByte()
        tcpHeader[17] = (checksum and 0xFF).toByte()

        val ipHeader = IPv4Packet.buildHeader(source, destination, IpProtocol.TCP, tcpHeader.size, identification = 2)
        val fullPacket = ipHeader + tcpHeader

        val parsed = ParsedPacket.parse(fullPacket, fullPacket.size)
        check(parsed is ParsedPacket.Tcp)
        assertThat(parsed.tcp.sourcePort).isEqualTo(40000)
        assertThat(parsed.tcp.destinationPort).isEqualTo(443)
        assertThat(parsed.tcp.sequenceNumber).isEqualTo(1000L)
        assertThat(parsed.tcp.isSyn).isTrue()
        assertThat(parsed.tcp.isAck).isFalse()
        assertThat(parsed.payloadLength).isEqualTo(0)
    }

    @Test
    fun `a non-IPv4 buffer (e_g_ IPv6) is Unsupported, not mis-parsed`() {
        val fakeIpv6First4Bits = byteArrayOf(0x60, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        assertThat(ParsedPacket.parse(fakeIpv6First4Bits, fakeIpv6First4Bits.size)).isEqualTo(ParsedPacket.Unsupported)
    }

    @Test
    fun `a truncated buffer is Unsupported, not a crash`() {
        val tooShort = byteArrayOf(0x45, 0, 0, 5)
        assertThat(ParsedPacket.parse(tooShort, tooShort.size)).isEqualTo(ParsedPacket.Unsupported)
    }
}
