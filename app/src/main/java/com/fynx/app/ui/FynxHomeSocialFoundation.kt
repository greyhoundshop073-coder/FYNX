package com.fynx.app.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class FynxPostVisibility { PUBLIC, FRIENDS_ONLY }

data class FynxPostComment(val id: String, val postId: String, val authorUsername: String, val text: String, val timestamp: Long)

data class FynxPost(
    val id: String,
    val authorUsername: String,
    val text: String,
    val timestamp: Long,
    val visibility: FynxPostVisibility = FynxPostVisibility.PUBLIC,
    val mediaUri: String? = null,
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val likedByCurrentUser: Boolean = false,
    val savedByCurrentUser: Boolean = false
)

object FynxHomePostStore {
    private const val PREFS = "fynx_home_posts"
    private const val COMMENTS_PREFS = "fynx_home_comments"
    private const val MAX_POSTS = 200
    private const val MAX_COMMENTS = 500

    private fun accountKey(context: Context): String =
        (FynxAuthStore.storedUsername(context) ?: "@preview").trim().lowercase().removePrefix("@").ifBlank { "preview" }
    private fun postKey(context: Context) = "posts_${accountKey(context)}"
    private fun commentsKey(context: Context) = "comments_${accountKey(context)}"

    fun load(context: Context): List<FynxPost> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(postKey(context), null) ?: return emptyList()
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id"); val text = item.optString("text").trim(); val author = item.optString("authorUsername")
                val mediaUri = item.optString("mediaUri").ifBlank { null }
                if (id.isNotBlank() && author.isNotBlank() && (text.isNotBlank() || mediaUri != null)) add(FynxPost(
                    id, author, text, item.optLong("timestamp", 0L),
                    runCatching { FynxPostVisibility.valueOf(item.optString("visibility")) }.getOrDefault(FynxPostVisibility.PUBLIC),
                    mediaUri, item.optInt("likeCount", 0).coerceAtLeast(0), item.optInt("commentCount", 0).coerceAtLeast(0),
                    item.optBoolean("likedByCurrentUser"), item.optBoolean("savedByCurrentUser")
                ))
            }
        }.sortedByDescending { it.timestamp }
    }.getOrElse { emptyList() }

    fun loadComments(context: Context, postId: String): List<FynxPostComment> = runCatching {
        val raw = context.getSharedPreferences(COMMENTS_PREFS, Context.MODE_PRIVATE).getString(commentsKey(context), null) ?: return emptyList()
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                if (item.optString("postId") == postId && item.optString("text").isNotBlank()) add(FynxPostComment(
                    item.optString("id"), postId, item.optString("authorUsername"), item.optString("text"), item.optLong("timestamp", 0L)
                ))
            }
        }.sortedBy { it.timestamp }
    }.getOrElse { emptyList() }

    private fun loadAllComments(context: Context): List<FynxPostComment> = runCatching {
        val raw = context.getSharedPreferences(COMMENTS_PREFS, Context.MODE_PRIVATE).getString(commentsKey(context), null) ?: return emptyList()
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) { val x = array.optJSONObject(i) ?: continue; add(FynxPostComment(x.optString("id"), x.optString("postId"), x.optString("authorUsername"), x.optString("text"), x.optLong("timestamp", 0L))) }
        }
    }.getOrElse { emptyList() }

    private fun save(context: Context, posts: List<FynxPost>) {
        val array = JSONArray()
        posts.sortedByDescending { it.timestamp }.take(MAX_POSTS).forEach { post -> array.put(JSONObject().apply {
            put("id", post.id); put("authorUsername", post.authorUsername); put("text", post.text); put("timestamp", post.timestamp); put("visibility", post.visibility.name)
            put("mediaUri", post.mediaUri ?: ""); put("likeCount", post.likeCount.coerceAtLeast(0)); put("commentCount", post.commentCount.coerceAtLeast(0)); put("likedByCurrentUser", post.likedByCurrentUser); put("savedByCurrentUser", post.savedByCurrentUser)
        }) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(postKey(context), array.toString()).apply()
    }

    private fun saveComments(context: Context, comments: List<FynxPostComment>) {
        val array = JSONArray(); comments.takeLast(MAX_COMMENTS).forEach { c -> array.put(JSONObject().apply {
            put("id", c.id); put("postId", c.postId); put("authorUsername", c.authorUsername); put("text", c.text); put("timestamp", c.timestamp)
        }) }
        context.getSharedPreferences(COMMENTS_PREFS, Context.MODE_PRIVATE).edit().putString(commentsKey(context), array.toString()).apply()
    }

    fun create(context: Context, text: String, visibility: FynxPostVisibility, mediaUri: String? = null): FynxPost? {
        val clean = text.trim(); val cleanMedia = mediaUri?.trim()?.takeIf { it.isNotBlank() }
        if (clean.isBlank() && cleanMedia == null) return null
        val username = FynxAuthStore.storedUsername(context)?.trim()?.let { if (it.startsWith("@")) it else "@$it" } ?: "@preview"
        val post = FynxPost(UUID.randomUUID().toString(), username, clean, System.currentTimeMillis(), visibility, cleanMedia)
        save(context, listOf(post) + load(context)); return post
    }

    fun toggleLike(context: Context, id: String) = save(context, load(context).map { if (it.id != id) it else { val liked = !it.likedByCurrentUser; it.copy(likedByCurrentUser = liked, likeCount = (it.likeCount + if (liked) 1 else -1).coerceAtLeast(0)) } })

    fun addComment(context: Context, postId: String, text: String): FynxPostComment? {
        val clean = text.trim(); if (clean.isBlank() || load(context).none { it.id == postId }) return null
        val author = FynxAuthStore.storedUsername(context)?.trim()?.let { if (it.startsWith("@")) it else "@$it" } ?: "@preview"
        val comment = FynxPostComment(UUID.randomUUID().toString(), postId, author, clean, System.currentTimeMillis())
        saveComments(context, loadAllComments(context) + comment)
        val count = loadComments(context, postId).size
        save(context, load(context).map { if (it.id == postId) it.copy(commentCount = count) else it })
        return comment
    }

    fun toggleSave(context: Context, id: String) = save(context, load(context).map { if (it.id == id) it.copy(savedByCurrentUser = !it.savedByCurrentUser) else it })
    fun delete(context: Context, id: String) { save(context, load(context).filterNot { it.id == id }); saveComments(context, loadAllComments(context).filterNot { it.postId == id }) }
}
