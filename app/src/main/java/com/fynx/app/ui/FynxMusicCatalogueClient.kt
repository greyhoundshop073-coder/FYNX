package com.fynx.app.ui

import android.content.Context
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
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
    private const val CATALOGUE_TIMEOUT_MS = 15_000L

    suspend fun listPublished(context: Context, query: String = ""): Result<List<FynxMusicCatalogueTrack>> {
        // A slow/offline backend must not leave the picker spinning forever.
        // withTimeoutOrNull cancels the underlying request; caller cancellation must
        // still propagate so a newly typed search is not overwritten by stale work.
        val tracks = try {
            withTimeoutOrNull(CATALOGUE_TIMEOUT_MS) {
                val suffix = query.trim().take(80)
                val path = if (suffix.isBlank()) {
                    "/api/social/music/catalogue"
                } else {
                    "/api/social/music/catalogue?q=" + java.net.URLEncoder.encode(suffix, "UTF-8")
                }
                val raw = FynxBackendClient.get(context, path).getOrThrow()
                val array = JSONArray(raw)
                val result = ArrayList<FynxMusicCatalogueTrack>(array.length())
                for (index in 0 until array.length()) {
                    val row = array.optJSONObject(index)
                    if (row == null) continue
                    val id = row.optLong("id", 0L)
                    val mediaId = row.optLong("mediaId", 0L)
                    if (id < 1L || mediaId < 1L) continue
                    result.add(
                        FynxMusicCatalogueTrack(
                            id = id,
                            mediaId = mediaId,
                            title = row.optString("title").trim().take(120),
                            artist = row.optString("artist").trim().take(120),
                            durationMs = row.optLong("durationMs", 0L).coerceAtLeast(0L)
                        )
                    )
                }
                result.toList()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return Result.failure(error)
        }

        return if (tracks != null) {
            Result.success(tracks)
        } else {
            Result.failure(IOException("Music catalogue request timed out. Please try again."))
        }
    }
}
