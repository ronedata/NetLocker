package com.netlocker.network

import com.google.common.truth.Truth.assertThat
import com.netlocker.domain.model.NetworkRule
import com.netlocker.domain.repository.NetworkRuleRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

private class ScheduleFakeNetworkRuleRepository(initial: Map<String, NetworkRule>) : NetworkRuleRepository {
    val rules = MutableStateFlow(initial)
    override fun observeRules(): Flow<Map<String, NetworkRule>> = rules
    override fun observeRule(packageName: String): Flow<NetworkRule> = rules.map { it[packageName] ?: NetworkRule.default(packageName) }
    override suspend fun getRuleOnce(packageName: String): NetworkRule = rules.value[packageName] ?: NetworkRule.default(packageName)
    override suspend fun setRule(packageName: String, wifiAllowed: Boolean, mobileDataAllowed: Boolean) {
        rules.value = rules.value + (packageName to NetworkRule(packageName, wifiAllowed, mobileDataAllowed))
    }
    override suspend fun setEnabled(packageName: String, enabled: Boolean) {
        rules.value[packageName]?.let { rules.value = rules.value + (packageName to it.copy(isEnabled = enabled)) }
    }
    override suspend fun setSchedule(packageName: String, enabled: Boolean, startMinute: Int, endMinute: Int, days: Int) {
        val current = rules.value[packageName] ?: NetworkRule.default(packageName)
        rules.value = rules.value + (packageName to current.copy(scheduleEnabled = enabled, scheduleStartMinute = startMinute, scheduleEndMinute = endMinute, scheduleDays = days))
    }
    override fun observeScheduledRules(): Flow<List<NetworkRule>> =
        rules.map { it.values.filter { rule -> rule.isEnabled && rule.scheduleEnabled } }
    override suspend fun deleteRule(packageName: String) {
        rules.value = rules.value - packageName
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleEvaluatorTest {

    private val tickMs = 60_000L

    @Test
    fun `does nothing while the master switch is off`() = runTest {
        val repo = ScheduleFakeNetworkRuleRepository(mapOf("com.a" to NetworkRule("com.a", scheduleEnabled = true, scheduleStartMinute = 0, scheduleEndMinute = 60)))
        val master = MutableStateFlow(false)
        val entered = mutableListOf<String>()
        var minute = 30 // inside the window, if it were ever evaluated
        val evaluator = ScheduleEvaluator(master, repo, { entered += it }, tickIntervalMs = tickMs, now = { minute }, today = { 3 })

        evaluator.start(backgroundScope)
        runCurrent()
        advanceTimeBy(tickMs * 5)
        runCurrent()

        assertThat(entered).isEmpty()
    }

    @Test
    fun `does nothing when no rule has a schedule`() = runTest {
        val repo = ScheduleFakeNetworkRuleRepository(mapOf("com.a" to NetworkRule("com.a")))
        val master = MutableStateFlow(true)
        val entered = mutableListOf<String>()
        val evaluator = ScheduleEvaluator(master, repo, { entered += it }, tickIntervalMs = tickMs, now = { 0 }, today = { 3 })

        evaluator.start(backgroundScope)
        runCurrent()
        advanceTimeBy(tickMs * 5)
        runCurrent()

        assertThat(entered).isEmpty()
    }

    @Test
    fun `fires exactly once when the window is freshly entered, not again while still inside`() = runTest {
        // 09:00 (540) - 17:00 (1020)
        val repo = ScheduleFakeNetworkRuleRepository(
            mapOf("com.a" to NetworkRule("com.a", scheduleEnabled = true, scheduleStartMinute = 540, scheduleEndMinute = 1020)),
        )
        val master = MutableStateFlow(true)
        val entered = mutableListOf<String>()
        var minute = 530 // just before the window
        val evaluator = ScheduleEvaluator(master, repo, { entered += it }, tickIntervalMs = tickMs, now = { minute }, today = { 3 })

        evaluator.start(backgroundScope)
        runCurrent() // baseline established at minute=530 (outside) — must not fire

        minute = 540 // window opens
        advanceTimeBy(tickMs)
        runCurrent()
        assertThat(entered).containsExactly("com.a")

        // Still inside on later ticks — must not fire again.
        minute = 700
        advanceTimeBy(tickMs)
        runCurrent()
        minute = 900
        advanceTimeBy(tickMs)
        runCurrent()
        assertThat(entered).containsExactly("com.a")
    }

    @Test
    fun `does not fire on the first tick if the window was already open at start`() = runTest {
        val repo = ScheduleFakeNetworkRuleRepository(
            mapOf("com.a" to NetworkRule("com.a", scheduleEnabled = true, scheduleStartMinute = 0, scheduleEndMinute = 600)),
        )
        val master = MutableStateFlow(true)
        val entered = mutableListOf<String>()
        val minute = 100 // already inside the window when the evaluator starts
        val evaluator = ScheduleEvaluator(master, repo, { entered += it }, tickIntervalMs = tickMs, now = { minute }, today = { 3 })

        evaluator.start(backgroundScope)
        runCurrent()
        advanceTimeBy(tickMs * 3)
        runCurrent()

        assertThat(entered).isEmpty()
    }

    @Test
    fun `fires again the next time the window re-opens`() = runTest {
        val repo = ScheduleFakeNetworkRuleRepository(
            mapOf("com.a" to NetworkRule("com.a", scheduleEnabled = true, scheduleStartMinute = 100, scheduleEndMinute = 200)),
        )
        val master = MutableStateFlow(true)
        val entered = mutableListOf<String>()
        var minute = 50
        val evaluator = ScheduleEvaluator(master, repo, { entered += it }, tickIntervalMs = tickMs, now = { minute }, today = { 3 })

        evaluator.start(backgroundScope)
        runCurrent()

        minute = 100 // first entry
        advanceTimeBy(tickMs)
        runCurrent()
        minute = 250 // window closes
        advanceTimeBy(tickMs)
        runCurrent()
        // "Next day" is simply the same minute-of-day recurring — isWithinSchedule has no
        // notion of a calendar day, only 0..1439 — so this is exactly what a real next-day
        // re-entry looks like from the evaluator's point of view.
        minute = 100
        advanceTimeBy(tickMs)
        runCurrent()

        assertThat(entered).containsExactly("com.a", "com.a")
    }

    @Test
    fun `toggling the master switch off stops future ticks from firing`() = runTest {
        val repo = ScheduleFakeNetworkRuleRepository(
            mapOf("com.a" to NetworkRule("com.a", scheduleEnabled = true, scheduleStartMinute = 100, scheduleEndMinute = 200)),
        )
        val master = MutableStateFlow(true)
        val entered = mutableListOf<String>()
        var minute = 50
        val evaluator = ScheduleEvaluator(master, repo, { entered += it }, tickIntervalMs = tickMs, now = { minute }, today = { 3 })

        evaluator.start(backgroundScope)
        runCurrent()

        master.value = false
        minute = 100 // would have entered the window, but the master is off
        advanceTimeBy(tickMs)
        runCurrent()

        assertThat(entered).isEmpty()
    }

    @Test
    fun `does not fire on a day the schedule is not set for`() = runTest {
        val monday = 1
        val tuesday = 2
        val repo = ScheduleFakeNetworkRuleRepository(
            mapOf(
                "com.a" to NetworkRule(
                    "com.a",
                    scheduleEnabled = true,
                    scheduleStartMinute = 100,
                    scheduleEndMinute = 200,
                    scheduleDays = com.netlocker.domain.model.dayBit(monday),
                ),
            ),
        )
        val master = MutableStateFlow(true)
        val entered = mutableListOf<String>()
        var minute = 50
        var day = tuesday
        val evaluator = ScheduleEvaluator(master, repo, { entered += it }, tickIntervalMs = tickMs, now = { minute }, today = { day })

        evaluator.start(backgroundScope)
        runCurrent()

        minute = 100 // would enter the window, but today (Tuesday) isn't a selected day
        advanceTimeBy(tickMs)
        runCurrent()
        assertThat(entered).isEmpty()

        day = monday
        minute = 50 // back outside the window, now on the correct day
        advanceTimeBy(tickMs)
        runCurrent()
        minute = 100 // enters the window on the selected day
        advanceTimeBy(tickMs)
        runCurrent()
        assertThat(entered).containsExactly("com.a")
    }
}
