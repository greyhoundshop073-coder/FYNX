package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private data class BillItem(val id: Long, val name: String, val amount: Double, val dueDate: String, val recurring: Boolean, val paid: Boolean)

private fun loadBills(context: Context): List<BillItem> = runCatching {
    val array = JSONArray(FynxMoneyStore.loadToolRecords(context, "bills"))
    buildList {
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val name = o.optString("name").trim()
            val amount = o.optDouble("amount", Double.NaN)
            if (name.isNotBlank() && amount.isFinite() && amount > 0) add(BillItem(o.optLong("id", i.toLong()), name, amount, o.optString("dueDate"), o.optBoolean("recurring"), o.optBoolean("paid")))
        }
    }
}.getOrDefault(emptyList())

private fun saveBills(context: Context, bills: List<BillItem>) {
    val array = JSONArray()
    bills.forEach { bill -> array.put(JSONObject().put("id", bill.id).put("name", bill.name).put("amount", bill.amount).put("dueDate", bill.dueDate).put("recurring", bill.recurring).put("paid", bill.paid)) }
    FynxMoneyStore.saveToolRecords(context, "bills", array.toString())
}

@Composable
fun BillsPaymentPanel() {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf("") }
    var recurring by remember { mutableStateOf(false) }
    var bills by remember(context) { mutableStateOf(loadBills(context)) }
    var nextId by remember(context) { mutableLongStateOf((bills.maxOfOrNull { it.id } ?: 0L) + 1L) }

    val outstanding = bills.filterNot { it.paid }.sumOf { it.amount }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Bills & Payment Reminders 🧾", style = MaterialTheme.typography.headlineSmall)
        Text("Track bills and due dates. FYNX does not make payments automatically.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Outstanding", style = MaterialTheme.typography.titleMedium)
                Text(money(outstanding), style = MaterialTheme.typography.headlineMedium)
                Text("${bills.count { !it.paid }} unpaid bill(s)")
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(name, { name = it }, label = { Text("Bill name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(amount, { amount = it }, label = { Text("Amount") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(dueDate, { dueDate = it }, label = { Text("Due date") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = recurring, onCheckedChange = { recurring = it })
            Text("Recurring bill")
        }
        Button(onClick = {
            val value = amount.toDoubleOrNull()
            if (name.isNotBlank() && value != null && value > 0 && dueDate.isNotBlank()) {
                bills = bills + BillItem(nextId++, name.trim(), value, dueDate.trim(), recurring, false)
                saveBills(context, bills)
                name = ""; amount = ""; dueDate = ""; recurring = false
            }
        }) { Text("Add Bill") }
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(bills, key = { it.id }) { bill ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(bill.name, style = MaterialTheme.typography.titleMedium)
                            Text(money(bill.amount))
                        }
                        Text("Due: ${bill.dueDate}${if (bill.recurring) " • Recurring" else ""}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (bill.paid) Text("🟢 Paid", color = MaterialTheme.colorScheme.primary)
                        else Button(onClick = { bills = bills.map { if (it.id == bill.id) it.copy(paid = true) else it }; saveBills(context, bills) }) { Text("Mark as Paid") }
                    }
                }
            }
        }
    }
}

private fun money(value: Double): String = String.format(Locale.US, "%.2f", value)
