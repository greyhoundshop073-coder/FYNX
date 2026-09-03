package com.fynx.app.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Production messaging boundary. The server is the source of truth for chat state. */
object FynxProductionMessaging {
    data class RemoteMessage(
        val id: String,
        val senderId: String,
        val recipientId: String,
        val text: String,
        val timestamp: Long,
        val delivered: Boolean,
        val read: Boolean,
        val edited: Boolean,
        val deleted: Boolean,
        val replyToId: String?
    )

    suspend fun history(context: Context, username: String): Result<List<RemoteMessage>> =
        FynxBackendClient.get(context, "/api/messages/${encodePathSegment(username)}")
            .mapCatching { raw ->
                val messages = JSONObject(raw).optJSONArray("messages") ?: JSONArray()
                buildList {
                    for (index in 0 until messages.length()) {
                        val item = messages.getJSONObject(index)
                        add(fromJson(item))
                    }
                }
            }

    suspend fun sendText(context: Context, recipientUsername: String, text: String, replyToId: String? = null): Result<RemoteMessage> {
        val normalizedRecipient = recipientUsername.trim().removePrefix("@").lowercase()
        val currentUsername = (FynxAuthStore.load(context).username ?: "").trim().removePrefix("@").lowercase()
        if (normalizedRecipient.isBlank()) return Result.failure(IllegalArgumentException("A recipient is required."))
        if (currentUsername.isNotBlank() && normalizedRecipient == currentUsername) {
            return Result.failure(IllegalArgumentException("You cannot send a message to your own account."))
        }
        val body = JSONObject().apply {
            put("recipientUsername", normalizedRecipient)
            put("text", text.trim())
            if (replyToId.isNullOrBlank()) put("replyToId", JSONObject.NULL) else put("replyToId", replyToId.toLongOrNull() ?: JSONObject.NULL)
        }
        return FynxBackendClient.postJson(context, "/api/messages", body.toString()).mapCatching { raw ->
            fromJson(JSONObject(raw).getJSONObject("message"))
        }
    }

    suspend fun markRead(context: Context, messageIds: List<String>): Result<Int> {
        val body = JSONObject().apply {
            put("messageIds", JSONArray(messageIds.mapNotNull { it.toLongOrNull() }))
        }
        return FynxBackendClient.postJson(context, "/api/messages/read", body.toString()).mapCatching { raw ->
            JSONObject(raw).optInt("updated", 0)
        }
    }

    fun toChatMessage(message: RemoteMessage, currentUserId: String): ChatMessage = ChatMessage(
        text = if (message.deleted) "Message deleted" else message.text,
        fromMe = message.senderId == currentUserId,
        id = message.id,
        timestamp = message.timestamp,
        delivered = message.delivered,
        read = message.read,
        replyToId = message.replyToId,
        edited = message.edited
    )

    fun fromJson(item: JSONObject): RemoteMessage = RemoteMessage(
        id = item.optString("id"),
        senderId = item.optString("sender_id", item.optString("senderId")),
        recipientId = item.optString("recipient_id", item.optString("recipientId")),
        text = item.optString("text"),
        timestamp = item.optDouble("timestamp", 0.0).toLong(),
        delivered = item.optBoolean("delivered", false),
        read = item.optBoolean("read", false),
        edited = item.optBoolean("edited", false),
        deleted = item.optBoolean("deleted", false),
        replyToId = item.optString("reply_to_id", item.optString("replyToId")).takeIf { it.isNotBlank() && it != "null" }
    )

    private fun encodePathSegment(value: String): String =
        java.net.URLEncoder.encode(value.trim().removePrefix("@"), Charsets.UTF_8.name())
}
