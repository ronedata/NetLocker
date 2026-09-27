package com.netlocker.network

import com.google.common.truth.Truth.assertThat
import com.netlocker.domain.model.InstalledApp
import com.netlocker.domain.model.NetworkRule
import com.netlocker.domain.repository.InstalledAppRepository
import com.netlocker.domain.repository.NetworkRuleRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

private class FakeInstalledAppRepository(initial: List<InstalledApp>) : InstalledAppRepository {
    val apps = MutableStateFlow(initial)
    override fun observeInstalledApps(includeSystemApps: Boolean): Flow<List<InstalledApp>> = apps
    override suspend fun refresh() = Unit
}

private class FakeNetworkRuleRepository(initial: Map<String, NetworkRule>) : NetworkRuleRepository {
    val rules = MutableStateFlow(initial)
    override fun observeRules(): Flow<Map<String, NetworkRule>> = rules
    override fun observeRule(packageName: String): Flow<NetworkRule> = rules.map { it[packageName] ?: NetworkRule.default(packageName) }
    override suspend fun getRuleOnce(packageName: String): NetworkRule = rules.value[packageName] ?: NetworkRule.default(packageName)
    override suspend fun setRule(packageName: String, wifiAllowed: Boolean, mobileDataAllowed: Boolean) {
        rules.value = rules.value + (packageName to NetworkRule(packageName, wifiAllowed, mobileDataAllowed))
    }
}

/** [RuleIndex] is the packet loop's only source of truth for "what's this uid allowed
 *  to do" — a bug here silently breaks enforcement for every app, so it gets its own
 *  focused test rather than only being covered indirectly through FirewallEngine. */
@OptIn(ExperimentalCoroutinesApi::class)
class RuleIndexTest {

    private fun app(pkg: String, uid: Int) =
        InstalledApp(packageName = pkg, uid = uid, label = pkg, versionName = null, isSystemApp = false, icon = null)

    @Test
    fun `is not ready until the first snapshot has been built`() = runTest {
        val apps = FakeInstalledAppRepository(listOf(app("com.a", 1001)))
        val rules = FakeNetworkRuleRepository(emptyMap())
        val index = RuleIndex(apps, rules)

        assertThat(index.isReady).isFalse()

        index.start(backgroundScope)
        // runCurrent(), not advanceUntilIdle(): a backgroundScope-launched collector
        // was found not to reliably run under advanceUntilIdle() with this project's
        // AGP/kotlinx-coroutines-test combination (verified with a minimal repro);
        // runCurrent() does flush it and is sufficient since nothing here uses delay().
        runCurrent()

        assertThat(index.isReady).isTrue()
    }

    @Test
    fun `resolves a rule by uid, defaulting to fully-allowed when none was saved`() = runTest {
        val apps = FakeInstalledAppRepository(listOf(app("com.blocked", 1001), app("com.default", 1002)))
        val rules = FakeNetworkRuleRepository(mapOf("com.blocked" to NetworkRule("com.blocked", false, false)))
        val index = RuleIndex(apps, rules)

        index.start(backgroundScope)
        runCurrent()

        assertThat(index.ruleForUid(1001)).isEqualTo(NetworkRule("com.blocked", false, false))
        assertThat(index.ruleForUid(1002)).isEqualTo(NetworkRule.default("com.default"))
    }

    @Test
    fun `an unknown uid (not in the installed-app snapshot) has no rule`() = runTest {
        val apps = FakeInstalledAppRepository(emptyList())
        val rules = FakeNetworkRuleRepository(emptyMap())
        val index = RuleIndex(apps, rules)

        index.start(backgroundScope)
        runCurrent()

        assertThat(index.ruleForUid(9999)).isNull()
    }

    @Test
    fun `updates its snapshot when the underlying rules change`() = runTest {
        val apps = FakeInstalledAppRepository(listOf(app("com.a", 1001)))
        val rules = FakeNetworkRuleRepository(emptyMap())
        val index = RuleIndex(apps, rules)

        index.start(backgroundScope)
        runCurrent()
        assertThat(index.ruleForUid(1001)?.wifiAllowed).isTrue()

        rules.setRule("com.a", wifiAllowed = false, mobileDataAllowed = true)
        runCurrent()

        assertThat(index.ruleForUid(1001)).isEqualTo(NetworkRule("com.a", false, true))
    }
}
