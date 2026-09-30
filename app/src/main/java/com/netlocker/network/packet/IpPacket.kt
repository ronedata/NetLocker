package com.netlocker.network.packet

import java.net.InetAddress

/** Common shape shared by [IPv4Packet] and [IPv6Packet] so the rest of the engine
 *  (ParsedPacket, FirewallEngine, the relay sessions) can handle either family without
 *  caring which one a given flow is — the only family-specific code left is each
 *  packet type's own header parsing/building. */
interface IpPacket {
    val raw: ByteArray
    val totalLength: Int
    val headerLength: Int
    val protocol: Int
    val sourceAddress: ByteArray
    val destinationAddress: ByteArray

    val payloadOffset: Int get() = headerLength
    val payloadLength: Int get() = totalLength - headerLength

    fun sourceInetAddress(): InetAddress = InetAddress.getByAddress(sourceAddress)
    fun destinationInetAddress(): InetAddress = InetAddress.getByAddress(destinationAddress)
}
