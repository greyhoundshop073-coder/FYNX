package com.fynx.app.ui

import android.content.Context
import org.json.JSONObject

object FynxHomePostReactionsClient {
    data class ReactionUser(val id: String, val username: String, val displayName: String, val reaction: String)

    data class ReactionState(
        val counts: Map<String, Int> = emptyMap(),
        val currentReaction: String? = null
    ) {
        val total: Int get() = counts.values.sum()
    }

    private fun parse(raw: String): ReactionState {
        val root = JSONObject(raw)
        val rawCounts = root.optJSONObject("reactions")
        val counts = buildMap {
            if (rawCounts != null) {
                val keys = rawCounts.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    put(key, rawCounts.optInt(key).coerceAtLeast(0))
                }
            }
        }
        return ReactionState(counts, root.optString("currentReaction").takeIf { it.isNotBlank() && it != "null" })
    }

    suspend fun state(context: Context, postId: String): Result<ReactionState> {
        val id = postId.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid post id"))
        return FynxBackendClient.get(context, "/api/social/posts/$id/reactions").mapCatching(::parse)
    }

    suspend fun set(context: Context, postId: String, reaction: String): Result<ReactionState> {
        val id = postId.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid post id"))
        val safe = reaction.trim().uppercase().takeIf { it in setOf("LIKE", "LOVE", "LAUGH", "WOW", "SAD") }
            ?: return Result.failure(IllegalArgumentException("invalid reaction"))
        return FynxBackendClient.postJson(context, "/api/social/posts/$id/reaction", JSONObject().put("reaction", safe).toString()).mapCatching(::parse)
    }

    suspend fun users(context: Context, postId: String, reaction: String? = null): Result<List<ReactionUser>> {
        val id = postId.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid post id"))
        val suffix = reaction?.trim()?.uppercase()?.takeIf { it in setOf("LIKE","LOVE","LAUGH","WOW","SAD") }?.let { "?type=$it" } ?: ""
        return FynxBackendClient.get(context, "/api/social/posts/$id/reaction-users$suffix").mapCatching { raw ->
            val array = JSONObject(raw).optJSONArray("users") ?: return@mapCatching emptyList()
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(ReactionUser(item.optString("id"), item.optString("username"), item.optString("displayName"), item.optString("reaction")))
                }
            }
        }
    }

    suspend fun clear(context: Context, postId: String): Result<ReactionState> {
        val id = postId.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid post id"))
        return FynxBackendClient.delete(context, "/api/social/posts/$id/reaction").mapCatching(::parse)
    }
}
