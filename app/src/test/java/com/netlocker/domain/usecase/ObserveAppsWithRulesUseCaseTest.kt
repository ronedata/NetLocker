package com.netlocker.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.netlocker.domain.model.InstalledApp
import com.netlocker.domain.model.NetworkRule
import com.netlocker.domain.repository.InstalledAppRepository
import com.netlocker.domain.repository.NetworkRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Test

private class FakeInstalledAppRepository(initial: List<InstalledApp>) : InstalledAppRepository {
    val apps = MutableStateFlow(initial)
    override fun observeInstalledApps(includeSystemApps: Boolean): Flow<List<InstalledApp>> =
        apps
    override suspend fun refresh() = Unit
}

private class FakeNetworkRuleRepository(initial: Map<String, NetworkRule>) : NetworkRuleRepository {
    val rules = MutableStateFlow(initial)
    override fun observeRules(): Flow<Map<String, NetworkRule>> = rules
    override fun observeRule(packageName: String): Flow<NetworkRule> =
        rules.map { it[packageName] ?: NetworkRule.default(packageName) }
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

class ObserveAppsWithRulesUseCaseTest {

    private fun app(pkg: String, label: String, isSystem: Boolean, uid: Int) =
        InstalledApp(packageName = pkg, uid = uid, label = label, versionName = "1.0", isSystemApp = isSystem, icon = null)

    @Test
    fun `user apps are sorted before system apps, alphabetically within each group`() = runTest {
        val apps = FakeInstalledAppRepository(
            listOf(
                app("com.system.b", "Zeta System", isSystem = true, uid = 2001),
                app("com.user.b", "Banana", isSystem = false, uid = 1002),
                app("com.system.a", "Alpha System", isSystem = true, uid = 2000),
                app("com.user.a", "Apple", isSystem = false, uid = 1001),
            ),
        )
        val rules = FakeNetworkRuleRepository(emptyMap())
        val useCase = ObserveAppsWithRulesUseCase(apps, rules)

        val result = useCase(includeSystemApps = true)
        val labels = result.first().map { it.app.label }

        assertThat(labels).isEqualTo(listOf("Apple", "Banana", "Alpha System", "Zeta System"))
    }

    @Test
    fun `an app with no saved rule defaults to fully allowed`() = runTest {
        val apps = FakeInstalledAppRepository(listOf(app("com.new.app", "New App", isSystem = false, uid = 1000)))
        val rules = FakeNetworkRuleRepository(emptyMap())
        val useCase = ObserveAppsWithRulesUseCase(apps, rules)

        val result = useCase(includeSystemApps = true).first()

        assertThat(result).hasSize(1)
        assertThat(result.first().rule).isEqualTo(NetworkRule.default("com.new.app"))
    }
}
