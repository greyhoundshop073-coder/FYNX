package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

private data class BillItem(val id: Long, val name: String, val amount: Double, val dueDate: String, val paid: Boolean)

@Composable
fun BillsPaymentPanel() {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf("") }
    var bills by remember { mutableStateOf(emptyList<BillItem>()) }
    var nextId by remember { mutableLongStateOf(1L) }

    val outstanding = bills.filterNot { it.paid }.sumOf { it.amount }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Bills & Payment Reminders 🧾", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Outstanding", style = MaterialTheme.typography.titleMedium)
                Text("${money(outstanding)}", style = MaterialTheme.typography.headlineMedium)
                Text("${bills.count { !it.paid }} unpaid bill(s)")
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(name, { name = it }, label = { Text("Bill name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(amount, { amount = it }, label = { Text("Amount") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(dueDate, { dueDate = it }, label = { Text("Due date (e.g. 15 Sep 2026)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            val value = amount.toDoubleOrNull()
            if (name.isNotBlank() && value != null && value > 0 && dueDate.isNotBlank()) {
                bills = bills + BillItem(nextId++, name.trim(), value, dueDate.trim(), false)
                name = ""; amount = ""; dueDate = ""
            }
        }) { Text("Add Bill") }
        Spacer(Modifier.height(12.dp))
        Text("Your Bills", style = MaterialTheme.typography.titleMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(bills, key = { it.id }) { bill ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(bill.name, style = MaterialTheme.typography.titleMedium)
                            Text("Due: ${bill.dueDate}")
                            Text(if (bill.paid) "Paid 🟢" else "Unpaid 🔔")
                        }
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                            Text(money(bill.amount))
                            if (!bill.paid) Button(onClick = { bills = bills.map { if (it.id == bill.id) it.copy(paid = true) else it } }) { Text("Mark paid") }
                        }
                    }
                }
            }
        }
    }
}

private fun money(value: Double): String = String.format(Locale.US, "%.2f", value)
