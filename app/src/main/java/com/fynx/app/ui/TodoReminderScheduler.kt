package com.fynx.app.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

object TodoReminderScheduler {
    private const val ACTION = "com.fynx.app.TODO_REMINDER"
    private const val EXTRA_ID = "todo_id"
    private const val EXTRA_TITLE = "todo_title"
    private const val REMINDER_FORMAT = "HH:mm 'on' yyyy-MM-dd"

    fun schedule(context: Context, todo: FynxTodo) {
        val reminder = todo.reminder ?: return
        val triggerAt = parseReminder(reminder) ?: return
        if (triggerAt <= System.currentTimeMillis()) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, TodoReminderReceiver::class.java).apply {
            action = ACTION
            putExtra(EXTRA_ID, todo.id)
            putExtra(EXTRA_TITLE, todo.title)
        }
        val pending = PendingIntent.getBroadcast(context, todo.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
    }

    fun cancel(context: Context, todoId: Long) {
        val intent = Intent(context, TodoReminderReceiver::class.java).apply { action = ACTION }
        val pending = PendingIntent.getBroadcast(context, todoId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
    }

    fun isValidReminder(value: String): Boolean = parseReminder(value) != null

    private fun parseReminder(value: String): Long? {
        val formatter = SimpleDateFormat(REMINDER_FORMAT, Locale.US).apply { isLenient = false }
        val position = ParsePosition(0)
        val parsed = formatter.parse(value, position)
        return if (parsed != null && position.index == value.length) parsed.time else null
    }
}
