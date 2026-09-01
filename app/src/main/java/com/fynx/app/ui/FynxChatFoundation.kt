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

class FynxChatStore {
    private val conversations = mutableListOf<FynxConversation>()

    fun conversations(): List<FynxConversation> = conversations.toList()

    fun getOrCreate(username: String, displayName: String): FynxConversation {
        return conversations.firstOrNull { it.participantUsername == username }
            ?: FynxConversation(
                id = "chat_" + username.removePrefix("@").replace(Regex("[^A-Za-z0-9_]"), "_"),
                participantUsername = username,
                participantName = displayName
            ).also { conversations += it }
    }

    fun sendMessage(
        conversationId: String,
        senderUsername: String,
        text: String,
        timestampMillis: Long = System.currentTimeMillis(),
        attachmentUri: String? = null
    ): FynxChatMessage? {
        val cleanText = text.trim()
        val index = conversations.indexOfFirst { it.id == conversationId }
        if (index < 0 || (cleanText.isEmpty() && attachmentUri == null)) return null

        val message = FynxChatMessage(
            id = "msg_" + conversationId + "_" + timestampMillis + "_" + conversations[index].messages.size,
            conversationId = conversationId,
            senderUsername = senderUsername,
            text = cleanText,
            timestampMillis = timestampMillis,
            attachmentUri = attachmentUri
        )
        val conversation = conversations[index]
        conversations[index] = conversation.copy(messages = conversation.messages + message)
        return message
    }
}
