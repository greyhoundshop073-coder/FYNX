package com.fynx.app.ui

import android.content.Context
import android.net.Uri
import android.util.Base64
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
    fun plainTextPendingMessageIsCommittedToDiskBeforeQueueReportsSuccess() {
        val item = FynxOfflineOutbox.Item(
            id = "offline-plain-${UUID.randomUUID()}",
            requestId = "plain-request-${UUID.randomUUID()}",
            recipient = "plain-recipient",
            text = "Offline plain text survives process death",
            replyToId = null,
            mediaId = null,
            mediaType = null,
            voiceDurationMs = 0L,
            createdAt = System.currentTimeMillis()
        )
        queuedId = item.id
        assertTrue("The local account must accept its pending text item",
            FynxOfflineOutbox.enqueue(context, item, expectedAccountKey = testUsername))
        val encodedAccount = Base64.encodeToString(
            testUsername.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        val preferencesFile = File(context.applicationInfo.dataDir, "shared_prefs/fynx_offline_outbox_$encodedAccount.xml")
        assertTrue("The outbox preference file must exist after enqueue returns", preferencesFile.isFile)
        val onDisk = preferencesFile.readText()
        assertTrue("The pending message ID must already be on disk", onDisk.contains(item.id))
        assertTrue("The pending message text must already be on disk", onDisk.contains(item.text))
        assertEquals(item.id, FynxOfflineOutbox.load(context).single().id)
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
    fun privateTextSendPersistsPendingBubbleAndReconnectReplacesItOnce() = runBlocking {
        val backendPreferences = context.getSharedPreferences("fynx_backend", Context.MODE_PRIVATE)
        val originalSecureToken = FynxSecureTokenStore.load(context)
        val originalLegacyToken = backendPreferences.getString("access_token", null)
        val originalExecutor = FynxBackendClient.requestExecutorForTests
        val recipient = "offline-private-${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val messageText = "Private message saved while offline"
        val requestId = "private-retry-${UUID.randomUUID()}"
        val serverMessageId = "990001"
        var acceptedRequestId: String? = null
        try {
            FynxSecureTokenStore.save(context, null)
            backendPreferences.edit().remove("access_token").commit()
            assertEquals(null, FynxBackendClient.accessToken(context))

            val queued = FynxProductionMessaging.sendText(
                context = context,
                recipientUsername = recipient,
                text = messageText,
                requestId = requestId,
                expectedAccountKey = testUsername,
                requestToken = null,
                localSenderId = "local-user"
            )
            assertTrue("A tokenless local session must save a pending private message", queued.isSuccess)
            val pending = queued.getOrThrow()
            assertTrue(pending.id.startsWith("offline-"))
            assertFalse(pending.delivered)
            assertEquals("true", pending.messagePayload["pendingLocalUpload"])
            assertEquals("Message", pending.messagePayload["pendingLabel"])

            val outboxItem = FynxOfflineOutbox.load(context).single()
            queuedId = outboxItem.id
            assertEquals(requestId, outboxItem.requestId)
            assertEquals(recipient, outboxItem.recipient)
            assertEquals(messageText, outboxItem.text)
            val pendingBubble = FynxProductionMessaging.toChatMessage(pending, "local-user")
            assertTrue("Pending private text must be shown as the signed-in user's own message", pendingBubble.fromMe)
            assertFalse(pendingBubble.delivered)
            assertTrue(FynxChatStore.saveDurably(context, recipient, listOf(pendingBubble)))
            assertEquals(pending.id, FynxChatStore.load(context, recipient).single().id)

            FynxSecureTokenStore.save(context, "local-private-reconnect-token")
            FynxBackendClient.requestExecutorForTests = { _, method, path, body, token ->
                when {
                    method == "POST" && path == "/api/messages" -> {
                        assertEquals("local-private-reconnect-token", token)
                        val request = org.json.JSONObject(requireNotNull(body))
                        acceptedRequestId = request.getString("clientMessageId")
                        assertEquals(requestId, acceptedRequestId)
                        assertEquals(recipient, request.getString("recipientUsername"))
                        assertEquals(messageText, request.getString("text"))
                        """{"message":{"id":"$serverMessageId","sender_id":"local-user","sender_username":"$testUsername","recipient_id":"peer-id","recipient_username":"$recipient","text":"$messageText","timestamp":${System.currentTimeMillis()},"delivered":true,"read":false,"edited":false,"deleted":false,"reply_to_id":null,"media_id":null,"media_type":null,"voice_duration_ms":0}}"""
                    }
                    method == "GET" && path == "/api/me" ->
                        """{"user":{"id":"local-user"}}"""
                    else -> throw AssertionError("Unexpected private-chat reconnect request: $method $path")
                }
            }

            assertEquals("A reconnect must drain one queued private text message", 1, FynxOfflineSync.drain(context))
            assertEquals(requestId, acceptedRequestId)
            assertTrue("A server-accepted message must remove its outbox record", FynxOfflineOutbox.load(context).isEmpty())
            val accepted = FynxChatStore.load(context, recipient)
            assertEquals("The pending bubble must be replaced, not duplicated", 1, accepted.size)
            assertEquals(serverMessageId, accepted.single().id)
            assertEquals(messageText, accepted.single().text)
            assertTrue(accepted.single().fromMe)
            assertTrue(accepted.single().delivered)
            assertFalse(accepted.single().messagePayload.containsKey("pendingLocalUpload"))
            queuedId = null
        } finally {
            FynxBackendClient.requestExecutorForTests = originalExecutor
            FynxSecureTokenStore.save(context, originalSecureToken)
            val editor = backendPreferences.edit()
            if (originalLegacyToken == null) editor.remove("access_token")
            else editor.putString("access_token", originalLegacyToken)
            editor.commit()
            FynxOfflineOutbox.load(context).filter { it.recipient == recipient }
                .forEach { FynxOfflineOutbox.remove(context, it.id, expectedAccountKey = testUsername) }
            FynxChatStore.clear(context, recipient)
        }
    }

}
