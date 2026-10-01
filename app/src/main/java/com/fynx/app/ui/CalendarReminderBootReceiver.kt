package com.fynx.app.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CalendarReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (FynxAuthStore.accountStorageKey(context).isNullOrBlank()) return
        val key = "events_${FynxAuthStore.accountStorageKey(context)?.trim()?.lowercase()?.map { c -> if (c.isLetterOrDigit()) c else '_' }?.joinToString("")?.take(80)?.ifBlank { "account" } ?: "signed_out"}"
        val raw = context.getSharedPreferences("fynx_calendar_events", Context.MODE_PRIVATE).getString(key, "") ?: ""
        raw.lineSequence().forEach { line ->
            val p = line.split("|", limit = 6)
            if (p.size >= 5) {
                val id = p[0].toLongOrNull() ?: return@forEach
                val repeat = if (p.size >= 6) p[5] else "None"
                CalendarReminderScheduler.schedule(context, FynxCalendarEvent(id, p[1], p[2], p[3], p[4], repeat))
            }
        }
    }
}
