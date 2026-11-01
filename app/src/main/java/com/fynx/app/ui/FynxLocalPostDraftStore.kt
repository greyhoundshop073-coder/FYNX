package com.fynx.app.ui

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** A private, account-scoped post draft. Media is copied into durable app files. */
data class FynxLocalPostDraft(
    val id: String,
    val caption: String,
    val visibility: String,
    val audience: String,
    val selectedAudienceIds: List<String>,
    val location: String?,
    val textBackgroundKey: String?,
    val mediaPaths: List<String>,
    val mediaTypes: List<String>,
    val musicId: Long?,
    val musicMediaId: Long?,
    val musicTitle: String?,
    val musicArtist: String?,
    val musicDurationMs: Long,
    val createdAt: Long
) {
    val title: String
        get() = caption.trim().ifBlank {
            when (mediaTypes.firstOrNull()) {
                "video" -> "Video post"
                "image" -> "Photo post"
                "audio" -> "Voice post"
                else -> "New post"
            }
        }.take(72)
}

object FynxLocalPostDraftStore {
    private const val MAX_DRAFTS = 12
    private const val PREFS_PREFIX = "fynx_post_drafts_"
    private const val KEY_DRAFTS = "drafts"

    private fun accountPrefs(context: Context) =
        context.getSharedPreferences(PREFS_PREFIX + accountNamespace(context), Context.MODE_PRIVATE)

    private fun accountNamespace(context: Context): String {
        val account = FynxAuthStore.accountStorageKey(context) ?: "signed_out"
        val digest = MessageDigest.getInstance("SHA-256").digest(account.toByteArray(Charsets.UTF_8))
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }

    private fun mediaExtension(context: Context, uri: Uri, type: String): String {
        val fileExtension = if (uri.scheme == "file") {
            uri.path?.let(::File)?.extension?.takeIf { it.isNotBlank() }
        } else null
        val mimeExtension = runCatching {
            context.contentResolver.getType(uri)?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        }.getOrNull()
        val fallback = when (type) {
            "video" -> "mp4"
            "audio" -> "m4a"
            else -> "jpg"
        }
        val extension = (fileExtension ?: mimeExtension ?: fallback).lowercase(Locale.ROOT)
        require(extension.matches(Regex("[a-z0-9]{1,8}"))) { "This media file has an unsupported extension." }
        return extension
    }

