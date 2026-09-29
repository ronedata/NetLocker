package com.netlocker.domain.model

import java.time.Instant
import java.time.ZoneId

/** How many connection attempts NetLocker blocked for one app on one day. [count] is the
 *  total; [dnsCount] is how many of those were DNS lookups rather than a data connection
 *  — a busy blocked app (e.g. a browser) can rack up far more DNS lookups than actual
 *  connection attempts, so splitting them out keeps the number meaningful. */
data class BlockedStat(
    val packageName: String,
    val count: Int,
    val dnsCount: Int,
    /** Epoch millis of the most recent blocked attempt. */
    val lastBlockedAt: Long,
) {
    val connectionCount: Int get() = count - dnsCount
}

/** The local calendar day (as an epoch-day number) that [nowMillis] falls on. */
fun dayOf(nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
    Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().toEpochDay()

/** Milliseconds from [nowMillis] until the next local midnight. */
fun millisUntilNextDay(nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val nextMidnight = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return nextMidnight - nowMillis
}
