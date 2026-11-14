package com.fynx.app.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Local group persistence foundation. Production sync can replace this store without changing the UI contract. */
object FynxGroupsStore {
    private const val PREFS = "fynx_groups_store"
    private const val GROUPS_KEY_PREFIX = "groups_"
    private const val PENDING_SYNC_PREFIX = "pending_sync_"

    private fun groupsKey(context: Context, accountKey: String? = FynxAuthStore.accountStorageKey(context)): String =
        GROUPS_KEY_PREFIX + (accountKey?.let(::storageKey) ?: "signed_out")

    fun load(context: Context, accountKey: String? = FynxAuthStore.accountStorageKey(context)): List<FynxGroup> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(groupsKey(context, accountKey), null) ?: return emptyList()
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
                    add(FynxGroup(item.optString("id"), item.optString("name"), item.optString("description"), runCatching { FynxGroupVisibility.valueOf(item.optString("visibility")) }.getOrDefault(FynxGroupVisibility.PRIVATE), item.optString("ownerUsername"), parsedMembers, item.optString("groupPhotoMediaId").ifBlank { null }))
                }
            }
                .filter { it.id.isNotBlank() && FynxGroupsBatch1.validate(it).isEmpty() }
                .distinctBy { it.id }
        }.getOrElse { emptyList() }
    }

    private fun pendingSyncKey(context: Context, accountKey: String? = FynxAuthStore.accountStorageKey(context)): String = PENDING_SYNC_PREFIX + groupsKey(context, accountKey)

    @Synchronized
    fun markPendingSync(context: Context, groupId: String, accountKey: String? = FynxAuthStore.accountStorageKey(context)) {
        if (groupId.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pending = prefs.getStringSet(pendingSyncKey(context, accountKey), emptySet()).orEmpty().toMutableSet()
        pending.add(groupId)
        prefs.edit().putStringSet(pendingSyncKey(context), pending).apply()
    }

    @Synchronized
    fun pendingSyncGroups(context: Context, accountKey: String? = FynxAuthStore.accountStorageKey(context)): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(pendingSyncKey(context, accountKey), emptySet()).orEmpty().toSet()

    @Synchronized
    fun markSynced(context: Context, groupId: String, accountKey: String? = FynxAuthStore.accountStorageKey(context)) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = pendingSyncKey(context, accountKey)
        val pending = prefs.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        pending.remove(groupId)
        prefs.edit().putStringSet(key, pending).apply()
    }

    fun save(context: Context, groups: List<FynxGroup>, accountKey: String? = FynxAuthStore.accountStorageKey(context)) {
        val array = JSONArray()
        groups.forEach { group ->
            array.put(JSONObject().apply {
                put("id", group.id); put("name", group.name); put("description", group.description)
                put("visibility", group.visibility.name); put("ownerUsername", group.ownerUsername); put("groupPhotoMediaId", group.groupPhotoMediaId ?: "")
                put("members", JSONArray().apply { group.members.forEach { member -> put(JSONObject().apply { put("username", member.username); put("role", member.role.name) }) } })
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(groupsKey(context, accountKey), array.toString()).apply()
    }

    fun add(context: Context, group: FynxGroup, accountKey: String? = FynxAuthStore.accountStorageKey(context)): Boolean {
        val valid = FynxGroupsBatch1.create(group) ?: return false
        val groups = load(context, accountKey)
        if (groups.any { it.name.equals(valid.name, ignoreCase = true) }) return false
        save(context, groups + valid, accountKey)
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
