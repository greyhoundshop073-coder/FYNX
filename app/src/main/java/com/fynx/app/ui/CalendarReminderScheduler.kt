package com.fynx.app.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Locale

object CalendarReminderScheduler {
    private const val ACTION = "com.fynx.app.CALENDAR_REMINDER"
    private const val EXTRA_ID = "calendar_id"
    private const val EXTRA_TITLE = "calendar_title"

    fun schedule(context: Context, event: FynxCalendarEvent) {
        val triggerAt = parseEventTime(event) ?: return
        if (triggerAt <= System.currentTimeMillis()) return
        val intent = Intent(context, CalendarReminderReceiver::class.java).apply {
            action = ACTION
            putExtra(EXTRA_ID, event.id)
            putExtra(EXTRA_TITLE, event.title)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            event.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
    }

    fun cancel(context: Context, eventId: Long) {
        val intent = Intent(context, CalendarReminderReceiver::class.java).apply { action = ACTION }
        val pending = PendingIntent.getBroadcast(
            context,
            eventId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
    }

    private fun parseEventTime(event: FynxCalendarEvent): Long? = try {
        if (event.time.isBlank()) return null
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse("${event.date} ${event.time}")?.time
    } catch (_: Exception) { null }
}
