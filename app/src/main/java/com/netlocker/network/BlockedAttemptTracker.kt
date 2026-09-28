package com.netlocker.network

import com.netlocker.domain.repository.BlockedStatsRepository
import com.netlocker.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Collects blocked-attempt events from the packet thread and writes them to storage in
 * batches. The packet thread only touches an in-memory counter (cheap, never blocks);
 * a background job flushes the totals every few seconds and once more when stopped.
 */
class BlockedAttemptTracker(
    private val repository: BlockedStatsRepository,
    private val deduper: AttemptDeduper = AttemptDeduper(),
) {
    private val pending = ConcurrentHashMap<String, AtomicInteger>()
    private var flushJob: Job? = null

    /** Called from the tun-reader thread for a flow that was just blocked. */
    fun onBlocked(packageName: String, flowKey: String) {
        val now = System.currentTimeMillis()
        if (!deduper.shouldCount(flowKey, now)) return
        pending.computeIfAbsent(packageName) { AtomicInteger() }.incrementAndGet()
    }

    fun start(scope: CoroutineScope) {
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
        flushJob?.cancel()
        flush()
    }

    private suspend fun flush() {
        val now = System.currentTimeMillis()
        for (packageName in pending.keys.toList()) {
            val delta = pending[packageName]?.getAndSet(0) ?: 0
            if (delta > 0) {
                runCatching { repository.record(packageName, delta, now) }
                    .onFailure { Logger.w(TAG, "could not save blocked count for $packageName", it) }
            }
        }
    }

    private companion object {
        const val TAG = "BlockedAttemptTracker"
        const val FLUSH_INTERVAL_MS = 5_000L
    }
}
