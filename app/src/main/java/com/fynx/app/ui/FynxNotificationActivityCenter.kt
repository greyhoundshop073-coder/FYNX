package com.fynx.app.ui

/** Activity-center helpers layered on top of the existing FynxNotification model. */
object FynxNotificationActivityCenter {
    fun filterByType(notifications: List<FynxNotification>, type: FynxNotificationType?): List<FynxNotification> =
        if (type == null) notifications else notifications.filter { it.type == type }

    fun unreadOnly(notifications: List<FynxNotification>, enabled: Boolean): List<FynxNotification> =
        if (enabled) notifications.filter { !it.read } else notifications

    fun markAllRead(notifications: List<FynxNotification>): List<FynxNotification> =
        notifications.map { it.copy(read = true) }

    fun remove(notifications: List<FynxNotification>, id: String): List<FynxNotification> =
        notifications.filterNot { it.id == id }

    fun clearRead(notifications: List<FynxNotification>): List<FynxNotification> =
        notifications.filterNot { it.read }

    fun targetId(notification: FynxNotification): String? = notification.targetId
}
