package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

private data class RecurringPayment(val id: Long, val name: String, val amount: Double, val nextDate: String, val frequency: String)

@Composable
fun SubscriptionsPanel() {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var nextDate by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf("Monthly") }
    var payments by remember { mutableStateOf(emptyList<RecurringPayment>()) }
    var nextId by remember { mutableLongStateOf(1L) }

    val monthlyEstimate = payments.sumOf { payment -> when (payment.frequency) { "Daily" -> payment.amount * 30.0; "Weekly" -> payment.amount * 4.33; "Yearly" -> payment.amount / 12.0; else -> payment.amount } }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Subscriptions & Recurring Payments 🔄", style = MaterialTheme.typography.headlineSmall)
        Text("Track recurring costs without making payments automatically.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
            Text("Estimated monthly recurring cost", style = MaterialTheme.typography.titleMedium)
            Text(money(monthlyEstimate), style = MaterialTheme.typography.headlineMedium)
            Text("${payments.size} active item(s)")
        } }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(name, { name = it }, label = { Text("Subscription / payment name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(amount, { amount = it }, label = { Text("Amount") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(nextDate, { nextDate = it }, label = { Text("Next payment date") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Daily", "Weekly", "Monthly", "Yearly").forEach { option -> FilterChip(selected = frequency == option, onClick = { frequency = option }, label = { Text(option) }) }
        }
        Spacer(Modifier.height(6.dp))
        Button(onClick = {
            val value = amount.toDoubleOrNull()
            if (name.isNotBlank() && value != null && value > 0 && nextDate.isNotBlank()) {
                payments = payments + RecurringPayment(nextId++, name.trim(), value, nextDate.trim(), frequency)
                name = ""; amount = ""; nextDate = ""
            }
        }) { Text("Add Recurring Payment") }
        Spacer(Modifier.height(10.dp))
        Text("Active Recurring Payments", style = MaterialTheme.typography.titleMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(payments, key = { it.id }) { payment ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(payment.name, style = MaterialTheme.typography.titleMedium); Text(money(payment.amount)) }
                    Text("${payment.frequency} • Next: ${payment.nextDate}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } }
            }
        }
    }
}

private fun money(value: Double): String = String.format(Locale.US, "%.2f", value)
