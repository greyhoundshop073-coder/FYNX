package com.fynx.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FynxRemoteConfigStartupTest {
    @Test
    fun applicationStartsWithFailClosedRemoteConfigDefaults() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext

        assertTrue(
            "FYNX must use its process-level Application initializer",
            application is FynxApplication
        )
        assertEquals(
            FynxRemoteFeatureFlagPolicy.defaults.keys,
            FynxRemoteFeatureFlags.values.value.keys
        )
        assertTrue(FynxRemoteFeatureFlagPolicy.defaults.values.none { it })
        assertFalse(FynxRemoteFeatureFlags.isEnabled("unknown_experiment"))
        assertFalse(FynxRemoteFeatureFlags.isEnabled("wallet_enabled"))
        assertFalse(FynxRemoteFeatureFlags.isEnabled("login_enabled"))
    }
}
