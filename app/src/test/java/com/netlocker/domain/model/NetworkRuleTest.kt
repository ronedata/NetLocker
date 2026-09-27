package com.netlocker.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Covers spec §20 Tests 1–4: the four Wi-Fi/Mobile-Data combinations must map to the
 *  exact access state the UI badges and enforcement decisions both key off of. */
class NetworkRuleTest {

    @Test
    fun `wifi on, mobile on yields ALLOWED`() {
        val rule = NetworkRule("pkg", wifiAllowed = true, mobileDataAllowed = true)
        assertThat(rule.accessState).isEqualTo(NetworkAccessState.ALLOWED)
    }

    @Test
    fun `wifi on, mobile off yields WIFI_ONLY`() {
        val rule = NetworkRule("pkg", wifiAllowed = true, mobileDataAllowed = false)
        assertThat(rule.accessState).isEqualTo(NetworkAccessState.WIFI_ONLY)
    }

    @Test
    fun `wifi off, mobile on yields MOBILE_ONLY`() {
        val rule = NetworkRule("pkg", wifiAllowed = false, mobileDataAllowed = true)
        assertThat(rule.accessState).isEqualTo(NetworkAccessState.MOBILE_ONLY)
    }

    @Test
    fun `wifi off, mobile off yields BLOCKED`() {
        val rule = NetworkRule("pkg", wifiAllowed = false, mobileDataAllowed = false)
        assertThat(rule.accessState).isEqualTo(NetworkAccessState.BLOCKED)
    }

    @Test
    fun `default rule for a never-configured app is fully allowed`() {
        val rule = NetworkRule.default("brand.new.app")
        assertThat(rule.wifiAllowed).isTrue()
        assertThat(rule.mobileDataAllowed).isTrue()
        assertThat(rule.accessState).isEqualTo(NetworkAccessState.ALLOWED)
    }
}
