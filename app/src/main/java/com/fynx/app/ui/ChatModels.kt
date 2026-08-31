package com.fynx.app.ui

data class ChatPreview(val name: String, val username: String, val lastMessage: String, val time: String)

data class ChatMessage(val text: String, val fromMe: Boolean)

val sampleChats = listOf(
    ChatPreview("FYNX Assistant", "@fynx", "How can I help you today?", "Now"),
    ChatPreview("Your first friend", "@username", "Start a conversation", "—")
)
