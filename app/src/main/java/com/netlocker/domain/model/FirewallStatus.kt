package com.netlocker.domain.model

/**
 * Real, observable state of the enforcement layer. The UI renders exactly this —
 * never an assumed/optimistic state — so a rule the OS refused to enforce is never
 * shown to the user as if it succeeded (see spec §21, "no fake success").
 */
sealed interface FirewallStatus {

    /** VPN not running. Rules are saved but nothing is currently being enforced. */
    data object Stopped : FirewallStatus

    /** User has not yet granted the system VPN consent dialog. */
    data object PermissionRequired : FirewallStatus

    /** Tunnel is up and actively filtering traffic per-app. */
    data object Active : FirewallStatus

    /** Enforcement could not start or was torn down; [reason] is shown verbatim to the user. */
    data class Error(val reason: String) : FirewallStatus
}
