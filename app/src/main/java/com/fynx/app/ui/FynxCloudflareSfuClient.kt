package com.fynx.app.ui

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * Authenticated Android boundary for the Cloudflare Realtime SFU routes.
 * The Cloudflare App Secret is intentionally never present on this client.
 */
internal object FynxCloudflareSfuClient {
    data class Publication(
        val sessionId: String,
        val userId: String,
        val trackNames: List<String>
    )

    data class Track(val mid: String, val trackName: String)

    data class Operation(
        val sessionDescriptionType: String?,
        val sessionDescriptionSdp: String?,
        val requiresImmediateRenegotiation: Boolean,
        val tracks: List<Track>
    )

    private val callIdPattern = Regex("^call[-_][a-zA-Z0-9_-]{1,70}$")
    private val resourceIdPattern = Regex("^[a-zA-Z0-9_-]{1,128}$")
    private val userIdPattern = Regex("^\\d{1,19}$")
    private val allowedTrackNames = setOf("microphone", "camera")

    suspend fun createSession(context: Context, callId: String): String {
        require(callIdPattern.matches(callId)) { "A valid active call is required for SFU setup." }
        val raw = FynxBackendClient.postJson(
            context,
            "/api/calls/sfu/sessions",
            JSONObject().put("callId", callId).toString()
        ).getOrThrow()
        val sessionId = JSONObject(raw).optString("sessionId").trim()
        require(resourceIdPattern.matches(sessionId)) { "The SFU returned an invalid media session." }
        return sessionId
    }

    suspend fun publish(
        context: Context,
        callId: String,
        sessionId: String,
        offerSdp: String,
        tracks: List<Track>
    ): Operation {
        requireValidIdentity(callId, sessionId)
        require(offerSdp.isNotBlank() && offerSdp.length <= 200_000) { "The local media offer is invalid." }
        require(tracks.isNotEmpty() && tracks.size <= 2 && tracks.map { it.trackName }.toSet().size == tracks.size) {
            "The local media track list is invalid."
        }
        val trackArray = JSONArray().apply {
            tracks.forEach { track ->
                require(resourceIdPattern.matches(track.mid) && track.trackName in allowedTrackNames)
                put(JSONObject().put("mid", track.mid).put("trackName", track.trackName))
            }
        }
        val body = JSONObject()
            .put("callId", callId)
            .put("sessionDescription", JSONObject().put("type", "offer").put("sdp", offerSdp))
            .put("tracks", trackArray)
        val raw = FynxBackendClient.postJson(
            context,
            "/api/calls/sfu/sessions/${Uri.encode(sessionId)}/publish",
            body.toString()
        ).getOrThrow()
        return parseOperation(raw)
    }

    suspend fun discover(context: Context, callId: String): List<Publication> {
        require(callIdPattern.matches(callId)) { "A valid active call is required for SFU discovery." }
        val raw = FynxBackendClient.get(
            context,
            "/api/calls/sfu/sessions?callId=${Uri.encode(callId)}"
        ).getOrThrow()
        return parsePublications(raw)
    }

    suspend fun subscribe(
        context: Context,
        callId: String,
        sessionId: String,
        publisherSessionId: String,
        trackNames: List<String>
    ): Operation {
        requireValidIdentity(callId, sessionId)
        require(resourceIdPattern.matches(publisherSessionId))
        require(trackNames.isNotEmpty() && trackNames.size <= 2 &&
            trackNames.all { it in allowedTrackNames } && trackNames.toSet().size == trackNames.size) {
            "The requested remote media track list is invalid."
        }
        val body = JSONObject()
            .put("callId", callId)
            .put("publisherSessionId", publisherSessionId)
            .put("trackNames", JSONArray(trackNames))
        val raw = FynxBackendClient.postJson(
            context,
            "/api/calls/sfu/sessions/${Uri.encode(sessionId)}/subscribe",
            body.toString()
        ).getOrThrow()
        return parseOperation(raw)
    }

    suspend fun renegotiate(
        context: Context,
        callId: String,
        sessionId: String,
        answerSdp: String
    ) {
        requireValidIdentity(callId, sessionId)
        require(answerSdp.isNotBlank() && answerSdp.length <= 200_000)
        val body = JSONObject()
            .put("callId", callId)
            .put("sessionDescription", JSONObject().put("type", "answer").put("sdp", answerSdp))
        FynxBackendClient.putJson(
            context,
            "/api/calls/sfu/sessions/${Uri.encode(sessionId)}/renegotiate",
            body.toString()
        ).getOrThrow()
    }

    suspend fun close(context: Context, callId: String, sessionId: String) {
        requireValidIdentity(callId, sessionId)
        val body = JSONObject().put("callId", callId).put("force", true).toString()
        FynxBackendClient.postJson(
            context,
            "/api/calls/sfu/sessions/${Uri.encode(sessionId)}/close",
            body
        ).getOrThrow()
    }

    internal fun parsePublications(raw: String): List<Publication> {
        val root = JSONObject(raw)
        val source = root.optJSONArray("publishers")
            ?: throw IllegalArgumentException("The SFU publication list is missing.")
        val seenSessions = mutableSetOf<String>()
        return buildList {
            for (index in 0 until source.length()) {
                val item = source.optJSONObject(index) ?: continue
                val sessionId = item.optString("sessionId").trim()
                val userId = item.optString("userId").trim()
                val names = item.optJSONArray("trackNames") ?: JSONArray()
                val trackNames = buildList {
                    for (nameIndex in 0 until names.length()) {
                        val name = names.optString(nameIndex)
                        if (name in allowedTrackNames && name !in this) add(name)
                    }
                }
                if (resourceIdPattern.matches(sessionId) && userIdPattern.matches(userId) &&
                    trackNames.isNotEmpty() && seenSessions.add(sessionId)) {
                    add(Publication(sessionId, userId, trackNames))
                }
            }
        }
    }

    internal fun parseOperation(raw: String): Operation {
        val root = JSONObject(raw)
        val description = root.optJSONObject("sessionDescription")
        val type = description?.optString("type")?.takeIf { it == "offer" || it == "answer" }
        val sdp = description?.optString("sdp")?.takeIf { it.isNotBlank() && it.length <= 200_000 }
        if (description != null && (type == null || sdp == null)) {
            throw IllegalArgumentException("The SFU returned an invalid session description.")
        }
        val source = root.optJSONArray("tracks") ?: JSONArray()
        val tracks = buildList {
            for (index in 0 until source.length()) {
                val item = source.optJSONObject(index) ?: continue
                val mid = item.optString("mid").trim()
                val name = item.optString("trackName").trim()
                if (resourceIdPattern.matches(mid) && name in allowedTrackNames) add(Track(mid, name))
            }
        }
        return Operation(
            sessionDescriptionType = type,
            sessionDescriptionSdp = sdp,
            requiresImmediateRenegotiation = root.optBoolean("requiresImmediateRenegotiation"),
            tracks = tracks
        )
    }

    private fun requireValidIdentity(callId: String, sessionId: String) {
        require(callIdPattern.matches(callId)) { "A valid active call is required for SFU media." }
        require(resourceIdPattern.matches(sessionId)) { "The SFU media session is invalid." }
    }
}
