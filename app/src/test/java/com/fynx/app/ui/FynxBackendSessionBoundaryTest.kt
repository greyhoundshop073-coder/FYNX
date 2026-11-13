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
}
