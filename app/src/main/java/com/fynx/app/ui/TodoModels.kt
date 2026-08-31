package com.fynx.app.ui

enum class TodoPriority { NORMAL, HIGH }

data class FynxTodo(
    val id: Long,
    val title: String,
    val completed: Boolean = false,
    val priority: TodoPriority = TodoPriority.NORMAL,
    val dueDate: String? = null
)
