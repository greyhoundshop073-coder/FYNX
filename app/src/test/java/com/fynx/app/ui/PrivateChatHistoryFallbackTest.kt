package com.fynx.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class PrivateChatHistoryFallbackTest {
    @Test
    fun cachedConversationIsNotReportedAsMissingWhenRemoteRefreshFails() {
        assertEquals(
            "Saved messages are shown. Could not refresh them yet; check your connection and retry.",
            privateChatHistoryFailureMessage(hasSavedMessages = true)
        )
    }

    @Test
    fun wrappedDnsAndSocketFailuresAreSafeToQueueForOfflineRetry() {
        assertTrue(FynxProductionMessaging.canQueueOffline(
            IllegalStateException("Request could not be completed", UnknownHostException("Unable to resolve host"))
        ))
        assertTrue(FynxProductionMessaging.canQueueOffline(
            IllegalStateException("Transport failed", ConnectException("Connection refused"))
        ))
        assertTrue(FynxProductionMessaging.canQueueOffline(SocketTimeoutException("Read timed out")))
        assertTrue(FynxProductionMessaging.canQueueOffline(IOException("HTTP 503 Service Unavailable")))
    }

    @Test
    fun authenticationAndValidationFailuresAreNotSilentlyQueuedAsOfflineMessages() {
        assertFalse(FynxProductionMessaging.canQueueOffline(IllegalStateException("HTTP 400 invalid message")))
        assertFalse(FynxProductionMessaging.canQueueOffline(IllegalStateException("HTTP 401 unauthorized")))
        assertFalse(FynxProductionMessaging.canQueueOffline(IllegalStateException("HTTP 403 forbidden")))
        assertFalse(FynxProductionMessaging.canQueueOffline(IllegalArgumentException("A recipient is required.")))
    }

    @Test
    fun emptyConversationKeepsTheInitialLoadFailureMessage() {
        assertEquals(
            "Messages could not load yet. Check your connection and retry.",
            privateChatHistoryFailureMessage(hasSavedMessages = false)
        )
    }
}
