package com.fynx.app.ui

/** Chat media/file and typing-state models. URI values are local references until backend sync is added. */
enum class FynxChatAttachmentType { IMAGE, VIDEO, VOICE, FILE, LINK }

data class FynxChatAttachment(
    val uri: String,
    val type: FynxChatAttachmentType,
    val displayName: String? = null,
    val mimeType: String? = null,
    val durationMillis: Long? = null
)

data class FynxTypingState(
    val conversationId: String,
    val username: String,
    val isTyping: Boolean,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

object FynxChatBatch2 {
    fun isValidAttachment(attachment: FynxChatAttachment): Boolean =
        attachment.uri.isNotBlank() && (attachment.type != FynxChatAttachmentType.FILE || !attachment.displayName.isNullOrBlank())

    fun attachmentLabel(attachment: FynxChatAttachment): String =
        attachment.displayName ?: when (attachment.type) {
            FynxChatAttachmentType.IMAGE -> "Photo"
            FynxChatAttachmentType.VIDEO -> "Video"
            FynxChatAttachmentType.VOICE -> "Voice message"
            FynxChatAttachmentType.FILE -> "File"
            FynxChatAttachmentType.LINK -> "Link"
        }

    fun setTyping(conversationId: String, username: String, isTyping: Boolean): FynxTypingState =
        FynxTypingState(conversationId, username, isTyping)

    fun isTypingFresh(state: FynxTypingState, nowMillis: Long = System.currentTimeMillis()): Boolean =
        state.isTyping && nowMillis - state.updatedAtMillis <= 5_000L

    fun normalizeLink(url: String): String? {
        val value = url.trim()
        if (value.isEmpty()) return null
        return if (value.startsWith("https://") || value.startsWith("http://")) value else null
    }
}
