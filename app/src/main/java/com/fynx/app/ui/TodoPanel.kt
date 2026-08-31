package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TodoPanel() {
    var nextId by remember { mutableLongStateOf(1L) }
    var title by remember { mutableStateOf("") }
    var highPriority by remember { mutableStateOf(false) }
    var dueDate by remember { mutableStateOf("") }
    var todos by remember { mutableStateOf(emptyList<FynxTodo>()) }
    var editingTodo by remember { mutableStateOf<FynxTodo?>(null) }

    if (editingTodo != null) {
        EditTodoPanel(todo = editingTodo!!, onCancel = { editingTodo = null }, onSave = { updated ->
            todos = todos.map { if (it.id == updated.id) updated else it }
            editingTodo = null
        })
        return
    }

    Column(Modifier.fillMaxSize()) {
        Text("To-Do", style = MaterialTheme.typography.headlineSmall)
        Text("Keep track of what you need to do.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(title, { title = it }, label = { Text("New task") }, singleLine = true, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                val clean = title.trim()
                if (clean.isNotEmpty()) {
                    todos = todos + FynxTodo(nextId++, clean, priority = if (highPriority) TodoPriority.HIGH else TodoPriority.NORMAL, dueDate = dueDate.trim().ifEmpty { null })
                    title = ""; dueDate = ""
                }
            }) { Text("Add") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(highPriority, { highPriority = it }); Text("High priority")
        }
        OutlinedTextField(dueDate, { dueDate = it }, label = { Text("Due date (optional)") }, placeholder = { Text("e.g. 2026-09-05") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(todos, key = { it.id }) { todo ->
                TodoRow(todo, onToggle = { todos = todos.map { if (it.id == todo.id) it.copy(completed = !it.completed) else it } }, onEdit = { editingTodo = todo }, onDelete = { todos = todos.filterNot { it.id == todo.id } })
            }
        }
    }
}

@Composable
private fun EditTodoPanel(todo: FynxTodo, onCancel: () -> Unit, onSave: (FynxTodo) -> Unit) {
    var title by remember(todo) { mutableStateOf(todo.title) }
    var dueDate by remember(todo) { mutableStateOf(todo.dueDate.orEmpty()) }
    var highPriority by remember(todo) { mutableStateOf(todo.priority == TodoPriority.HIGH) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Text("Edit task", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { onSave(todo.copy(title = title.trim().ifEmpty { todo.title }, dueDate = dueDate.trim().ifEmpty { null }, priority = if (highPriority) TodoPriority.HIGH else TodoPriority.NORMAL)) }) { Text("Save") }
        }
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(title, { title = it }, label = { Text("Task") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(dueDate, { dueDate = it }, label = { Text("Due date (optional)") }, placeholder = { Text("e.g. 2026-09-05") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(highPriority, { highPriority = it }); Text("High priority")
        }
    }
}

@Composable
private fun TodoRow(todo: FynxTodo, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(todo.completed, { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(todo.title, style = MaterialTheme.typography.bodyLarge)
                if (todo.priority == TodoPriority.HIGH) Text("High priority", style = MaterialTheme.typography.labelSmall)
                if (todo.dueDate != null) Text("Due: ${todo.dueDate}", style = MaterialTheme.typography.labelSmall)
                if (todo.completed) Text("Completed", style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = onEdit) { Text("Edit") }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}
