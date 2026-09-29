package com.netlocker.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkRuleScheduleTest {

    // Wednesday, arbitrary — every-day-mask tests don't care which day this is.
    private val wednesday = 3

    private fun ruleWithSchedule(enabled: Boolean, start: Int, end: Int, days: Int = ALL_DAYS_MASK) =
        NetworkRule("pkg", scheduleEnabled = enabled, scheduleStartMinute = start, scheduleEndMinute = end, scheduleDays = days)

    @Test fun `disabled schedule is never within window`() {
        val rule = ruleWithSchedule(enabled = false, start = 0, end = 360)
        assertFalse(rule.isWithinSchedule(0, wednesday))
        assertFalse(rule.isWithinSchedule(100, wednesday))
    }

    @Test fun `ordinary same-day window, every day selected`() {
        // 09:00 (540) - 17:00 (1020)
        val rule = ruleWithSchedule(enabled = true, start = 540, end = 1020)
        assertFalse(rule.isWithinSchedule(539, wednesday))
        assertTrue(rule.isWithinSchedule(540, wednesday)) // start is inclusive
        assertTrue(rule.isWithinSchedule(800, wednesday))
        assertFalse(rule.isWithinSchedule(1020, wednesday)) // end is exclusive
        assertFalse(rule.isWithinSchedule(1021, wednesday))
    }

    @Test fun `window crossing midnight, every day selected`() {
        // 22:00 (1320) - 06:00 (360)
        val rule = ruleWithSchedule(enabled = true, start = 1320, end = 360)
        assertTrue(rule.isWithinSchedule(1320, wednesday)) // start is inclusive
        assertTrue(rule.isWithinSchedule(1439, wednesday)) // 23:59
        assertTrue(rule.isWithinSchedule(0, wednesday)) // midnight
        assertTrue(rule.isWithinSchedule(359, wednesday))
        assertFalse(rule.isWithinSchedule(360, wednesday)) // end is exclusive
        assertFalse(rule.isWithinSchedule(720, wednesday)) // noon — clearly outside
    }

    @Test fun `start equal to end blocks the entire day`() {
        val rule = ruleWithSchedule(enabled = true, start = 600, end = 600)
        // start <= end branch: `in 600 until 600` is empty, so nothing is "inside" —
        // this documents the actual (all-open, not all-blocked) behaviour of that edge
        // case, which the UI separately warns the user about (see ScheduleForm).
        assertFalse(rule.isWithinSchedule(600, wednesday))
        assertFalse(rule.isWithinSchedule(0, wednesday))
    }

    @Test fun `same-day window only applies on a selected day`() {
        val monday = 1
        val tuesday = 2
        val rule = ruleWithSchedule(enabled = true, start = 540, end = 1020, days = dayBit(monday))
        assertTrue(rule.isWithinSchedule(800, monday))
        assertFalse(rule.isWithinSchedule(800, tuesday))
    }

    @Test fun `overnight window with only the start day selected still covers the morning after`() {
        val friday = 5
        val saturday = 6
        // Friday 22:00 - Saturday 06:00, only Friday checked — "block Friday night" should
        // still be active during Saturday's early hours, not just up to midnight.
        val rule = ruleWithSchedule(enabled = true, start = 1320, end = 360, days = dayBit(friday))
        assertTrue(rule.isWithinSchedule(1320, friday)) // Friday evening
        assertTrue(rule.isWithinSchedule(0, saturday)) // Saturday midnight — still Friday's window
        assertTrue(rule.isWithinSchedule(359, saturday)) // Saturday 5:59am
        assertFalse(rule.isWithinSchedule(360, saturday)) // Saturday 6:00am — window over
        assertFalse(rule.isWithinSchedule(1320, saturday)) // Saturday 22:00 — Saturday not selected
    }

    @Test fun `hasDay reflects only the selected bits`() {
        val rule = ruleWithSchedule(enabled = true, start = 0, end = 1, days = dayBit(0) or dayBit(6))
        assertTrue(rule.hasDay(0)) // Sunday
        assertTrue(rule.hasDay(6)) // Saturday
        assertFalse(rule.hasDay(3)) // Wednesday
    }

    @Test fun `nowMinuteOfDay stays within a single day's range`() {
        val minute = nowMinuteOfDay()
        assertTrue(minute in 0..1439)
    }

    @Test fun `nowDayOfWeekIndex stays within 0 to 6`() {
        val day = nowDayOfWeekIndex()
        assertTrue(day in 0..6)
    }

    @Test fun `dayOfWeekIndex maps Sunday to 0 and Saturday to 6`() {
        // 2026-09-27 is a Sunday, 2026-10-03 the following Saturday.
        assertEquals(0, dayOfWeekIndex(java.time.LocalDate.of(2026, 9, 27)))
        assertEquals(1, dayOfWeekIndex(java.time.LocalDate.of(2026, 9, 28))) // Monday
        assertEquals(6, dayOfWeekIndex(java.time.LocalDate.of(2026, 10, 3)))
    }
}
