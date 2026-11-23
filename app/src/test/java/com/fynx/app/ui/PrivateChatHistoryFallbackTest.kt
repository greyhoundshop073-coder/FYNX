package com.fynx.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PrivateChatHistoryFallbackTest {
    @Test
    fun cachedConversationIsNotReportedAsMissingWhenRemoteRefreshFails() {
        assertEquals(
            "Saved messages are shown. Could not refresh them yet; check your connection and retry.",
            privateChatHistoryFailureMessage(hasSavedMessages = true)
        )
    }

    @Test
    fun emptyConversationKeepsTheInitialLoadFailureMessage() {
        assertEquals(
            "Messages could not load yet. Check your connection and retry.",
            privateChatHistoryFailureMessage(hasSavedMessages = false)
        )
    }
}
