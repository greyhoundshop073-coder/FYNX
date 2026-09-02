package com.fynx.app.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Production messaging boundary. The UI can keep using ChatMessage while the
 * backend becomes the source of truth for authenticated conversations.
 */
object FynxProductionMessaging {
    data class RemoteMessage(
        val id: String,
        val senderId: String,
        val recipientId: String,
        val text: String,
        val timestamp: Long,
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
                        add(
                            RemoteMessage(
                                id = item.optString("id"),
                                senderId = item.optString("sender_id"),
                                recipientId = item.optString("recipient_id"),
                                text = item.optString("text"),
                                timestamp = item.optDouble("timestamp", 0.0).toLong(),
                                edited = item.optBoolean("edited"),
                                deleted = item.optBoolean("deleted"),
                                replyToId = item.optString("reply_to_id").takeIf { it.isNotBlank() && it != "null" }
                            )
                        )
                    }
                }
            }

    suspend fun sendText(context: Context, recipientUsername: String, text: String, replyToId: String? = null): Result<RemoteMessage> {
        val body = JSONObject().apply {
            put("recipientUsername", recipientUsername.trim().removePrefix("@"))
            put("text", text.trim())
            if (replyToId.isNullOrBlank()) put("replyToId", JSONObject.NULL) else put("replyToId", replyToId.toLongOrNull() ?: JSONObject.NULL)
        }
        return FynxBackendClient.postJson(context, "/api/messages", body.toString()).mapCatching { raw ->
            val item = JSONObject(raw).getJSONObject("message")
            RemoteMessage(
                id = item.optString("id"),
                senderId = item.optString("senderId"),
                recipientId = item.optString("recipientId"),
                text = item.optString("text"),
                timestamp = item.optDouble("timestamp", 0.0).toLong(),
                edited = item.optBoolean("edited"),
                deleted = item.optBoolean("deleted"),
                replyToId = item.optString("replyToId").takeIf { it.isNotBlank() && it != "null" }
            )
        }
    }

    fun toChatMessage(message: RemoteMessage, currentUserId: String): ChatMessage = ChatMessage(
        text = if (message.deleted) "Message deleted" else message.text,
        fromMe = message.senderId == currentUserId,
        id = message.id,
        timestamp = message.timestamp,
        delivered = true,
        read = message.senderId == currentUserId,
        replyToId = message.replyToId,
        edited = message.edited
    )

    private fun encodePathSegment(value: String): String =
        java.net.URLEncoder.encode(value.trim().removePrefix("@"), Charsets.UTF_8.name())
}
