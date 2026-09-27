package com.netlocker.network.packet

/** Result of parsing one raw packet read from the tun device. */
sealed interface ParsedPacket {

    data class Tcp(
        val ip: IPv4Packet,
        val tcp: TcpHeader,
        val payloadOffset: Int,
        val payloadLength: Int,
    ) : ParsedPacket

    data class Udp(
        val ip: IPv4Packet,
        val udp: UdpHeader,
        val payloadOffset: Int,
        val payloadLength: Int,
    ) : ParsedPacket

    /** IPv4 but not TCP/UDP (e.g. ICMP), or not IPv4 at all — see IPv4Packet's scope note. */
    data object Unsupported : ParsedPacket

    companion object {
        fun parse(buffer: ByteArray, length: Int): ParsedPacket {
            val ip = IPv4Packet.parseOrNull(buffer, length) ?: return Unsupported
            return when (ip.protocol) {
                IpProtocol.TCP -> {
                    val tcp = TcpHeader.parseOrNull(buffer, ip.payloadOffset, ip.payloadLength) ?: return Unsupported
                    val segOffset = ip.payloadOffset + tcp.headerLength
                    val segLength = ip.totalLength - segOffset
                    Tcp(ip, tcp, segOffset, segLength)
                }
                IpProtocol.UDP -> {
                    val udp = UdpHeader.parseOrNull(buffer, ip.payloadOffset, ip.payloadLength) ?: return Unsupported
                    val segOffset = ip.payloadOffset + UdpHeader.HEADER_LENGTH
                    val segLength = ip.totalLength - segOffset
                    Udp(ip, udp, segOffset, segLength)
                }
                else -> Unsupported
            }
        }
    }
}
