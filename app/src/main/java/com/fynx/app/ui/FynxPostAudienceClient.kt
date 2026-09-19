package com.fynx.app.ui

import android.content.Context
import org.json.JSONObject

data class FynxFriend(
    val id: String,
    val username: String,
    val displayName: String
)

object FynxPostAudienceClient {
    suspend fun friends(context: Context): Result<List<FynxFriend>> = runCatching {
        val raw = FynxBackendClient.get(context, "/api/friends").getOrThrow()
        val array = JSONObject(raw).optJSONArray("friends")
        buildList {
            if (array != null) {
                for (index in 0 until array.length()) {
                    val row = array.optJSONObject(index) ?: continue
                    val id = row.optString("id").trim()
                    val username = row.optString("username").trim()
                    if (id.isBlank() || username.isBlank()) continue
                    add(FynxFriend(id, username, row.optString("display_name").trim().ifBlank { username }))
                }
            }
        }
    }
}
