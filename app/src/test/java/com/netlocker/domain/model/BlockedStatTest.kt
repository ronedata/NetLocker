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

    @Test fun `label pluralises`() {
        assertEquals("1 blocked attempt today", blockedTodayLabel(1))
        assertEquals("12 blocked attempts today", blockedTodayLabel(12))
    }
}
