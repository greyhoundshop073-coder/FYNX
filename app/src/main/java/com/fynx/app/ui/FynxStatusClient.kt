package com.fynx.app.ui

import android.content.Context
import android.net.Uri
import android.util.Base64
import org.json.JSONObject

/** Account-scoped Status API. Local StatusStore remains as a cache/fallback. */
object FynxStatusClient {
    suspend fun uploadMedia(context: Context, uri: Uri, mimeType: String): Result<String> = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Could not read selected media.")
        val body = JSONObject().put("mimeType", mimeType).put("dataBase64", Base64.encodeToString(bytes, Base64.NO_WRAP)).toString()
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
