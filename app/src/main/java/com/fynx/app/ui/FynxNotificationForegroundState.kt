package com.fynx.app.ui

/** Prevent duplicate FCM alerts when the matching conversation is open in the foreground. */
object FynxNotificationForegroundState {
    @Volatile private var foreground = false
    @Volatile private var activePrivateChat = ""
    @Volatile private var activeGroup = ""

    fun setForeground(isForeground: Boolean) { foreground = isForeground }

    fun setActivePrivateChat(username: String) {
        activePrivateChat = normalize(username)
        activeGroup = ""
    }

    fun clearActivePrivateChat(username: String) {
        if (activePrivateChat == normalize(username)) activePrivateChat = ""
    }

    fun setActiveGroup(groupId: String) {
        activeGroup = groupId.trim()
        activePrivateChat = ""
    }

    fun clearActiveGroup(groupId: String) {
        if (activeGroup == groupId.trim()) activeGroup = ""
    }

    fun shouldSuppressPrivateNotification(username: String): Boolean =
        foreground && activePrivateChat.isNotBlank() && activePrivateChat == normalize(username)

    fun shouldSuppressGroupNotification(groupId: String): Boolean =
        foreground && activeGroup.isNotBlank() && activeGroup == groupId.trim()

    private fun normalize(username: String): String = username.trim().removePrefix("@").lowercase()
}
