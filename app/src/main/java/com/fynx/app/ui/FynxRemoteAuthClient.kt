package com.fynx.app.ui

import android.content.Context
import org.json.JSONObject

/** Connects the existing FYNX auth surface to the real server identity system. */
object FynxRemoteAuthClient {
    data class ResultData(val username: String, val displayName: String, val phone: String, val token: String)

    suspend fun register(context: Context, displayName: String, username: String, phone: String, password: String): Result<ResultData> =
        call(context, "/api/auth/register", JSONObject().apply {
            put("displayName", displayName)
            put("username", username)
            put("phone", phone)
            put("password", password)
        })

    suspend fun login(context: Context, username: String, password: String): Result<ResultData> =
        call(context, "/api/auth/login", JSONObject().apply {
            put("username", username)
            put("password", password)
        })

    private suspend fun call(context: Context, path: String, body: JSONObject): Result<ResultData> =
        FynxBackendClient.postJson(context, path, body.toString()).mapCatching { raw ->
            val json = JSONObject(raw)
            val user = json.getJSONObject("user")
            ResultData(
                username = user.getString("username"),
                displayName = user.optString("display_name"),
                phone = user.optString("phone"),
                token = json.getString("accessToken")
            )
        }
}
