package com.fynx.app.ui

import android.content.Context
import org.json.JSONObject

object FynxHomePostReactionsClient {
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
        return FynxBackendClient.putJson(context, "/api/social/posts/$id/reaction", JSONObject().put("reaction", safe).toString()).mapCatching(::parse)
    }

    suspend fun clear(context: Context, postId: String): Result<ReactionState> {
        val id = postId.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid post id"))
        return FynxBackendClient.delete(context, "/api/social/posts/$id/reaction").mapCatching(::parse)
    }
}
