package com.fynx.app.ui

data class FynxChatMessage(
    val id: String,
    val conversationId: String,
    val senderUsername: String,
    val text: String,
    val timestampMillis: Long,
    val attachmentUri: String? = null
)

data class FynxConversation(
    val id: String,
    val participantUsername: String,
    val participantName: String,
    val messages: List<FynxChatMessage> = emptyList()
)
