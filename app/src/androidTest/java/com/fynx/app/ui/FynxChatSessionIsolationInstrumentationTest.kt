package com.fynx.app.ui

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Simulates two local signed-in account namespaces without network or login calls.
 * It verifies that private history, groups, pending group sync and the offline
 * outbox are hidden from the other account and restored when the original account
 * becomes active again. The emulator UI cold-restart journey is certified separately.
 */
@RunWith(AndroidJUnit4::class)
class FynxChatSessionIsolationInstrumentationTest {
    @Test
    fun accountSwitchKeepsChatGroupsAndPendingWorkIsolatedAndRestoresEachSession() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val auth = context.getSharedPreferences("fynx_auth", Context.MODE_PRIVATE)
        val originalValues = auth.all
        val suffix = UUID.randomUUID().toString().replace("-", "")
        val accountA = "chat-session-a-$suffix"
        val accountB = "chat-session-b-$suffix"
        val chatKey = "chat-session-peer-$suffix"
        val groupAId = "chat-session-group-a-$suffix"
        val groupBId = "chat-session-group-b-$suffix"
        val outboxAId = "chat-session-outbox-a-$suffix"
        val outboxBId = "chat-session-outbox-b-$suffix"

        fun switchTo(account: String) {
            assertTrue(
                "The isolated test account could not be selected",
                auth.edit().putString("username", account).putBoolean("signed_in", true).commit()
            )
            assertEquals(account, FynxAuthStore.accountStorageKey(context))
        }

        fun group(id: String, owner: String, name: String) = FynxGroup(
            id = id,
            name = name,
            description = "Local session isolation fixture",
            visibility = FynxGroupVisibility.PRIVATE,
            ownerUsername = owner,
            members = listOf(FynxGroupMember(owner, FynxGroupRole.ADMIN))
        )

        fun persistSession(account: String, groupId: String, outboxId: String, message: String) {
            val chatMessage = ChatMessage(
                text = message,
                fromMe = true,
                id = "message-$outboxId",
                senderUsername = account
            )
            FynxChatStore.save(context, chatKey, listOf(chatMessage))
            assertTrue(FynxGroupsStore.add(context, group(groupId, account, groupId)))
            FynxGroupsStore.markPendingSync(context, groupId)
            val pending = FynxOfflineOutbox.Item(
                id = outboxId,
                requestId = UUID.randomUUID().toString(),
                recipient = "peer-$account",
                text = message,
                replyToId = null,
                mediaId = null,
                mediaType = null,
                voiceDurationMs = 0L,
                createdAt = System.currentTimeMillis()
            )
            assertTrue(FynxOfflineOutbox.enqueue(context, pending, FynxAuthStore.accountStorageKey(context)))
        }

        fun removeSessionData(account: String, groupId: String, outboxId: String) {
            switchTo(account)
            FynxChatStore.clear(context, chatKey)
            FynxGroupsStore.removeGroup(context, groupId)
            FynxOfflineOutbox.remove(context, outboxId)
        }

        try {
            switchTo(accountA)
            persistSession(accountA, groupAId, outboxAId, "private message for account A")
            val accountAKey = requireNotNull(FynxAuthStore.accountStorageKey(context))
            assertTrue(FynxGroupsStore.pendingSyncGroups(context).contains(groupAId))
            assertEquals(outboxAId, FynxOfflineOutbox.load(context).single().id)

            switchTo(accountB)
            assertTrue("Account B must not see Account A private history", FynxChatStore.load(context, chatKey).isEmpty())
            assertFalse("Account B must not see Account A groups", FynxGroupsStore.load(context).any { it.id == groupAId })
            assertFalse("Account B must not see Account A pending group sync", FynxGroupsStore.pendingSyncGroups(context).contains(groupAId))
            assertTrue("Account B must not see Account A offline outbox", FynxOfflineOutbox.load(context).isEmpty())
            persistSession(accountB, groupBId, outboxBId, "private message for account B")

            switchTo(accountA)
            assertEquals("private message for account A", FynxChatStore.load(context, chatKey).single().text)
            assertTrue(FynxGroupsStore.load(context).any { it.id == groupAId })
            assertFalse(FynxGroupsStore.load(context).any { it.id == groupBId })
            assertTrue(FynxGroupsStore.pendingSyncGroups(context).contains(groupAId))
            assertFalse(FynxGroupsStore.pendingSyncGroups(context).contains(groupBId))
            assertEquals(outboxAId, FynxOfflineOutbox.load(context).single().id)

            switchTo(accountB)
            assertEquals("private message for account B", FynxChatStore.load(context, chatKey).single().text)
            assertTrue(FynxGroupsStore.load(context).any { it.id == groupBId })
            assertFalse(FynxGroupsStore.load(context).any { it.id == groupAId })
            assertTrue(FynxGroupsStore.pendingSyncGroups(context).contains(groupBId))
            assertEquals(outboxBId, FynxOfflineOutbox.load(context).single().id)
            assertFalse("The two accounts must use separate storage namespaces", accountAKey == FynxAuthStore.accountStorageKey(context))
        } finally {
            runCatching { removeSessionData(accountA, groupAId, outboxAId) }
            runCatching { removeSessionData(accountB, groupBId, outboxBId) }
            auth.edit().clear().commit()
            val editor = auth.edit()
            originalValues.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toMutableSet())
                }
            }
            editor.commit()
        }
    }
}
