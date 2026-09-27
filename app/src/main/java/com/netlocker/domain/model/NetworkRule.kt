package com.netlocker.domain.model

/**
 * The user-configured network access rule for one app, identified by [packageName].
 *
 * Defaults to fully allowed: a freshly discovered app is never silently restricted —
 * NetLocker only blocks what the user explicitly turns off.
 */
data class NetworkRule(
    val packageName: String,
    val wifiAllowed: Boolean = true,
    val mobileDataAllowed: Boolean = true,
) {
    val accessState: NetworkAccessState
        get() = when {
            wifiAllowed && mobileDataAllowed -> NetworkAccessState.ALLOWED
            wifiAllowed && !mobileDataAllowed -> NetworkAccessState.WIFI_ONLY
            !wifiAllowed && mobileDataAllowed -> NetworkAccessState.MOBILE_ONLY
            else -> NetworkAccessState.BLOCKED
        }

    companion object {
        fun default(packageName: String) = NetworkRule(packageName)
    }
}

/** Human-facing summary of a [NetworkRule], used for badges/status text (see spec §13). */
enum class NetworkAccessState {
    ALLOWED,
    WIFI_ONLY,
    MOBILE_ONLY,
    BLOCKED,
}

/** An [InstalledApp] paired with its current [NetworkRule] — what the UI actually renders. */
data class AppWithRule(
    val app: InstalledApp,
    val rule: NetworkRule,
)
