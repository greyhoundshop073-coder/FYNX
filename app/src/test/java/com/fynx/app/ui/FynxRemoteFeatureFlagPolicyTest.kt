package com.fynx.app.ui

import com.fynx.app.FynxRemoteFeatureFlagPolicy
import com.fynx.app.FynxRemoteFeatureFlags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FynxRemoteFeatureFlagPolicyTest {
    @Test
    fun allowlistedOptionalExperimentsAreDisabledByDefault() {
        assertEquals(
            setOf(
                FynxRemoteFeatureFlagPolicy.HOME_FEED_EXPERIMENT,
                FynxRemoteFeatureFlagPolicy.MARKETPLACE_DISCOVERY_EXPERIMENT,
                FynxRemoteFeatureFlagPolicy.AI_VOICE_EXPERIMENT
            ),
            FynxRemoteFeatureFlagPolicy.defaults.keys
        )
        assertTrue(FynxRemoteFeatureFlagPolicy.defaults.values.none { it })
    }

    @Test
    fun flagReaderStartsWithSafeDefaultsAndRejectsUnknownKeys() {
        FynxRemoteFeatureFlagPolicy.defaults.keys.forEach { key ->
            assertFalse("Flag must start disabled: $key", FynxRemoteFeatureFlags.isEnabled(key))
        }
        assertFalse(FynxRemoteFeatureFlags.isEnabled("unknown_experiment"))
    }

    @Test
    fun publishedStateContainsOnlyAllowlistedFlags() {
        assertEquals(
            FynxRemoteFeatureFlagPolicy.defaults.keys,
            FynxRemoteFeatureFlags.values.value.keys
        )
    }

    @Test
    fun unknownFlagsFailClosed() {
        assertFalse(FynxRemoteFeatureFlagPolicy.isSupported("unknown_experiment"))
        assertFalse(FynxRemoteFeatureFlagPolicy.defaultValue("unknown_experiment"))
    }

    @Test
    fun authenticationSafetyAndFinancialFlowsAreNeverControllable() {
        listOf(
            "login_enabled",
            "account_recovery_enabled",
            "safety_reporting_enabled",
            "blocking_enabled",
            "wallet_enabled",
            "payments_enabled",
            "payment_verification_enabled",
            "orders_enabled",
            "refunds_enabled",
            "disputes_enabled",
            "authorization_enabled"
        ).forEach { key ->
            assertFalse("Critical flow must not be allowlisted: $key", FynxRemoteFeatureFlagPolicy.isSupported(key))
            assertFalse("Critical flow must remain disabled: $key", FynxRemoteFeatureFlags.isEnabled(key))
        }
    }
}
