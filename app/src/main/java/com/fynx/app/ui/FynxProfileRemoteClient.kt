package com.fynx.app.ui

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.net.URLEncoder

object FynxProfileRemoteClient {
    data class Profile(
        val id: String,
        val username: String,
        val displayName: String,
        val bio: String,
        val country: String,
        val verified: Boolean,
        val profilePhotoMediaId: String?,
        val activityVisible: Boolean,
        val relationship: String,
        val viewerSentRequest: Boolean,
        val viewerReceivedRequest: Boolean,
        val mutualFriends: Int,
        val postCount: Int
    )

    data class Report(val id: String, val status: String)

    suspend fun get(context: Context, username: String): Result<Profile> {
        val encoded = URLEncoder.encode(username.trim().removePrefix("@"), "UTF-8")
        return FynxBackendClient.get(context, "/api/social/profile/$encoded").mapCatching { raw ->
            val p = JSONObject(raw).getJSONObject("profile")
            Profile(
                id = p.optString("id"),
                username = p.optString("username"),
                displayName = p.optString("displayName"),
                bio = p.optString("bio"),
                country = p.optString("country"),
                verified = p.optBoolean("verified"),
                profilePhotoMediaId = p.optString("profilePhotoMediaId").takeIf { it.isNotBlank() && it != "null" },
                activityVisible = p.optBoolean("activityVisible"),
                relationship = p.optString("relationship"),
                viewerSentRequest = p.optBoolean("viewerSentRequest"),
                viewerReceivedRequest = p.optBoolean("viewerReceivedRequest"),
                mutualFriends = p.optInt("mutualFriends"),
                postCount = p.optInt("postCount")
            )
        }
    }

    suspend fun update(
        context: Context,
        displayName: String,
        username: String,
        bio: String,
        country: String = "",
        profilePhotoMediaId: String? = null
    ): Result<Profile> = FynxBackendClient.patchJson(
        context,
        "/api/social/profile/me",
        JSONObject().apply {
            put("displayName", displayName.trim())
            put("username", username.trim().removePrefix("@"))
            put("bio", bio.trim())
            put("country", country.trim())
            put("profilePhotoMediaId", profilePhotoMediaId?.toLongOrNull() ?: JSONObject.NULL)
        }.toString()
    ).mapCatching { raw ->
        val p = JSONObject(raw).getJSONObject("profile")
        Profile(p.optString("id"), p.optString("username"), p.optString("display_name"), p.optString("bio"), p.optString("country"), p.optBoolean("verified"), p.optString("profile_photo_media_id").takeIf { it.isNotBlank() && it != "null" }, true, "self", false, false, 0, 0)
    }

    suspend fun uploadProfilePhoto(context: Context, uri: Uri): Result<String> {
        val mime = context.contentResolver.getType(uri)?.lowercase()?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
        return FynxProductionMessaging.uploadMedia(context, uri, mime).map { it.id }
    }

    suspend fun report(context: Context, username: String, reason: String, details: String = ""): Result<Report> {
        val encoded = URLEncoder.encode(username.trim().removePrefix("@"), "UTF-8")
        return FynxBackendClient.postJson(context, "/api/social/profile/$encoded/report", JSONObject().apply { put("reason", reason.trim().ifBlank { "Other" }); put("details", details.trim()) }.toString()).mapCatching { raw ->
            val r = JSONObject(raw).getJSONObject("report")
            Report(r.optString("id"), r.optString("status"))
        }
    }
}
