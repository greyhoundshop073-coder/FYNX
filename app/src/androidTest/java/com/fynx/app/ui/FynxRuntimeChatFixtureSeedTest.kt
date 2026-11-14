package com.fynx.app.ui

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        // Reserve the account-scoped store that the debug-local UI login will use,
        // but leave the session signed out so the real login gate is exercised.
        val auth = context.getSharedPreferences("fynx_auth", Context.MODE_PRIVATE)
        assertTrue(
            auth.edit()
                .putString("username", username)
                .putString("display_name", "FYNX Runtime Owner")
                .putBoolean("signed_in", false)
                .commit()
        )
        assertEquals(username, FynxAuthStore.accountStorageKey(context))
        assertFalse("The fixture must not pre-authenticate the app before its debug-local login flow", auth.getBoolean("signed_in", false))

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
    }
}
