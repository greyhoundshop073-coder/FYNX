package com.fynx.app.ui

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import com.fynx.app.MainActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FynxFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        FynxNotificationDeviceManager.onTokenChanged(applicationContext, token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        if (data.isEmpty()) return
        val notificationId = data["notificationId"]?.takeIf { it.isNotBlank() } ?: return
        val title = data["title"]?.takeIf { it.isNotBlank() } ?: "FYNX"
        val body = data["body"]?.takeIf { it.isNotBlank() } ?: "You have a new FYNX notification."
        val route = data["route"]?.takeIf { it.isNotBlank() } ?: "fynx://home"
        val type = data["type"]?.uppercase().orEmpty()
        val channel = when (type) {
            "MESSAGE" -> FynxNotificationFoundation.MESSAGES_CHANNEL
            "FRIEND_REQUEST", "FOLLOW", "STORY", "COMMENT", "REACTION" -> FynxNotificationFoundation.FRIENDS_CHANNEL
            "GROUP" -> FynxNotificationFoundation.GROUPS_CHANNEL
            "MARKETPLACE_ORDER", "MARKETPLACE_PRICE", "WALLET_ACTIVITY" -> FynxNotificationFoundation.MONEY_CHANNEL
            "REMINDER" -> FynxNotificationFoundation.REMINDERS_CHANNEL
            else -> FynxNotificationFoundation.FRIENDS_CHANNEL
        }
        val groupId = data["targetId"]?.trim()?.takeIf { it.isNotBlank() }
            ?: runCatching {
                val parsed = Uri.parse(route)
                if (type == "GROUP") parsed.lastPathSegment?.takeIf { it.isNotBlank() } else null
            }.getOrNull()
        if (type == "GROUP" && groupId != null &&
            (!FynxConversationPreferences.groupNotifications(this, groupId) ||
                FynxConversationPreferences.groupMuted(this, groupId))) return
        val chatUsername = if (type == "MESSAGE") {
            data["targetId"]?.trim()?.takeIf { it.isNotBlank() }
                ?: runCatching { Uri.parse(route).pathSegments.lastOrNull()?.takeIf { it.isNotBlank() } }.getOrNull()
        } else null
        val notificationBody = if (type == "MESSAGE" && chatUsername != null &&
            !FynxConversationPreferences.chatMessagePreviews(this, chatUsername)) {
            "You have a new message."
        } else body
        if (type == "MESSAGE" && chatUsername != null &&
            !FynxConversationPreferences.chatNotifications(this, chatUsername)) return

        FynxNotificationFoundation.createChannels(this)
        val stableHash = notificationId.hashCode()
        val safeRoute = runCatching { Uri.parse(route) }.getOrNull() ?: Uri.parse("fynx://home")
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            this.data = safeRoute
            setPackage(packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            stableHash,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        FynxNotificationFoundation.show(
            context = this,
            channelId = channel,
            id = stableHash,
            title = title,
            message = notificationBody,
            stableKey = notificationId,
            contentIntent = pendingIntent
        )
    }
}
