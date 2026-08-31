package com.fynx.app.ui

data class ChatPreview(
    val name: String,
    val username: String,
    val lastMessage: String,
    val time: String,
    val unreadCount: Int = 0,
    val online: Boolean = false
)

data class ChatMessage(
    val text: String,
    val fromMe: Boolean,
    val id: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val delivered: Boolean = false,
    val read: Boolean = false,
    val replyToId: String? = null,
    val reaction: String? = null,
    val edited: Boolean = false,
    val attachmentUri: String? = null,
    val attachmentType: String? = null,
    val voiceUri: String? = null,
    val voiceDurationMs: Long = 0L
)

val sampleChats = listOf(
    ChatPreview("FYNX Assistant", "@fynx", "How can I help you today?", "Now"),
    ChatPreview("Your first friend", "@username", "Start a conversation", "—")
)
