package com.fynx.app.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Local-first social feed contract. Backend synchronization will replace this store later. */
enum class FynxPostVisibility { PUBLIC, FRIENDS_ONLY }

data class FynxPost(
    val id: String,
    val authorUsername: String,
    val text: String,
    val timestamp: Long,
    val visibility: FynxPostVisibility = FynxPostVisibility.PUBLIC,
    val likedByCurrentUser: Boolean = false,
    val savedByCurrentUser: Boolean = false
)

object FynxHomePostStore {
    private const val PREFS = "fynx_home_posts"
    private const val MAX_POSTS = 200

    private fun accountKey(context: Context): String =
        (FynxAuthStore.storedUsername(context) ?: "@preview")
            .trim().lowercase().removePrefix("@").ifBlank { "preview" }

    private fun key(context: Context) = "posts_${accountKey(context)}"

    fun load(context: Context): List<FynxPost> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(context), null) ?: return emptyList()
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val text = item.optString("text").trim()
                val author = item.optString("authorUsername")
                if (id.isNotBlank() && text.isNotBlank() && author.isNotBlank()) {
                    add(FynxPost(
                        id = id,
                        authorUsername = author,
                        text = text,
                        timestamp = item.optLong("timestamp", 0L),
                        visibility = runCatching { FynxPostVisibility.valueOf(item.optString("visibility")) }.getOrDefault(FynxPostVisibility.PUBLIC),
                        likedByCurrentUser = item.optBoolean("likedByCurrentUser"),
                        savedByCurrentUser = item.optBoolean("savedByCurrentUser")
                    ))
                }
            }
        }.sortedByDescending { it.timestamp }
    }.getOrElse { emptyList() }

    private fun save(context: Context, posts: List<FynxPost>) {
        val array = JSONArray()
        posts.sortedByDescending { it.timestamp }.take(MAX_POSTS).forEach { post ->
            array.put(JSONObject().apply {
                put("id", post.id)
                put("authorUsername", post.authorUsername)
                put("text", post.text)
                put("timestamp", post.timestamp)
                put("visibility", post.visibility.name)
                put("likedByCurrentUser", post.likedByCurrentUser)
                put("savedByCurrentUser", post.savedByCurrentUser)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key(context), array.toString()).apply()
    }

    fun create(context: Context, text: String, visibility: FynxPostVisibility): FynxPost? {
        val clean = text.trim()
        if (clean.isBlank()) return null
        val username = FynxAuthStore.storedUsername(context)?.trim()?.let { if (it.startsWith("@")) it else "@$it" } ?: "@preview"
        val post = FynxPost(UUID.randomUUID().toString(), username, clean, System.currentTimeMillis(), visibility)
        save(context, listOf(post) + load(context))
        return post
    }

    fun toggleLike(context: Context, id: String) = save(context, load(context).map { if (it.id == id) it.copy(likedByCurrentUser = !it.likedByCurrentUser) else it })
    fun toggleSave(context: Context, id: String) = save(context, load(context).map { if (it.id == id) it.copy(savedByCurrentUser = !it.savedByCurrentUser) else it })
    fun delete(context: Context, id: String) = save(context, load(context).filterNot { it.id == id })
}
