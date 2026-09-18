package com.fynx.app.ui

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

data class FynxAiConversationSummary(val id: String, val title: String, val createdAt: Long, val updatedAt: Long)
data class FynxAiStoredMessage(val id: String, val role: String, val text: String, val timestamp: Long, val attachmentIds: List<String> = emptyList())
data class FynxAiConversation(val id: String, val title: String, val createdAt: Long, val updatedAt: Long, val messages: List<FynxAiStoredMessage>)
data class FynxAiPendingMessageAction(val actionId: String, val recipientUsername: String, val recipientDisplayName: String, val message: String)\ndata class FynxAiConversationReply(val conversationId: String, val userMessageId: String, val assistantMessage: FynxAiStoredMessage, val pendingAction: FynxAiPendingMessageAction? = null)

object FynxAiConversationClient {
    suspend fun create(context: Context): Result<FynxAiConversation> =
        FynxBackendClient.postJson(context, "/api/assistant/conversations", JSONObject().toString())
            .mapCatching { parseConversation(JSONObject(it).getJSONObject("conversation")) }

    suspend fun list(context: Context): Result<List<FynxAiConversationSummary>> =
        FynxBackendClient.get(context, "/api/assistant/conversations").mapCatching { raw ->
            val array = JSONObject(raw).optJSONArray("conversations") ?: JSONArray()
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(FynxAiConversationSummary(item.getString("id"), item.optString("title", "New conversation"), item.optLong("createdAt", 0L), item.optLong("updatedAt", 0L)))
                }
            }
        }

    suspend fun get(context: Context, conversationId: String): Result<FynxAiConversation> =
        FynxBackendClient.get(context, "/api/assistant/conversations/${encode(conversationId)}")
            .mapCatching { parseConversation(JSONObject(it).getJSONObject("conversation")) }

    suspend fun delete(context: Context, conversationId: String): Result<Unit> =
        FynxBackendClient.delete(context, "/api/assistant/conversations/${encode(conversationId)}").map { Unit }

    suspend fun send(context: Context, conversationId: String, message: String, mediaIds: List<String> = emptyList()): Result<FynxAiConversationReply> {
        val body = JSONObject().put("message", message.trim()).put("mediaIds", JSONArray(mediaIds.distinct().take(4)))
        return FynxBackendClient.postJson(context, "/api/assistant/conversations/${encode(conversationId)}/message", body.toString()).mapCatching { raw ->
            val json = JSONObject(raw)
            val reply = json.getJSONObject("assistantMessage")
            FynxAiConversationReply(
                conversationId = json.getString("conversationId"),
                userMessageId = json.getString("userMessageId"),
                assistantMessage = FynxAiStoredMessage(reply.getString("id"), "assistant", reply.optString("text"), reply.optLong("timestamp", System.currentTimeMillis())),
                pendingAction = json.optJSONObject("pendingAction")?.let { action ->
                    val recipient = action.optJSONObject("recipient")
                    FynxAiPendingMessageAction(action.getString("actionId"), recipient?.optString("username","") ?: "", recipient?.optString("displayName","") ?: "", action.optString("message"))
                }
            )
        }
    }

    suspend fun cancelMessage(context: Context, actionId: String): Result<Unit> =\n        FynxBackendClient.postJson(context, "/api/assistant/message-cancel", JSONObject().put("actionId", actionId).toString()).map { Unit }\n\n    suspend fun confirmMessage(context: Context, actionId: String): Result<String> =\n        FynxBackendClient.postJson(context, "/api/assistant/message-confirm", JSONObject().put("actionId", actionId).toString()).mapCatching {\n            JSONObject(it).getJSONObject("message").optString("text")\n        }\n\n    suspend fun uploadImage(context: Context, uri: Uri): Result<String> {
        val mime = context.contentResolver.getType(uri)?.trim()?.lowercase().orEmpty()
        if (!mime.startsWith("image/")) return Result.failure(IllegalArgumentException("Select an image file."))
        return FynxProductionMessaging.uploadMedia(context, uri, mime).map { it.id }
    }

    private fun parseConversation(item: JSONObject): FynxAiConversation {
        val messagesArray = item.optJSONArray("messages") ?: JSONArray()
        val messages = buildList {
            for (i in 0 until messagesArray.length()) {
                val message = messagesArray.getJSONObject(i)
                val attachments = message.optJSONArray("attachments") ?: JSONArray()
                val ids = buildList {
                    for (j in 0 until attachments.length()) add(attachments.getJSONObject(j).optString("id"))
                }.filter { it.isNotBlank() }
                add(FynxAiStoredMessage(message.getString("id"), message.optString("role"), message.optString("text"), message.optLong("timestamp", 0L), ids))
            }
        }
        return FynxAiConversation(
            item.getString("id"), item.optString("title", "New conversation"),
            item.optLong("createdAt", 0L), item.optLong("updatedAt", 0L), messages
        )
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
}
