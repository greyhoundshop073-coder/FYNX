package com.fynx.app.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Single local source of truth for the FYNX notification center. */
object FynxNotificationStore {
    private const val PREFS = "fynx_notification_store"
    private const val KEY = "notifications"
    private const val MAX_NOTIFICATIONS = 200

    fun load(context: Context): List<FynxNotification> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id")
                    val title = item.optString("title")
                    val message = item.optString("message")
                    if (id.isNotBlank() && title.isNotBlank() && message.isNotBlank()) {
                        add(FynxNotification(
                            id = id,
                            type = runCatching { FynxNotificationType.valueOf(item.optString("type")) }
                                .getOrDefault(FynxNotificationType.SAFETY),
                            title = title,
                            message = message,
                            timestamp = item.optLong("timestamp", System.currentTimeMillis()),
                            read = item.optBoolean("read", false),
                            targetId = item.optString("targetId").ifBlank { null },
                            sourceUsername = item.optString("sourceUsername").ifBlank { null }
                        ))
                    }
                }
            }.sortedByDescending { it.timestamp }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, notifications: List<FynxNotification>) {
        val array = JSONArray()
        notifications.sortedByDescending { it.timestamp }.take(MAX_NOTIFICATIONS).forEach { notification ->
            array.put(JSONObject().apply {
                put("id", notification.id)
                put("type", notification.type.name)
                put("title", notification.title)
                put("message", notification.message)
                put("timestamp", notification.timestamp)
                put("read", notification.read)
                put("targetId", notification.targetId)
                put("sourceUsername", notification.sourceUsername)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, array.toString()).apply()
    }

    fun add(context: Context, notification: FynxNotification) {
        val current = load(context).filterNot { it.id == notification.id }
        save(context, listOf(notification) + current)
    }

    fun markRead(context: Context, id: String) = save(context, load(context).markNotificationRead(id))

    fun markAllRead(context: Context) = save(context, FynxNotificationActivityCenter.markAllRead(load(context)))

    fun clearRead(context: Context) = save(context, FynxNotificationActivityCenter.clearRead(load(context)))
}
