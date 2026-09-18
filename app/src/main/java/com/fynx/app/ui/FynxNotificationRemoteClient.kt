package com.fynx.app.ui

import android.content.Context
import org.json.JSONObject
import java.net.URLEncoder

/** Server-backed notification feed. Safety notifications are authoritative. */
object FynxNotificationRemoteClient {
    data class Feed(val notifications: List<FynxNotification>, val unreadCount: Int)

    suspend fun loadFeed(context: Context): Result<Feed> =
        FynxBackendClient.get(context, "/api/notifications").mapCatching { raw ->
            val body = JSONObject(raw)
            val array = body.optJSONArray("notifications")
            val notifications = buildList {
                if (array != null) {
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
                                sourceUsername = item.optString("sourceUsername").ifBlank { null },
                                targetIds = buildList {
                                    val ids = item.optJSONArray("targetIds")
                                    if (ids != null) for (targetIndex in 0 until ids.length()) ids.optString(targetIndex).trim().takeIf { it.isNotEmpty() }?.let(::add)
                                    if (isEmpty()) item.optString("targetId").trim().takeIf { it.isNotEmpty() }?.let(::add)
                                },
                                sourceUsernames = buildList {
                                    val names = item.optJSONArray("sourceUsernames")
                                    if (names != null) for (nameIndex in 0 until names.length()) names.optString(nameIndex).trim().takeIf { it.isNotEmpty() }?.let(::add)
                                    if (isEmpty()) item.optString("sourceUsername").trim().takeIf { it.isNotEmpty() }?.let(::add)
                                }
                            ))
                        }
                    }
                }
            }
            Feed(notifications, body.optInt("unreadCount", notifications.count { !it.read }))
        }

    suspend fun load(context: Context): Result<List<FynxNotification>> =
        loadFeed(context).map { it.notifications }

    suspend fun markRead(context: Context, id: String): Result<Unit> {
        val encoded = URLEncoder.encode(id, "UTF-8").replace("+", "%20")
        return FynxBackendClient.postJson(context, "/api/notifications/$encoded/read", "{}").map { Unit }
    }

    suspend fun markAllRead(context: Context): Result<Unit> =
        FynxBackendClient.postJson(context, "/api/notifications/read-all", "{}").map { Unit }
}
