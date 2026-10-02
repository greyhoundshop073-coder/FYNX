package com.fynx.app.ui

enum class FynxNotificationType { MESSAGE, CALL, FRIEND_REQUEST, FOLLOW, STORY, REMINDER, SAFETY, GROUP, REACTION, COMMENT, MARKETPLACE_ORDER, WALLET_ACTIVITY }

data class FynxNotification(
    val id: String,
    val type: FynxNotificationType,
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)
