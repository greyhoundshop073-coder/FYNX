package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

private data class SavingsGoal(val id: Long, val name: String, val target: Double, val saved: Double, val targetDate: String)

@Composable
fun SavingsGoalsPanel() {
    var name by remember { mutableStateOf("") }
    var targetText by remember { mutableStateOf("") }
    var savedText by remember { mutableStateOf("") }
    var targetDate by remember { mutableStateOf("") }
    var nextId by remember { mutableLongStateOf(1L) }
    var goals by remember { mutableStateOf(emptyList<SavingsGoal>()) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Savings Goals 🎯", style = MaterialTheme.typography.headlineSmall)
        Text("Set targets and track your savings progress locally.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(name, { name = it }, label = { Text("Goal name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(targetText, { targetText = it }, label = { Text("Target amount") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(savedText, { savedText = it }, label = { Text("Currently saved") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(targetDate, { targetDate = it }, label = { Text("Target date") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            val target = targetText.toDoubleOrNull()
            val saved = savedText.toDoubleOrNull() ?: 0.0
            if (name.isNotBlank() && target != null && target > 0 && saved >= 0 && targetDate.isNotBlank()) {
                goals = goals + SavingsGoal(nextId++, name.trim(), target, saved.coerceAtMost(target), targetDate.trim())
                name = ""; targetText = ""; savedText = ""; targetDate = ""
            }
        }) { Text("Add Savings Goal") }
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(goals, key = { it.id }) { goal ->
                val progress = (goal.saved / goal.target).coerceIn(0.0, 1.0)
                val remaining = (goal.target - goal.saved).coerceAtLeast(0.0)
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(goal.name, style = MaterialTheme.typography.titleMedium)
                        Text("${money(goal.saved)} / ${money(goal.target)}")
                    }
                    LinearProgressIndicator(progress = { progress.toFloat() }, modifier = Modifier.fillMaxWidth())
                    Text("${money(progress * 100.0)}% • Target: ${goal.targetDate}")
                    if (progress >= 1.0) Text("🟢 Goal completed", color = MaterialTheme.colorScheme.primary)
                    else Text("${money(remaining)} remaining", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } }
            }
        }
    }
}

private fun money(value: Double): String = String.format(Locale.US, "%.2f", value)
