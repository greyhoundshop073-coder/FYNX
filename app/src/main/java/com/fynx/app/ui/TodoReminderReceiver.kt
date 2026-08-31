package com.fynx.app.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.fynx.app.MainActivity

class TodoReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("todo_title") ?: "FYNX task reminder"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "fynx_todo_reminders"
        manager.createNotificationChannel(NotificationChannel(channelId, "To-Do reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val openIntent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = android.app.PendingIntent.getActivity(context, 0, openIntent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(com.fynx.app.R.drawable.ic_fynx_logo)
            .setContentTitle("FYNX reminder")
            .setContentText(title)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify(title.hashCode(), notification)
    }
}
