package com.netlocker.network.relay

/**
 * Identifies one relayed flow. The tun-assigned client address is always the same
 * fixed address (see NetLockerVpnService's addAddress call) so it is not part of the
 * key — (protocol, client source port, destination address, destination port) is
 * already unique per RFC 793 connection-identification rules.
 */
data class SessionKey(
    val protocol: Int,
    val sourcePort: Int,
    val destinationAddress: String,
    val destinationPort: Int,
)
