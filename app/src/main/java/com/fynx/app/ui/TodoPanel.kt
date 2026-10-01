package com.fynx.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val todoDateFormat = DateTimeFormatter.ISO_LOCAL_DATE

enum class TodoFilter { ALL, TODAY, ACTIVE, COMPLETED, HIGH_PRIORITY }

@Composable
fun TodoPanel() {
    val context = LocalContext.current
    var nextId by remember { mutableLongStateOf(TodoStore.nextId(context)) }
    var title by remember { mutableStateOf("") }
    var highPriority by remember { mutableStateOf(false) }
    var dueDate by remember { mutableStateOf("") }
    var reminder by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(TodoFilter.TODAY) }
    var todos by remember { mutableStateOf(TodoStore.load(context)) }
    var editingTodo by remember { mutableStateOf<FynxTodo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun persist(value: List<FynxTodo>) {
        todos = value
        TodoStore.save(context, value)
    }

    if (editingTodo != null) {
        EditTodoPanel(editingTodo!!, { editingTodo = null }) { updated ->
            TodoReminderScheduler.cancel(context, updated.id)
            if (!updated.completed && updated.reminder != null) TodoReminderScheduler.schedule(context, updated)
            persist(todos.map { if (it.id == updated.id) updated else it })
            editingTodo = null
        }
        return
    }

    val today = LocalDate.now().format(todoDateFormat)
    val visibleTodos = todos.filter { todo ->
        val matchesSearch = search.isBlank() || todo.title.contains(search.trim(), ignoreCase = true)
        val matchesFilter = when (filter) {
            TodoFilter.ALL -> true
            TodoFilter.TODAY -> todo.dueDate == today || (!todo.completed && todo.dueDate == null)
            TodoFilter.ACTIVE -> !todo.completed
            TodoFilter.COMPLETED -> todo.completed
            TodoFilter.HIGH_PRIORITY -> todo.priority == TodoPriority.HIGH && !todo.completed
        }
        matchesSearch && matchesFilter
    }
    val activeCount = todos.count { !it.completed }
    val completedCount = todos.count { it.completed }
    val overdueCount = todos.count { !it.completed && it.dueDate != null && it.dueDate!! < today }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Daily Planning ✅", style = MaterialTheme.typography.headlineSmall)
        Text("Organize today, stay on top of deadlines and get reminders.", color = FynxDesign.TextSecondary, style = MaterialTheme.typography.bodyMedium)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlannerStat("Active", activeCount.toString(), Modifier.weight(1f))
            PlannerStat("Done", completedCount.toString(), Modifier.weight(1f))
            PlannerStat("Overdue", overdueCount.toString(), Modifier.weight(1f))
        }

        OutlinedTextField(
            search, { search = it }, label = { Text("Search tasks") }, singleLine = true,
            shape = FynxDesign.ControlShape, modifier = Modifier.fillMaxWidth()
        )

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TodoFilter.values().forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { filter = option },
                    label = { Text(option.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }) },
                    shape = FynxDesign.ControlShape
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(), shape = FynxDesign.CardShape,
            colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface),
            border = BorderStroke(1.dp, FynxDesign.Outline)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Add a task", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        title, { title = it }, label = { Text("New task") }, singleLine = true,
                        shape = FynxDesign.ControlShape, modifier = Modifier.weight(1f)
                    )
                    Button(
                        enabled = title.trim().isNotEmpty(),
                        onClick = {
                            val clean = title.trim()
                            if (clean.isNotEmpty()) {
                                val todo = FynxTodo(
                                    nextId++, clean,
                                    priority = if (highPriority) TodoPriority.HIGH else TodoPriority.NORMAL,
                                    dueDate = dueDate.trim().ifEmpty { null },
                                    reminder = reminder.trim().ifEmpty { null }
                                )
                                if (todo.dueDate != null && !TodoStore.isValidDate(todo.dueDate!!)) {
                                    error = "Use a valid due date such as 2026-09-05"
                                } else if (todo.reminder != null && !TodoReminderScheduler.isValidReminder(todo.reminder!!)) {
                                    error = "Use reminder format: 09:00 on 2026-09-05"
                                } else {
                                    persist(todos + todo)
                                    if (!todo.completed && todo.reminder != null) TodoReminderScheduler.schedule(context, todo)
                                    title = ""; dueDate = ""; reminder = ""; highPriority = false; error = null
                                }
                            }
                        }, shape = FynxDesign.ControlShape
                    ) { Text("Add") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(highPriority, { highPriority = it })
                    Text("High priority")
                }
                OutlinedTextField(dueDate, { dueDate = it }, label = { Text("Due date (optional)") }, placeholder = { Text("e.g. 2026-09-05") }, singleLine = true, shape = FynxDesign.ControlShape, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(reminder, { reminder = it }, label = { Text("Reminder (optional)") }, placeholder = { Text("e.g. 09:00 on 2026-09-05") }, singleLine = true, shape = FynxDesign.ControlShape, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }

        if (visibleTodos.isEmpty()) {
            Card(Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, border = BorderStroke(1.dp, FynxDesign.Outline)) {
                Text(if (search.isBlank()) "No tasks in this view. Add one above. 🎯" else "No matching tasks.", Modifier.padding(16.dp), color = FynxDesign.TextSecondary)
            }
        }

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visibleTodos, key = { it.id }) { todo ->
                TodoRow(
                    todo,
                    onToggle = {
                        val updated = todo.copy(completed = !todo.completed)
                        if (updated.completed) TodoReminderScheduler.cancel(context, todo.id)
                        else if (updated.reminder != null) TodoReminderScheduler.schedule(context, updated)
                        persist(todos.map { if (it.id == todo.id) updated else it })
                    },
                    onEdit = { editingTodo = todo },
                    onDelete = { TodoReminderScheduler.cancel(context, todo.id); persist(todos.filterNot { it.id == todo.id }) }
                )
            }
        }
    }
}

