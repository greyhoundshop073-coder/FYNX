package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToLong

private data class MoneyEntry(val id: Long, val title: String, val amount: Double, val type: String)

@Composable
fun MoneyToolsPanel() {
    var balance by remember { mutableStateOf(0.0) }
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Income") }
    var entries by remember { mutableStateOf(emptyList<MoneyEntry>()) }
    var nextId by remember { mutableLongStateOf(1L) }

    val income = entries.filter { it.type == "Income" }.sumOf { it.amount }
    val expenses = entries.filter { it.type == "Expense" }.sumOf { it.amount }
    val net = income - expenses

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Money Tools 💰", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Money overview", style = MaterialTheme.typography.titleMedium)
                Text("Balance: ${formatMoney(balance + net)}", style = MaterialTheme.typography.headlineMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Income ${formatMoney(income)}")
                    Text("Expenses ${formatMoney(expenses)}")
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(title, { title = it }, label = { Text("Description") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(amount, { amount = it }, label = { Text("Amount") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = type == "Income", onClick = { type = "Income" }, label = { Text("Income") })
            FilterChip(selected = type == "Expense", onClick = { type = "Expense" }, label = { Text("Expense") })
            Spacer(Modifier.weight(1f))
            Button(onClick = {
                val value = amount.toDoubleOrNull()
                if (!title.isBlank() && value != null && value > 0) {
                    entries = entries + MoneyEntry(nextId++, title.trim(), value, type)
                    title = ""
                    amount = ""
                }
            }) { Text("Add") }
        }
        Spacer(Modifier.height(12.dp))
        Text("Transactions", style = MaterialTheme.typography.titleMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries.reversed(), key = { it.id }) { entry ->
                ListItem(
                    headlineContent = { Text(entry.title) },
                    supportingContent = { Text(entry.type) },
                    trailingContent = { Text("${if (entry.type == "Expense") "-" else "+"}${formatMoney(entry.amount)}") }
                )
            }
        }
    }
}

private fun formatMoney(value: Double): String = "${(value * 100).roundToLong() / 100.0}"
