package com.fynx.app.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class FynxCallRecord(
    val id: String,
    val name: String,
    val type: String,
    val time: String,
    val missed: Boolean = false,
    val status: String = "Completed"
)

object FynxCallsStore {
    private const val PREFS = "fynx_calls_store"
    private const val CALLS_KEY = "calls"
    private const val MAX_HISTORY = 50

    fun load(context: Context): List<FynxCallRecord> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(CALLS_KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id").trim()
                    val name = item.optString("name").trim()
                    val type = item.optString("type").trim()
                    if (id.isNotBlank() && name.isNotBlank() && type in setOf("Voice call", "Video call")) {
                        val missed = item.optBoolean("missed", false)
                        add(FynxCallRecord(
                            id = id,
                            name = name,
                            type = type,
                            time = item.optString("time").trim().ifBlank { "Recent" },
                            missed = missed,
                            status = item.optString("status").trim().ifBlank { if (missed) "Missed" else "Completed" }
                        ))
                    }
                }
            }.distinctBy { it.id }.take(MAX_HISTORY)
        }.getOrElse { emptyList() }
    }

    fun save(context: Context, calls: List<FynxCallRecord>) {
        val array = JSONArray()
        calls.asSequence()
            .filter { it.id.isNotBlank() && it.name.isNotBlank() && it.type in setOf("Voice call", "Video call") }
            .distinctBy { it.id }
            .take(MAX_HISTORY)
            .forEach { call ->
                array.put(JSONObject().apply {
                    put("id", call.id)
                    put("name", call.name)
                    put("type", call.type)
                    put("time", call.time)
                    put("missed", call.missed)
                    put("status", call.status)
                })
            }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(CALLS_KEY, array.toString()).apply()
    }

    fun add(context: Context, call: FynxCallRecord) {
        save(context, listOf(call) + load(context))
    }

    fun updateStatus(context: Context, id: String, status: String, missed: Boolean? = null) {
        val safeStatus = status.trim().take(80).ifBlank { "Completed" }
        val updated = load(context).map { call ->
            if (call.id == id) call.copy(status = safeStatus, missed = missed ?: call.missed) else call
        }
        save(context, updated)
    }
}
