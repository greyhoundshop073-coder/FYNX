package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

private data class FynxTransaction(val id: Long, val title: String, val amount: Double, val type: String, val date: String, val note: String)

@Composable
fun TransactionHistoryPanel() {
    var title by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Expense") }
    var date by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var nextId by remember { mutableLongStateOf(1L) }
    var transactions by remember { mutableStateOf(emptyList<FynxTransaction>()) }

    val income = transactions.filter { it.type == "Income" }.sumOf { it.amount }
    val expenses = transactions.filter { it.type == "Expense" }.sumOf { it.amount }
    val net = income - expenses

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Transaction History 💸", style = MaterialTheme.typography.headlineSmall)
        Text("Record income and expenses locally for a clear money history.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))

        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Summary", style = MaterialTheme.typography.titleMedium)
            Text("Income: ${money(income)}")
            Text("Expenses: ${money(expenses)}")
            Text("Net: ${money(net)}", color = if (net >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        } }
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(title, { title = it }, label = { Text("Transaction name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(amountText, { amountText = it }, label = { Text("Amount") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Income", "Expense").forEach { option -> FilterChip(selected = type == option, onClick = { type = option }, label = { Text(option) }) }
        }
        OutlinedTextField(date, { date = it }, label = { Text("Date") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            val amount = amountText.toDoubleOrNull()
            if (title.isNotBlank() && amount != null && amount > 0 && date.isNotBlank()) {
                transactions = transactions + FynxTransaction(nextId++, title.trim(), amount, type, date.trim(), note.trim())
                title = ""
                amountText = ""
                date = ""
                note = ""
            }
        }) { Text("Save Transaction") }
        Spacer(Modifier.height(10.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(transactions.reversed(), key = { it.id }) { transaction ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(transaction.title, style = MaterialTheme.typography.titleMedium)
                        Text(if (transaction.type == "Income") "+${money(transaction.amount)}" else "-${money(transaction.amount)}", color = if (transaction.type == "Income") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    }
                    Text("${transaction.type} • ${transaction.date}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (transaction.note.isNotBlank()) Text(transaction.note)
                } }
            }
        }
    }
}

private fun money(value: Double): String = String.format(Locale.US, "%.2f", value)
