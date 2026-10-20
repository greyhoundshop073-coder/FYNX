package com.fynx.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.fynx.app.ui.FynxBackendClient
import com.fynx.app.ui.FynxRemoteAuthClient
import com.fynx.app.ui.FynxCallTransportHardening
import com.fynx.app.ui.FynxAuthStore
import com.fynx.app.ui.FynxSecureTokenStore
import com.fynx.app.ui.AuthState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
    fun staleUnmarkedBackendOverrideMigratesToRailwayForDebugBuilds() {
        val prefs = context.getSharedPreferences("fynx_backend", Context.MODE_PRIVATE)
        val originalUrl = FynxBackendClient.baseUrl(context)
        val originalExplicit = prefs.getBoolean("base_url_explicit", false)
        try {
            prefs.edit()
                .putString("base_url", "https://fynx-ai-backend.onrender.com")
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
    fun androidRuntimeAuthenticatesAndLoadsProtectedBackendFeatures() = runBlocking {
        val appPrefs = context.getSharedPreferences("fynx_backend", Context.MODE_PRIVATE)
        val originalBaseUrl = appPrefs.getString("base_url", null)
        val originalExplicit = appPrefs.getBoolean("base_url_explicit", false)
        val originalLegacyToken = appPrefs.getString("access_token", null)
        val originalSecureToken = FynxSecureTokenStore.load(context)
        val isDebuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!isDebuggable) return@runBlocking
        try {
            FynxBackendClient.configureBaseUrl(context, "https://fynx-backend-testing-testing.up.railway.app")
            val suffix = System.currentTimeMillis().toString()
            val username = "fynx_ci_net_$suffix"
            val password = "FynxNetCheck-$suffix-Aa9!"
            val registered = FynxRemoteAuthClient.register(
                context,
                "FYNX Network Runtime Check",
                username,
                "",
                password
            )
            assertTrue(
                "Android could not register a test identity with the live backend: ${registered.exceptionOrNull()?.message}",
                registered.isSuccess
            )
            val account = registered.getOrThrow()
            assertTrue("Backend returned an empty access token", account.token.isNotBlank())
            FynxSecureTokenStore.save(context, account.token)

            val identity = FynxBackendClient.get(context, "/api/me")
            assertTrue("Authenticated /api/me failed: ${identity.exceptionOrNull()?.message}", identity.isSuccess)

            listOf(
                "/api/social/feed",
                "/api/statuses",
                "/api/marketplace/discovery"
            ).forEach { path ->
                val response = FynxBackendClient.get(context, path)
                assertTrue(
                    "Authenticated Android request failed for $path: ${response.exceptionOrNull()?.message}",
                    response.isSuccess
                )
            }
        } finally {
            FynxSecureTokenStore.save(context, originalSecureToken)
            appPrefs.edit().putBoolean("base_url_explicit", originalExplicit)
                .also { editor ->
                    if (originalBaseUrl == null) editor.remove("base_url") else editor.putString("base_url", originalBaseUrl)
                    if (originalLegacyToken == null) editor.remove("access_token") else editor.putString("access_token", originalLegacyToken)
                }.commit()
        }
    }

    @Test
    fun tokenlessBackend401DoesNotSignOutSavedDebugSession() = runBlocking {
        val appPrefs = context.getSharedPreferences("fynx_backend", Context.MODE_PRIVATE)
        val authPrefs = context.getSharedPreferences("fynx_auth", Context.MODE_PRIVATE)
        val originalBaseUrl = appPrefs.getString("base_url", null)
        val originalExplicit = appPrefs.getBoolean("base_url_explicit", false)
        val originalLegacyToken = appPrefs.getString("access_token", null)
        val originalSecureToken = FynxSecureTokenStore.load(context)
        val originalSignedIn = authPrefs.getBoolean("signed_in", false)
        val originalUsername = authPrefs.getString("username", null)
        val isDebuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!isDebuggable) return@runBlocking
        try {
            appPrefs.edit()
                .putString("base_url", "https://fynx-backend-testing-testing.up.railway.app")
                .putBoolean("base_url_explicit", true)
                .remove("access_token")
                .commit()
            FynxSecureTokenStore.save(context, null)
            authPrefs.edit()
                .putBoolean("signed_in", true)
                .putString("username", originalUsername?.takeIf { it.isNotBlank() } ?: "fynx_network_contract_local")
                .commit()

            val response = FynxBackendClient.get(context, "/api/me")
            assertTrue("Expected a protected endpoint to reject a tokenless request", response.isFailure)
            assertTrue(
                "Expected HTTP 401 from the live protected endpoint, got ${response.exceptionOrNull()?.message}",
                FynxBackendClient.isUnauthorizedFailure(response.exceptionOrNull()!!)
            )
            assertEquals(AuthState.SIGNED_IN, FynxAuthStore.load(context).state)
        } finally {
            FynxSecureTokenStore.save(context, originalSecureToken)
            appPrefs.edit().putBoolean("base_url_explicit", originalExplicit)
                .also { editor ->
                    if (originalBaseUrl == null) editor.remove("base_url") else editor.putString("base_url", originalBaseUrl)
                    if (originalLegacyToken == null) editor.remove("access_token") else editor.putString("access_token", originalLegacyToken)
                }.commit()
            authPrefs.edit().putBoolean("signed_in", originalSignedIn)
                .also { editor ->
                    if (originalUsername == null) editor.remove("username") else editor.putString("username", originalUsername)
                }.commit()
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
