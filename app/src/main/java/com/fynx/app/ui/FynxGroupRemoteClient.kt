package com.fynx.app.ui

import android.content.Context
import org.json.JSONObject
import org.json.JSONArray

object FynxGroupRemoteClient {
    data class RemoteMessage(
        val id: String,
        val text: String,
        val senderUsername: String,
        val timestamp: Long,
        val attachmentMediaId: String?,
        val attachmentType: String?,
        val attachmentUrl: String?
    )

    suspend fun syncGroup(context: Context, group: FynxGroup): Result<Unit> = runCatching {
        val body = JSONObject().apply {
            put("ownerUsername", group.ownerUsername.trim().removePrefix("@"))
            put("name", group.name)
            put("description", group.description)
            put("visibility", group.visibility.name)
            put("members", JSONArray().apply { group.members.forEach { put(it.username.trim().removePrefix("@")) } })
        }
        FynxBackendClient.postJson(context, "/api/groups/${group.id}/sync", body.toString()).getOrThrow()
    }

    suspend fun loadMessages(context: Context, groupId: String): Result<List<RemoteMessage>> = runCatching {
        val raw = FynxBackendClient.get(context, "/api/groups/$groupId/messages").getOrThrow()
        val array = JSONObject(raw).optJSONArray("messages") ?: JSONArray()
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(RemoteMessage(
                    id = item.getString("id"),
                    text = item.optString("text"),
                    senderUsername = item.optString("senderUsername"),
                    timestamp = item.optLong("timestamp"),
                    attachmentMediaId = item.optString("attachmentMediaId").takeIf { it.isNotBlank() && it != "null" },
                    attachmentType = item.optString("attachmentType").takeIf { it.isNotBlank() && it != "null" },
                    attachmentUrl = item.optString("attachmentUrl").takeIf { it.isNotBlank() && it != "null" }
                ))
            }
        }
    }

    suspend fun sendMessage(context: Context, groupId: String, message: ChatMessage): Result<RemoteMessage> = runCatching {
        val mediaId = message.attachmentUri?.substringAfterLast('/')?.takeIf { it.all(Char::isDigit) }
        val body = JSONObject().apply {
            put("id", message.id)
            put("text", message.text)
            if (mediaId != null) put("attachmentMediaId", mediaId.toLong())
            if (message.attachmentType != null) put("attachmentType", message.attachmentType)
        }
        val raw = FynxBackendClient.postJson(context, "/api/groups/$groupId/messages", body.toString()).getOrThrow()
        val item = JSONObject(raw).getJSONObject("message")
        RemoteMessage(
            id = item.getString("id"),
            text = item.optString("text"),
            senderUsername = item.optString("senderUsername"),
            timestamp = item.optLong("timestamp"),
            attachmentMediaId = item.optString("attachmentMediaId").takeIf { it.isNotBlank() && it != "null" },
            attachmentType = item.optString("attachmentType").takeIf { it.isNotBlank() && it != "null" },
            attachmentUrl = item.optString("attachmentUrl").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    fun toChatMessage(message: RemoteMessage, currentUsername: String, baseUrl: String): ChatMessage = ChatMessage(
        text = message.text,
        fromMe = message.senderUsername.equals(currentUsername.removePrefix("@"), ignoreCase = true),
        id = message.id,
        timestamp = message.timestamp,
        delivered = true,
        read = true,
        attachmentUri = message.attachmentUrl?.let { if (it.startsWith("http")) it else baseUrl.trimEnd('/') + it },
        attachmentType = message.attachmentType
    )
}
