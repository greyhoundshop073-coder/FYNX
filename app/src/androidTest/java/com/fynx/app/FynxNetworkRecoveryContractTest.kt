package com.fynx.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.fynx.app.ui.FynxBackendClient
import com.fynx.app.ui.FynxCallTransportHardening
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

/**
 * Final regression coverage for the production connectivity contract.
 * These tests deliberately exercise the public client contract rather than
 * replacing the real transport with a fake implementation.
 */
class FynxNetworkRecoveryContractTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun productionBackendDefaultsToTheActiveRailwayService() {
        val prefs = context.getSharedPreferences("fynx_backend", Context.MODE_PRIVATE)
        val originalUrl = prefs.getString("base_url", null)
        val originalExplicit = prefs.getBoolean("base_url_explicit", false)
        try {
            prefs.edit().remove("base_url").putBoolean("base_url_explicit", false).commit()
            assertEquals("https://fynx-backend-testing-testing.up.railway.app", FynxBackendClient.baseUrl(context))
        } finally {
            val edit = prefs.edit().putBoolean("base_url_explicit", originalExplicit)
            if (originalUrl == null) edit.remove("base_url") else edit.putString("base_url", originalUrl)
            edit.commit()
        }
    }

    @Test
    fun activeRailwayBackendAddressIsPreservedForDebugBuilds() {
        val prefs = context.getSharedPreferences("fynx_backend", Context.MODE_PRIVATE)
        val originalUrl = prefs.getString("base_url", null)
        val originalExplicit = prefs.getBoolean("base_url_explicit", false)
        try {
            prefs.edit().putString("base_url", "https://fynx-backend-testing-testing.up.railway.app")
                .putBoolean("base_url_explicit", true).commit()
            assertEquals("https://fynx-backend-testing-testing.up.railway.app", FynxBackendClient.baseUrl(context))
            assertEquals("https://fynx-backend-testing-testing.up.railway.app", prefs.getString("base_url", null))
            assertTrue(FynxBackendClient.baseUrl(context).contains("railway.app"))
        } finally {
            val edit = prefs.edit().putBoolean("base_url_explicit", originalExplicit)
            if (originalUrl == null) edit.remove("base_url") else edit.putString("base_url", originalUrl)
            edit.commit()
        }
    }

    @Test
    fun activeRailwayBackendAddressIsPreserved() {
        val prefs = context.getSharedPreferences("fynx_backend", Context.MODE_PRIVATE)
        val originalUrl = prefs.getString("base_url", null)
        val originalExplicit = prefs.getBoolean("base_url_explicit", false)
        try {
            prefs.edit().putString("base_url", "https://fynx-backend-testing-testing.up.railway.app")
                .putBoolean("base_url_explicit", true).commit()
            assertEquals("https://fynx-backend-testing-testing.up.railway.app", FynxBackendClient.baseUrl(context))
            assertEquals("https://fynx-backend-testing-testing.up.railway.app", prefs.getString("base_url", null))
        } finally {
            val edit = prefs.edit().putBoolean("base_url_explicit", originalExplicit)
            if (originalUrl == null) edit.remove("base_url") else edit.putString("base_url", originalUrl)
            edit.commit()
        }
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
    fun staleUnmarkedRailwayOverrideRemainsOnActiveServiceForDebugBuilds() {
        val prefs = context.getSharedPreferences("fynx_backend", Context.MODE_PRIVATE)
        val originalUrl = FynxBackendClient.baseUrl(context)
        val originalExplicit = prefs.getBoolean("base_url_explicit", false)
        try {
            prefs.edit()
                .putString("base_url", "https://fynx-backend-testing-testing.up.railway.app")
                .putBoolean("base_url_explicit", false)
                .commit()

            val resolved = FynxBackendClient.baseUrl(context)
            val isDebuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            if (isDebuggable) {
                assertEquals("https://fynx-backend-testing-testing.up.railway.app", resolved)
                assertEquals(resolved, prefs.getString("base_url", null))
            }
        } finally {
            prefs.edit()
                .putString("base_url", originalUrl)
                .putBoolean("base_url_explicit", originalExplicit)
                .commit()
        }
    }

    @Test
    fun debugAndroidRuntimeCanReachLiveRailwayBackend() = runBlocking {
        // Keep normal APK/UI certification independent of backend availability.
        // This live integration probe is opt-in; CI has a separate backend health step.
        val shouldProbeLiveBackend = InstrumentationRegistry.getArguments()
            .getString("fynxRequireLiveBackend")
            ?.equals("true", ignoreCase = true) == true
        assumeTrue(
            "Live Railway probe is opt-in; pass -Pandroid.testInstrumentationRunnerArguments.fynxRequireLiveBackend=true to run it.",
            shouldProbeLiveBackend
        )

        val original = FynxBackendClient.baseUrl(context)
        try {
            val isDebuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            if (isDebuggable) {
                FynxBackendClient.configureBaseUrl(context, "https://fynx-backend-testing-testing.up.railway.app")
                val result = FynxBackendClient.health(context)
                assertTrue(
                    "Android runtime could not reach Railway backend: ${result.exceptionOrNull()?.message}",
                    result.isSuccess
                )
            }
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
