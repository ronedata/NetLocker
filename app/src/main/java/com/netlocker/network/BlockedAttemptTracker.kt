package com.netlocker.network

import com.netlocker.domain.repository.BlockedEventsRepository
import com.netlocker.domain.repository.BlockedStatsRepository
import com.netlocker.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Collects blocked-attempt events from the packet thread and writes them to storage in
 * batches. The packet thread only touches in-memory counters/queues (cheap, never blocks);
 * a background job flushes them every few seconds and once more when stopped.
 */
class BlockedAttemptTracker(
    private val statsRepository: BlockedStatsRepository,
    private val eventsRepository: BlockedEventsRepository,
    /** Settings' "Show blocked destinations" — destinations are queued for [eventsRepository]
     *  only while this is true; see [PreferencesManager.showBlockedDestinations]. */
    private val showBlockedDestinations: Flow<Boolean>,
    private val deduper: AttemptDeduper = AttemptDeduper(),
) {
    private val pendingTotal = ConcurrentHashMap<String, AtomicInteger>()
    private val pendingDns = ConcurrentHashMap<String, AtomicInteger>()
    private val pendingEvents = ConcurrentLinkedQueue<PendingEvent>()

    @Volatile private var destinationLoggingEnabled = false

    private var flushJob: Job? = null
    private var prefJob: Job? = null

    /** Called from the tun-reader thread for a flow that was just blocked. */
    fun onBlocked(packageName: String, flowKey: String, isDns: Boolean, destination: String) {
        val now = System.currentTimeMillis()
        if (!deduper.shouldCount(flowKey, now)) return
        pendingTotal.computeIfAbsent(packageName) { AtomicInteger() }.incrementAndGet()
        if (isDns) pendingDns.computeIfAbsent(packageName) { AtomicInteger() }.incrementAndGet()
        if (destinationLoggingEnabled) pendingEvents.add(PendingEvent(packageName, destination, now))
    }

    fun start(scope: CoroutineScope) {
        prefJob?.cancel()
        prefJob = scope.launch {
            showBlockedDestinations.collect { enabled ->
                destinationLoggingEnabled = enabled
                // Don't let events queued a moment before the user turned this off slip
                // through on the next flush — that would resurrect data they just asked
                // to be rid of.
                if (!enabled) pendingEvents.clear()
            }
        }
        flushJob?.cancel()
        flushJob = scope.launch {
            while (true) {
                delay(FLUSH_INTERVAL_MS)
                flush()
                deduper.prune(System.currentTimeMillis())
            }
        }
    }

    /** Stops the periodic job and writes whatever is still pending. */
    suspend fun stop() {
        prefJob?.cancel()
        flushJob?.cancel()
        flush()
    }

    private suspend fun flush() {
        val now = System.currentTimeMillis()
        for (packageName in pendingTotal.keys.toList()) {
            val delta = pendingTotal[packageName]?.getAndSet(0) ?: 0
            val dnsDelta = pendingDns[packageName]?.getAndSet(0) ?: 0
            if (delta > 0) {
                runCatching { statsRepository.record(packageName, delta, dnsDelta, now) }
                    .onFailure { Logger.w(TAG, "could not save blocked count for $packageName", it) }
            }
        }
        val events = generateSequence { pendingEvents.poll() }.toList()
        for (event in events) {
            runCatching { eventsRepository.record(event.packageName, event.destination, event.atMillis) }
                .onFailure { Logger.w(TAG, "could not save blocked destination for ${event.packageName}", it) }
        }
    }

    private data class PendingEvent(val packageName: String, val destination: String, val atMillis: Long)

    private companion object {
        const val TAG = "BlockedAttemptTracker"
        const val FLUSH_INTERVAL_MS = 5_000L
    }
}
