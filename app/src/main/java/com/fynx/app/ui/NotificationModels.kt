package com.fynx.app.ui

enum class FynxNotificationType { MESSAGE, FRIEND_REQUEST, STORY, REMINDER, SAFETY, GROUP, REACTION, COMMENT, MARKETPLACE_ORDER, WALLET_ACTIVITY }

data class FynxNotification(
    val id: String,
    val type: FynxNotificationType,
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val read: Boolean = false,
    val targetId: String? = null,
    val sourceUsername: String? = null
)

fun List<FynxNotification>.markNotificationRead(id: String): List<FynxNotification> =
    map { if (it.id == id) it.copy(read = true) else it }

fun List<FynxNotification>.unreadNotificationCount(): Int = count { !it.read }
