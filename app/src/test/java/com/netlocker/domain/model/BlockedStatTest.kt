package com.netlocker.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class BlockedStatTest {
    private val dhaka = ZoneId.of("Asia/Dhaka")

    private fun millis(y: Int, m: Int, d: Int, h: Int, min: Int) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, dhaka).toInstant().toEpochMilli()

    @Test fun `day number changes exactly at local midnight`() {
        val late = millis(2026, 9, 28, 23, 59)
        val early = millis(2026, 9, 29, 0, 1)
        assertNotEquals(dayOf(late, dhaka), dayOf(early, dhaka))
        assertEquals(dayOf(millis(2026, 9, 28, 0, 1), dhaka), dayOf(late, dhaka))
    }

    @Test fun `millis until next day counts down to local midnight`() {
        assertEquals(60_000L, millisUntilNextDay(millis(2026, 9, 28, 23, 59), dhaka))
        assertEquals(24 * 60 * 60_000L, millisUntilNextDay(millis(2026, 9, 28, 0, 0), dhaka))
    }

    @Test fun `connections label pluralises`() {
        assertEquals("1 connection blocked today", connectionsBlockedTodayLabel(1))
        assertEquals("12 connections blocked today", connectionsBlockedTodayLabel(12))
    }

    @Test fun `dns label pluralises`() {
        assertEquals("1 DNS lookup blocked today", dnsBlockedTodayLabel(1))
        assertEquals("12 DNS lookups blocked today", dnsBlockedTodayLabel(12))
    }

    @Test fun `connectionCount is the total minus the dns count`() {
        val stat = BlockedStat(packageName = "pkg", count = 10, dnsCount = 7, lastBlockedAt = 0L)
        assertEquals(3, stat.connectionCount)
    }
}
