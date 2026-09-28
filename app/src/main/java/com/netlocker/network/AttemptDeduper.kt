package com.netlocker.network

/**
 * Turns "a blocked packet was seen" into "a blocked connection attempt".
 *
 * A blocked app doesn't give up after one packet: a TCP SYN is retransmitted with the same
 * source port at 1s, 3s, 7s…, and a UDP app sends many datagrams from one socket. Counting
 * every packet would make one attempt look like a dozen, so the same flow key is counted
 * only once per [windowMillis]. A genuinely new attempt uses a new source port (new key).
 */
class AttemptDeduper(private val windowMillis: Long = 30_000L) {
    private val lastCounted = HashMap<String, Long>()

    @Synchronized
    fun shouldCount(flowKey: String, nowMillis: Long): Boolean {
        val last = lastCounted[flowKey]
        if (last != null && nowMillis - last < windowMillis) return false
        lastCounted[flowKey] = nowMillis
        return true
    }

    /** Drops entries older than the window so the map can't grow without bound. */
    @Synchronized
    fun prune(nowMillis: Long) {
        lastCounted.entries.removeAll { nowMillis - it.value >= windowMillis }
    }
}
