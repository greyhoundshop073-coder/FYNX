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

    @Synchronized
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

    /**
     * Atomically persist a newly-created group and its pending server-sync marker.
     * A force-stop between two separate writes could otherwise leave a local group
     * with no marker, allowing a later empty remote response to hide it.
     */
    @Synchronized
    fun addPendingGroupDurably(
        context: Context,
        group: FynxGroup,
        accountKey: String? = FynxAuthStore.accountStorageKey(context)
    ): Boolean {
        val valid = FynxGroupsBatch1.create(group) ?: return false
        val current = load(context, accountKey)
        if (current.any { it.name.equals(valid.name, ignoreCase = true) } ||
            current.any { it.id == valid.id }
        ) return false

        val array = JSONArray()
        (current + valid).forEach { value ->
            array.put(JSONObject().apply {
                put("id", value.id)
                put("name", value.name)
                put("description", value.description)
                put("visibility", value.visibility.name)
                put("ownerUsername", value.ownerUsername)
                put("groupPhotoMediaId", value.groupPhotoMediaId ?: "")
                put("members", JSONArray().apply {
                    value.members.forEach { member ->
                        put(JSONObject().apply {
                            put("username", member.username)
                            put("role", member.role.name)
                        })
                    }
                })
            })
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pendingKey = pendingSyncKey(context, accountKey)
        val pending = prefs.getStringSet(pendingKey, emptySet()).orEmpty().toMutableSet()
        pending.add(valid.id)
        return prefs.edit()
            .putString(groupsKey(context, accountKey), array.toString())
            .putStringSet(pendingKey, pending)
            .commit()
    }

    @Synchronized
    fun markPendingSync(context: Context, groupId: String, accountKey: String? = FynxAuthStore.accountStorageKey(context)) {
        if (groupId.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pending = prefs.getStringSet(pendingSyncKey(context, accountKey), emptySet()).orEmpty().toMutableSet()
        pending.add(groupId)
        // commit() makes the locally-created group eligible for recovery before the
        // create flow starts its network request or the process can be killed.
        check(prefs.edit().putStringSet(pendingSyncKey(context, accountKey), pending).commit()) {
            "Pending group synchronization state could not be persisted."
        }
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
        check(prefs.edit().putStringSet(key, pending).commit()) {
            "Group synchronization state could not be persisted."
        }
    }

    @Synchronized
    fun save(context: Context, groups: List<FynxGroup>, accountKey: String? = FynxAuthStore.accountStorageKey(context)) {
        val array = JSONArray()
        groups.forEach { group ->
            array.put(JSONObject().apply {
                put("id", group.id); put("name", group.name); put("description", group.description)
                put("visibility", group.visibility.name); put("ownerUsername", group.ownerUsername); put("groupPhotoMediaId", group.groupPhotoMediaId ?: "")
                put("members", JSONArray().apply { group.members.forEach { member -> put(JSONObject().apply { put("username", member.username); put("role", member.role.name) }) } })
            })
        }
        // Do not rely on SharedPreferences.apply()'s asynchronous disk write here:
        // force-stop immediately after group creation must not lose the only local copy.
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(groupsKey(context, accountKey), array.toString()).commit()) {
            "Group data could not be persisted on this device."
        }
    }

    @Synchronized
    fun add(context: Context, group: FynxGroup, accountKey: String? = FynxAuthStore.accountStorageKey(context)): Boolean =
        addPendingGroupDurably(context, group, accountKey)

    @Synchronized
    fun updateGroup(context: Context, updated: FynxGroup, accountKey: String? = FynxAuthStore.accountStorageKey(context)): Boolean {
        if (FynxGroupsBatch1.validate(updated).isNotEmpty()) return false
        val groups = load(context, accountKey)
        if (groups.none { it.id == updated.id }) return false
        save(context, groups.map { if (it.id == updated.id) updated else it }, accountKey)
        return true
    }

    @Synchronized
    fun removeGroup(context: Context, groupId: String, accountKey: String? = FynxAuthStore.accountStorageKey(context)): Boolean {
        if (groupId.isBlank()) return false
        val groups = load(context, accountKey)
        if (groups.none { it.id == groupId }) return false
        save(context, groups.filterNot { it.id == groupId }, accountKey)
        // A removed group must never be recreated by a later reconnect drain.
        markSynced(context, groupId, accountKey)
        // The message store resolves its own account namespace; never clear the new
        // account's cache if a delayed removal completes after a session switch.
        if (FynxAuthStore.accountStorageKey(context) == accountKey) {
            FynxChatStore.clear(context, "group_$groupId")
        }
        return true
    }

    private fun storageKey(value: String): String = value.map { character ->
        when {
            character.isLetterOrDigit() -> character
            else -> '_'
        }
    }.joinToString("").take(80).ifBlank { "account" }
}
