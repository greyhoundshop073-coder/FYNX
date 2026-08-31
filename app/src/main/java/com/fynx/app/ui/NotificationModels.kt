package com.fynx.app.ui

enum class FynxNotificationType { MESSAGE, FRIEND_REQUEST, STORY, REMINDER, SAFETY, GROUP }

data class FynxNotification(
    val id: String,
    val type: FynxNotificationType,
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val read: Boolean = false
)

fun List<FynxNotification>.markNotificationRead(id: String): List<FynxNotification> =
    map { if (it.id == id) it.copy(read = true) else it }

fun List<FynxNotification>.unreadNotificationCount(): Int = count { !it.read }
