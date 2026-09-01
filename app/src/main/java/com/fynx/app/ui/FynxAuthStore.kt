package com.fynx.app.ui

import android.content.Context

object FynxAuthStore {
    private const val PREFS = "fynx_auth"
    private const val KEY_SIGNED_IN = "signed_in"
    private const val KEY_USERNAME = "username"

    fun load(context: Context): AuthSession {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val signedIn = prefs.getBoolean(KEY_SIGNED_IN, false)
        val username = prefs.getString(KEY_USERNAME, null)
        return if (signedIn && !username.isNullOrBlank()) {
            AuthSession(AuthState.SIGNED_IN, username)
        } else {
            AuthSession()
        }
    }

    fun save(context: Context, username: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SIGNED_IN, true)
            .putString(KEY_USERNAME, username)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