@Composable
private fun PlannerStat(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = FynxDesign.CardShape, border = BorderStroke(1.dp, FynxDesign.Outline)) {
        Column(Modifier.padding(10.dp)) {
            Text(label, color = FynxDesign.TextSecondary, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun EditTodoPanel(todo: FynxTodo, onCancel: () -> Unit, onSave: (FynxTodo) -> Unit) {
    var title by remember(todo) { mutableStateOf(todo.title) }
    var dueDate by remember(todo) { mutableStateOf(todo.dueDate.orEmpty()) }
    var reminder by remember(todo) { mutableStateOf(todo.reminder.orEmpty()) }
    var highPriority by remember(todo) { mutableStateOf(todo.priority == TodoPriority.HIGH) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel, shape = FynxDesign.ControlShape) { Text("Cancel") }
            Text("Edit task", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = {
                val d = dueDate.trim().ifEmpty { null }
                val r = reminder.trim().ifEmpty { null }
                if (d != null && !TodoStore.isValidDate(d)) return@TextButton
                if (r != null && !TodoReminderScheduler.isValidReminder(r)) return@TextButton
                onSave(todo.copy(title = title.trim().ifEmpty { todo.title }, dueDate = d, reminder = r, priority = if (highPriority) TodoPriority.HIGH else TodoPriority.NORMAL))
            }, shape = FynxDesign.ControlShape) { Text("Save") }
        }
        HorizontalDivider(color = FynxDesign.Outline)
        OutlinedTextField(title, { title = it }, label = { Text("Task") }, singleLine = true, shape = FynxDesign.ControlShape, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(dueDate, { dueDate = it }, label = { Text("Due date (optional)") }, placeholder = { Text("e.g. 2026-09-05") }, singleLine = true, shape = FynxDesign.ControlShape, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(reminder, { reminder = it }, label = { Text("Reminder (optional)") }, placeholder = { Text("e.g. 09:00 on 2026-09-05") }, singleLine = true, shape = FynxDesign.ControlShape, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(highPriority, { highPriority = it }); Text("High priority") }
    }
}

@Composable
private fun TodoRow(todo: FynxTodo, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface), border = BorderStroke(1.dp, FynxDesign.Outline)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(todo.completed, { onToggle() })
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(todo.title, style = MaterialTheme.typography.bodyLarge)
                if (todo.priority == TodoPriority.HIGH) Text("High priority", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                if (todo.dueDate != null) Text("Due: ${todo.dueDate}", color = FynxDesign.TextSecondary, style = MaterialTheme.typography.labelSmall)
                if (todo.reminder != null) Text("Reminder: ${todo.reminder}", color = FynxDesign.TextSecondary, style = MaterialTheme.typography.labelSmall)
                if (todo.completed) Text("Completed", color = FynxDesign.TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = onEdit, shape = FynxDesign.ControlShape) { Text("Edit") }
            TextButton(onClick = onDelete, shape = FynxDesign.ControlShape) { Text("Delete") }
        }
    }
}
