package com.fynx.app.ui

import android.content.Context
import java.io.File
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the actual group reconnect drain with a deterministic local transport.
 * No live service, test account, network access, or remote group is used.
 */
@RunWith(AndroidJUnit4::class)
class FynxGroupReconnectDrainInstrumentationTest {
    private lateinit var context: Context
    private lateinit var authPreferences: android.content.SharedPreferences
    private lateinit var backendPreferences: android.content.SharedPreferences
    private var originalAuthValues: Map<String, *> = emptyMap<String, Any>()
    private var originalSecureToken: String? = null
    private var originalLegacyToken: String? = null
    private var originalExecutor: (suspend (Context, String, String, String?, String?) -> String)? = null
    private lateinit var username: String
    private lateinit var group: FynxGroup
    private lateinit var pendingMessage: ChatMessage

    @Before
    fun prepareIsolatedGroupAccount() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        authPreferences = context.getSharedPreferences("fynx_auth", Context.MODE_PRIVATE)
        backendPreferences = context.getSharedPreferences("fynx_backend", Context.MODE_PRIVATE)
        originalAuthValues = authPreferences.all
        originalSecureToken = FynxSecureTokenStore.load(context)
        originalLegacyToken = backendPreferences.getString("access_token", null)
        originalExecutor = FynxBackendClient.requestExecutorForTests

        username = "group-reconnect-${UUID.randomUUID().toString().replace("-", "").take(12)}"
        assertTrue(
            authPreferences.edit()
                .putString("username", username)
                .putBoolean("signed_in", true)
                .putBoolean("account_created", true)
                .commit()
        )
        FynxSecureTokenStore.save(context, "local-group-drain-token-${UUID.randomUUID()}")
        assertEquals(username, FynxAuthStore.accountStorageKey(context))
        assertTrue(FynxBackendClient.hasAccessToken(context))

        group = FynxGroup(
            id = "offline-group-${UUID.randomUUID()}",
            name = "Reconnect test group",
            description = "Disposable local reconnect fixture",
            visibility = FynxGroupVisibility.PRIVATE,
            ownerUsername = username,
            members = listOf(FynxGroupMember(username, FynxGroupRole.ADMIN))
        )
        assertTrue(FynxGroupsStore.addPendingGroupDurably(context, group, username))

        pendingMessage = ChatMessage(
            text = "Pending group message after reconnect",
            fromMe = true,
            id = "offline-group-message-${UUID.randomUUID()}",
            timestamp = System.currentTimeMillis(),
            delivered = false,
            read = false,
            senderUsername = username
        )
        assertTrue(FynxChatStore.saveDurably(context, "group_${group.id}", listOf(pendingMessage)))
        assertEquals(pendingMessage.id, FynxChatStore.load(context, "group_${group.id}").single().id)
        val groupPreferencesFile = File(context.applicationInfo.dataDir, "shared_prefs/fynx_groups_store.xml")
        assertTrue("Group preferences must be committed before force-stop", groupPreferencesFile.isFile)
        val groupPreferencesOnDisk = groupPreferencesFile.readText()
        assertTrue("The group record must be on disk", groupPreferencesOnDisk.contains(group.id))
        assertTrue("Group definition and pending-sync marker must both be committed",
            groupPreferencesOnDisk.split(group.id).size - 1 >= 2)
        val chatPreferencesFile = File(context.applicationInfo.dataDir, "shared_prefs/fynx_chat_store.xml")
        assertTrue("Group history preferences must be committed before force-stop", chatPreferencesFile.isFile)
        assertTrue("The pending group message must be on disk", chatPreferencesFile.readText().contains(pendingMessage.text))
    }

    @After
    fun removeFixtureAndRestoreSession() {
        FynxBackendClient.requestExecutorForTests = originalExecutor
        runCatching { FynxGroupsStore.removeGroup(context, group.id, username) }
        runCatching { FynxChatStore.clear(context, "group_${group.id}") }

        FynxSecureTokenStore.save(context, originalSecureToken)
        val backendEditor = backendPreferences.edit()
        if (originalLegacyToken == null) backendEditor.remove("access_token")
        else backendEditor.putString("access_token", originalLegacyToken)
        backendEditor.commit()

        authPreferences.edit().clear().commit()
        val editor = authPreferences.edit()
        originalAuthValues.forEach { (key, value) ->
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

    @Test
    fun reconnectDrainSyncsPendingGroupAndReplacesTheUndeliveredMessage() = runBlocking {
        val calls = mutableListOf<String>()
        val serverMessageId = "880001"
        FynxBackendClient.requestExecutorForTests = { _, method, path, body, token ->
            assertEquals("The drain must use the initiating account's token", FynxBackendClient.accessToken(context), token)
            when {
                method == "POST" && path == "/api/groups/${group.id}/sync" -> {
                    calls += path
                    """{"ok":true}"""
                }
                method == "POST" && path == "/api/groups/${group.id}/messages" -> {
                    calls += path
                    val request = JSONObject(requireNotNull(body))
                    assertEquals(pendingMessage.id, request.getString("id"))
                    assertEquals(pendingMessage.text, request.getString("text"))
                    """{"message":{"id":"$serverMessageId","text":"${pendingMessage.text}","senderUsername":"$username","timestamp":${System.currentTimeMillis()}}}"""
                }
                else -> throw AssertionError("Unexpected group reconnect request: $method $path")
            }
        }

        val completed = FynxGroupRemoteClient.drainPendingWork(context)

        assertEquals("The newly created group and its queued message must both drain", 2, completed)
        assertFalse(group.id in FynxGroupsStore.pendingSyncGroups(context, username))
        assertEquals(
            listOf("/api/groups/${group.id}/sync", "/api/groups/${group.id}/messages"),
            calls
        )
        val saved = FynxChatStore.load(context, "group_${group.id}")
        assertEquals(1, saved.size)
        assertEquals(serverMessageId, saved.single().id)
        assertEquals(pendingMessage.text, saved.single().text)
        assertTrue(saved.single().delivered)
        assertTrue(saved.single().fromMe)
    }

    @Test
    fun failedGroupCreationSyncKeepsTheGroupAndItsMessageQueued() = runBlocking {
        val calls = mutableListOf<String>()
        FynxBackendClient.requestExecutorForTests = { _, method, path, _, token ->
            assertEquals(FynxBackendClient.accessToken(context), token)
            calls += "$method $path"
            if (method == "POST" && path == "/api/groups/${group.id}/sync") {
                throw IOException("simulated backend unavailable during group sync")
            }
            throw AssertionError("A group message must not be sent before its group sync succeeds: $method $path")
        }

        val completed = FynxGroupRemoteClient.drainPendingWork(context)

        assertEquals(0, completed)
        assertTrue(group.id in FynxGroupsStore.pendingSyncGroups(context, username))
        assertEquals(listOf("POST /api/groups/${group.id}/sync"), calls)
        val saved = FynxChatStore.load(context, "group_${group.id}")
        assertEquals(1, saved.size)
        assertEquals(pendingMessage.id, saved.single().id)
        assertFalse(saved.single().delivered)
    }
}
