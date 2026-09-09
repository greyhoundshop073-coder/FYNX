package com.fynx.app.ui

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fynx.app.MainActivity

class TodoReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("todo_title") ?: "FYNX task reminder"
        val notificationId = title.hashCode()
        val openIntent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        FynxNotificationFoundation.show(
            context = context,
            channelId = FynxNotificationFoundation.REMINDERS_CHANNEL,
            id = notificationId,
            title = "FYNX reminder",
            message = title,
            stableKey = "todo-reminder:$notificationId:$title",
            contentIntent = pending
        )
    }
}
