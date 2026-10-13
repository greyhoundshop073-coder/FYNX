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

private data class ReceiptRecord(val id: Long, val title: String, val category: String, val amount: Double, val date: String, val note: String)

private fun loadReceiptRecords(context: Context): List<ReceiptRecord> = runCatching {
    val array = JSONArray(FynxMoneyStore.loadToolRecords(context, "receipts"))
    buildList {
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val title = o.optString("title").trim()
            val category = o.optString("category").trim()
            val amount = o.optDouble("amount", Double.NaN)
            if (title.isNotBlank() && category.isNotBlank() && amount.isFinite() && amount > 0) add(ReceiptRecord(o.optLong("id", i.toLong()), title, category, amount, o.optString("date"), o.optString("note")))
        }
    }
}.getOrDefault(emptyList())

private fun saveReceiptRecords(context: Context, records: List<ReceiptRecord>) {
    val array = JSONArray()
    records.forEach { item -> array.put(JSONObject().put("id", item.id).put("title", item.title).put("category", item.category).put("amount", item.amount).put("date", item.date).put("note", item.note)) }
    FynxMoneyStore.saveToolRecords(context, "receipts", array.toString())
}

@Composable
fun ReceiptsExpensePanel() {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }
    var records by remember(context) { mutableStateOf(loadReceiptRecords(context)) }
    var nextId by remember(context) { mutableLongStateOf((records.maxOfOrNull { it.id } ?: 0L) + 1L) }

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
        validationError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Button(onClick = {
            val value = amount.toDoubleOrNull()
            when {
                title.isBlank() || category.isBlank() -> validationError = "Enter a receipt name and category."
                value == null || !value.isFinite() || value <= 0 -> validationError = "Enter a valid amount greater than zero."
                !FynxMoneyStore.isValidLocalDate(date) -> validationError = "Use a valid date in YYYY-MM-DD format."
                else -> {
                records = records + ReceiptRecord(nextId++, title.trim(), category.trim(), value, date.trim(), note.trim())
                saveReceiptRecords(context, records)
                title = ""; category = ""; amount = ""; date = ""; note = ""; validationError = null
                }
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
