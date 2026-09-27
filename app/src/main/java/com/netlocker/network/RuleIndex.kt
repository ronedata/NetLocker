package com.netlocker.network

import com.netlocker.domain.model.NetworkRule
import com.netlocker.domain.repository.InstalledAppRepository
import com.netlocker.domain.repository.NetworkRuleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Fast uid -> [NetworkRule] lookup for the packet-processing hot path.
 *
 * The packet loop runs many times per second and must never suspend, take a lock, or
 * touch Room directly — so this class keeps a plain (lock-free, @Volatile-published)
 * snapshot map that is rebuilt on a background coroutine whenever the installed-app
 * list or the persisted rules change, and read by the firewall engine with a single
 * unsynchronized field read.
 */
class RuleIndex(
    private val installedAppRepository: InstalledAppRepository,
    private val networkRuleRepository: NetworkRuleRepository,
) {
    @Volatile
    private var uidToRule: Map<Int, NetworkRule> = emptyMap()

    /** True once at least one snapshot has been built, so callers can distinguish
     *  "no rule for this uid" (fully allowed) from "index not ready yet" (fail safe/closed). */
    @Volatile
    var isReady: Boolean = false
        private set

    fun start(scope: CoroutineScope) {
        combine(
            installedAppRepository.observeInstalledApps(includeSystemApps = true),
            networkRuleRepository.observeRules(),
        ) { apps, rules ->
            apps.associate { app -> app.uid to (rules[app.packageName] ?: NetworkRule.default(app.packageName)) }
        }.onEach { snapshot ->
            uidToRule = snapshot
            isReady = true
        }.launchIn(scope)
    }

    /** Looks up the effective rule for [uid]. Unknown uids (index not ready, or a
     *  uid Android didn't report as an installed app, e.g. a core system service)
     *  fail *open* only when [isReady] — otherwise callers should treat it as blocked. */
    fun ruleForUid(uid: Int): NetworkRule? = uidToRule[uid]
}
