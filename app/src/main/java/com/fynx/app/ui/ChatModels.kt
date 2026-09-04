package com.fynx.app.ui

data class ChatPreview(
    val name: String,
    val username: String,
    val lastMessage: String,
    val time: String,
    val unreadCount: Int = 0,
    val online: Boolean = false,
    val avatarUri: String? = null
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
    val voiceDurationMs: Long = 0L,
    val mediaId: String? = null,
    val senderName: String? = null,
    val senderUsername: String? = null,
    val senderAvatarUri: String? = null
)
