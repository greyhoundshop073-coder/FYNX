package com.fynx.app.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Small local persistence layer for friend/request state. No backend required. */
class FynxFriendsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("fynx_friends", Context.MODE_PRIVATE)
    private val key = "profiles"

    fun load(): List<FriendProfile> {
        val raw = prefs.getString(key, null) ?: return samplePeople
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val o = array.getJSONObject(index)
                FriendProfile(
                    displayName = o.optString("displayName"),
                    username = o.optString("username"),
                    bio = o.optString("bio"),
                    hasProfilePhoto = o.optBoolean("hasProfilePhoto"),
                    isFriend = o.optBoolean("isFriend"),
                    requestSent = o.optBoolean("requestSent")
                )
            }
        }.getOrElse { samplePeople }
    }

    fun save(profiles: List<FriendProfile>) {
        val array = JSONArray()
        profiles.forEach { person ->
            array.put(JSONObject().apply {
                put("displayName", person.displayName)
                put("username", person.username)
                put("bio", person.bio)
                put("hasProfilePhoto", person.hasProfilePhoto)
                put("isFriend", person.isFriend)
                put("requestSent", person.requestSent)
            })
        }
        prefs.edit().putString(key, array.toString()).apply()
    }

    fun update(username: String, transform: (FriendProfile) -> FriendProfile) {
        save(load().map { if (it.username == username) transform(it) else it })
    }
}
