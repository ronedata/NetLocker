package com.netlocker.network

import android.app.NotificationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FirewallNotificationSpecTest {

    @Test
    fun `minimal and standard use different channels`() {
        assertNotEquals(FirewallNotificationSpec.channelId(true), FirewallNotificationSpec.channelId(false))
        assertEquals(FirewallNotificationSpec.CHANNEL_STANDARD, FirewallNotificationSpec.channelId(false))
    }

    @Test
    fun `minimal channel is lower importance than standard`() {
        assertEquals(NotificationManager.IMPORTANCE_MIN, FirewallNotificationSpec.importance(true))
        assertEquals(NotificationManager.IMPORTANCE_LOW, FirewallNotificationSpec.importance(false))
    }
}
