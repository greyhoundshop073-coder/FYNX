package com.fynx.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FynxCallSignalDeliveryTest {
    @Test
    fun serverCallErrorsAreNotDiscardedAsUnknownSignals() {
        assertTrue(FynxCallTransportHardening.isValidCallSignal("error"))
    }

    @Test
    fun callSignalIsRejectedWithoutAnAccountMatchedSocket() {
        assertFalse(
            FynxCallTransportHardening.wasRealtimeFrameAccepted(
                socketMatchesAccount = false,
                accountAuthorized = true,
                socketAccepted = true
            )
        )
    }

    @Test
    fun callSignalIsRejectedWithoutCurrentAccountAuthorization() {
        assertFalse(
            FynxCallTransportHardening.wasRealtimeFrameAccepted(
                socketMatchesAccount = true,
                accountAuthorized = false,
                socketAccepted = true
            )
        )
    }

    @Test
    fun callSignalIsRejectedWhenTheSocketDoesNotAcceptTheFrame() {
        assertFalse(
            FynxCallTransportHardening.wasRealtimeFrameAccepted(
                socketMatchesAccount = true,
                accountAuthorized = true,
                socketAccepted = false
            )
        )
    }

    @Test
    fun callSignalIsAcceptedOnlyWhenAllTransportChecksPass() {
        assertTrue(
            FynxCallTransportHardening.wasRealtimeFrameAccepted(
                socketMatchesAccount = true,
                accountAuthorized = true,
                socketAccepted = true
            )
        )
    }
}
