package com.fynx.app.ui

import android.content.Context
import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import java.io.File

data class FynxStatusInteractions(
    val viewCount: Int = 0,
    val likeCount: Int = 0,
    val replyCount: Int = 0,
    val reactionCounts: Map<String, Int> = emptyMap(),
    val likedByMe: Boolean = false,
    val myReaction: String? = null
)

/** Authenticated Status API. The local store is only a cache/fallback and never the source of shared truth. */
object FynxStatusClient {
    suspend fun uploadMedia(context: Context, uri: Uri, mimeType: String): Result<String> = runCatching {
        val input = if (uri.scheme.equals("file", true)) uri.path?.let { File(it).inputStream() } else context.contentResolver.openInputStream(uri)
        val bytes = input?.use { stream ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(32 * 1024)
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                total += read
                if (total > 12 * 1024 * 1024) error("Selected media is too large. Maximum size is 12 MB.")
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        } ?: error("Could not read selected media.")
        if (bytes.isEmpty()) error("Selected media is empty.")
        val normalizedMime = mimeType.trim().lowercase()
        if (!normalizedMime.startsWith("image/") && !normalizedMime.startsWith("video/") && !normalizedMime.startsWith("audio/")) error("Unsupported Status media type.")
        val body = JSONObject().put("mimeType", normalizedMime).put("dataBase64", Base64.encodeToString(bytes, Base64.NO_WRAP)).toString()
        val raw = FynxBackendClient.postJson(context, "/api/media", body).getOrThrow()
        JSONObject(raw).getJSONObject("media").getString("id")
    }

    suspend fun create(context: Context, status: FynxStatus, mediaId: String?): Result<Unit> = runCatching {
        val body = JSONObject().apply {
            put("id", status.id); put("type", status.type.name); put("text", status.text ?: "")
            if (mediaId != null) put("mediaId", mediaId)
            put("backgroundColor", status.textStyle.backgroundColor); put("foregroundColor", status.textStyle.foregroundColor)
            put("font", status.textStyle.font.name); put("alignment", status.textStyle.alignment)
            put("privateStatus", status.audience != FynxStatusAudience.EVERYONE)
            put("audience", status.audience.name)
            put("voiceDurationMs", status.voiceDurationMs)
        }.toString()
        FynxBackendClient.postJson(context, "/api/statuses", body).getOrThrow()
    }

    suspend fun list(context: Context): Result<List<FynxStatus>> = runCatching {
        val raw = FynxBackendClient.get(context, "/api/statuses").getOrThrow()
        val items = JSONObject(raw).getJSONArray("statuses")
        buildList {
            for (i in 0 until items.length()) {
                val o = items.getJSONObject(i)
                val type = runCatching { FynxStatusType.valueOf(o.getString("type")) }.getOrNull() ?: continue
                val font = runCatching { FynxStatusTextFont.valueOf(o.optString("font", "CLASSIC")) }.getOrDefault(FynxStatusTextFont.CLASSIC)
                val audience = runCatching { FynxStatusAudience.valueOf(o.optString("audience", if (o.optBoolean("privateStatus")) "FRIENDS" else "EVERYONE")) }.getOrDefault(FynxStatusAudience.EVERYONE)
                add(FynxStatus(
                    id=o.getString("id"), ownerUsername=o.getString("ownerUsername"), ownerDisplayName=o.optString("ownerDisplayName"),
                    type=type, contentUri=o.optString("mediaUrl").ifBlank { null }, text=o.optString("text").ifBlank { null },
                    createdAtMillis=o.optLong("createdAtMillis"), expiresAtMillis=o.optLong("expiresAtMillis"),
                    textStyle=FynxStatusTextStyle(o.optLong("backgroundColor",0xFF111111),o.optLong("foregroundColor",0xFFFFFFFF),font,o.optInt("alignment",1)),
                    privateStatus=o.optBoolean("privateStatus"), voiceDurationMs=o.optLong("voiceDurationMs",0L), audience=audience
                ))
            }
        }
    }

    suspend fun interactions(context: Context, statusId: String): Result<FynxStatusInteractions> = runCatching {
        val raw = FynxBackendClient.get(context, "/api/statuses/${statusId.trim()}/interactions").getOrThrow()
        val o = JSONObject(raw)
        val counts = mutableMapOf<String, Int>()
        val reactionObject = o.optJSONObject("reactionCounts")
        if (reactionObject != null) reactionObject.keys().forEach { key -> counts[key] = reactionObject.optInt(key, 0) }
        FynxStatusInteractions(
            viewCount=o.optInt("viewCount"), likeCount=o.optInt("likeCount"), replyCount=o.optInt("replyCount"),
            reactionCounts=counts, likedByMe=o.optBoolean("likedByMe"), myReaction=o.optString("myReaction").ifBlank { null }
        )
    }

    suspend fun markViewed(context: Context, statusId: String): Result<Unit> = runCatching {
        FynxBackendClient.postJson(context, "/api/statuses/${statusId.trim()}/view", "{}").getOrThrow()
    }

    suspend fun toggleLike(context: Context, statusId: String): Result<Boolean> = runCatching {
        val raw = FynxBackendClient.postJson(context, "/api/statuses/${statusId.trim()}/like", "{}").getOrThrow()
        JSONObject(raw).optBoolean("liked")
    }

    suspend fun react(context: Context, statusId: String, reaction: String): Result<String?> = runCatching {
        val body = JSONObject().put("reaction", reaction).toString()
        val raw = FynxBackendClient.postJson(context, "/api/statuses/${statusId.trim()}/reaction", body).getOrThrow()
        JSONObject(raw).optString("reaction").ifBlank { null }
    }

    suspend fun reply(context: Context, statusId: String, body: String): Result<Unit> = runCatching {
        val raw = FynxBackendClient.postJson(context, "/api/statuses/${statusId.trim()}/reply", JSONObject().put("body", body.trim()).toString()).getOrThrow()
        JSONObject(raw)
    }

    suspend fun delete(context: Context, statusId: String): Result<Unit> = runCatching {
        FynxBackendClient.delete(context, "/api/statuses/${statusId.trim()}").getOrThrow()
    }
}
