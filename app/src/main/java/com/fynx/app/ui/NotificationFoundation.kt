package com.fynx.app.ui

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.content.Context
import android.content.Intent
import androidx.core.app.Person
import com.fynx.app.MainActivity
import android.content.pm.PackageManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Locale
import java.util.UUID

object FynxNotificationFoundation {
    const val FRIENDS_CHANNEL = "fynx_friends_v2"
    const val MESSAGES_CHANNEL = "fynx_messages_v2"
    const val CALLS_CHANNEL = "fynx_calls_v2"
    const val GIFTS_CHANNEL = "fynx_gifts_v2"
    const val MONEY_CHANNEL = "fynx_money_v2"
    const val REMINDERS_CHANNEL = "fynx_reminders_v2"
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
        val notificationSound = android.provider.Settings.System.DEFAULT_NOTIFICATION_URI
        val notificationAudio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val ringtoneSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val ringtoneAudio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val channels = listOf(
            NotificationChannel(FRIENDS_CHANNEL, "Friends", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(notificationSound, notificationAudio)
            },
            NotificationChannel(MESSAGES_CHANNEL, "Messages", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(notificationSound, notificationAudio)
            },
            NotificationChannel(CALLS_CHANNEL, "Calls", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Incoming FYNX voice and video calls"
                enableVibration(true)
                setShowBadge(true)
                setSound(ringtoneSound, ringtoneAudio)
            },
            NotificationChannel(GIFTS_CHANNEL, "Gifts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(notificationSound, notificationAudio)
            },
            NotificationChannel(MONEY_CHANNEL, "Money", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(notificationSound, notificationAudio)
            },
            NotificationChannel(REMINDERS_CHANNEL, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(notificationSound, notificationAudio)
            }
        )
        manager.createNotificationChannels(channels)
    }

    fun isSpeakNotificationsEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(key(KEY_SPEAK, context), false)

    fun setSpeakNotificationsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(key(KEY_SPEAK, context), enabled).apply()
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
        contentIntent: PendingIntent? = null,
        incomingCall: FynxIncomingCall? = null
    ) {
        createChannels(context)
        val isCall = channelId == CALLS_CHANNEL || title.startsWith("Incoming Voice call") || title.startsWith("Incoming Video call")
        val effectiveChannelId = if (isCall) CALLS_CHANNEL else channelId
        val type = typeForChannel(effectiveChannelId)
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

        val builder = NotificationCompat.Builder(context, effectiveChannelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(if (isCall) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(!isCall)
            .setCategory(if (isCall) NotificationCompat.CATEGORY_CALL else NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(if (isCall) NotificationCompat.DEFAULT_ALL else NotificationCompat.DEFAULT_VIBRATE)

        if (incomingCall != null && isCall) {
            val caller = Person.Builder()
                .setName("@${incomingCall.fromUsername.removePrefix("@").ifBlank { incomingCall.fromUserId }}")
                .setImportant(true)
                .build()
            val viewIntent = callIntent(context, incomingCall, "VIEW")
            val answerIntent = callIntent(context, incomingCall, "ANSWER")
            val declineIntent = callIntent(context, incomingCall, "DECLINE")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setStyle(NotificationCompat.CallStyle.forIncomingCall(caller, declineIntent, answerIntent))
                    .addPerson(caller)
                    .setOngoing(true)
                    .setFullScreenIntent(viewIntent, true)
            } else {
                builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declineIntent)
                    .addAction(android.R.drawable.ic_menu_call, "Answer", answerIntent)
                    .setFullScreenIntent(viewIntent, true)
            }
            builder.setContentIntent(viewIntent)
            builder.setTimeoutAfter(60_000L)
        } else {
            if (contentIntent != null) builder.setContentIntent(contentIntent)
            if (isCall) builder.setTimeoutAfter(60_000L)
        }

        NotificationManagerCompat.from(context).notify(id, builder.build())
        speak(context, title, message)
    }

    private fun callIntent(context: Context, call: FynxIncomingCall, action: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = "com.fynx.app.action.INCOMING_CALL"
            putExtra("fynx_call_action", action)
            putExtra("fynx_call_id", call.callId)
            putExtra("fynx_call_from_user_id", call.fromUserId)
            putExtra("fynx_call_from_username", call.fromUsername)
            putExtra("fynx_call_video", call.video)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val requestCode = (call.callId.hashCode() * 31 + action.hashCode()).and(0x7fffffff)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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
