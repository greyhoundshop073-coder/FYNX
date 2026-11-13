package com.fynx.app.ui

import android.content.Context
import android.net.Uri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Durable outbox synchronizer. It is deliberately isolated from the existing
 * chat UI so the transport can be upgraded without replacing the conversation
 * surface. Each item is removed only after the server accepts it.
 */
object FynxOfflineSync {
    suspend fun drain(context: Context): Int = withContext(Dispatchers.IO) {
        var completed = 0
        val items = FynxOfflineOutbox.load(context)
        for (item in items) {
            var resolvedMediaId = item.mediaId
            if (resolvedMediaId.isNullOrBlank() && !item.localMediaPath.isNullOrBlank()) {
                val localFile = File(item.localMediaPath)
                if (!localFile.isFile || localFile.length() !in 1..(12L * 1024L * 1024L)) break
                val uploaded = FynxProductionMessaging.uploadMedia(
                    context,
                    Uri.fromFile(localFile),
                    item.localMediaMimeType
                ).getOrNull() ?: break
                resolvedMediaId = uploaded.id
                FynxOfflineOutbox.updateMediaId(context, item.id, uploaded.id)
            }
            val payload = if (!resolvedMediaId.isNullOrBlank()) item.messagePayload + ("mediaId" to resolvedMediaId) else item.messagePayload
            val result = if (item.messageType == "text") {
                FynxProductionMessaging.sendText(
                    context = context,
                    recipientUsername = item.recipient,
                    text = item.text,
                    requestId = item.requestId,
                    replyToId = item.replyToId,
                    mediaId = resolvedMediaId,
                    mediaType = item.mediaType,
                    voiceDurationMs = item.voiceDurationMs,
                    allowOfflineQueue = false
                )
            } else {
                FynxProductionMessaging.sendStructuredMessage(
                    context = context,
                    recipientUsername = item.recipient,
                    messageType = item.messageType,
                    payload = payload,
                    replyToId = item.replyToId,
                    mediaId = resolvedMediaId,
                    mediaType = item.mediaType,
                    allowOfflineQueue = false,
                    requestId = item.requestId
                )
            }
            if (result.isSuccess) {
                val remote = result.getOrThrow()
                val currentUserId = FynxBackendClient.currentUserId(context).getOrNull()
                if (currentUserId.isNullOrBlank()) break
                FynxChatStore.replaceMessage(
                    context,
                    item.recipient,
                    item.id,
                    FynxProductionMessaging.toChatMessage(remote, currentUserId)
                )
                FynxOfflineOutbox.remove(context, item.id)
                completed++
            } else {
                // Keep the item durable. A later connectivity cycle can retry it.
                break
            }
        }
        completed
    }
}
