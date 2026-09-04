package com.fynx.app.ui

import android.content.Context
import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import java.io.File

/** Account-scoped Status API. Local StatusStore remains as a cache/fallback. */
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
            put("privateStatus", status.privateStatus); put("voiceDurationMs", status.voiceDurationMs)
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
                add(FynxStatus(
                    id=o.getString("id"), ownerUsername=o.getString("ownerUsername"), ownerDisplayName=o.optString("ownerDisplayName"),
                    type=type, contentUri=o.optString("mediaUrl").ifBlank { null }, text=o.optString("text").ifBlank { null },
                    createdAtMillis=o.optLong("createdAtMillis"), expiresAtMillis=o.optLong("expiresAtMillis"),
                    textStyle=FynxStatusTextStyle(o.optLong("backgroundColor",0xFF111111),o.optLong("foregroundColor",0xFFFFFFFF),font,o.optInt("alignment",1)),
                    privateStatus=o.optBoolean("privateStatus"), voiceDurationMs=o.optLong("voiceDurationMs",0L)
                ))
            }
        }
    }
}
