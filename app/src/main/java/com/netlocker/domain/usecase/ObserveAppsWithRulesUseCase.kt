package com.netlocker.domain.usecase

import com.netlocker.domain.model.AppWithRule
import com.netlocker.domain.model.NetworkRule
import com.netlocker.domain.repository.InstalledAppRepository
import com.netlocker.domain.repository.NetworkRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Joins the installed-app list with persisted rules and orders the result the way
 * the home screen wants it: user-installed apps first (alphabetical), then system
 * apps (alphabetical) — matching spec §4 ("Default-এ user-installed apps আগে").
 */
class ObserveAppsWithRulesUseCase(
    private val installedAppRepository: InstalledAppRepository,
    private val networkRuleRepository: NetworkRuleRepository,
) {
    operator fun invoke(includeSystemApps: Boolean): Flow<List<AppWithRule>> =
        combine(
            installedAppRepository.observeInstalledApps(includeSystemApps),
            networkRuleRepository.observeRules(),
        ) { apps, rules ->
            apps
                .map { app ->
                    AppWithRule(app, rules[app.packageName] ?: NetworkRule.default(app.packageName), hasRule = app.packageName in rules)
                }
                .sortedWith(compareBy({ it.app.isSystemApp }, { it.app.label.lowercase() }))
        }
}
