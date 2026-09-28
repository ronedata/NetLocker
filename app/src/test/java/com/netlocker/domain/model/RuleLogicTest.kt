package com.netlocker.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Rules-tab logic: status calculation, disabled-rule semantics, filters, counts, labels. */
class RuleLogicTest {

    private fun rule(wifi: Boolean, mobile: Boolean, enabled: Boolean = true) =
        NetworkRule("pkg", wifi, mobile, isEnabled = enabled)

    @Test
    fun `status is computed from the two flags`() {
        assertThat(rule(true, true).status).isEqualTo(RuleStatus.ALLOWED)
        assertThat(rule(false, false).status).isEqualTo(RuleStatus.BLOCKED)
        assertThat(rule(true, false).status).isEqualTo(RuleStatus.WIFI_ONLY)
        assertThat(rule(false, true).status).isEqualTo(RuleStatus.MOBILE_ONLY)
    }

    @Test
    fun `a disabled rule has status DISABLED whatever its saved flags`() {
        assertThat(rule(false, false, enabled = false).status).isEqualTo(RuleStatus.DISABLED)
        assertThat(rule(true, true, enabled = false).status).isEqualTo(RuleStatus.DISABLED)
    }

    @Test
    fun `a disabled rule is not enforced - it behaves exactly like no rule`() {
        val paused = rule(wifi = false, mobile = false, enabled = false)
        assertThat(paused.effectiveWifiAllowed).isTrue()
        assertThat(paused.effectiveMobileDataAllowed).isTrue()
        assertThat(paused.isEffectivelyOpen).isTrue()
        assertThat(paused.accessState).isEqualTo(NetworkAccessState.ALLOWED)
    }

    @Test
    fun `an enabled restrictive rule is enforced and not effectively open`() {
        val blocked = rule(wifi = false, mobile = false)
        assertThat(blocked.effectiveWifiAllowed).isFalse()
        assertThat(blocked.effectiveMobileDataAllowed).isFalse()
        assertThat(blocked.isEffectivelyOpen).isFalse()
    }

    @Test
    fun `the saved flags survive being disabled, so re-enabling restores the rule`() {
        val paused = rule(wifi = true, mobile = false, enabled = false)
        val resumed = paused.copy(isEnabled = true)
        assertThat(resumed.status).isEqualTo(RuleStatus.WIFI_ONLY)
    }

    @Test
    fun `filters match only their own status, and DISABLED only shows under ALL`() {
        assertThat(RuleFilter.BLOCKED.matches(RuleStatus.BLOCKED)).isTrue()
        assertThat(RuleFilter.BLOCKED.matches(RuleStatus.ALLOWED)).isFalse()
        assertThat(RuleFilter.WIFI_ONLY.matches(RuleStatus.WIFI_ONLY)).isTrue()
        assertThat(RuleFilter.MOBILE_ONLY.matches(RuleStatus.MOBILE_ONLY)).isTrue()
        assertThat(RuleFilter.ALLOWED.matches(RuleStatus.ALLOWED)).isTrue()
        for (filter in RuleFilter.entries.filter { it != RuleFilter.ALL }) {
            assertThat(filter.matches(RuleStatus.DISABLED)).isFalse()
        }
        assertThat(RuleFilter.ALL.matches(RuleStatus.DISABLED)).isTrue()
    }

    @Test
    fun `counts come from the actual rules`() {
        val counts = countRules(
            listOf(
                rule(false, false),
                rule(false, false),
                rule(true, false),
                rule(false, true),
                rule(true, true),
                rule(false, false, enabled = false),
            ),
        )
        assertThat(counts.all).isEqualTo(6)
        assertThat(counts.blocked).isEqualTo(2)
        assertThat(counts.wifiOnly).isEqualTo(1)
        assertThat(counts.mobileOnly).isEqualTo(1)
        assertThat(counts.allowed).isEqualTo(1)
        assertThat(counts.of(RuleFilter.BLOCKED)).isEqualTo(2)
    }

    @Test
    fun `counts of an empty list are all zero`() {
        assertThat(countRules(emptyList())).isEqualTo(RuleCounts())
    }

    @Test
    fun `active rules label pluralises correctly`() {
        assertThat(activeRulesLabel(0)).isEqualTo("0 active rules")
        assertThat(activeRulesLabel(1)).isEqualTo("1 active rule")
        assertThat(activeRulesLabel(5)).isEqualTo("5 active rules")
        assertThat(activeRulesLabel(24)).isEqualTo("24 active rules")
    }

    @Test
    fun `a rule with no persisted row defaults to fully allowed and enabled`() {
        val default = NetworkRule.default("brand.new.app")
        assertThat(default.isEnabled).isTrue()
        assertThat(default.isEffectivelyOpen).isTrue()
    }
}
