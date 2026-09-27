package com.netlocker.domain.usecase

import com.netlocker.domain.repository.NetworkRuleRepository
import com.netlocker.network.FirewallController

/**
 * Persists a rule change and immediately nudges the running firewall (if active) to
 * re-evaluate it — a rule flip should be near-instant, not wait for the next packet's
 * natural cache miss. See network/FirewallController for what "nudge" means in practice.
 */
class UpdateNetworkRuleUseCase(
    private val networkRuleRepository: NetworkRuleRepository,
    private val firewallController: FirewallController,
) {
    suspend operator fun invoke(packageName: String, wifiAllowed: Boolean, mobileDataAllowed: Boolean) {
        networkRuleRepository.setRule(packageName, wifiAllowed, mobileDataAllowed)
        firewallController.notifyRuleChanged(packageName)
    }
}
