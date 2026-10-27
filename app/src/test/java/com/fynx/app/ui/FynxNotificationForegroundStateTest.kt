package com.fynx.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FynxNotificationForegroundStateTest {
    private fun resetState() {
        FynxNotificationForegroundState.setForeground(false)
        FynxNotificationForegroundState.setActivePrivateChat("test-reset")
        FynxNotificationForegroundState.clearActivePrivateChat("test-reset")
        FynxNotificationForegroundState.setActiveGroup("test-reset")
        FynxNotificationForegroundState.clearActiveGroup("test-reset")
    }

    @Test
    fun privateChatNotificationIsSuppressedOnlyForTheOpenForegroundChat() {
        resetState()
        FynxNotificationForegroundState.setActivePrivateChat("@Alice")
        FynxNotificationForegroundState.setForeground(true)
        assertTrue(FynxNotificationForegroundState.shouldSuppressPrivateNotification("alice"))
        assertTrue(FynxNotificationForegroundState.shouldSuppressPrivateNotification("@ALICE"))
        assertFalse(FynxNotificationForegroundState.shouldSuppressPrivateNotification("bob"))
        FynxNotificationForegroundState.setForeground(false)
        assertFalse(FynxNotificationForegroundState.shouldSuppressPrivateNotification("alice"))
        resetState()
    }

    @Test
    fun openingAGroupClearsPrivateChatAndOnlySuppressesThatGroup() {
        resetState()
        FynxNotificationForegroundState.setActivePrivateChat("alice")
        FynxNotificationForegroundState.setActiveGroup("group-1")
        FynxNotificationForegroundState.setForeground(true)
        assertFalse(FynxNotificationForegroundState.shouldSuppressPrivateNotification("alice"))
        assertTrue(FynxNotificationForegroundState.shouldSuppressGroupNotification("group-1"))
        assertFalse(FynxNotificationForegroundState.shouldSuppressGroupNotification("group-2"))
        resetState()
    }
}
