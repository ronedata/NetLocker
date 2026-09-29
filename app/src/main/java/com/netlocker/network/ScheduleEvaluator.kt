package com.netlocker.network

import com.netlocker.domain.model.NetworkRule
import com.netlocker.domain.model.nowDayOfWeekIndex
import com.netlocker.domain.model.nowMinuteOfDay
import com.netlocker.domain.repository.NetworkRuleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Ticks once a minute — and only while there is something to tick for — to enforce the
 * optional per-app "block during this time window" schedule (Settings §Schedule).
 *
 * Deliberately does nothing (no coroutine even running) unless **both** the Settings
 * master switch is on **and** at least one rule actually has a schedule set — see
 * [com.netlocker.util.PreferencesManager.scheduleMasterEnabled] and
 * [NetworkRuleRepository.observeScheduledRules]. That keeps the zero-overhead default true
 * for the overwhelming majority of installs that never touch this feature at all.
 *
 * A schedule window closing again needs no action here — the next new connection simply
 * gets evaluated fresh by [FirewallEngine.decideForNewFlow], which already checks
 * [NetworkRule.isWithinSchedule] itself. Only *entering* a window needs an explicit nudge,
 * to cut an already-open connection immediately rather than leaving it running until it
 * happens to close on its own.
 */
class ScheduleEvaluator(
    private val scheduleMasterEnabled: Flow<Boolean>,
    private val networkRuleRepository: NetworkRuleRepository,
    /** Called once for each app whose block window just started. */
    private val onScheduleWindowEntered: (packageName: String) -> Unit,
    /** Overridable only by tests, so a tick doesn't mean waiting a real minute. */
    private val tickIntervalMs: Long = TICK_INTERVAL_MS,
    /** Overridable only by tests, so "now" doesn't depend on when the test happens to run. */
    private val now: () -> Int = ::nowMinuteOfDay,
    /** Overridable only by tests, so "today" doesn't depend on when the test happens to run. */
    private val today: () -> Int = ::nowDayOfWeekIndex,
) {
    private var job: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope) {
        job?.cancel()
        job = scope.launch {
            combine(
                scheduleMasterEnabled,
                networkRuleRepository.observeScheduledRules(),
            ) { masterEnabled, scheduledRules -> if (masterEnabled) scheduledRules else emptyList() }
                // A new list (master flipped, or a schedule was added/removed/edited)
                // restarts the tick loop below from a fresh baseline, cancelling any
                // previous one — the standard way to react to a changing input set.
                .collectLatest { scheduledRules -> tick(scheduledRules) }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /** Loops forever for a fixed [scheduledRules] snapshot — never suspends before that
     *  first list is at least computed, so it correctly does nothing for an empty list. */
    private suspend fun tick(scheduledRules: List<NetworkRule>) {
        if (scheduledRules.isEmpty()) return

        // Baseline: whether each app's window happens to already be open right now. Never
        // fires onScheduleWindowEntered from this — only a live minute-to-minute flip does,
        // so restarting this loop (e.g. an unrelated schedule elsewhere was edited) can't
        // spuriously invalidate a session that was already inside its normal window.
        val wasActive = scheduledRules.associate { it.packageName to it.isWithinSchedule(now(), today()) }.toMutableMap()

        while (true) {
            delay(tickIntervalMs)
            for (rule in scheduledRules) {
                val active = rule.isWithinSchedule(now(), today())
                if (active && wasActive[rule.packageName] != true) {
                    onScheduleWindowEntered(rule.packageName)
                }
                wasActive[rule.packageName] = active
            }
        }
    }

    companion object {
        const val TICK_INTERVAL_MS = 60_000L
    }
}
