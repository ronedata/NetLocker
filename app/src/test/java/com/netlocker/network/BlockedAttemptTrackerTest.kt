package com.netlocker.network

import com.google.common.truth.Truth.assertThat
import com.netlocker.domain.model.BlockedEvent
import com.netlocker.domain.repository.BlockedEventsRepository
import com.netlocker.domain.repository.BlockedStatsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

private class FakeBlockedStatsRepository : BlockedStatsRepository {
    data class Record(val packageName: String, val delta: Int, val dnsDelta: Int)
    val records = mutableListOf<Record>()
    override fun observeToday(): Flow<Map<String, com.netlocker.domain.model.BlockedStat>> = throw NotImplementedError()
    override suspend fun record(packageName: String, delta: Int, dnsDelta: Int, atMillis: Long) {
        records += Record(packageName, delta, dnsDelta)
    }
}

private class FakeBlockedEventsRepository : BlockedEventsRepository {
    val recorded = mutableListOf<BlockedEvent>()
    var cleared = 0
    override fun observeRecent(packageName: String, limit: Int): Flow<List<BlockedEvent>> = throw NotImplementedError()
    override suspend fun record(packageName: String, destination: String, atMillis: Long) {
        recorded += BlockedEvent(packageName, destination, atMillis)
    }
    override suspend fun clearAll() {
        cleared++
        recorded.clear()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BlockedAttemptTrackerTest {

    @Test
    fun `splits total and dns counts per app on flush`() = runTest {
        val stats = FakeBlockedStatsRepository()
        val events = FakeBlockedEventsRepository()
        val tracker = BlockedAttemptTracker(stats, events, MutableStateFlow(false))

        tracker.onBlocked("com.a", "6/1/dest:443", isDns = false, destination = "1.2.3.4:443")
        tracker.onBlocked("com.a", "17/2/dest:53", isDns = true, destination = "8.8.8.8:53")
        tracker.onBlocked("com.a", "17/3/dest:53", isDns = true, destination = "8.8.8.8:53")
        tracker.stop()

        assertThat(stats.records).containsExactly(FakeBlockedStatsRepository.Record("com.a", 3, 2))
    }

    @Test
    fun `retransmits of the same flow are only counted once`() = runTest {
        val stats = FakeBlockedStatsRepository()
        val events = FakeBlockedEventsRepository()
        val tracker = BlockedAttemptTracker(stats, events, MutableStateFlow(false), deduper = AttemptDeduper(windowMillis = 30_000))

        tracker.onBlocked("com.a", "same-key", isDns = false, destination = "1.2.3.4:443")
        tracker.onBlocked("com.a", "same-key", isDns = false, destination = "1.2.3.4:443")
        tracker.stop()

        assertThat(stats.records).containsExactly(FakeBlockedStatsRepository.Record("com.a", 1, 0))
    }

    @Test
    fun `does not log destinations while the setting is off`() = runTest {
        val stats = FakeBlockedStatsRepository()
        val events = FakeBlockedEventsRepository()
        val showDestinations = MutableStateFlow(false)
        val tracker = BlockedAttemptTracker(stats, events, showDestinations)

        tracker.start(backgroundScope)
        runCurrent()
        tracker.onBlocked("com.a", "k1", isDns = false, destination = "1.2.3.4:443")
        tracker.stop()

        assertThat(events.recorded).isEmpty()
    }

    @Test
    fun `logs destinations while the setting is on`() = runTest {
        val stats = FakeBlockedStatsRepository()
        val events = FakeBlockedEventsRepository()
        val showDestinations = MutableStateFlow(true)
        val tracker = BlockedAttemptTracker(stats, events, showDestinations)

        tracker.start(backgroundScope)
        runCurrent()
        tracker.onBlocked("com.a", "k1", isDns = false, destination = "1.2.3.4:443")
        tracker.stop()

        assertThat(events.recorded).containsExactly(BlockedEvent("com.a", "1.2.3.4:443", events.recorded.first().atMillis))
    }

    @Test
    fun `turning the setting off drops events queued but not yet flushed`() = runTest {
        val stats = FakeBlockedStatsRepository()
        val events = FakeBlockedEventsRepository()
        val showDestinations = MutableStateFlow(true)
        val tracker = BlockedAttemptTracker(stats, events, showDestinations)

        tracker.start(backgroundScope)
        runCurrent()
        tracker.onBlocked("com.a", "k1", isDns = false, destination = "1.2.3.4:443")
        showDestinations.value = false // turned off before the next flush
        runCurrent()
        tracker.stop()

        assertThat(events.recorded).isEmpty()
    }
}
