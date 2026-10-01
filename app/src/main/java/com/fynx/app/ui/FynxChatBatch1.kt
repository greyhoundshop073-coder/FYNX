package com.fynx.app.ui

enum class FynxMessageStatus { SENDING, SENT, DELIVERED, READ, FAILED }

data class FynxChatMessageState(
    val message: FynxChatMessage,
    val status: FynxMessageStatus = FynxMessageStatus.SENT,
    val replyToMessageId: String? = null,
    val reactions: Set<String> = emptySet(),
    val pinned: Boolean = false,
    val deleted: Boolean = false
)

class FynxChatBatch1Store {
    private val messages = mutableListOf<FynxChatMessageState>()
    private val mutedConversations = mutableSetOf<String>()

    fun add(message: FynxChatMessage): FynxChatMessageState {
        val state = FynxChatMessageState(message = message)
        messages += state
        return state
    }

    fun all(conversationId: String): List<FynxChatMessageState> =
        messages.filter { it.message.conversationId == conversationId && !it.deleted }

    fun updateStatus(id: String, status: FynxMessageStatus) {
        update(id) { it.copy(status = status) }
    }

    fun toggleReaction(id: String, reaction: String) {
        update(id) { state ->
            val next = state.reactions.toMutableSet()
            if (!next.add(reaction)) next.remove(reaction)
            state.copy(reactions = next)
        }
    }

    fun setReply(id: String, replyToId: String?) {
        update(id) { it.copy(replyToMessageId = replyToId) }
    }

    fun togglePinned(id: String) {
        update(id) { it.copy(pinned = !it.pinned) }
    }

    fun delete(id: String) {
        update(id) { it.copy(deleted = true) }
    }

    fun toggleMute(conversationId: String): Boolean {
        if (!mutedConversations.add(conversationId)) mutedConversations.remove(conversationId)
        return conversationId in mutedConversations
    }

    fun isMuted(conversationId: String): Boolean = conversationId in mutedConversations

    private fun update(id: String, transform: (FynxChatMessageState) -> FynxChatMessageState) {
        val index = messages.indexOfFirst { it.message.id == id }
        if (index >= 0) messages[index] = transform(messages[index])
    }
}
