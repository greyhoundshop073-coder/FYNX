package com.fynx.app.ui

data class ChatPreview(
    val id: String,
    val name: String,
    val username: String,
    val lastMessage: String,
    val unreadCount: Int = 0,
    val online: Boolean = false
)

data class ChatMessage(
    val id: String,
    val chatId: String,
    val text: String,
    val fromMe: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val read: Boolean = false
)
