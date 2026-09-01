package com.fynx.app.ui

/** User notification controls layered on top of the existing notification model. */
data class FynxNotificationPreferences(
    val enabled: Boolean = true,
    val pushEnabled: Boolean = true,
    val reactionsEnabled: Boolean = true,
    val commentsEnabled: Boolean = true,
    val friendRequestsEnabled: Boolean = true,
    val messagesEnabled: Boolean = true,
    val storiesEnabled: Boolean = true,
    val remindersEnabled: Boolean = true,
    val groupEnabled: Boolean = true,
    val marketplaceEnabled: Boolean = true,
    val walletEnabled: Boolean = true,
    val quietMode: Boolean = false
)

object FynxNotificationControlsBatch3 {
    fun update(
        current: FynxNotificationPreferences,
        enabled: Boolean? = null,
        pushEnabled: Boolean? = null,
        quietMode: Boolean? = null
    ): FynxNotificationPreferences = current.copy(
        enabled = enabled ?: current.enabled,
        pushEnabled = pushEnabled ?: current.pushEnabled,
        quietMode = quietMode ?: current.quietMode
    )

    fun isTypeEnabled(preferences: FynxNotificationPreferences, type: FynxNotificationType): Boolean =
        if (!preferences.enabled || preferences.quietMode) false else when (type) {
            FynxNotificationType.REACTION -> preferences.reactionsEnabled
            FynxNotificationType.COMMENT -> preferences.commentsEnabled
            FynxNotificationType.FRIEND_REQUEST -> preferences.friendRequestsEnabled
            FynxNotificationType.MESSAGE -> preferences.messagesEnabled
            FynxNotificationType.STORY -> preferences.storiesEnabled
            FynxNotificationType.REMINDER -> preferences.remindersEnabled
            FynxNotificationType.SAFETY -> true
            FynxNotificationType.GROUP -> preferences.groupEnabled
            FynxNotificationType.MARKETPLACE_ORDER -> preferences.marketplaceEnabled
            FynxNotificationType.WALLET_ACTIVITY -> preferences.walletEnabled
        }

    fun shouldPush(preferences: FynxNotificationPreferences, type: FynxNotificationType): Boolean =
        preferences.pushEnabled && isTypeEnabled(preferences, type)
}
