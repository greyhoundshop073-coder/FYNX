package com.fynx.app.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Reads the real ordered media set attached to a Home post. */
object FynxHomePostMediaClient {
    data class PostMediaItem(
        val id: String,
        val mediaType: String,
        val position: Int,
        val mediaUrl: String
    )

    suspend fun list(context: Context, postId: String): Result<List<PostMediaItem>> = runCatching {
        val numericId = postId.toLongOrNull()
            ?: throw IllegalArgumentException("Invalid FYNX post id.")
        val raw = FynxBackendClient.get(context, "/api/social/posts/$numericId/media").getOrThrow()
        val array = JSONObject(raw).optJSONArray("media") ?: JSONArray()
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val id = item.optString("id").takeIf { it.isNotBlank() }
                    ?: continue
                val type = item.optString("mediaType").lowercase()
                if (type != "image" && type != "video" && type != "audio") continue
                val url = item.optString("mediaUrl").takeIf { it.isNotBlank() }
                    ?: continue
                add(
                    PostMediaItem(
                        id = id,
                        mediaType = type,
                        position = item.optInt("position", index),
                        mediaUrl = url
                    )
                )
            }
        }.sortedBy { it.position }.take(4)
    }
}
