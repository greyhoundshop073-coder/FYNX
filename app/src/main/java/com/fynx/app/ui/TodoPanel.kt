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
    var todos by remember { mutableStateOf(emptyList<FynxTodo>()) }

    Column(Modifier.fillMaxSize()) {
        Text("To-Do", style = MaterialTheme.typography.headlineSmall)
        Text("Keep track of what you need to do.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("New task") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                val clean = title.trim()
                if (clean.isNotEmpty()) {
                    todos = todos + FynxTodo(nextId++, clean, priority = if (highPriority) TodoPriority.HIGH else TodoPriority.NORMAL)
                    title = ""
                }
            }) { Text("Add") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = highPriority, onCheckedChange = { highPriority = it })
            Text("High priority")
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(todos, key = { it.id }) { todo ->
                TodoRow(
                    todo = todo,
                    onToggle = { todos = todos.map { if (it.id == todo.id) it.copy(completed = !it.completed) else it } },
                    onDelete = { todos = todos.filterNot { it.id == todo.id } }
                )
            }
        }
    }
}

@Composable
private fun TodoRow(todo: FynxTodo, onToggle: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = todo.completed, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(todo.title, style = MaterialTheme.typography.bodyLarge)
                if (todo.priority == TodoPriority.HIGH) Text("High priority", style = MaterialTheme.typography.labelSmall)
                if (todo.completed) Text("Completed", style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}
