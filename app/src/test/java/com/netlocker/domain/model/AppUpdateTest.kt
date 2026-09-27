package com.netlocker.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppUpdateTest {

    @Test
    fun `a higher minor version is newer`() {
        assertThat(isNewerVersion(current = "1.0.0", candidate = "1.1.0")).isTrue()
    }

    @Test
    fun `a higher patch version is newer`() {
        assertThat(isNewerVersion(current = "1.0.0", candidate = "1.0.1")).isTrue()
    }

    @Test
    fun `numeric comparison beats lexicographic — 1_10_0 is newer than 1_9_0`() {
        assertThat(isNewerVersion(current = "1.9.0", candidate = "1.10.0")).isTrue()
    }

    @Test
    fun `the same version is not newer`() {
        assertThat(isNewerVersion(current = "1.0.0", candidate = "1.0.0")).isFalse()
    }

    @Test
    fun `an older version is not newer`() {
        assertThat(isNewerVersion(current = "1.2.0", candidate = "1.1.0")).isFalse()
    }

    @Test
    fun `a leading v prefix on the candidate tag is ignored`() {
        assertThat(isNewerVersion(current = "1.0.0", candidate = "v1.1.0")).isTrue()
    }

    @Test
    fun `a non-numeric component falls back to string inequality without throwing`() {
        assertThat(isNewerVersion(current = "1.0.0-beta", candidate = "1.0.0-beta")).isFalse()
    }
}
