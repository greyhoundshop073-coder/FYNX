package com.fynx.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class FynxGroupReadReceiptPersistenceTest {
    @Test
    fun appliesServerReadersOnlyToMatchingMessagesAndPreservesTheRestOfEachMessage() {
        val incoming = ChatMessage(
            text = "Incoming group message",
            fromMe = false,
            id = "group-message-0001",
            reaction = "❤️",
            readBy = listOf("old-reader")
        )
        val localPending = ChatMessage(
            text = "Local pending reply",
            fromMe = true,
            id = "group-message-0002",
            delivered = false
        )

        val updated = applyGroupReadReceiptUpdates(
            listOf(incoming, localPending),
            mapOf(incoming.id to listOf("reader-a", "reader-b"))
        )

        assertEquals(listOf("reader-a", "reader-b"), updated[0].readBy)
        assertEquals(incoming.text, updated[0].text)
        assertEquals(incoming.reaction, updated[0].reaction)
        assertEquals(localPending, updated[1])
    }

    @Test
    fun emptyReaderListClearsAStaleReceiptSnapshot() {
        val message = ChatMessage(
            text = "Incoming group message",
            fromMe = false,
            id = "group-message-0003",
            readBy = listOf("reader-from-old-snapshot")
        )

        val updated = applyGroupReadReceiptUpdates(
            listOf(message),
            mapOf(message.id to emptyList())
        )

        assertEquals(emptyList<String>(), updated.single().readBy)
    }

    @Test
    fun noReceiptUpdatesReturnTheOriginalSnapshot() {
        val messages = listOf(
            ChatMessage("One", false, id = "group-message-0004"),
            ChatMessage("Two", true, id = "group-message-0005", delivered = false)
        )

        assertSame(messages, applyGroupReadReceiptUpdates(messages, emptyMap()))
    }
}
