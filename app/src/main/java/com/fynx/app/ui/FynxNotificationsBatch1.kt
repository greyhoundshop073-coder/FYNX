package com.fynx.app.ui

/** Notification foundation shared by social, chat, groups, marketplace and money features. */
enum class FynxNotificationType {
    REACTION, COMMENT, FRIEND_REQUEST, MESSAGE, GROUP_ACTIVITY, MARKETPLACE_ORDER, WALLET_ACTIVITY
}

data class FynxNotification(
    val id: String,
    val recipientUsername: String,
    val type: FynxNotificationType,
    val title: String,
    val message: String,
    val targetId: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

object FynxNotificationsBatch1 {
    fun validate(notification: FynxNotification): Boolean =
        notification.id.isNotBlank() &&
            notification.recipientUsername.isNotBlank() &&
            notification.title.isNotBlank() &&
            notification.message.isNotBlank()

    fun markRead(notification: FynxNotification): FynxNotification = notification.copy(isRead = true)

    fun markUnread(notification: FynxNotification): FynxNotification = notification.copy(isRead = false)

    fun unreadCount(notifications: List<FynxNotification>, recipientUsername: String): Int =
        notifications.count { it.recipientUsername == recipientUsername && !it.isRead }
}
