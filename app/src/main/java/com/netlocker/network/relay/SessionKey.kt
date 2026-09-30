package com.netlocker.network.relay

/**
 * Identifies one relayed flow. The tun-assigned client address is always one of two
 * fixed addresses (IPv4 or IPv6 — see NetLockerVpnService's addAddress calls), and
 * [destinationAddress]'s family already implies which one applies to a given flow, so
 * it isn't part of the key — (protocol, client source port, destination address,
 * destination port) is already unique per RFC 793 connection-identification rules.
 */
data class SessionKey(
    val protocol: Int,
    val sourcePort: Int,
    val destinationAddress: String,
    val destinationPort: Int,
)
