package com.fynx.app.ui

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Locale
import java.util.UUID

object FynxNotificationFoundation {
    const val FRIENDS_CHANNEL = "fynx_friends"
    const val MESSAGES_CHANNEL = "fynx_messages"
    const val CALLS_CHANNEL = "fynx_calls"
    const val GIFTS_CHANNEL = "fynx_gifts"
    const val MONEY_CHANNEL = "fynx_money"
    const val REMINDERS_CHANNEL = "fynx_reminders"
    private const val PREFS = "fynx_notification_preferences"
    private const val KEY_SPEAK = "speak_notifications"
    private const val KEY_DEDUPE = "recent_notification_ids"

    private fun accountKey(context: Context): String =
        FynxAuthStore.accountStorageKey(context)?.let { value ->
            value.map { character -> if (character.isLetterOrDigit()) character else '_' }
                .joinToString("").take(80).ifBlank { "account" }
        } ?: "signed_out"

    private fun key(base: String, context: Context): String = "${base}_${accountKey(context)}"

    private fun shouldShow(context: Context, stableKey: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val dedupeKey = key(KEY_DEDUPE, context)
        val now = System.currentTimeMillis()
        val values = prefs.getStringSet(dedupeKey, emptySet()).orEmpty()
        val fresh = values.mapNotNull { entry ->
            val parts = entry.split("|", limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val timestamp = parts[1].toLongOrNull() ?: return@mapNotNull null
            if (now - timestamp < 30_000L) entry else null
        }.toMutableSet()
        if (fresh.any { it.startsWith("$stableKey|") }) return false
        fresh.add("$stableKey|$now")
        prefs.edit().putStringSet(dedupeKey, fresh).apply()
        return true
    }

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channels = listOf(
            NotificationChannel(FRIENDS_CHANNEL, "Friends", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(MESSAGES_CHANNEL, "Messages", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CALLS_CHANNEL, "Calls", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Incoming FYNX voice and video calls"
                enableVibration(true)
                setShowBadge(true)
            },
            NotificationChannel(GIFTS_CHANNEL, "Gifts", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(MONEY_CHANNEL, "Money", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(REMINDERS_CHANNEL, "Reminders", NotificationManager.IMPORTANCE_DEFAULT)
        )
        manager.createNotificationChannels(channels)
    }

    fun isSpeakNotificationsEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(key(KEY_SPEAK, context), false)

    fun setSpeakNotificationsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(key(KEY_SPEAK, context), enabled).apply()
        if (!enabled) stopSpeaking()
    }

    private var activeTts: TextToSpeech? = null

    private fun stopSpeaking() {
        activeTts?.stop()
        activeTts?.shutdown()
        activeTts = null
    }

    private fun speak(context: Context, title: String, message: String) {
        if (!isSpeakNotificationsEnabled(context)) return
        stopSpeaking()
        val text = "$title. $message"
        val utteranceId = UUID.randomUUID().toString()
        activeTts = TextToSpeech(context.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                stopSpeaking()
                return@TextToSpeech
            }
            activeTts?.language = Locale.getDefault()
            activeTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onError(utteranceId: String?) { stopSpeaking() }
                override fun onDone(utteranceId: String?) { stopSpeaking() }
            })
            activeTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }
    }

    fun show(
        context: Context,
        channelId: String,
        id: Int,
        title: String,
        message: String,
        stableKey: String = "$channelId:$id:$title:$message",
        contentIntent: PendingIntent? = null
    ) {
        createChannels(context)
        val type = typeForChannel(channelId)
        val preferences = FynxNotificationPreferencesClient.cached(context)
        if (!FynxNotificationControlsBatch3.shouldPush(preferences, type)) return
        if (!shouldShow(context, stableKey)) return

        FynxNotificationStore.add(
            context,
            FynxNotification(
                id = "system-$id-${System.currentTimeMillis()}",
                type = type,
                title = title,
                message = message
            )
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(if (channelId == CALLS_CHANNEL) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(channelId != CALLS_CHANNEL)
            .setCategory(if (channelId == CALLS_CHANNEL) NotificationCompat.CATEGORY_CALL else NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(if (channelId == CALLS_CHANNEL) NotificationCompat.DEFAULT_ALL else 0)
        if (contentIntent != null) builder.setContentIntent(contentIntent)
        if (channelId == CALLS_CHANNEL) builder.setTimeoutAfter(60_000L)
        NotificationManagerCompat.from(context).notify(id, builder.build())
        speak(context, title, message)
    }

    fun cancel(context: Context, id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }

    private fun typeForChannel(channelId: String): FynxNotificationType = when (channelId) {
        MESSAGES_CHANNEL, CALLS_CHANNEL -> FynxNotificationType.MESSAGE
        FRIENDS_CHANNEL -> FynxNotificationType.FRIEND_REQUEST
        GIFTS_CHANNEL -> FynxNotificationType.REACTION
        MONEY_CHANNEL -> FynxNotificationType.WALLET_ACTIVITY
        REMINDERS_CHANNEL -> FynxNotificationType.REMINDER
        else -> FynxNotificationType.SAFETY
    }
}
