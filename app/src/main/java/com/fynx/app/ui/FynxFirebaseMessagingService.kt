package com.fynx.app.ui

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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
            "FRIEND_REQUEST", "STORY", "COMMENT", "REACTION" -> FynxNotificationFoundation.FRIENDS_CHANNEL
            "GROUP" -> FynxNotificationFoundation.MESSAGES_CHANNEL
            "MARKETPLACE_ORDER", "WALLET_ACTIVITY" -> FynxNotificationFoundation.MONEY_CHANNEL
            "REMINDER" -> FynxNotificationFoundation.REMINDERS_CHANNEL
            else -> FynxNotificationFoundation.FRIENDS_CHANNEL
        }
        FynxNotificationFoundation.createChannels(this)
        val stableHash = notificationId.hashCode()
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(route)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
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
            message = body,
            stableKey = notificationId,
            contentIntent = pendingIntent
        )
    }
}
