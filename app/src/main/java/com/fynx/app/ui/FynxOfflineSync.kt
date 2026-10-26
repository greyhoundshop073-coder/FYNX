package com.fynx.app.ui

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Durable outbox synchronizer. It is deliberately isolated from the existing
 * chat UI so the transport can be upgraded without replacing the conversation
 * surface. Each item is removed only after the server accepts it or a history
 * reconciliation proves it already exists.
 */
object FynxOfflineSync {
    suspend fun drain(context: Context): Int = withContext(Dispatchers.IO) {
        var completed = 0
        val items = FynxOfflineOutbox.load(context)
        for (item in items) {
            val result = if (item.messageType == "text") {
                FynxProductionMessaging.sendText(
                    context = context,
                    recipientUsername = item.recipient,
                    text = item.text,
                    requestId = item.requestId,
                    replyToId = item.replyToId,
                    mediaId = item.mediaId,
                    mediaType = item.mediaType,
                    voiceDurationMs = item.voiceDurationMs,
                    allowOfflineQueue = false
                )
            } else {
                FynxProductionMessaging.sendStructuredMessage(
                    context = context,
                    recipientUsername = item.recipient,
                    messageType = item.messageType,
                    payload = item.messagePayload,
                    requestId = item.requestId,
                    replyToId = item.replyToId,
                    mediaId = item.mediaId,
                    mediaType = item.mediaType,
                    allowOfflineQueue = false
                )
            }
            if (result.isSuccess) {
                val remote = result.getOrThrow()
                val currentUserId = FynxBackendClient.currentUserId(context).getOrNull()
                if (currentUserId.isNullOrBlank()) {
                    // The server accepted the send, but the local pending ID cannot be reconciled
                    // safely yet. Keep this durable item; its stable requestId makes the next
                    // retry idempotent, and it can replace the pending bubble once identity loads.
                    break
                }
                FynxChatStore.replaceMessage(
                    context,
                    item.recipient,
                    item.id,
                    FynxProductionMessaging.toChatMessage(remote, currentUserId)
                )
                FynxOfflineOutbox.remove(context, item.id)
                completed++
            } else {
                // Keep the item durable. The next connectivity/startup cycle can retry it.
                break
            }
        }
        completed
    }
}
