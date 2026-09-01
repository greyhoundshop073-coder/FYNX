package com.fynx.app.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class FynxCallRecord(
    val id: String,
    val name: String,
    val type: String,
    val time: String,
    val missed: Boolean = false
)

object FynxCallsStore {
    private const val PREFS = "fynx_calls_store"
    private const val CALLS_KEY = "calls"
    private const val MAX_HISTORY = 50

    fun load(context: Context): List<FynxCallRecord> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(CALLS_KEY, null) ?: return defaultCalls()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val name = item.optString("name")
                    val type = item.optString("type")
                    if (name.isNotBlank() && type.isNotBlank()) {
                        add(FynxCallRecord(
                            id = item.optString("id").ifBlank { "call-$index" },
                            name = name,
                            type = type,
                            time = item.optString("time"),
                            missed = item.optBoolean("missed", false)
                        ))
                    }
                }
            }.ifEmpty { defaultCalls() }
        }.getOrElse { defaultCalls() }
    }

    fun save(context: Context, calls: List<FynxCallRecord>) {
        val array = JSONArray()
        calls.take(MAX_HISTORY).forEach { call ->
            array.put(JSONObject().apply {
                put("id", call.id)
                put("name", call.name)
                put("type", call.type)
                put("time", call.time)
                put("missed", call.missed)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(CALLS_KEY, array.toString()).apply()
    }

    fun add(context: Context, call: FynxCallRecord) {
        save(context, listOf(call) + load(context))
    }

    private fun defaultCalls(): List<FynxCallRecord> = listOf(
        FynxCallRecord("call-maria", "Maria", "Voice call", "Today, 10:32"),
        FynxCallRecord("call-alex", "Alex", "Video call", "Yesterday, 18:41", missed = true),
        FynxCallRecord("call-david", "David", "Voice call", "Monday, 09:18")
    )
}
