package com.fynx.app.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.fynx.app.MainActivity

class CalendarReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("calendar_title") ?: "Calendar event"
        val eventId = intent.getLongExtra("calendar_id", System.currentTimeMillis())
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "fynx_calendar_reminders"
        manager.createNotificationChannel(
            NotificationChannel(channelId, "Calendar reminders", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val openIntent = Intent(context, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
        val pending = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("FYNX calendar reminder")
            .setContentText(title)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify(eventId.hashCode(), notification)
    }
}
