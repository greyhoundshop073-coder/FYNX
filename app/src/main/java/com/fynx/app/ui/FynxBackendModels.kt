package com.fynx.app.ui

/**
 * Server-facing contracts for the next FYNX backend phase.
 * These models intentionally mirror the existing chat UI without replacing
 * the local store. They give us one stable boundary for authenticated sync.
 */
data class FynxServerSession(
    val accessToken: String,
    val userId: String,
    val username: String,
    val expiresAtEpochMs: Long
)

data class FynxServerMessage(
    val id: String,
    val conversationId: String,
    val senderUsername: String,
    val recipientUsername: String,
    val text: String,
    val timestamp: Long,
    val edited: Boolean = false,
    val deleted: Boolean = false,
    val replyToId: String? = null
)

data class FynxMessageSyncResult(
    val messages: List<FynxServerMessage>,
    val nextCursor: String? = null
)

enum class FynxBackendAvailability {
    DISABLED,
    CONFIGURED
}
