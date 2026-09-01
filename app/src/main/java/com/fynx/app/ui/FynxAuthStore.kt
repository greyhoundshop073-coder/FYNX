package com.fynx.app.ui

import android.content.Context

object FynxAuthStore {
    private const val PREFS = "fynx_auth"
    private const val KEY_SIGNED_IN = "signed_in"
    private const val KEY_USERNAME = "username"
    private const val KEY_DISPLAY_NAME = "display_name"
    private const val KEY_PHONE = "phone"
    private const val KEY_ACCOUNT_CREATED = "account_created"

    fun load(context: Context): AuthSession {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val signedIn = prefs.getBoolean(KEY_SIGNED_IN, false)
        val username = prefs.getString(KEY_USERNAME, null)
        return if (signedIn && !username.isNullOrBlank()) AuthSession(AuthState.SIGNED_IN, username) else AuthSession()
    }

    fun hasAccount(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ACCOUNT_CREATED, false)

    fun storedUsername(context: Context): String? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_USERNAME, null)

    fun saveAccount(context: Context, displayName: String, username: String, phone: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ACCOUNT_CREATED, true)
            .putString(KEY_DISPLAY_NAME, displayName)
            .putString(KEY_USERNAME, username)
            .putString(KEY_PHONE, phone)
            .putBoolean(KEY_SIGNED_IN, true)
            .apply()
    }

    fun save(context: Context, username: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_SIGNED_IN, true)
            .putString(KEY_USERNAME, username)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_SIGNED_IN, false)
            .apply()
    }
}
