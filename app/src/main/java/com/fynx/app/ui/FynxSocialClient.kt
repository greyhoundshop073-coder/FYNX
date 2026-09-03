package com.fynx.app.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Real account-scoped friends, search, and blocking API for FYNX. */
object FynxSocialClient {
    data class User(val username: String, val displayName: String, val phone: String, val id: String = "")
    data class FriendRequest(val id: String, val username: String, val displayName: String, val status: String)

    suspend fun searchUsers(context: Context, query: String, phoneSearch: Boolean = false): Result<List<User>> {
        val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
        val mode = if (phoneSearch) "phone" else "username"
        return FynxBackendClient.get(context, "/api/users/search?q=$encoded&mode=$mode").mapCatching { raw ->
            val users = JSONObject(raw).getJSONArray("users")
            buildList {
                for (index in 0 until users.length()) {
                    val item = users.getJSONObject(index)
                    add(User(item.getString("username"), item.optString("display_name"), item.optString("phone"), item.optString("id")))
                }
            }
        }
    }

    suspend fun friends(context: Context): Result<List<User>> =
        FynxBackendClient.get(context, "/api/friends").mapCatching { raw -> parseUsers(JSONObject(raw).getJSONArray("friends")) }

    suspend fun requests(context: Context): Result<List<FriendRequest>> =
        FynxBackendClient.get(context, "/api/friends/requests").mapCatching { raw ->
            val items = JSONObject(raw).getJSONArray("requests")
            buildList {
                for (index in 0 until items.length()) {
                    val item = items.getJSONObject(index)
                    add(FriendRequest(item.getString("id"), item.getString("username"), item.optString("display_name"), item.getString("status")))
                }
            }
        }

    suspend fun sendRequest(context: Context, username: String): Result<Unit> =
        FynxBackendClient.postJson(context, "/api/friends/request", JSONObject().put("username", username).toString()).map { }

    suspend fun acceptRequest(context: Context, id: String): Result<Unit> =
        FynxBackendClient.postJson(context, "/api/friends/requests/$id/accept", "{}").map { }

    suspend fun rejectRequest(context: Context, id: String): Result<Unit> =
        FynxBackendClient.postJson(context, "/api/friends/requests/$id/reject", "{}").map { }

    suspend fun cancelRequest(context: Context, id: String): Result<Unit> =
        FynxBackendClient.delete(context, "/api/friends/requests/$id").map { }

    suspend fun removeFriend(context: Context, username: String): Result<Unit> =
        FynxBackendClient.delete(context, "/api/friends/${encode(username)}").map { }

    suspend fun block(context: Context, username: String): Result<Unit> =
        FynxBackendClient.postJson(context, "/api/blocks/${encode(username)}", "{}").map { }

    suspend fun unblock(context: Context, username: String): Result<Unit> =
        FynxBackendClient.delete(context, "/api/blocks/${encode(username)}").map { }

    suspend fun blocked(context: Context): Result<List<User>> =
        FynxBackendClient.get(context, "/api/blocks").mapCatching { raw -> parseUsers(JSONObject(raw).getJSONArray("blocks")) }

    private fun parseUsers(items: JSONArray): List<User> = buildList {
        for (index in 0 until items.length()) {
            val item = items.getJSONObject(index)
            add(User(item.getString("username"), item.optString("display_name"), item.optString("phone"), item.optString("id")))
        }
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value.trim(), "UTF-8").replace("+", "%20")
}
