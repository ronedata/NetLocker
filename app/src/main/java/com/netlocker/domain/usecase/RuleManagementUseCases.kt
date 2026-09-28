package com.netlocker.domain.usecase

import com.netlocker.domain.model.InstalledApp
import com.netlocker.domain.model.NetworkRule
import com.netlocker.domain.model.RuleStatus
import com.netlocker.domain.repository.InstalledAppRepository
import com.netlocker.domain.repository.NetworkRuleRepository
import com.netlocker.network.FirewallController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** A saved rule plus the installed app it belongs to ([app] is null if it was uninstalled). */
data class RuleWithApp(
    val rule: NetworkRule,
    val app: InstalledApp?,
) {
    val displayName: String get() = app?.label ?: rule.packageName
}

/**
 * Backs the Rules tab: only apps the user has a rule for, ordered most-restrictive
 * first (Blocked, Wi-Fi Only, Mobile Only, Allowed, Disabled) then by name — so the
 * things worth reviewing are at the top.
 */
class ObserveRulesWithAppsUseCase(
    private val installedAppRepository: InstalledAppRepository,
    private val networkRuleRepository: NetworkRuleRepository,
) {
    operator fun invoke(): Flow<List<RuleWithApp>> = combine(
        installedAppRepository.observeInstalledApps(includeSystemApps = true),
        networkRuleRepository.observeRules(),
    ) { apps, rules ->
        val appsByPackage = apps.associateBy { it.packageName }
        rules.values
            .map { RuleWithApp(it, appsByPackage[it.packageName]) }
            .sortedWith(
                compareBy<RuleWithApp> { it.rule.status.ordinal }
                    .thenBy { it.displayName.lowercase() },
            )
    }
}

/** Pauses/resumes a rule, then nudges the running firewall so it takes effect at once. */
class SetRuleEnabledUseCase(
    private val networkRuleRepository: NetworkRuleRepository,
    private val firewallController: FirewallController,
) {
    suspend operator fun invoke(packageName: String, enabled: Boolean) {
        networkRuleRepository.setEnabled(packageName, enabled)
        firewallController.notifyRuleChanged(packageName)
    }
}

/**
 * Deletes a rule — the app goes back to default (fully allowed). This only removes
 * NetLocker's own rule; it never touches the app itself.
 */
class DeleteRuleUseCase(
    private val networkRuleRepository: NetworkRuleRepository,
    private val firewallController: FirewallController,
) {
    suspend operator fun invoke(packageName: String) {
        networkRuleRepository.deleteRule(packageName)
        firewallController.notifyRuleChanged(packageName)
    }
}

/** Convenience used by tests/UI to describe a status without importing the enum's order. */
val RuleStatus.isEnforcedWhenFirewallActive: Boolean get() = this != RuleStatus.DISABLED
