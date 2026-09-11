package com.fynx.app.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Local group persistence foundation. Production sync can replace this store without changing the UI contract. */
object FynxGroupsStore {
    private const val PREFS = "fynx_groups_store"
    private const val GROUPS_KEY_PREFIX = "groups_"

    private fun groupsKey(context: Context): String =
        GROUPS_KEY_PREFIX + (FynxAuthStore.accountStorageKey(context)?.let(::storageKey) ?: "signed_out")

    fun load(context: Context): List<FynxGroup> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(groupsKey(context), null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val members = item.optJSONArray("members") ?: JSONArray()
                    val parsedMembers = buildList {
                        for (memberIndex in 0 until members.length()) {
                            val member = members.getJSONObject(memberIndex)
                            add(FynxGroupMember(member.optString("username"), runCatching { FynxGroupRole.valueOf(member.optString("role")) }.getOrDefault(FynxGroupRole.MEMBER)))
                        }
                    }
                    add(FynxGroup(item.optString("id"), item.optString("name"), item.optString("description"), runCatching { FynxGroupVisibility.valueOf(item.optString("visibility")) }.getOrDefault(FynxGroupVisibility.PRIVATE), item.optString("ownerUsername"), parsedMembers))
                }
            }.filter { FynxGroupsBatch1.validate(it).isEmpty() }
        }.getOrElse { emptyList() }
    }

    fun save(context: Context, groups: List<FynxGroup>) {
        val array = JSONArray()
        groups.forEach { group ->
            array.put(JSONObject().apply {
                put("id", group.id); put("name", group.name); put("description", group.description)
                put("visibility", group.visibility.name); put("ownerUsername", group.ownerUsername)
                put("members", JSONArray().apply { group.members.forEach { member -> put(JSONObject().apply { put("username", member.username); put("role", member.role.name) }) } })
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(groupsKey(context), array.toString()).apply()
    }

    fun add(context: Context, group: FynxGroup): Boolean {
        val valid = FynxGroupsBatch1.create(group) ?: return false
        val groups = load(context)
        if (groups.any { it.name.equals(valid.name, ignoreCase = true) }) return false
        save(context, groups + valid)
        return true
    }

    fun updateGroup(context: Context, updated: FynxGroup): Boolean {
        if (FynxGroupsBatch1.validate(updated).isNotEmpty()) return false
        val groups = load(context)
        if (groups.none { it.id == updated.id }) return false
        save(context, groups.map { if (it.id == updated.id) updated else it })
        return true
    }

    fun removeGroup(context: Context, groupId: String): Boolean {
        if (groupId.isBlank()) return false
        val groups = load(context)
        if (groups.none { it.id == groupId }) return false
        save(context, groups.filterNot { it.id == groupId })
        FynxChatStore.clear(context, "group_$groupId")
        return true
    }

    private fun storageKey(value: String): String = value.map { character ->
        when {
            character.isLetterOrDigit() -> character
            else -> '_'
        }
    }.joinToString("").take(80).ifBlank { "account" }
}
