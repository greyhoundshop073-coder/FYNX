package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

private data class ReceiptRecord(val id: Long, val title: String, val category: String, val amount: Double, val date: String, val note: String)

@Composable
fun ReceiptsExpensePanel() {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var nextId by remember { mutableLongStateOf(1L) }
    var records by remember { mutableStateOf(emptyList<ReceiptRecord>()) }

    val filtered = records.filter { record ->
        search.isBlank() || listOf(record.title, record.category, record.date, record.note).any { it.contains(search, ignoreCase = true) }
    }.reversed()
    val total = records.sumOf { it.amount }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Receipts & Expense Records 🧾", style = MaterialTheme.typography.headlineSmall)
        Text("Keep a simple record of purchases and expenses.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
            Text("Recorded expenses", style = MaterialTheme.typography.titleMedium)
            Text(money(total), style = MaterialTheme.typography.headlineMedium)
            Text("${records.size} record(s)")
        } }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(title, { title = it }, label = { Text("Receipt / expense name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(category, { category = it }, label = { Text("Category") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(amount, { amount = it }, label = { Text("Amount") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(date, { date = it }, label = { Text("Date") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(note, { note = it }, label = { Text("Notes (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            val value = amount.toDoubleOrNull()
            if (title.isNotBlank() && category.isNotBlank() && value != null && value > 0 && date.isNotBlank()) {
                records = records + ReceiptRecord(nextId++, title.trim(), category.trim(), value, date.trim(), note.trim())
                title = ""; category = ""; amount = ""; date = ""; note = ""
            }
        }) { Text("Save Record") }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(search, { search = it }, label = { Text("Search records") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { record ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(record.title, style = MaterialTheme.typography.titleMedium); Text(money(record.amount)) }
                    Text("${record.category} • ${record.date}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (record.note.isNotBlank()) Text(record.note)
                } }
            }
        }
    }
}

private fun money(value: Double): String = String.format(Locale.US, "%.2f", value)
