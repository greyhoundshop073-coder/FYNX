package com.fynx.app.ui

/**
 * Applies the server's authoritative per-member read-receipt snapshot to the
 * currently displayed group messages without changing order or unrelated fields.
 */
internal fun applyGroupReadReceiptUpdates(
    messages: List<ChatMessage>,
    readByMessageId: Map<String, List<String>>
): List<ChatMessage> {
    if (readByMessageId.isEmpty()) return messages

    return messages.map { message ->
        val readers = readByMessageId[message.id] ?: return@map message
        if (message.readBy == readers) message else message.copy(readBy = readers)
    }
}
