package com.fynx.app.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object TodoStore {
    private const val PREFS = "fynx_todo_store"
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private fun accountKey(context: Context): String =
        FynxAuthStore.accountStorageKey(context)?.let(::storageKey) ?: "signed_out"

    private fun todosKey(context: Context) = "todos_${accountKey(context)}"
    private fun nextIdKey(context: Context) = "next_id_${accountKey(context)}"

    fun load(context: Context): List<FynxTodo> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(todosKey(context), "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(FynxTodo(
                        id = o.getLong("id"),
                        title = o.getString("title"),
                        completed = o.optBoolean("completed", false),
                        priority = if (o.optString("priority") == TodoPriority.HIGH.name) TodoPriority.HIGH else TodoPriority.NORMAL,
                        dueDate = o.optString("dueDate").takeIf { it.isNotBlank() },
                        reminder = o.optString("reminder").takeIf { it.isNotBlank() }
                    ))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, todos: List<FynxTodo>) {
        val array = JSONArray()
        todos.forEach { todo ->
            array.put(JSONObject().apply {
                put("id", todo.id)
                put("title", todo.title)
                put("completed", todo.completed)
                put("priority", todo.priority.name)
                put("dueDate", todo.dueDate ?: "")
                put("reminder", todo.reminder ?: "")
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(todosKey(context), array.toString())
            .putLong(nextIdKey(context), (todos.maxOfOrNull { it.id } ?: 0L) + 1L)
            .apply()
    }

    fun nextId(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = nextIdKey(context)
        val stored = prefs.getLong(key, 1L)
        val safe = maxOf(stored, load(context).maxOfOrNull { it.id }?.plus(1L) ?: 1L)
        if (safe != stored) prefs.edit().putLong(key, safe).apply()
        return safe
    }

    fun isValidDate(value: String): Boolean = runCatching { LocalDate.parse(value, dateFormatter); true }.getOrDefault(false)

    private fun storageKey(value: String): String = value.map { character ->
        when {
            character.isLetterOrDigit() -> character
            else -> '_'
        }
    }.joinToString("").take(80).ifBlank { "account" }
}
