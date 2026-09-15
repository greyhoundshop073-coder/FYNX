package com.fynx.app.ui

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

object FynxNotificationDeviceManager {
    private const val PREFS = "fynx_fcm_device"
    private const val KEY_TOKEN = "token"
    private const val PROVIDER = "fcm"

    suspend fun registerCurrentToken(context: Context): Result<Unit> {
        if (!FynxBackendClient.hasAccessToken(context) || FynxAuthStore.load(context).state != AuthState.SIGNED_IN) return Result.success(Unit)
        return runCatching {
            val token = FirebaseMessaging.getInstance().token.await().trim()
            require(token.isNotBlank()) { "FCM registration token is empty" }
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val previous = prefs.getString(KEY_TOKEN, null)?.trim()
            if (previous != null && previous != token) {
                FynxBackendClient.delete(context, "/api/notification-devices").getOrThrow()
            }
            FynxBackendClient.postJson(
                context,
                "/api/notification-devices",
                JSONObject().put("provider", PROVIDER).put("token", token).toString()
            ).getOrThrow()
            prefs.edit().putString(KEY_TOKEN, token).apply()
        }
    }

    suspend fun unregisterCurrentAccount(context: Context): Result<Unit> {
        return runCatching {
            FynxBackendClient.delete(context, "/api/notification-devices").getOrThrow()
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_TOKEN).apply()
        }
    }

    fun onTokenChanged(context: Context, token: String) {
        if (token.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_TOKEN, token).apply()
        if (!FynxBackendClient.hasAccessToken(context) || FynxAuthStore.load(context).state != AuthState.SIGNED_IN) return
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching {
                FynxBackendClient.postJson(
                    context,
                    "/api/notification-devices",
                    JSONObject().put("provider", PROVIDER).put("token", token).toString()
                ).getOrThrow()
            }
        }
    }
}
