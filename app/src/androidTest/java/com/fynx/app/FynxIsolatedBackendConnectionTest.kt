package com.fynx.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fynx.app.ui.FynxBackendClient
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs only in the isolated-backend APK job. This checks the URL inside the
 * Android app and performs a real HTTPS request from the emulator itself.
 */
@RunWith(AndroidJUnit4::class)
class FynxIsolatedBackendConnectionTest {
    @Test
    fun packagedBackendOverrideIsUsedAndAndroidCanReachHealthEndpoint() = runBlocking {
        val expectedBaseUrl = "https://fynx-backend-testing-testing.up.railway.app"
        assumeTrue(
            "This certification applies only to the isolated-backend APK",
            BuildConfig.FYNX_BACKEND_BASE_URL.trimEnd('/') == expectedBaseUrl
        )

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(expectedBaseUrl, FynxBackendClient.baseUrl(context))

        val health = FynxBackendClient.health(context)
        assertTrue(
            "Android app could not reach $expectedBaseUrl/health: ${health.exceptionOrNull()}",
            health.isSuccess
        )

        val payload = JSONObject(health.getOrThrow())
        assertTrue("Backend health must report ok=true", payload.optBoolean("ok", false))
        assertEquals("fynx-backend", payload.optString("service"))
    }
}
