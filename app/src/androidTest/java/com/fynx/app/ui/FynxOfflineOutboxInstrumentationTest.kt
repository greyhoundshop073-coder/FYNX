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
}
