package com.netlocker.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.netlocker.domain.model.FirewallStatus
import com.netlocker.domain.model.NetworkRule
import com.netlocker.domain.repository.NetworkRuleRepository
import com.netlocker.network.FirewallController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

// Named distinctly from ObserveAppsWithRulesUseCaseTest's fake — both are top-level
// `private` classes in this same package, and Kotlin/JVM still requires distinct
// generated class names across files in a package regardless of visibility.
private class RecordingNetworkRuleRepository : NetworkRuleRepository {
    val saved = mutableMapOf<String, NetworkRule>()
    override fun observeRules(): Flow<Map<String, NetworkRule>> = throw NotImplementedError("not needed for this test")
    override fun observeRule(packageName: String): Flow<NetworkRule> = throw NotImplementedError("not needed for this test")
    override suspend fun getRuleOnce(packageName: String): NetworkRule = saved[packageName] ?: NetworkRule.default(packageName)
    override suspend fun setRule(packageName: String, wifiAllowed: Boolean, mobileDataAllowed: Boolean) {
        saved[packageName] = NetworkRule(packageName, wifiAllowed, mobileDataAllowed)
    }
    override suspend fun setEnabled(packageName: String, enabled: Boolean) {
        saved[packageName]?.let { saved[packageName] = it.copy(isEnabled = enabled) }
    }
    override suspend fun deleteRule(packageName: String) {
        saved.remove(packageName)
    }
}

private class FakeFirewallController : FirewallController {
    override val status: StateFlow<FirewallStatus> = MutableStateFlow(FirewallStatus.Active)
    val notifiedPackages = mutableListOf<String>()
    override fun vpnPermissionIntent() = null
    override fun start() = Unit
    override fun stop() = Unit
    override suspend fun notifyRuleChanged(packageName: String) {
        notifiedPackages += packageName
    }
}

class UpdateNetworkRuleUseCaseTest {

    @Test
    fun `persists the rule and nudges the running firewall`() = runTest {
        val repo = RecordingNetworkRuleRepository()
        val controller = FakeFirewallController()
        val useCase = UpdateNetworkRuleUseCase(repo, controller)

        useCase("com.example.game", wifiAllowed = false, mobileDataAllowed = false)

        assertThat(repo.saved["com.example.game"]).isEqualTo(NetworkRule("com.example.game", false, false))
        assertThat(controller.notifiedPackages).containsExactly("com.example.game")
    }
}
