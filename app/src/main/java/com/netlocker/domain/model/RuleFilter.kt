package com.netlocker.domain.model

/** Filter chips on the Rules tab. */
enum class RuleFilter {
    ALL,
    BLOCKED,
    WIFI_ONLY,
    MOBILE_ONLY,
    ALLOWED;

    /** Whether a rule with [status] belongs under this filter. A disabled rule only
     *  appears under [ALL] — it isn't being enforced, so it isn't "blocked" etc. */
    fun matches(status: RuleStatus): Boolean = when (this) {
        ALL -> true
        BLOCKED -> status == RuleStatus.BLOCKED
        WIFI_ONLY -> status == RuleStatus.WIFI_ONLY
        MOBILE_ONLY -> status == RuleStatus.MOBILE_ONLY
        ALLOWED -> status == RuleStatus.ALLOWED
    }
}

/** Per-filter rule counts, always computed from the real rule list (never cached). */
data class RuleCounts(
    val all: Int = 0,
    val blocked: Int = 0,
    val wifiOnly: Int = 0,
    val mobileOnly: Int = 0,
    val allowed: Int = 0,
) {
    fun of(filter: RuleFilter): Int = when (filter) {
        RuleFilter.ALL -> all
        RuleFilter.BLOCKED -> blocked
        RuleFilter.WIFI_ONLY -> wifiOnly
        RuleFilter.MOBILE_ONLY -> mobileOnly
        RuleFilter.ALLOWED -> allowed
    }
}

fun countRules(rules: List<NetworkRule>): RuleCounts = RuleCounts(
    all = rules.size,
    blocked = rules.count { RuleFilter.BLOCKED.matches(it.status) },
    wifiOnly = rules.count { RuleFilter.WIFI_ONLY.matches(it.status) },
    mobileOnly = rules.count { RuleFilter.MOBILE_ONLY.matches(it.status) },
    allowed = rules.count { RuleFilter.ALLOWED.matches(it.status) },
)

/** "1 connection blocked today" / "12 connections blocked today". */
fun connectionsBlockedTodayLabel(count: Int): String =
    if (count == 1) "1 connection blocked today" else "$count connections blocked today"

/** "1 DNS lookup blocked today" / "12 DNS lookups blocked today". */
fun dnsBlockedTodayLabel(count: Int): String =
    if (count == 1) "1 DNS lookup blocked today" else "$count DNS lookups blocked today"

/** "0 active rules" / "1 active rule" / "5 active rules" — "active" = enabled. */
fun activeRulesLabel(activeCount: Int): String =
    if (activeCount == 1) "1 active rule" else "$activeCount active rules"
