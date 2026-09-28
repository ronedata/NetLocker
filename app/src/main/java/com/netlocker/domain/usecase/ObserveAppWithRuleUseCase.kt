package com.netlocker.domain.usecase

import com.netlocker.domain.model.AppWithRule
import com.netlocker.domain.model.NetworkRule
import com.netlocker.domain.repository.InstalledAppRepository
import com.netlocker.domain.repository.NetworkRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapNotNull

/** Backs the App Details screen (spec §12): one app's info + its live rule. */
class ObserveAppWithRuleUseCase(
    private val installedAppRepository: InstalledAppRepository,
    private val networkRuleRepository: NetworkRuleRepository,
) {
    operator fun invoke(packageName: String): Flow<AppWithRule> =
        combine(
            installedAppRepository.observeInstalledApps(includeSystemApps = true),
            networkRuleRepository.observeRules(),
        ) { apps, rules ->
            val app = apps.find { it.packageName == packageName } ?: return@combine null
            AppWithRule(app, rules[packageName] ?: NetworkRule.default(packageName), hasRule = packageName in rules)
        }.mapNotNull { it }
}
