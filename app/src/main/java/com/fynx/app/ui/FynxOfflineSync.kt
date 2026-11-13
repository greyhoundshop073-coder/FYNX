package com.fynx.app.ui

import android.content.Context
import android.net.Uri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Durable outbox synchronizer. It is deliberately isolated from the existing
 * chat UI so the transport can be upgraded without replacing the conversation
 * surface. Each item is removed only after the server accepts it.
 */
object FynxOfflineSync {
    private val drainMutex = Mutex()

    private fun conversationStoreKey(context: Context, item: FynxOfflineOutbox.Item): String {
        val recipient = item.recipient.trim().removePrefix("@")
        val candidates = listOf(item.recipient, "@$recipient")
        return candidates.firstOrNull { candidate ->
            FynxChatStore.load(context, candidate).any { it.id == item.id }
        } ?: "@$recipient"
    }

    suspend fun drain(context: Context): Int = drainMutex.withLock {
        withContext(Dispatchers.IO) {
        val accountKey = FynxAuthStore.accountStorageKey(context)?.takeIf { it.isNotBlank() } ?: return@withContext 0
        var completed = 0
        val items = FynxOfflineOutbox.load(context)
        for (item in items) {
            if (FynxAuthStore.accountStorageKey(context) != accountKey) break
            val acceptedMessageId = item.sentMessageId?.takeIf { it.isNotBlank() }
            if (acceptedMessageId != null) {
                val linked = if (!item.marketplaceListingId.isNullOrBlank()) {
                    FynxR6GIntegrationClient.attachListingToMessage(context, acceptedMessageId, item.marketplaceListingId)
                } else {
                    Result.success(Unit)
                }
                if (linked.isFailure) break
                if (FynxAuthStore.accountStorageKey(context) != accountKey) break
                FynxOfflineOutbox.remove(context, item.id)
                completed++
                continue
            }
            var resolvedMediaId = item.mediaId
            if (resolvedMediaId.isNullOrBlank() && !item.localMediaPath.isNullOrBlank()) {
                if (FynxAuthStore.accountStorageKey(context) != accountKey) break
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
            if (FynxAuthStore.accountStorageKey(context) != accountKey) break
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
                if (FynxAuthStore.accountStorageKey(context) != accountKey) break
                FynxChatStore.replaceMessage(
                    context,
                    conversationStoreKey(context, item),
                    item.id,
                    FynxProductionMessaging.toChatMessage(remote, currentUserId)
                )
                FynxOfflineOutbox.markSent(context, item.id, remote.id)
                // Keep this item until the attached listing context is also accepted.
                // The stable requestId makes a retry safe if the message already exists.
                if (!item.marketplaceListingId.isNullOrBlank()) {
                    val linked = FynxR6GIntegrationClient.attachListingToMessage(
                        context, remote.id, item.marketplaceListingId
                    )
                    if (linked.isFailure) break
                }
                if (FynxAuthStore.accountStorageKey(context) != accountKey) break
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
}
