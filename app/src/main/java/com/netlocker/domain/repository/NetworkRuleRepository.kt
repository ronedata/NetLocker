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

    /**
     * Creates or updates a rule and marks it **enabled** (changing an app's access is an
     * explicit request to enforce it, so a previously paused rule is resumed). Preserves
     * the rule's original creation time. Survives process death and device reboot.
     */
    suspend fun setRule(packageName: String, wifiAllowed: Boolean, mobileDataAllowed: Boolean)

    /** Pauses/resumes an existing rule without touching its saved Wi-Fi/Mobile values. */
    suspend fun setEnabled(packageName: String, enabled: Boolean)

    /** Removes the rule entirely — the app returns to default (fully allowed). */
    suspend fun deleteRule(packageName: String)
}
