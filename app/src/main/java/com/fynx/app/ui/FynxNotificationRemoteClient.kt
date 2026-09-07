package com.fynx.app.ui

import android.content.Context
import org.json.JSONObject
import java.net.URLEncoder

/** Server-backed notification feed. Safety notifications are authoritative. */
object FynxNotificationRemoteClient {
    suspend fun load(context: Context): Result<List<FynxNotification>> =
        FynxBackendClient.get(context, "/api/notifications").mapCatching { raw ->
            val array = JSONObject(raw).optJSONArray("notifications") ?: return@mapCatching emptyList()
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val type = runCatching {
                        FynxNotificationType.valueOf(item.optString("type"))
                    }.getOrDefault(FynxNotificationType.SAFETY)
                    val id = item.optString("id")
                    val title = item.optString("title")
                    val message = item.optString("message")
                    if (id.isNotBlank() && title.isNotBlank() && message.isNotBlank()) {
                        add(FynxNotification(
                            id = id,
                            type = type,
                            title = title,
                            message = message,
                            timestamp = item.optLong("timestamp", System.currentTimeMillis()),
                            read = item.optBoolean("read", false),
                            targetId = item.optString("targetId").ifBlank { null },
                            sourceUsername = item.optString("sourceUsername").ifBlank { null }
                        ))
                    }
                }
            }
        }

    suspend fun markRead(context: Context, id: String): Result<Unit> {
        val encoded = URLEncoder.encode(id, "UTF-8").replace("+", "%20")
        return FynxBackendClient.postJson(context, "/api/notifications/$encoded/read", "{}").map { Unit }
    }
}
