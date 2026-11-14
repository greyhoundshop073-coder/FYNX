package com.fynx.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FynxBackendSessionBoundaryTest {
    @Test
    fun staleUnauthorizedResponseCannotClearTheNextAccountsSession() {
        assertFalse(FynxBackendClient.shouldClearRejectedAccessToken("account-A-token", "account-B-token"))
    }

    @Test
    fun rejectedTokenStillClearsTheSessionWhenItIsTheCurrentToken() {
        assertTrue(FynxBackendClient.shouldClearRejectedAccessToken("current-token", "current-token"))
    }

    @Test
    fun missingRequestTokenCannotClearALaterLogin() {
        assertFalse(FynxBackendClient.shouldClearRejectedAccessToken(null, "new-login-token"))
        assertFalse(FynxBackendClient.shouldClearRejectedAccessToken("", "new-login-token"))
        assertFalse(FynxBackendClient.shouldClearRejectedAccessToken("   ", "new-login-token"))
    }

    @Test
    fun alreadyClearedSessionIsNotClearedAgain() {
        assertFalse(FynxBackendClient.shouldClearRejectedAccessToken("expired-token", null))
    }

    @Test
    fun groupSendAndAttachmentUploadMustRemainInTheInitiatingAccountAndToken() {
        assertTrue(FynxBackendClient.isRequestSessionCurrent("alice", "alice", "token-a", "token-a"))
        assertFalse(FynxBackendClient.isRequestSessionCurrent("alice", "bob", "token-a", "token-b"))
        assertFalse(FynxBackendClient.isRequestSessionCurrent("alice", "alice", "token-a", "token-b"))
        assertFalse(FynxBackendClient.isRequestSessionCurrent(null, null, null, null))
    }
}
