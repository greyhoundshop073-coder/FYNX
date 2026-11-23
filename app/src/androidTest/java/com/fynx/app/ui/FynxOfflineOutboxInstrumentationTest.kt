package com.fynx.app.ui

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the real Android file-copy and account-scoped outbox with no network
 * access. The only account values used here are disposable local test namespaces.
 */
@RunWith(AndroidJUnit4::class)
class FynxOfflineOutboxInstrumentationTest {
    private lateinit var context: Context
    private lateinit var authPreferences: android.content.SharedPreferences
    private var originalAuthValues: Map<String, *> = emptyMap<String, Any>()
    private lateinit var testUsername: String
    private var sourceFile: File? = null
    private var queuedMediaFile: File? = null
    private var queuedId: String? = null

    @Before
    fun prepareIsolatedLocalAccount() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        authPreferences = context.getSharedPreferences("fynx_auth", Context.MODE_PRIVATE)
        originalAuthValues = authPreferences.all
        testUsername = "offline-outbox-test-${UUID.randomUUID()}"
        // Set the account namespace directly: this test deliberately performs no
        // login request, token refresh, FCM registration, or server call.
        assertTrue(
            authPreferences.edit()
                .putString("username", testUsername)
                .putBoolean("signed_in", true)
                .commit()
        )
        assertEquals(testUsername, FynxAuthStore.accountStorageKey(context))
        assertTrue(FynxOfflineOutbox.load(context).isEmpty())
    }

    @After
    fun removeTestFilesAndRestoreAuthPreferences() {
        queuedId?.let { runCatching { FynxOfflineOutbox.remove(context, it) } }
        sourceFile?.delete()
        queuedMediaFile?.delete()
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
    fun attachmentBytesSurviveReloadRemainAccountIsolatedAndDeleteWithOutboxItem() = runBlocking {
        val payload = ByteArray(8_193) { index -> ((index * 31) % 251).toByte() }
        val input = File(context.cacheDir, "offline-outbox-input-${UUID.randomUUID()}.jpg")
        sourceFile = input
        input.writeBytes(payload)

        val itemId = "offline-test-${UUID.randomUUID()}"
        queuedId = itemId
        val item = FynxOfflineOutbox.Item(
            id = itemId,
            requestId = UUID.randomUUID().toString(),
            recipient = "recipient@example",
            text = "Offline attachment test",
            replyToId = null,
            mediaId = null,
            mediaType = "image",
            voiceDurationMs = 0L,
            createdAt = System.currentTimeMillis()
        )

        val storedPath = FynxOfflineOutbox.enqueueAttachment(
            context = context,
            item = item,
            sourceUri = Uri.fromFile(input),
            mimeType = "image/jpeg"
        )
        assertNotNull("Attachment must be copied to app-owned storage before enqueue succeeds", storedPath)
        val storedFile = File(requireNotNull(storedPath))
        queuedMediaFile = storedFile
        assertTrue("The queued media copy must exist", storedFile.isFile)
        assertArrayEquals("The app-owned copy must preserve the selected media bytes", payload, storedFile.readBytes())

        val persisted = FynxOfflineOutbox.load(context).single()
        assertEquals(itemId, persisted.id)
        assertEquals(item.requestId, persisted.requestId)
        assertEquals("recipient@example", persisted.recipient)
        assertEquals(storedFile.absolutePath, persisted.localMediaPath)
        assertEquals("image/jpeg", persisted.localMediaMimeType)
        assertArrayEquals(payload, File(requireNotNull(persisted.localMediaPath)).readBytes())

        // A different local account must see an empty outbox, while switching
        // back to the original local account restores the queued record.
        val otherAccount = "offline-outbox-other-${UUID.randomUUID()}"
        assertTrue(authPreferences.edit().putString("username", otherAccount).commit())
        assertTrue("A different account must not see the first account's queued media", FynxOfflineOutbox.load(context).isEmpty())

        assertTrue(authPreferences.edit().putString("username", testUsername).commit())
        assertEquals(itemId, FynxOfflineOutbox.load(context).single().id)

        FynxOfflineOutbox.remove(context, itemId)
        assertTrue("Removing the queued item must remove its persisted outbox entry", FynxOfflineOutbox.load(context).isEmpty())
        assertFalse("Removing the queued item must delete its app-owned media copy", storedFile.exists())
        queuedMediaFile = null
        queuedId = null
    }

    @Test
    fun attachmentStagingFailureDoesNotCreateAnOutboxEntry() = runBlocking {
        val item = FynxOfflineOutbox.Item(
            id = "offline-failed-${UUID.randomUUID()}",
            requestId = UUID.randomUUID().toString(),
            recipient = "recipient@example",
            text = "Must remain selected after local staging failure",
            replyToId = null,
            mediaId = null,
            mediaType = "image",
            voiceDurationMs = 0L,
            createdAt = System.currentTimeMillis()
        )
        val result = FynxOfflineOutbox.enqueueAttachment(
            context = context,
            item = item,
            sourceUri = Uri.parse("content://com.fynx.offline-test/missing-image"),
            mimeType = "image/jpeg"
        )
        assertEquals(null, result)
        assertTrue("A failed local copy must not enqueue a message that cannot be retried", FynxOfflineOutbox.load(context).isEmpty())
    }
    @Test
    fun structuredMessagesPersistAcrossReloadAndRemainAccountScoped() {
        val types = listOf("location", "contact", "poll", "sticker", "gif")
        val queued = types.map { type ->
            val id = "offline-structured-$type-${UUID.randomUUID()}"
            val mediaBacked = type == "sticker" || type == "gif"
            FynxOfflineOutbox.Item(
                id = id,
                requestId = "request-$id",
                recipient = "recipient@example",
                text = "",
                replyToId = null,
                mediaId = if (mediaBacked) "12345" else null,
                mediaType = if (mediaBacked) "image" else null,
                voiceDurationMs = 0L,
                createdAt = System.currentTimeMillis(),
                messageType = type,
                messagePayload = when (type) {
                    "location" -> mapOf("latitude" to "6.5244", "longitude" to "3.3792")
                    "contact" -> mapOf("name" to "Test Contact", "phone" to "+2348000000000")
                    "poll" -> mapOf("question" to "Choose?", "options" to "A\u001FB")
                    else -> mapOf("caption" to type)
                }
            )
        }
        try {
            queued.forEach { item ->
                assertTrue("Structured item ${item.messageType} must enqueue",
                    FynxOfflineOutbox.enqueue(context, item, expectedAccountKey = testUsername))
            }

            // Re-reading SharedPreferences models process recreation: no in-memory queue is needed.
            val restored = FynxOfflineOutbox.load(context).associateBy { it.messageType }
            assertEquals(types.toSet(), restored.keys)
            queued.forEach { original ->
                val persisted = requireNotNull(restored[original.messageType])
                assertEquals(original.id, persisted.id)
                assertEquals(original.requestId, persisted.requestId)
                assertEquals(original.messagePayload, persisted.messagePayload)
                assertEquals(original.mediaId, persisted.mediaId)
            }

            val otherAccount = "offline-structured-other-${UUID.randomUUID()}"
            assertTrue(authPreferences.edit().putString("username", otherAccount).commit())
            assertTrue("A different account must not see structured pending messages",
                FynxOfflineOutbox.load(context).isEmpty())
        } finally {
            assertTrue(authPreferences.edit().putString("username", testUsername).commit())
            queued.forEach { FynxOfflineOutbox.remove(context, it.id, expectedAccountKey = testUsername) }
        }
        assertTrue(FynxOfflineOutbox.load(context).isEmpty())
    }


    @Test
    fun serverAcceptedStructuredMessageReplacesPendingBubbleAndRejectsStaleSnapshot() {
        val types = listOf("location", "contact", "poll", "sticker", "gif")
        types.forEachIndexed { index, type ->
            val recipient = "structured-replacement-$type-${UUID.randomUUID()}"
            try {
                val localId = "offline-pending-$type-${UUID.randomUUID()}"
                val serverId = (System.currentTimeMillis() * 10L + index).toString()
                val payload = when (type) {
                    "location" -> mapOf("latitude" to "6.5244", "longitude" to "3.3792")
                    "contact" -> mapOf("name" to "Test Contact", "phone" to "+2348000000000")
                    "poll" -> mapOf("question" to "Choose?", "options" to "A\u001FB")
                    else -> mapOf("caption" to type)
                }
                val pending = ChatMessage(
                    text = "",
                    fromMe = true,
                    id = localId,
                    delivered = false,
                    messageType = type,
                    messagePayload = payload
                )
                FynxChatStore.save(context, recipient, listOf(pending))

                val accepted = ChatMessage(
                    text = "",
                    fromMe = true,
                    id = serverId,
                    delivered = true,
                    read = false,
                    messageType = type,
                    messagePayload = payload
                )
                FynxChatStore.replaceMessage(context, recipient, localId, accepted)

                val afterAcceptance = FynxChatStore.load(context, recipient)
                assertEquals("Only the server-accepted bubble should remain for $type", 1, afterAcceptance.size)
                assertEquals(serverId, afterAcceptance.single().id)
                assertTrue(afterAcceptance.single().delivered)
                assertEquals(type, afterAcceptance.single().messageType)
                assertEquals(payload, afterAcceptance.single().messagePayload)

                // A late Compose snapshot containing the old local ID must not
                // resurrect the pending bubble after server acceptance.
                FynxChatStore.save(context, recipient, listOf(pending))
                val afterStaleSnapshot = FynxChatStore.load(context, recipient)
                assertEquals("Stale pending state must not replace accepted $type message", 1, afterStaleSnapshot.size)
                assertEquals(serverId, afterStaleSnapshot.single().id)
                assertEquals(type, afterStaleSnapshot.single().messageType)
            } finally {
                FynxChatStore.clear(context, recipient)
            }
        }
    }


    
    @Test
    fun structuredSendFailureQueuesAndReconnectDrainReplacesPendingForEveryType() = runBlocking {
        val types = listOf("location", "contact", "poll", "sticker", "gif")
        val recipients = types.associateWith { "offline-reconnect-$it-${UUID.randomUUID()}" }
        val pendingIds = mutableMapOf<String, String>()
        var online = false
        var serverSequence = 70000L
        val acceptedRequestIds = mutableMapOf<String, String>()
        FynxBackendClient.requestExecutorForTests = { _, method, path, body, _ ->
            when {
                method == "GET" && path == "/api/me" -> """{"user":{"id":"local-user"}}"""
                method == "GET" && path.startsWith("/api/messages/") -> """{"messages":[]}"""
                method == "POST" && path == "/api/messages" && !online ->
                    throw java.io.IOException("simulated connection failure")
                method == "POST" && path == "/api/messages" && online -> {
                    val request = org.json.JSONObject(requireNotNull(body))
                    val type = request.getString("messageType")
                    val payload = request.getJSONObject("messagePayload")
                    acceptedRequestIds[type] = request.getString("clientMessageId")
                    val acceptedId = (serverSequence++).toString()
                    """{"message":{"id":"$acceptedId","sender_id":"local-user","sender_username":"$testUsername","recipient_id":"peer","recipient_username":"${recipients[type]}","text":"","timestamp":${System.currentTimeMillis()},"delivered":true,"read":false,"edited":false,"deleted":false,"message_type":"$type","message_payload":$payload}}"""
                }
                else -> throw AssertionError("Unexpected test transport request: $method $path")
            }
        }
        try {
            types.forEach { type ->
                val payload = when (type) {
                    "location" -> mapOf("latitude" to "6.5244", "longitude" to "3.3792")
                    "contact" -> mapOf("name" to "Offline Contact", "phone" to "+2348000000000")
                    "poll" -> mapOf("question" to "Reconnect?", "options" to "Yes\\u001FNo")
                    else -> mapOf("caption" to type)
                }
                val mediaBacked = type == "sticker" || type == "gif"
                val requestId = "reconnect-request-$type-${UUID.randomUUID()}"
                val sent = FynxProductionMessaging.sendStructuredMessage(
                    context = context,
                    recipientUsername = recipients.getValue(type),
                    messageType = type,
                    payload = payload,
                    mediaId = if (mediaBacked) "12345" else null,
                    mediaType = if (mediaBacked) "image" else null,
                    requestId = requestId,
                    expectedAccountKey = testUsername,
                    requestToken = null
                )
                assertTrue("Offline $type send should return a pending local message", sent.isSuccess)
                val pending = sent.getOrThrow()
                assertTrue(pending.id.startsWith("offline-"))
                assertFalse(pending.delivered)
                pendingIds[type] = pending.id
                FynxChatStore.save(context, recipients.getValue(type), listOf(FynxProductionMessaging.toChatMessage(pending, "local-user")))
            }
            val queued = FynxOfflineOutbox.load(context)
            assertEquals(types.toSet(), queued.map { it.messageType }.toSet())
            assertEquals(types.size, queued.size)
            assertTrue(queued.all { it.requestId.startsWith("reconnect-request-") })

            online = true
            assertEquals(types.size, FynxOfflineSync.drain(context))
            assertEquals(
                "Reconnect must replay the exact clientMessageId originally persisted for each structured send",
                queued.associate { it.messageType to it.requestId },
                acceptedRequestIds
            )
            assertTrue(FynxOfflineOutbox.load(context).isEmpty())
            types.forEach { type ->
                val messages = FynxChatStore.load(context, recipients.getValue(type))
                assertEquals("Only the accepted server bubble should remain for $type", 1, messages.size)
                assertFalse(messages.single().id == pendingIds.getValue(type))
                assertTrue(messages.single().delivered)
                assertEquals(type, messages.single().messageType)
            }
        } finally {
            FynxBackendClient.requestExecutorForTests = null
            FynxOfflineOutbox.load(context).forEach { FynxOfflineOutbox.remove(context, it.id, expectedAccountKey = testUsername) }
            recipients.values.forEach { FynxChatStore.clear(context, it) }
        }
    }

    @Test
    fun plainTextOfflineSendPersistsPendingBubbleAndDrainsWithStableId() = runBlocking {
        val recipient = "plain-text-offline-${UUID.randomUUID()}"
        val requestId = "plain-text-request-${UUID.randomUUID()}"
        var online = false
        var acceptedClientId: String? = null
        FynxBackendClient.requestExecutorForTests = { _, method, path, body, _ ->
            when {
                method == "GET" && path == "/api/me" -> """{"user":{"id":"local-user"}}"""
                method == "GET" && path.startsWith("/api/messages/") -> """{"messages":[]}"""
                method == "POST" && path == "/api/messages" && !online ->
                    throw java.io.IOException("Unable to resolve host")
                method == "POST" && path == "/api/messages" && online -> {
                    val request = org.json.JSONObject(requireNotNull(body))
                    acceptedClientId = request.getString("clientMessageId")
                    """{"message":{"id":"880001","sender_id":"local-user","sender_username":"$testUsername","recipient_id":"peer","recipient_username":"$recipient","text":"Queued while offline","timestamp":${System.currentTimeMillis()},"delivered":true,"read":false,"edited":false,"deleted":false}}"""
                }
                else -> throw AssertionError("Unexpected test transport request: $method $path")
            }
        }
        try {
            assertTrue("DNS failures must be recognized as retryable", FynxProductionMessaging.canQueueOffline(java.io.IOException("Unable to resolve host")))
            val result = FynxProductionMessaging.sendText(
                context = context,
                recipientUsername = recipient,
                text = "Queued while offline",
                requestId = requestId,
                expectedAccountKey = testUsername,
                requestToken = null
            )
            assertTrue("An offline text send should become a local pending message", result.isSuccess)
            val pending = result.getOrThrow()
            assertTrue(pending.id.startsWith("offline-"))
            assertFalse(pending.delivered)
            FynxChatStore.save(context, recipient, listOf(FynxProductionMessaging.toChatMessage(pending, "local-user")))
            assertEquals("The pending bubble must survive a fresh store read", pending.id, FynxChatStore.load(context, recipient).single().id)
            assertEquals(requestId, FynxOfflineOutbox.load(context).single().requestId)

            online = true
            assertEquals(1, FynxOfflineSync.drain(context))
            assertEquals(requestId, acceptedClientId)
            assertTrue(FynxOfflineOutbox.load(context).isEmpty())
            val accepted = FynxChatStore.load(context, recipient).single()
            assertEquals("880001", accepted.id)
            assertTrue(accepted.delivered)
        } finally {
            FynxBackendClient.requestExecutorForTests = null
            FynxOfflineOutbox.load(context).forEach { FynxOfflineOutbox.remove(context, it.id, expectedAccountKey = testUsername) }
            FynxChatStore.clear(context, recipient)
        }
    }

    @Test
    fun locallyCreatedGroupsAndPendingSyncSurviveFreshStoreReads() {
        val groupId = "durable-group-${UUID.randomUUID()}"
        val group = FynxGroup(
            id = groupId,
            name = "Durable test group",
            description = "Local persistence test",
            visibility = FynxGroupVisibility.PRIVATE,
            ownerUsername = testUsername,
            members = listOf(FynxGroupMember(testUsername, FynxGroupRole.ADMIN))
        )
        try {
            assertTrue(FynxGroupsStore.addPendingGroupDurably(context, group, testUsername))
            val restored = FynxGroupsStore.load(context, testUsername).single { it.id == groupId }
            assertEquals(testUsername, restored.ownerUsername)
            assertEquals(FynxGroupRole.ADMIN, restored.members.single().role)
            assertTrue(FynxGroupsStore.pendingSyncGroups(context, testUsername).contains(groupId))
        } finally {
            FynxGroupsStore.removeGroup(context, groupId, testUsername)
        }
    }

}