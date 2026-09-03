package com.fynx.app.ui

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
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
    const val GIFTS_CHANNEL = "fynx_gifts"
    const val MONEY_CHANNEL = "fynx_money"
    const val REMINDERS_CHANNEL = "fynx_reminders"
    private const val PREFS = "fynx_notification_preferences"
    private const val KEY_SPEAK = "speak_notifications"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channels = listOf(
            NotificationChannel(FRIENDS_CHANNEL, "Friends", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(MESSAGES_CHANNEL, "Messages", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(GIFTS_CHANNEL, "Gifts", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(MONEY_CHANNEL, "Money", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(REMINDERS_CHANNEL, "Reminders", NotificationManager.IMPORTANCE_DEFAULT)
        )
        manager.createNotificationChannels(channels)
    }

    fun isSpeakNotificationsEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SPEAK, false)

    fun setSpeakNotificationsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_SPEAK, enabled).apply()
        if (!enabled) stopSpeaking(context)
    }

    private var activeTts: TextToSpeech? = null

    private fun stopSpeaking(context: Context) {
        activeTts?.stop()
        activeTts?.shutdown()
        activeTts = null
    }

    private fun speak(context: Context, title: String, message: String) {
        if (!isSpeakNotificationsEnabled(context)) return
        stopSpeaking(context)
        val text = "$title. $message"
        val utteranceId = UUID.randomUUID().toString()
        activeTts = TextToSpeech(context.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                activeTts?.shutdown()
                activeTts = null
                return@TextToSpeech
            }
            activeTts?.language = Locale.getDefault()
            activeTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onError(utteranceId: String?) { stopSpeaking(context) }
                override fun onDone(utteranceId: String?) { stopSpeaking(context) }
            })
            activeTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }
    }

    fun show(context: Context, channelId: String, id: Int, title: String, message: String) {
        FynxNotificationStore.add(
            context,
            FynxNotification(
                id = "system-$id-${System.currentTimeMillis()}",
                type = typeForChannel(channelId),
                title = title,
                message = message
            )
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
        speak(context, title, message)
    }

    private fun typeForChannel(channelId: String): FynxNotificationType = when (channelId) {
        MESSAGES_CHANNEL -> FynxNotificationType.MESSAGE
        FRIENDS_CHANNEL -> FynxNotificationType.FRIEND_REQUEST
        GIFTS_CHANNEL -> FynxNotificationType.REACTION
        MONEY_CHANNEL -> FynxNotificationType.WALLET_ACTIVITY
        REMINDERS_CHANNEL -> FynxNotificationType.REMINDER
        else -> FynxNotificationType.SAFETY
    }
}
