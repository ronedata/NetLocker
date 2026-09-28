package com.netlocker.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttemptDeduperTest {

    @Test fun `first sighting counts, retransmits inside the window do not`() {
        val deduper = AttemptDeduper(windowMillis = 30_000)
        assertTrue(deduper.shouldCount("6/50001/1.2.3.4:443", 0))
        assertFalse(deduper.shouldCount("6/50001/1.2.3.4:443", 1_000)) // SYN retry
        assertFalse(deduper.shouldCount("6/50001/1.2.3.4:443", 3_000))
        assertFalse(deduper.shouldCount("6/50001/1.2.3.4:443", 29_999))
    }

    @Test fun `a new source port is a new attempt`() {
        val deduper = AttemptDeduper(windowMillis = 30_000)
        assertTrue(deduper.shouldCount("6/50001/1.2.3.4:443", 0))
        assertTrue(deduper.shouldCount("6/50002/1.2.3.4:443", 100))
    }

    @Test fun `the same flow counts again once the window has passed`() {
        val deduper = AttemptDeduper(windowMillis = 30_000)
        assertTrue(deduper.shouldCount("k", 0))
        assertTrue(deduper.shouldCount("k", 30_000))
    }

    @Test fun `prune forgets old entries`() {
        val deduper = AttemptDeduper(windowMillis = 30_000)
        deduper.shouldCount("k", 0)
        deduper.prune(60_000)
        assertTrue(deduper.shouldCount("k", 60_001))
    }
}
