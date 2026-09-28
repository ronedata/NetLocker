package com.netlocker.domain.model

/**
 * The user-configured network access rule for one app, identified by [packageName].
 *
 * A rule exists only for apps the user explicitly configured (a row in the database);
 * an app with no row is simply "default" — fully allowed. NetLocker never silently
 * restricts anything the user didn't turn off.
 *
 * [isEnabled] lets the user pause a rule without losing it: a disabled rule keeps its
 * saved [wifiAllowed]/[mobileDataAllowed] values but is **not enforced** — the app
 * behaves exactly as if it had no rule. Every enforcement decision must therefore go
 * through the `effective*` properties below, never the raw flags.
 */
data class NetworkRule(
    val packageName: String,
    val wifiAllowed: Boolean = true,
    val mobileDataAllowed: Boolean = true,
    val isEnabled: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    /** What is actually enforced for Wi-Fi: a disabled rule allows everything. */
    val effectiveWifiAllowed: Boolean get() = !isEnabled || wifiAllowed

    /** What is actually enforced for Mobile Data: a disabled rule allows everything. */
    val effectiveMobileDataAllowed: Boolean get() = !isEnabled || mobileDataAllowed

    /** True when nothing is restricted — the app never needs to enter the VPN tunnel. */
    val isEffectivelyOpen: Boolean get() = effectiveWifiAllowed && effectiveMobileDataAllowed

    /** Access as actually enforced (a disabled rule reads as [NetworkAccessState.ALLOWED]). */
    val accessState: NetworkAccessState
        get() = when {
            effectiveWifiAllowed && effectiveMobileDataAllowed -> NetworkAccessState.ALLOWED
            effectiveWifiAllowed && !effectiveMobileDataAllowed -> NetworkAccessState.WIFI_ONLY
            !effectiveWifiAllowed && effectiveMobileDataAllowed -> NetworkAccessState.MOBILE_ONLY
            else -> NetworkAccessState.BLOCKED
        }

    /** Status shown on the Rules tab: like [accessState], but distinguishes a paused rule. */
    val status: RuleStatus
        get() = if (!isEnabled) {
            RuleStatus.DISABLED
        } else {
            when (accessState) {
                NetworkAccessState.ALLOWED -> RuleStatus.ALLOWED
                NetworkAccessState.WIFI_ONLY -> RuleStatus.WIFI_ONLY
                NetworkAccessState.MOBILE_ONLY -> RuleStatus.MOBILE_ONLY
                NetworkAccessState.BLOCKED -> RuleStatus.BLOCKED
            }
        }

    companion object {
        fun default(packageName: String) = NetworkRule(packageName)
    }
}

/** Human-facing summary of what is enforced for an app, used for badges/status text. */
enum class NetworkAccessState {
    ALLOWED,
    WIFI_ONLY,
    MOBILE_ONLY,
    BLOCKED,
}

/** Rule status on the Rules tab. Order = display order (most restrictive first). */
enum class RuleStatus {
    BLOCKED,
    WIFI_ONLY,
    MOBILE_ONLY,
    ALLOWED,
    DISABLED,
}

/** An [InstalledApp] paired with its current [NetworkRule] — what the Apps tab renders. */
data class AppWithRule(
    val app: InstalledApp,
    val rule: NetworkRule,
)
