package com.fynx.app.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Local group persistence foundation. A future backend can replace this without changing the UI contract. */
object FynxGroupsStore {
    private const val PREFS = "fynx_groups_store"
    private const val GROUPS_KEY = "groups"

    fun load(context: Context): List<FynxGroup> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(GROUPS_KEY, null) ?: return defaultGroups()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val members = item.optJSONArray("members") ?: JSONArray()
                    val parsedMembers = buildList {
                        for (memberIndex in 0 until members.length()) {
                            val member = members.getJSONObject(memberIndex)
                            add(FynxGroupMember(member.optString("username"), runCatching {
                                FynxGroupRole.valueOf(member.optString("role"))
                            }.getOrDefault(FynxGroupRole.MEMBER)))
                        }
                    }
                    add(
                        FynxGroup(
                            id = item.optString("id"),
                            name = item.optString("name"),
                            description = item.optString("description"),
                            visibility = runCatching {
                                FynxGroupVisibility.valueOf(item.optString("visibility"))
                            }.getOrDefault(FynxGroupVisibility.PRIVATE),
                            ownerUsername = item.optString("ownerUsername"),
                            members = parsedMembers
                        )
                    )
                }
            }.filter { FynxGroupsBatch1.validate(it).isEmpty() }
                .ifEmpty { defaultGroups() }
        }.getOrElse { defaultGroups() }
    }

    fun save(context: Context, groups: List<FynxGroup>) {
        val array = JSONArray()
        groups.forEach { group ->
            array.put(JSONObject().apply {
                put("id", group.id)
                put("name", group.name)
                put("description", group.description)
                put("visibility", group.visibility.name)
                put("ownerUsername", group.ownerUsername)
                put("members", JSONArray().apply {
                    group.members.forEach { member ->
                        put(JSONObject().apply {
                            put("username", member.username)
                            put("role", member.role.name)
                        })
                    }
                })
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(GROUPS_KEY, array.toString())
            .apply()
    }

    fun add(context: Context, group: FynxGroup): Boolean {
        val valid = FynxGroupsBatch1.create(group) ?: return false
        val groups = load(context)
        if (groups.any { it.name.equals(valid.name, ignoreCase = true) }) return false
        save(context, groups + valid)
        return true
    }

    private fun defaultGroups(): List<FynxGroup> = listOf(
        FynxGroup("family", "Family", "Family conversations and updates", FynxGroupVisibility.PRIVATE, "@username", listOf(
            FynxGroupMember("@username", FynxGroupRole.ADMIN),
            FynxGroupMember("@family", FynxGroupRole.MEMBER)
        )),
        FynxGroup("friends", "Friends", "Stay connected with friends", FynxGroupVisibility.PRIVATE, "@username", listOf(
            FynxGroupMember("@username", FynxGroupRole.ADMIN),
            FynxGroupMember("@username2", FynxGroupRole.MEMBER)
        )),
        FynxGroup("fynx-community", "FYNX Community", "A community for FYNX members", FynxGroupVisibility.PUBLIC, "@username", listOf(
            FynxGroupMember("@username", FynxGroupRole.ADMIN),
            FynxGroupMember("@fynx", FynxGroupRole.MEMBER)
        ))
    )
}
