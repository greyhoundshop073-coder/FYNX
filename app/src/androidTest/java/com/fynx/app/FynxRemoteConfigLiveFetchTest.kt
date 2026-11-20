package com.fynx.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Live Firebase certification. Run only after the source-controlled, all-false template
 * has been safely published by the canonical-branch rollout workflow.
 */
@RunWith(AndroidJUnit4::class)
class FynxRemoteConfigLiveFetchTest {
    @Test
    fun publishedAllowlistedParametersAreFetchedFromFirebaseAndRemainDisabled() {
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        val completed = CountDownLatch(1)
        var fetchSucceeded = false
        var fetchError: Exception? = null
        var activationSucceeded = false

        remoteConfig.fetch(0L).addOnCompleteListener { fetchTask ->
            if (!fetchTask.isSuccessful) {
                fetchError = fetchTask.exception
                completed.countDown()
            } else {
                remoteConfig.activate().addOnCompleteListener { activationTask ->
                    activationSucceeded = activationTask.isSuccessful
                    fetchError = activationTask.exception
                    fetchSucceeded = true
                    completed.countDown()
                }
            }
        }

        assertTrue(
            "Timed out waiting for a live Firebase Remote Config fetch",
            completed.await(60, TimeUnit.SECONDS)
        )
        assertTrue("Firebase Remote Config fetch failed: $fetchError", fetchSucceeded)
        assertTrue("Firebase Remote Config activation failed: $fetchError", activationSucceeded)

        FynxRemoteFeatureFlagPolicy.defaults.keys.forEach { key ->
            val value = remoteConfig.getValue(key)
            assertEquals(
                "Parameter $key must come from the published Firebase template, not only an in-app default",
                FirebaseRemoteConfig.VALUE_SOURCE_REMOTE,
                value.source
            )
            assertFalse("Safety rollout requires $key to remain false", value.asBoolean())
        }
    }
}
