package com.netlocker.data

import com.netlocker.data.repository.GithubUpdateRepositoryImpl.Companion.MESSAGE_GENERIC_FAILURE
import com.netlocker.data.repository.GithubUpdateRepositoryImpl.Companion.MESSAGE_NO_CONNECTION
import com.netlocker.data.repository.GithubUpdateRepositoryImpl.Companion.friendlyUpdateError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class UpdateMessagesTest {
    @Test fun `offline errors get a plain connection message`() {
        assertEquals(MESSAGE_NO_CONNECTION, friendlyUpdateError(UnknownHostException("Unable to resolve host \"api.github.com\"")))
        assertEquals(MESSAGE_NO_CONNECTION, friendlyUpdateError(SocketTimeoutException("timeout")))
    }

    @Test fun `unknown errors never leak the raw exception text`() {
        val message = friendlyUpdateError(IllegalStateException("boom github.com"))
        assertEquals(MESSAGE_GENERIC_FAILURE, message)
    }

    @Test fun `messages do not mention where updates come from`() {
        listOf(MESSAGE_NO_CONNECTION, MESSAGE_GENERIC_FAILURE).forEach {
            assertFalse(it.contains("github", ignoreCase = true))
        }
    }
}
