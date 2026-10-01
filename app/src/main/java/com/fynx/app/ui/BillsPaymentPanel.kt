package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

private data class BillItem(val id: Long, val name: String, val amount: Double, val dueDate: String, val recurring: Boolean, val paid: Boolean)

@Composable
fun BillsPaymentPanel() {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf("") }
    var recurring by remember { mutableStateOf(false) }
    var bills by remember { mutableStateOf(emptyList<BillItem>()) }
    var nextId by remember { mutableLongStateOf(1L) }

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
                        else Button(onClick = { bills = bills.map { if (it.id == bill.id) it.copy(paid = true) else it } }) { Text("Mark as Paid") }
                    }
                }
            }
        }
    }
}

private fun money(value: Double): String = String.format(Locale.US, "%.2f", value)
