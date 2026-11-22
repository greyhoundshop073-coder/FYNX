package com.fynx.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FynxInChatSoundPolicyTest {
    @Test
    fun allowsAlertWhenNotificationsAreEnabledAndChatIsNotMuted() {
        assertTrue(shouldPlayInChatAlert(notificationsEnabled = true, muted = false))
    }

    @Test
    fun suppressesAlertWhenChatNotificationsAreDisabled() {
        assertFalse(shouldPlayInChatAlert(notificationsEnabled = false, muted = false))
    }

    @Test
    fun suppressesAlertWhenLegacyMuteIsEnabled() {
        assertFalse(shouldPlayInChatAlert(notificationsEnabled = true, muted = true))
    }

    @Test
    fun suppressesAlertWhenNotificationsAreDisabledAndChatIsMuted() {
        assertFalse(shouldPlayInChatAlert(notificationsEnabled = false, muted = true))
    }
}
