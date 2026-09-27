package com.netlocker.domain.repository

import com.netlocker.domain.model.NetworkRule
import kotlinx.coroutines.flow.Flow

/** Source of truth for user-configured per-app network rules. Backed by Room; see data/local. */
interface NetworkRuleRepository {

    /** All persisted rules, keyed by package name. Apps with no explicit rule are absent
     *  here — callers should treat a missing entry as [NetworkRule.default]. */
    fun observeRules(): Flow<Map<String, NetworkRule>>

    /** A single app's rule, defaulting to fully-allowed if none was ever set. */
    fun observeRule(packageName: String): Flow<NetworkRule>

    suspend fun getRuleOnce(packageName: String): NetworkRule

    /** Persists a rule. Survives process death and device reboot (Room + disk). */
    suspend fun setRule(packageName: String, wifiAllowed: Boolean, mobileDataAllowed: Boolean)
}
