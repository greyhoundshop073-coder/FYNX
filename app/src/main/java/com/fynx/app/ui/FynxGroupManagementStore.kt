package com.fynx.app.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Local, failure-safe persistence for group settings and moderation state. */
object FynxGroupManagementStore {
    private const val PREFS = "fynx_group_management"
    private const val DATA_KEY = "data"

    data class GroupManagementData(
        val settings: FynxGroupSettings,
        val state: FynxGroupManagementState
    )

    fun load(context: Context, groupId: String): GroupManagementData {
        val item = runCatching {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(DATA_KEY, null) ?: return@runCatching null
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getJSONObject(it) }
                .firstOrNull { it.optString("groupId") == groupId }
        }.getOrNull()

        if (item == null) return GroupManagementData(
            FynxGroupSettings(groupId),
            FynxGroupManagementState(groupId)
        )

        val settings = FynxGroupSettings(
            groupId = groupId,
            allowMemberPosts = item.optBoolean("allowMemberPosts", true),
            allowMemberInvites = item.optBoolean("allowMemberInvites", true),
            allowMarketplaceShares = item.optBoolean("allowMarketplaceShares", true),
            notificationsEnabled = item.optBoolean("notificationsEnabled", true)
        )
        val blocked = item.optJSONArray("blocked")?.let { array ->
            (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() }.toSet()
        } ?: emptySet()
        val moderators = item.optJSONArray("moderators")?.let { array ->
            (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() }.toSet()
        } ?: emptySet()
        return GroupManagementData(settings, FynxGroupManagementState(groupId, blocked, moderators))
    }

    fun save(context: Context, data: GroupManagementData) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = runCatching { JSONArray(prefs.getString(DATA_KEY, "[]") ?: "[]") }.getOrDefault(JSONArray())
        val next = JSONArray()
        for (index in 0 until current.length()) {
            val item = current.optJSONObject(index)
            if (item != null && item.optString("groupId") != data.settings.groupId) next.put(item)
        }
        next.put(JSONObject().apply {
            put("groupId", data.settings.groupId)
            put("allowMemberPosts", data.settings.allowMemberPosts)
            put("allowMemberInvites", data.settings.allowMemberInvites)
            put("allowMarketplaceShares", data.settings.allowMarketplaceShares)
            put("notificationsEnabled", data.settings.notificationsEnabled)
            put("blocked", JSONArray(data.state.blockedUsernames.toList()))
            put("moderators", JSONArray(data.state.moderatorUsernames.toList()))
        })
        prefs.edit().putString(DATA_KEY, next.toString()).apply()
    }
}