    fun list(context: Context): List<FynxLocalPostDraft> {
        val raw = accountPrefs(context).getString(KEY_DRAFTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val paths = item.optJSONArray("mediaPaths") ?: JSONArray()
                    val types = item.optJSONArray("mediaTypes") ?: JSONArray()
                    val validMedia = (0 until paths.length()).mapNotNull { mediaIndex ->
                        val path = paths.optString(mediaIndex).takeIf(String::isNotBlank) ?: return@mapNotNull null
                        if (!File(path).isFile) return@mapNotNull null
                        path to types.optString(mediaIndex, "image")
                    }
                    val mediaPaths = validMedia.map { it.first }
                    val alignedTypes = validMedia.map { it.second }
                    val friendIds = item.optJSONArray("selectedAudienceIds") ?: JSONArray()
                    add(
                        FynxLocalPostDraft(
                            id = item.optString("id"),
                            caption = item.optString("caption"),
                            visibility = item.optString("visibility", FynxPostVisibility.FRIENDS_ONLY.name),
                            audience = item.optString("audience", FynxPostAudience.FRIENDS.name),
                            selectedAudienceIds = (0 until friendIds.length()).map { friendIds.optString(it) }.filter(String::isNotBlank),
                            location = item.optString("location").takeIf { it.isNotBlank() },
                            textBackgroundKey = item.optString("textBackgroundKey").takeIf { it.isNotBlank() },
                            mediaPaths = mediaPaths,
                            mediaTypes = alignedTypes,
                            musicId = item.optLong("musicId").takeIf { it > 0L },
                            musicMediaId = item.optLong("musicMediaId").takeIf { it > 0L },
                            musicTitle = item.optString("musicTitle").takeIf { it.isNotBlank() },
                            musicArtist = item.optString("musicArtist").takeIf { it.isNotBlank() },
                            musicDurationMs = item.optLong("musicDurationMs").coerceAtLeast(0L),
                            createdAt = item.optLong("createdAt")
                        )
                    )
                }
            }.sortedByDescending { it.createdAt }
        }.getOrDefault(emptyList())
    }

    fun save(
        context: Context,
        existingId: String?,
        caption: String,
        visibility: FynxPostVisibility,
        audience: FynxPostAudience,
        selectedAudienceIds: Set<String>,
        location: String?,
        textBackground: FynxPostTextBackground?,
        media: List<Pair<Uri, String>>,
        music: FynxMusicCatalogueTrack?
    ): Result<FynxLocalPostDraft> = runCatching {
        require(caption.isNotBlank() || media.isNotEmpty() || music != null) {
            "Add a caption, photo, video or music before saving a draft."
        }
        require(media.size <= 4) { "A post draft can contain up to four media items." }

        val previous = existingId?.let { id -> list(context).firstOrNull { it.id == id } }
        val draftId = existingId ?: UUID.randomUUID().toString()
        val directory = File(context.filesDir, "post_drafts").apply {
            if (!exists() && !mkdirs()) error("Draft storage could not be created.")
        }
        val copied = ArrayList<File>()
        try {
            val mediaPaths = media.mapIndexed { index, pair ->
                val uri = pair.first
                val type = pair.second
                val extension = mediaExtension(context, uri, type)
                val target = File(directory, "${draftId}-${index}-${UUID.randomUUID()}.${extension}")
                // Register before opening/copying so a partial file is also removed if any
                // subsequent I/O or validation step fails.
                copied += target
                val input = if (uri.scheme == "file") {
                    File(uri.path ?: error("Draft media path is missing.")).inputStream()
                } else {
                    context.contentResolver.openInputStream(uri) ?: error("A selected media item could not be saved to the draft.")
                }
                input.use { source -> target.outputStream().use { output -> source.copyTo(output) } }
                require(target.isFile && target.length() > 0L) { "A selected media item could not be saved to the draft." }
                target.absolutePath
            }

            val draft = FynxLocalPostDraft(
                id = draftId,
                caption = caption.take(4000),
                visibility = visibility.name,
                audience = audience.name,
                selectedAudienceIds = selectedAudienceIds.toList(),
                location = location,
                textBackgroundKey = textBackground?.key,
                mediaPaths = mediaPaths,
                mediaTypes = media.map { it.second },
                musicId = music?.id,
                musicMediaId = music?.mediaId,
                musicTitle = music?.title,
                musicArtist = music?.artist,
                musicDurationMs = music?.durationMs ?: 0L,
                createdAt = System.currentTimeMillis()
            )
            val current = list(context).filterNot { it.id == draftId }
            val retained = (listOf(draft) + current).take(MAX_DRAFTS)
            val json = JSONArray()
            retained.forEach { item ->
                json.put(JSONObject().apply {
                    put("id", item.id)
                    put("caption", item.caption)
                    put("visibility", item.visibility)
                    put("audience", item.audience)
                    put("selectedAudienceIds", JSONArray(item.selectedAudienceIds))
                    put("location", item.location)
                    put("textBackgroundKey", item.textBackgroundKey)
                    put("mediaPaths", JSONArray(item.mediaPaths))
                    put("mediaTypes", JSONArray(item.mediaTypes))
                    put("musicId", item.musicId)
                    put("musicMediaId", item.musicMediaId)
                    put("musicTitle", item.musicTitle)
                    put("musicArtist", item.musicArtist)
                    put("musicDurationMs", item.musicDurationMs)
                    put("createdAt", item.createdAt)
                })
            }
            check(accountPrefs(context).edit().putString(KEY_DRAFTS, json.toString()).commit()) {
                "Draft could not be saved. Please try again."
            }
            previous?.mediaPaths?.forEach { old -> if (old !in mediaPaths) runCatching { File(old).delete() } }
            (current.drop(MAX_DRAFTS - 1)).flatMap { it.mediaPaths }.forEach { old -> runCatching { File(old).delete() } }
            draft
        } catch (failure: Throwable) {
            copied.forEach { runCatching { it.delete() } }
            throw failure
        }
    }

    fun delete(context: Context, id: String) {
        val current = list(context)
        val removed = current.firstOrNull { it.id == id } ?: return
        val retained = current.filterNot { it.id == id }
        val json = JSONArray()
        retained.forEach { item ->
            json.put(JSONObject().apply {
                put("id", item.id)
                put("caption", item.caption)
                put("visibility", item.visibility)
                put("audience", item.audience)
                put("selectedAudienceIds", JSONArray(item.selectedAudienceIds))
                put("location", item.location)
                put("textBackgroundKey", item.textBackgroundKey)
                put("mediaPaths", JSONArray(item.mediaPaths))
                put("mediaTypes", JSONArray(item.mediaTypes))
                put("musicId", item.musicId)
                put("musicMediaId", item.musicMediaId)
                put("musicTitle", item.musicTitle)
                put("musicArtist", item.musicArtist)
                put("musicDurationMs", item.musicDurationMs)
                put("createdAt", item.createdAt)
            })
        }
        check(accountPrefs(context).edit().putString(KEY_DRAFTS, json.toString()).commit()) {
            "Draft could not be deleted. Please try again."
        }
        removed.mediaPaths.forEach { path -> runCatching { File(path).delete() } }
    }
}
