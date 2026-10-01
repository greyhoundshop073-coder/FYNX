package com.fynx.app.ui

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fynx.app.MainActivity

class TodoReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val scheduledAccount = intent.getStringExtra("todo_account")?.trim()?.lowercase() ?: return
        val currentAccount = FynxAuthStore.accountStorageKey(context)?.trim()?.lowercase() ?: return
        // AlarmManager can retain a reminder across logout/account switching.
        // Never expose the previous account's task to the current account.
        if (scheduledAccount != currentAccount) return

        val title = intent.getStringExtra("todo_title") ?: "FYNX task reminder"
        val todoId = intent.getLongExtra("todo_id", 0L)
        val notificationId = ("$currentAccount:$todoId").hashCode()
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
            stableKey = "todo-reminder:$currentAccount:$todoId:$title",
            contentIntent = pending
        )
    }
}
