package com.fynx.app.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Device-local relationship state. Server synchronization will replace this store in the backend stage. */
class FynxFriendsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("fynx_friends", Context.MODE_PRIVATE)
    private val key = "profiles_${normalize(FynxAuthStore.storedUsername(context) ?: "@preview")}" 

    fun load(): List<FriendProfile> = runCatching {
        val raw = prefs.getString(key, null) ?: return emptyList()
        val array = JSONArray(raw)
        List(array.length()) { index ->
            val o = array.getJSONObject(index)
            FriendProfile(
                displayName = o.optString("displayName"),
                username = o.optString("username"),
                bio = o.optString("bio"),
                hasProfilePhoto = o.optBoolean("hasProfilePhoto"),
                status = runCatching { FynxFriendStatus.valueOf(o.optString("status")) }.getOrDefault(FynxFriendStatus.NONE)
            )
        }.filter { it.username.isNotBlank() }
    }.getOrElse { emptyList() }

    fun save(profiles: List<FriendProfile>) {
        val array = JSONArray()
        profiles.distinctBy { normalize(it.username) }.forEach { person ->
            array.put(JSONObject().apply {
                put("displayName", person.displayName)
                put("username", normalize(person.username))
                put("bio", person.bio)
                put("hasProfilePhoto", person.hasProfilePhoto)
                put("status", person.status.name)
            })
        }
        prefs.edit().putString(key, array.toString()).apply()
    }

    fun upsert(profile: FriendProfile) {
        val normalized = normalize(profile.username)
        save(load().filterNot { normalize(it.username) == normalized } + profile.copy(username = normalized))
    }

    fun setStatus(username: String, status: FynxFriendStatus) {
        val normalized = normalize(username)
        save(load().map { if (normalize(it.username) == normalized) it.copy(status = status) else it })
    }

    fun sendRequest(profile: FriendProfile) = upsert(profile.copy(status = FynxFriendStatus.OUTGOING_PENDING))
    fun cancelRequest(username: String) = setStatus(username, FynxFriendStatus.NONE)
    fun acceptRequest(username: String) = setStatus(username, FynxFriendStatus.FRIENDS)
    fun declineRequest(username: String) = setStatus(username, FynxFriendStatus.DECLINED)
    fun removeFriend(username: String) = setStatus(username, FynxFriendStatus.NONE)
    fun block(username: String) = setStatus(username, FynxFriendStatus.BLOCKED)

    private companion object {
        fun normalize(username: String): String = username.trim().let { if (it.startsWith("@")) it else "@$it" }
    }
}
