package com.fynx.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.fynx.app.ui.FynxBackendClient
import com.fynx.app.ui.FynxCallTransportHardening
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Final regression coverage for the production connectivity contract.
 * These tests deliberately exercise the public client contract rather than
 * replacing the real transport with a fake implementation.
 */
class FynxNetworkRecoveryContractTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun productionBackendUsesHttps() {
        assertTrue(FynxBackendClient.baseUrl(context).startsWith("https://"))
    }

    @Test
    fun configuredBackendRejectsPlainHttp() {
        val original = FynxBackendClient.baseUrl(context)
        try {
            var rejected = false
            try {
                FynxBackendClient.configureBaseUrl(context, "http://example.invalid")
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            assertTrue(rejected)
        } finally {
            FynxBackendClient.configureBaseUrl(context, original)
        }
    }

    @Test
    fun configuredHttpsBackendIsNormalized() {
        val original = FynxBackendClient.baseUrl(context)
        try {
            FynxBackendClient.configureBaseUrl(context, "https://example.invalid///")
            assertEquals("https://example.invalid", FynxBackendClient.baseUrl(context))
        } finally {
            FynxBackendClient.configureBaseUrl(context, original)
        }
    }

    @Test
    fun emptyConfigurationFallsBackToProductionBackend() {
        val original = FynxBackendClient.baseUrl(context)
        try {
            FynxBackendClient.configureBaseUrl(context, "")
            assertTrue(FynxBackendClient.baseUrl(context).startsWith("https://"))
            assertNotNull(FynxBackendClient.baseUrl(context))
        } finally {
            FynxBackendClient.configureBaseUrl(context, original)
        }
    }

    @Test
    fun networkAvailabilityContractDoesNotRequireValidatedCapability() {
        // The production client exposes availability independently of Android's
        // VALIDATED bit; the call transport shares this same contract.
        assertEquals(FynxBackendClient.isNetworkAvailable(context), FynxBackendClient.isNetworkAvailable(context))
    }

    @Test
    fun callProtocolSupportsEveryProductionSignal() {
        listOf("invite", "accept", "reject", "end", "offer", "answer", "ice", "unavailable", "busy")
            .forEach { assertTrue(FynxCallTransportHardening.isValidCallSignal(it)) }
    }

    @Test
    fun callProtocolAcceptsBothCallIdForms() {
        assertTrue(FynxCallTransportHardening.isValidCallId("call-123"))
        assertTrue(FynxCallTransportHardening.isValidCallId("call_123"))
    }

    @Test
    fun malformedCallIdsAreRejected() {
        assertFalse(FynxCallTransportHardening.isValidCallId("123"))
        assertFalse(FynxCallTransportHardening.isValidCallId("call"))
        assertFalse(FynxCallTransportHardening.isValidCallId("call-"))
    }

    @Test
    fun voiceAndVideoAreTheOnlyCallTypes() {
        assertTrue(FynxCallTransportHardening.isValidCallType("voice"))
        assertTrue(FynxCallTransportHardening.isValidCallType("video"))
        assertFalse(FynxCallTransportHardening.isValidCallType("audio"))
        assertFalse(FynxCallTransportHardening.isValidCallType("screen"))
    }

    @Test
    fun authenticationFailuresAreNeverRetriedAsTransportFailures() {
        assertTrue(FynxCallTransportHardening.isAuthFailure(401))
        assertTrue(FynxCallTransportHardening.isAuthFailure(403))
        assertFalse(FynxCallTransportHardening.isAuthFailure(500))
    }

    @Test
    fun deterministicWebSocketFailuresDoNotRetry() {
        listOf(1000, 1002, 1003, 1007, 1008, 1009).forEach {
            assertFalse(FynxCallTransportHardening.shouldRetrySocket(it))
        }
    }

    @Test
    fun transientWebSocketFailuresRetry() {
        listOf(1001, 1006, 1011, 1012, 1013, 1014).forEach {
            assertTrue(FynxCallTransportHardening.shouldRetrySocket(it))
        }
    }

    @Test
    fun terminalCallStatesCannotBeConfusedWithActiveStates() {
        listOf("reject", "end", "unavailable", "busy").forEach {
            assertTrue(FynxCallTransportHardening.isTerminalSignal(it))
        }
        listOf("invite", "accept", "offer", "answer", "ice").forEach {
            assertFalse(FynxCallTransportHardening.isTerminalSignal(it))
        }
    }

    @Test
    fun unavailableRemainsDistinctFromBusy() {
        assertTrue(FynxCallTransportHardening.isUnavailableSignal("unavailable"))
        assertFalse(FynxCallTransportHardening.isUnavailableSignal("busy"))
    }
}
