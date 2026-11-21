package com.fynx.app.ui

import android.content.Context
import java.io.File
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Seeds an explicitly disposable, local-only one-to-one conversation for the
 * focused Chat & Groups emulator capture. No token, backend or remote user is used.
 *
 * The runtime script invokes this test a second time after the full instrumentation
 * suite, so tests which reset authentication cannot erase the fixture before capture.
 */
@RunWith(AndroidJUnit4::class)
class FynxRuntimeChatFixtureSeedTest {
    @Test
    fun seedDisposablePrivateConversationForLocalRuntimeCapture() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("The fixture must write into the installed FYNX application, not the test APK", "com.fynx.app", context.packageName)
        // The focused runtime shell passes the same username the UI login will use.
        // This matters when CI has a username but deliberately has no backend password.
        val username = InstrumentationRegistry.getArguments()
            .getString("username")
            ?.trim()
            ?.removePrefix("@")
            ?.takeIf { it.isNotBlank() }
            ?: "marketplace_visual_check"
        val peerUsername = "fynx_runtime_peer"
        val now = System.currentTimeMillis()

        // Seed the explicitly local, debug-only session used by after-login UI tests.
        // This is not server authentication: both token stores are cleared above and
        // the fixture below verifies that no backend credential is available.
        // A previous instrumentation run or emulator snapshot may leave a stale
        // bearer token behind; the app's normal 401 handling would correctly clear
        // the auth session, which also changes FynxChatStore's account namespace.
        // Clear only credential values, preserving backend URL/configuration.
        assertTrue(
            context.getSharedPreferences("fynx_secure_auth", Context.MODE_PRIVATE)
                .edit().remove("access_token").commit()
        )
        assertTrue(
            context.getSharedPreferences("fynx_backend", Context.MODE_PRIVATE)
                .edit().remove("access_token").commit()
        )
        assertNull("Local visual fixture must never inherit a server credential", FynxBackendClient.accessToken(context))

        val auth = context.getSharedPreferences("fynx_auth", Context.MODE_PRIVATE)
        assertTrue(
            auth.edit()
                .putString("username", username)
                .putString("display_name", "FYNX Runtime Owner")
                .putBoolean("account_created", true)
                .putBoolean("signed_in", true)
                .commit()
        )
        assertEquals(username, FynxAuthStore.accountStorageKey(context))
        assertTrue("The disposable fixture must retain its local account namespace", FynxAuthStore.hasAccount(context))
        assertTrue("The app-side visual fixture must open the local debug session directly at Home", FynxAuthStore.load(context).state == AuthState.SIGNED_IN)
        assertNull("The local debug session must remain unauthenticated to the backend", FynxBackendClient.accessToken(context))

        val peerMessage = ChatMessage(
            text = "FYNX offline private fixture: hello from the test peer",
            fromMe = false,
            id = "runtime-peer-message-1",
            timestamp = now - 120_000L,
            delivered = true,
            read = true,
            senderName = "FYNX Runtime Peer",
            senderUsername = peerUsername
        )
        val ownerReply = ChatMessage(
            text = "FYNX local reply: the private conversation history restored",
            fromMe = true,
            id = "runtime-owner-message-2",
            timestamp = now - 60_000L,
            delivered = true,
            read = true,
            senderName = "FYNX Runtime Owner",
            senderUsername = username
        )

        FynxChatStore.save(context, peerUsername, listOf(peerMessage, ownerReply))
        FynxChatStore.savePreview(
            context,
            ChatPreview(
                name = "FYNX Runtime Peer",
                username = peerUsername,
                lastMessage = ownerReply.text,
                time = "Now",
                unreadCount = 0,
                online = false
            )
        )

        assertEquals(listOf(peerMessage, ownerReply), FynxChatStore.load(context, peerUsername))
        assertEquals(peerUsername, FynxChatStore.loadPreviews(context).firstOrNull()?.username)

        // save()/savePreview() intentionally use apply() for normal UI writes.
        // This fixture is consumed by a separately launched app process immediately
        // after instrumentation exits, so wait for the queued disk writes to finish
        // and assert the exact account-scoped records from the persisted preference map.
        val chatPreferences = context.getSharedPreferences("fynx_chat_store", Context.MODE_PRIVATE)
        // An empty commit() can return without scheduling a disk write, so it does
        // not reliably wait for the earlier apply() operations above. Make a real
        // marker mutation: its synchronous disk write is queued after those pending
        // SharedPreferences writes and therefore establishes the process boundary.
        val flushMarker = System.currentTimeMillis().toString()
        assertTrue(
            "The private-chat fixture must be durably flushed before the UI process starts",
            chatPreferences.edit().putString("runtime_fixture_flush_marker", flushMarker).commit()
        )
        assertEquals(
            "The synchronous marker write must be visible before the app process starts",
            flushMarker,
            chatPreferences.getString("runtime_fixture_flush_marker", null)
        )
        val normalizedUsername = username.trim().lowercase()
        val persistedHistory = chatPreferences.getString(
            "chat_${normalizedUsername}_${peerUsername}", null
        ).orEmpty()
        val persistedPreviews = chatPreferences.getString(
            "chat_previews_${normalizedUsername}", null
        ).orEmpty()
        assertTrue(
            "The exact account-scoped private history must be present on disk",
            persistedHistory.contains(peerMessage.text) && persistedHistory.contains(ownerReply.text)
        )
        assertTrue(
            "The exact account-scoped peer preview must be present on disk",
            persistedPreviews.contains(peerUsername) && persistedPreviews.contains("FYNX Runtime Peer")
        )
        // Read the actual app-private XML file, not only SharedPreferences' in-memory map.
        val chatStoreFile = File(context.applicationInfo.dataDir, "shared_prefs/fynx_chat_store.xml")
        assertTrue("The installed FYNX app must have a real fynx_chat_store.xml file", chatStoreFile.isFile)
        val diskSnapshot = chatStoreFile.readText()
        assertTrue("The persisted XML must contain the peer message", diskSnapshot.contains(peerMessage.text))
        assertTrue("The persisted XML must contain the owner reply", diskSnapshot.contains(ownerReply.text))
        assertTrue("The persisted XML must contain the account-scoped preview", diskSnapshot.contains("chat_previews_" + normalizedUsername))

        assertEquals(2, FynxChatStore.load(context, peerUsername).size)
        assertNull("Tokenless local entry must remain independent of a backend credential", FynxBackendClient.accessToken(context))

        // Exercise the exact local sign-in transition used by FynxApp's auth gate.
        // The account-scoped cache must remain reachable after the UI marks this
        // saved-device session signed in, and the backend token must remain absent.
        FynxAuthStore.save(context, username)
        assertEquals(username.trim().lowercase(), FynxAuthStore.accountStorageKey(context))
        assertEquals(peerUsername, FynxChatStore.loadPreviews(context).firstOrNull()?.username)
        assertEquals(2, FynxChatStore.load(context, peerUsername).size)
        assertNull(FynxBackendClient.accessToken(context))
        assertTrue("Restore the signed-out gate for the following UI login", auth.edit().putBoolean("signed_in", false).commit())
    }
}
