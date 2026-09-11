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
    private const val EXTRA_ACCOUNT = "todo_account"
    private const val REMINDER_FORMAT = "HH:mm 'on' yyyy-MM-dd"

    private fun accountKey(context: Context): String? = FynxAuthStore.accountStorageKey(context)?.trim()?.lowercase()
    private fun requestCode(context: Context, todoId: Long): Int = ("${accountKey(context)}:$todoId").hashCode()

    fun schedule(context: Context, todo: FynxTodo) {
        val account = accountKey(context) ?: return
        val reminder = todo.reminder ?: return
        val triggerAt = parseReminder(reminder) ?: return
        if (triggerAt <= System.currentTimeMillis()) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, TodoReminderReceiver::class.java).apply {
            action = ACTION
            putExtra(EXTRA_ID, todo.id)
            putExtra(EXTRA_TITLE, todo.title)
            putExtra(EXTRA_ACCOUNT, account)
        }
        val pending = PendingIntent.getBroadcast(context, requestCode(context, todo.id), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
    }

    fun cancel(context: Context, todoId: Long) {
        val intent = Intent(context, TodoReminderReceiver::class.java).apply { action = ACTION }
        val pending = PendingIntent.getBroadcast(context, requestCode(context, todoId), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
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
