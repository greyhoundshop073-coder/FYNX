package com.fynx.app.ui

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fynx.app.MainActivity

class CalendarReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val scheduledAccount = intent.getStringExtra("calendar_account")?.trim()?.lowercase() ?: return
        val currentAccount = FynxAuthStore.accountStorageKey(context)?.trim()?.lowercase() ?: return
        // A reminder can survive logout/account switching in AlarmManager. Never
        // surface the old account's event to the newly authenticated account.
        if (scheduledAccount != currentAccount) return

        val title = intent.getStringExtra("calendar_title") ?: "Calendar event"
        val id = intent.getLongExtra("calendar_id", System.currentTimeMillis())
        val date = intent.getStringExtra("calendar_date") ?: return
        val time = intent.getStringExtra("calendar_time") ?: return
        val repeat = intent.getStringExtra("calendar_repeat") ?: "None"
        val notificationId = ("$currentAccount:$id").hashCode()
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
            title = "FYNX calendar reminder",
            message = title,
            stableKey = "calendar-reminder:$currentAccount:$id:$date:$time",
            contentIntent = pending
        )
        if (repeat != "None") {
            CalendarReminderScheduler.schedule(
                context,
                FynxCalendarEvent(id, title, date, time, "", repeat)
            )
        }
    }
}
