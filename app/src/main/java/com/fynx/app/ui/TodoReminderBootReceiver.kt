package com.fynx.app.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TodoReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        TodoStore.load(context).forEach { todo ->
            if (!todo.completed && todo.reminder != null) TodoReminderScheduler.schedule(context, todo)
        }
    }
}
