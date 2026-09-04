package com.fynx.app.ui

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * FYNX Status foundation. Statuses expire after 24 hours and support text,
 * photo, video and voice media. This layer deliberately keeps media in private
 * app storage so picker URIs do not disappear after the picker lifecycle.
 */
enum class FynxStatusType { TEXT, PHOTO, VIDEO, VOICE }

enum class FynxStatusTextFont { CLASSIC, CLEAN, BOLD, SERIF, TYPEWRITER }

data class FynxStatusTextStyle(
    val backgroundColor: Long = 0xFF111111,
    val foregroundColor: Long = 0xFFFFFFFF,
    val font: FynxStatusTextFont = FynxStatusTextFont.CLASSIC,
    val alignment: Int = 1
)

data class FynxStatus(
    val id: String,
    val ownerUsername: String,
    val ownerDisplayName: String,
    val type: FynxStatusType,
    val contentUri: String? = null,
    val text: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val expiresAtMillis: Long = createdAtMillis + FYNX_STATUS_EXPIRY_MS,
    val textStyle: FynxStatusTextStyle = FynxStatusTextStyle(),
    val privateStatus: Boolean = false,
    val voiceDurationMs: Long = 0L,
    val muted: Boolean = false
) {
    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean = nowMillis >= expiresAtMillis
}

const val FYNX_STATUS_EXPIRY_MS = 24L * 60L * 60L * 1000L
const val FYNX_STATUS_MAX_TEXT_LENGTH = 700
const val FYNX_STATUS_MAX_VIDEO_DURATION_MS = 90_000L
const val FYNX_STATUS_MAX_VOICE_DURATION_MS = 30_000L

object FynxStatusStore {
    private const val PREFS = "fynx_status_foundation"
    private const val KEY_STATUSES = "statuses"

    fun load(context: Context): List<FynxStatus> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_STATUSES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val type = runCatching { FynxStatusType.valueOf(o.optString("type")) }.getOrNull() ?: continue
                    val font = runCatching { FynxStatusTextFont.valueOf(o.optString("font", FynxStatusTextFont.CLASSIC.name)) }.getOrDefault(FynxStatusTextFont.CLASSIC)
                    val status = FynxStatus(
                        id = o.optString("id"), ownerUsername = o.optString("ownerUsername"), ownerDisplayName = o.optString("ownerDisplayName"),
                        type = type, contentUri = o.optString("contentUri").ifBlank { null }, text = o.optString("text").ifBlank { null },
                        createdAtMillis = o.optLong("createdAtMillis"), expiresAtMillis = o.optLong("expiresAtMillis"),
                        textStyle = FynxStatusTextStyle(o.optLong("backgroundColor", 0xFF111111), o.optLong("foregroundColor", 0xFFFFFFFF), font, o.optInt("alignment", 1)),
                        privateStatus = o.optBoolean("privateStatus"), voiceDurationMs = o.optLong("voiceDurationMs", 0L), muted = o.optBoolean("muted")
                    )
                    if (status.id.isNotBlank() && status.ownerUsername.isNotBlank() && !status.isExpired()) add(status)
                }
            }.sortedByDescending { it.createdAtMillis }
        }.getOrElse { emptyList() }
    }

    fun save(context: Context, status: FynxStatus) {
        val active = load(context).filterNot { it.id == status.id && it.ownerUsername == status.ownerUsername } + status
        val array = JSONArray()
        active.filterNot { it.isExpired() }.take(200).forEach { array.put(toJson(it)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_STATUSES, array.toString()).apply()
    }

    fun delete(context: Context, statusId: String) {
        val status = load(context).firstOrNull { it.id == statusId }
        val remaining = load(context).filterNot { it.id == statusId }
        val array = JSONArray()
        remaining.forEach { array.put(toJson(it)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_STATUSES, array.toString()).apply()
        status?.contentUri?.let { path -> runCatching { File(Uri.parse(path).path ?: "").delete() } }
    }

    suspend fun persistMedia(context: Context, sourceUri: Uri, type: FynxStatusType): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val input = if (sourceUri.scheme.equals("file", true)) sourceUri.path?.let { File(it).inputStream() } else context.contentResolver.openInputStream(sourceUri)
                ?: return@runCatching null
            val extension = when (type) {
                FynxStatusType.PHOTO -> ".jpg"
                FynxStatusType.VIDEO -> ".mp4"
                FynxStatusType.VOICE -> ".m4a"
                FynxStatusType.TEXT -> return@runCatching null
            }
            val file = File(context.filesDir, "fynx_status_${UUID.randomUUID()}$extension")
            input.use { stream -> FileOutputStream(file).use { output ->
                val buffer = ByteArray(32 * 1024)
                var total = 0L
                while (true) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > 12 * 1024 * 1024) error("Status media is too large. Maximum size is 12 MB.")
                    output.write(buffer, 0, read)
                }
            } }
            Uri.fromFile(file)
        }.getOrNull()
    }

    private fun toJson(status: FynxStatus) = JSONObject().apply {
        put("id", status.id); put("ownerUsername", status.ownerUsername); put("ownerDisplayName", status.ownerDisplayName)
        put("type", status.type.name); put("contentUri", status.contentUri ?: ""); put("text", status.text ?: "")
        put("createdAtMillis", status.createdAtMillis); put("expiresAtMillis", status.expiresAtMillis)
        put("backgroundColor", status.textStyle.backgroundColor); put("foregroundColor", status.textStyle.foregroundColor)
        put("font", status.textStyle.font.name); put("alignment", status.textStyle.alignment)
        put("privateStatus", status.privateStatus); put("voiceDurationMs", status.voiceDurationMs); put("muted", status.muted)
    }
}