package com.fynx.app.ui

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object FynxNotificationDeviceManager {
    private const val PREFS = "fynx_fcm_device"
    private const val KEY_TOKEN = "token"
    private const val PROVIDER = "fcm"

    private suspend fun currentToken(): String = suspendCancellableCoroutine { continuation ->
        val task = FirebaseMessaging.getInstance().token
        task.addOnCompleteListener { completed ->
            if (!completed.isSuccessful) {
                continuation.resumeWithException(completed.exception ?: IllegalStateException("FCM token request failed"))
            } else {
                continuation.resume(completed.result?.trim().orEmpty())
            }
        }
    }

    suspend fun registerCurrentToken(context: Context): Result<Unit> {
        if (!FynxBackendClient.hasAccessToken(context) || FynxAuthStore.load(context).state != AuthState.SIGNED_IN) return Result.success(Unit)
        return runCatching {
            val token = currentToken()
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

    /**
     * Best-effort, non-blocking logout cleanup. It intentionally starts before
     * the auth store is cleared so the authenticated DELETE can still use the
     * current session token. The local FCM token is removed immediately so a
     * subsequent account cannot inherit it from local storage.
     */
    fun unregisterCurrentAccount(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_TOKEN).apply()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                if (FynxBackendClient.hasAccessToken(context)) {
                    FynxBackendClient.delete(context, "/api/notification-devices").getOrThrow()
                }
            }
        }
    }

    fun onTokenChanged(context: Context, token: String) {
        if (token.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_TOKEN, token).apply()
        if (!FynxBackendClient.hasAccessToken(context) || FynxAuthStore.load(context).state != AuthState.SIGNED_IN) return
        CoroutineScope(Dispatchers.IO).launch {
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
