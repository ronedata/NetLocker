package com.netlocker.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ByteFormatTest {
    @Test fun `bytes below one KB stay in bytes`() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("999 B", formatBytes(999))
    }

    @Test fun `larger sizes pick the right unit and precision`() {
        assertEquals("1.50 KB", formatBytes(1_500))
        assertEquals("12.3 MB", formatBytes(12_300_000))
        assertEquals("123 MB", formatBytes(123_000_000))
        assertEquals("1.20 GB", formatBytes(1_200_000_000))
    }

    @Test fun `text size scales are fixed except follow-system`() {
        assertEquals(1.0f, TextSize.DEFAULT.scale)
        assertEquals(null, TextSize.SYSTEM.scale)
        assert(TextSize.SMALL.scale!! < TextSize.DEFAULT.scale!!)
        assert(TextSize.LARGE.scale!! > TextSize.DEFAULT.scale!!)
    }
}
