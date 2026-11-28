package com.fynx.app.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.PeerConnection

/**
 * Fetches short-lived ICE configuration for an already-active FYNX call.
 * Long-lived Cloudflare TURN key material never leaves the backend.
 */
object FynxCallIceServerProvider {
    data class Configuration(
        val servers: List<PeerConnection.IceServer>,
        val refreshAfterSeconds: Long,
        val expiresInSeconds: Long
    )

    private val callIdPattern = Regex("^call[-_][a-zA-Z0-9_-]{1,70}$")
    private val cloudflareTurnUrl = Regex(
        "^turns?:turn\\.cloudflare\\.com:(?:3478|443|80|5349)(?:\\?transport=(?:udp|tcp))?$",
        RegexOption.IGNORE_CASE
    )

    suspend fun fetch(context: Context, callId: String): Result<Configuration> {
        val normalizedCallId = callId.trim()
        if (!callIdPattern.matches(normalizedCallId)) {
            return Result.failure(IllegalArgumentException("A valid active call is required for relay configuration."))
        }
        val body = JSONObject().put("callId", normalizedCallId).toString()
        return FynxBackendClient.postJson(context, "/api/calls/ice-servers", body)
            .mapCatching(::parseResponse)
    }

    internal fun parseResponse(raw: String): Configuration {
        val root = JSONObject(raw)
        val source = root.optJSONArray("iceServers")
            ?: throw IllegalArgumentException("Call relay configuration is missing.")
        val servers = ArrayList<PeerConnection.IceServer>(source.length())
        var hasTurn = false

        for (index in 0 until source.length()) {
            val item = source.optJSONObject(index) ?: continue
            val urls = readUrls(item.opt("urls"))
            if (urls.isEmpty()) continue

            val safeUrls = urls.filter { url ->
                url.startsWith("stun:stun.cloudflare.com:3478", ignoreCase = true) ||
                    cloudflareTurnUrl.matches(url)
            }
            if (safeUrls.isEmpty()) continue

            val hasTurnUrls = safeUrls.any { it.startsWith("turn:", true) || it.startsWith("turns:", true) }
            if (hasTurnUrls) {
                val username = item.optString("username").trim()
                val credential = item.optString("credential").trim()
                require(username.isNotBlank() && credential.isNotBlank()) {
                    "Call relay credentials are incomplete."
                }
                require(username.length <= 512 && credential.length <= 512) {
                    "Call relay credentials are invalid."
                }
                hasTurn = true
                servers += PeerConnection.IceServer.builder(safeUrls)
                    .setUsername(username)
                    .setPassword(credential)
                    .createIceServer()
            } else {
                servers += PeerConnection.IceServer.builder(safeUrls).createIceServer()
            }
        }

        require(hasTurn) { "The call relay service did not return a usable TURN server." }
        require(servers.isNotEmpty()) { "No usable call connectivity servers were returned." }

        val expiresIn = root.optLong("expiresInSeconds", 0L)
        val refreshAfter = root.optLong("refreshAfterSeconds", 0L)
        require(expiresIn in 300L..172800L && refreshAfter in 60L until expiresIn) {
            "Call relay credential lifetime is invalid."
        }
        return Configuration(
            servers = servers,
            refreshAfterSeconds = refreshAfter,
            expiresInSeconds = expiresIn
        )
    }

    private fun readUrls(value: Any?): List<String> = when (value) {
        is String -> listOf(value.trim()).filter { it.isNotBlank() }
        is JSONArray -> buildList {
            for (index in 0 until value.length()) {
                val url = value.optString(index).trim()
                if (url.isNotBlank()) add(url)
            }
        }
        else -> emptyList()
    }
}
