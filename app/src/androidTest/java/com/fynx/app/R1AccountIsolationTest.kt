package com.fynx.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fynx.app.ui.AuthState
import com.fynx.app.ui.FynxAuthStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * R1 runtime boundary test.
 *
 * This test deliberately exercises the same Android app storage boundary used
 * by the real sign-in/logout/account-switch path. It does not fabricate remote
 * users or backend responses. It verifies that switching from account A to B
 * cannot carry unscoped local session data forward.
 */
@RunWith(AndroidJUnit4::class)
class R1AccountIsolationTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        FynxAuthStore.clear(context)
    }

    @Test
    fun accountSwitchClearsLegacySessionDataAndBindsNewIdentity() {
        FynxAuthStore.saveAccount(context, "Account A", "@accountA", "+234000000001")
        assertEquals("@accountA", FynxAuthStore.storedUsername(context))
        assertEquals("@accounta", FynxAuthStore.accountStorageKey(context))

        context.getSharedPreferences("fynx_feed_cache", Context.MODE_PRIVATE)
            .edit()
            .putString("feed", "account-A")
            .apply()
        context.getSharedPreferences("fynx_notification_store", Context.MODE_PRIVATE)
            .edit()
            .putString("notifications", "account-A")
            .apply()
        File(context.filesDir, "fynx_post_account_a").writeText("account-A")
        File(context.filesDir, "fynx_status_account_a").writeText("account-A")

        // The real save path must perform the same boundary cleanup when a
        // different authenticated account enters the same app installation.
        FynxAuthStore.saveAccount(context, "Account B", "@accountB", "+234000000002")

        assertEquals(AuthState.SIGNED_IN, FynxAuthStore.load(context).state)
        assertEquals("@accountB", FynxAuthStore.storedUsername(context))
        assertEquals("@accountb", FynxAuthStore.accountStorageKey(context))
        assertEquals(
            null,
            context.getSharedPreferences("fynx_feed_cache", Context.MODE_PRIVATE)
                .getString("feed", null)
        )
        assertEquals(
            null,
            context.getSharedPreferences("fynx_notification_store", Context.MODE_PRIVATE)
                .getString("notifications", null)
        )
        assertFalse(File(context.filesDir, "fynx_post_account_a").exists())
        assertFalse(File(context.filesDir, "fynx_status_account_a").exists())
    }

    @Test
    fun logoutRemovesAuthenticatedIdentityButPreservesAccountCreatedState() {
        FynxAuthStore.saveAccount(context, "Account A", "@accountA", "+234000000001")
        assertTrue(FynxAuthStore.hasAccount(context))
        assertEquals(AuthState.SIGNED_IN, FynxAuthStore.load(context).state)

        FynxAuthStore.clear(context)

        assertTrue(FynxAuthStore.hasAccount(context))
        assertEquals(AuthState.SIGNED_OUT, FynxAuthStore.load(context).state)
        assertNull(FynxAuthStore.storedUsername(context))
        assertNull(FynxAuthStore.accountStorageKey(context))
    }
}