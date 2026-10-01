package com.fynx.app.ui

import android.content.Context
import org.json.JSONArray

/**
 * Controlled FYNX music catalogue.
 *
 * Ordinary users can only read/select tracks published by FYNX. They never upload
 * audio through this client. Catalogue writes will be exposed only through the
 * admin management surface.
 */
data class FynxMusicCatalogueTrack(
    val id: Long,
    val mediaId: Long,
    val title: String,
    val artist: String,
    val durationMs: Long
)

object FynxMusicCatalogueClient {
    suspend fun listPublished(context: Context, query: String = ""): Result<List<FynxMusicCatalogueTrack>> = runCatching {
        val suffix = query.trim().take(80)
        val path = if (suffix.isBlank()) {
            "/api/social/music/catalogue"
        } else {
            "/api/social/music/catalogue?q=" + java.net.URLEncoder.encode(suffix, "UTF-8")
        }
        val raw = FynxBackendClient.get(context, path).getOrThrow()
        val rows = JSONArray(raw).let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    val row = array.optJSONObject(index) ?: continue
                    val id = row.optLong("id", 0L)
                    val mediaId = row.optLong("mediaId", 0L)
                    if (id < 1L || mediaId < 1L) continue
                    add(
                        FynxMusicCatalogueTrack(
                            id = id,
                            mediaId = mediaId,
                            title = row.optString("title").trim().take(120),
                            artist = row.optString("artist").trim().take(120),
                            durationMs = row.optLong("durationMs", 0L).coerceAtLeast(0L)
                        )
                    )
                }
            }
        }
        rows
    }
}
