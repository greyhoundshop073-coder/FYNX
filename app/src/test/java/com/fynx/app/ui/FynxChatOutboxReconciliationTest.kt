package com.fynx.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FynxChatOutboxReconciliationTest {
    private val pending = ChatMessage(
        text = "",
        fromMe = true,
        id = "offline-poll-1",
        timestamp = 100L,
        delivered = false,
        messageType = "poll",
        messagePayload = mapOf("question" to "Choose?", "options" to "A\u001FB")
    )
    private val authoritative = pending.copy(id = "9812", delivered = true)
    private val aliases = mapOf(pending.id to authoritative.id)

    @Test
    fun replacesPendingSnapshotWithAuthoritativeServerMessage() {
        val result = reconcilePendingOutboxMessages(listOf(pending), listOf(authoritative), aliases)
        assertEquals(listOf(authoritative), result.messages)
        assertEquals(aliases, result.remainingAliases)
    }

    @Test
    fun preservesServerMessageWhenOlderSnapshotHasNotSeenPendingItemYet() {
        val earlier = ChatMessage(text = "Earlier", fromMe = false, id = "7001", timestamp = 50L)
        val result = reconcilePendingOutboxMessages(
            snapshot = listOf(earlier),
            previous = listOf(earlier, authoritative),
            aliases = aliases
        )
        assertEquals(listOf(earlier, authoritative), result.messages)
        assertEquals(aliases, result.remainingAliases)
    }

    @Test
    fun clearsAliasOnceUiSnapshotContainsServerMessage() {
        val result = reconcilePendingOutboxMessages(listOf(authoritative), listOf(authoritative), aliases)
        assertEquals(listOf(authoritative), result.messages)
        assertEquals(emptyMap<String, String>(), result.remainingAliases)
    }

    @Test
    fun leavesUnrelatedSnapshotsUntouchedWhenThereAreNoAliases() {
        val message = ChatMessage(text = "Hello", fromMe = true, id = "1001", timestamp = 1L)
        val snapshot = listOf(message)
        val result = reconcilePendingOutboxMessages(snapshot, emptyList(), emptyMap())
        assertEquals(snapshot, result.messages)
        assertEquals(emptyMap<String, String>(), result.remainingAliases)
    }
}
