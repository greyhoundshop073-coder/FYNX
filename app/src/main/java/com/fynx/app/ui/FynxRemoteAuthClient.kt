package com.fynx.app.ui

import android.content.Context
import org.json.JSONObject

/** Connects the existing FYNX auth surface to the real server identity system. */
object FynxRemoteAuthClient {
    data class ResultData(
        val username: String,
        val displayName: String,
        val phone: String,
        val token: String,
        val email: String = "",
        val emailVerificationSent: Boolean = false
    )

    suspend fun register(
        context: Context,
        displayName: String,
        username: String,
        phone: String,
        password: String,
        email: String = ""
    ): Result<ResultData> =
        call(context, "/api/auth/register", JSONObject().apply {
            put("displayName", displayName)
            put("username", username)
            put("phone", phone)
            put("password", password)
            if (email.isNotBlank()) put("email", email.trim())
        })

    suspend fun login(context: Context, username: String, password: String): Result<ResultData> =
        call(context, "/api/auth/login", JSONObject().apply {
            put("username", username)
            put("password", password)
        })

    suspend fun requestEmailVerification(context: Context, email: String): Result<Boolean> =
        FynxBackendClient.postJson(
            context,
            "/api/auth/email/verification/resend",
            JSONObject().put("email", email.trim()).toString()
        ).mapCatching { raw -> JSONObject(raw).optBoolean("deliveryConfigured", false) }

    suspend fun verifyEmail(context: Context, email: String, code: String): Result<Unit> =
        FynxBackendClient.postJson(
            context,
            "/api/auth/email/verify",
            JSONObject().put("email", email.trim()).put("code", code.trim()).toString()
        ).mapCatching { raw ->
            require(JSONObject(raw).optBoolean("emailVerified")) { "FYNX email verification was not confirmed." }
            Unit
        }

    suspend fun requestPasswordReset(context: Context, email: String): Result<Boolean> =
        FynxBackendClient.postJson(
            context,
            "/api/auth/password-reset/request",
            JSONObject().put("email", email.trim()).toString()
        ).mapCatching { raw -> JSONObject(raw).optBoolean("deliveryConfigured", false) }

    suspend fun confirmPasswordReset(
        context: Context,
        email: String,
        code: String,
        newPassword: String
    ): Result<Unit> =
        FynxBackendClient.postJson(
            context,
            "/api/auth/password-reset/confirm",
            JSONObject().put("email", email.trim())
                .put("code", code.trim())
                .put("newPassword", newPassword).toString()
        ).mapCatching { Unit }

    /**
     * Connects the account already saved on this device without signing the user
     * out. A legacy local-only account is migrated only in a debuggable build,
     * only when it exactly matches the saved username, and only after the user
     * enters a password and requests the connection.
     */
    suspend fun connectSavedAccount(context: Context, password: String): Result<ResultData> {
        val username = FynxAuthStore.storedUsername(context)?.trim().orEmpty()
        if (username.isBlank()) return Result.failure(IllegalStateException("No saved FYNX account is available."))
        if (password.isBlank()) return Result.failure(IllegalArgumentException("Enter your account password to connect."))
        val loginResult = login(context, username, password)
        if (loginResult.isSuccess) return loginResult

        val isDebuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val isExactSavedLocalAccount = isDebuggable &&
            FynxAuthStore.hasAccount(context) &&
            FynxAuthStore.storedUsername(context).equals(username, ignoreCase = true)
        val failure = loginResult.exceptionOrNull()
        if (!isExactSavedLocalAccount || failure == null || !FynxBackendClient.isUnauthorizedFailure(failure)) {
            return loginResult
        }

        return register(
            context,
            FynxAuthStore.storedDisplayName(context) ?: username,
            username,
            FynxAuthStore.storedPhone(context).orEmpty(),
            password
        )
    }

    private suspend fun call(context: Context, path: String, body: JSONObject): Result<ResultData> =
        FynxBackendClient.postJson(context, path, body.toString()).mapCatching { raw ->
            val json = JSONObject(raw)
            val user = json.getJSONObject("user")
            val token = json.getString("accessToken").trim()
            require(token.isNotBlank()) { "FYNX authentication response did not include an access token." }
            ResultData(
                username = user.getString("username"),
                displayName = user.optString("display_name"),
                phone = user.optString("phone"),
                token = token,
                email = user.optString("email"),
                emailVerificationSent = json.optBoolean("emailVerificationSent", false)
            )
        }
}
