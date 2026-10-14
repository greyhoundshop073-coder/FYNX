package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private data class SavingsGoal(val id: Long, val name: String, val target: Double, val saved: Double, val targetDate: String)

private fun loadSavingsGoals(context: Context): List<SavingsGoal> = runCatching {
    val array = JSONArray(FynxMoneyStore.loadToolRecords(context, "savings_goals"))
    buildList {
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val name = o.optString("name").trim()
            val target = o.optDouble("target", Double.NaN)
            val saved = o.optDouble("saved", Double.NaN)
            if (name.isNotBlank() && target.isFinite() && target > 0 && saved.isFinite() && saved >= 0) add(SavingsGoal(o.optLong("id", i.toLong()), name, target, saved.coerceAtMost(target), o.optString("targetDate")))
        }
    }
}.getOrDefault(emptyList())

private fun saveSavingsGoals(context: Context, goals: List<SavingsGoal>) {
    val array = JSONArray()
    goals.forEach { item -> array.put(JSONObject().put("id", item.id).put("name", item.name).put("target", item.target).put("saved", item.saved).put("targetDate", item.targetDate)) }
    FynxMoneyStore.saveToolRecords(context, "savings_goals", array.toString())
}

@Composable
fun SavingsGoalsPanel() {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var targetText by remember { mutableStateOf("") }
    var savedText by remember { mutableStateOf("") }
    var targetDate by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }
    var goals by remember(context) { mutableStateOf(loadSavingsGoals(context)) }
    var nextId by remember(context) { mutableLongStateOf((goals.maxOfOrNull { it.id } ?: 0L) + 1L) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Savings Goals 🎯", style = MaterialTheme.typography.headlineSmall)
        Text("Set targets and track your savings progress locally.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(name, { name = it }, label = { Text("Goal name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(targetText, { targetText = it }, label = { Text("Target amount") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(savedText, { savedText = it }, label = { Text("Currently saved") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(targetDate, { targetDate = it }, label = { Text("Target date") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        validationError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Button(onClick = {
            val target = targetText.toDoubleOrNull()
            val saved = savedText.toDoubleOrNull() ?: 0.0
            when {
                name.isBlank() -> validationError = "Enter a goal name."
                target == null || !target.isFinite() || target <= 0 -> validationError = "Enter a valid target greater than zero."
                !saved.isFinite() || saved < 0 -> validationError = "Enter a valid saved amount."
                !FynxMoneyStore.isValidLocalDate(targetDate) -> validationError = "Use a valid date in YYYY-MM-DD format."
                else -> {
                goals = goals + SavingsGoal(nextId++, name.trim(), target, saved.coerceAtMost(target), targetDate.trim())
                saveSavingsGoals(context, goals)
                name = ""; targetText = ""; savedText = ""; targetDate = ""; validationError = null
                }
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
