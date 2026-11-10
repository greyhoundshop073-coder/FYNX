package com.fynx.app.ui

import com.fynx.app.FynxRemoteFeatureFlagPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FynxRemoteFeatureFlagPolicyTest {
    @Test
    fun optionalExperimentsAreDisabledByDefault() {
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
    fun unknownFlagsFailClosed() {
        assertFalse(FynxRemoteFeatureFlagPolicy.isSupported("unknown_experiment"))
        assertFalse(FynxRemoteFeatureFlagPolicy.defaultValue("unknown_experiment"))
    }

    @Test
    fun criticalAccountAndFinancialFlowsAreNotRemotelyControllable() {
        listOf(
            "login_enabled",
            "account_recovery_enabled",
            "safety_reporting_enabled",
            "wallet_enabled",
            "payments_enabled",
            "orders_enabled",
            "authorization_enabled"
        ).forEach { key ->
            assertFalse("Unexpected remote flag allowlist entry: $key", FynxRemoteFeatureFlagPolicy.isSupported(key))
        }
    }
}