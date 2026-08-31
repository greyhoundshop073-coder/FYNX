package com.fynx.app.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CalendarReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val prefs = context.getSharedPreferences("fynx_calendar_events", Context.MODE_PRIVATE)
        val raw = prefs.getString("events", "") ?: return
        raw.lineSequence().forEach { line ->
            val parts = line.split("|", limit = 5)
            if (parts.size == 5) {
                val id = parts[0].toLongOrNull() ?: return@forEach
                val event = FynxCalendarEvent(id, parts[1], parts[2], parts[3], parts[4])
                CalendarReminderScheduler.schedule(context, event)
            }
        }
    }
}
