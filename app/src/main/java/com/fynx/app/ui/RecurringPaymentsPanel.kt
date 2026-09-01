package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

private data class RecurringPayment(val id: Long, val name: String, val amount: Double, val frequency: String, val nextDate: String, val active: Boolean)

@Composable
fun RecurringPaymentsPanel() {
    var name by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf("Monthly") }
    var nextDate by remember { mutableStateOf("") }
    var nextId by remember { mutableLongStateOf(1L) }
    var payments by remember { mutableStateOf(emptyList<RecurringPayment>()) }

    val activePayments = payments.filter { it.active }
    val monthlyEstimate = activePayments.sumOf { payment ->
        when (payment.frequency) {
            "Weekly" -> payment.amount * 52.0 / 12.0
            "Yearly" -> payment.amount / 12.0
            else -> payment.amount
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Recurring Payments & Subscriptions 🔄", style = MaterialTheme.typography.headlineSmall)
        Text("Track subscriptions and recurring costs. FYNX does not charge or cancel anything automatically.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Estimated monthly recurring cost", style = MaterialTheme.typography.titleMedium)
                Text(formatMoney(monthlyEstimate), style = MaterialTheme.typography.headlineMedium)
            }
        }
        OutlinedTextField(name, { name = it }, label = { Text("Subscription name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(amountText, { amountText = it }, label = { Text("Amount") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(nextDate, { nextDate = it }, label = { Text("Next payment date") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Weekly", "Monthly", "Yearly").forEach { option ->
                FilterChip(selected = frequency == option, onClick = { frequency = option }, label = { Text(option) })
            }
        }
        Button(onClick = {
            val amount = amountText.toDoubleOrNull()
            if (name.isNotBlank() && amount != null && amount > 0 && nextDate.isNotBlank()) {
                payments = payments + RecurringPayment(nextId++, name.trim(), amount, frequency, nextDate.trim(), true)
                name = ""; amountText = ""; nextDate = ""
            }
        }) { Text("Add Subscription") }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(payments, key = { it.id }) { payment ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(payment.name, style = MaterialTheme.typography.titleMedium)
                            Text(formatMoney(payment.amount))
                        }
                        Text("${payment.frequency} • Next: ${payment.nextDate}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (payment.active) Button(onClick = { payments = payments.map { if (it.id == payment.id) it.copy(active = false) else it } }) { Text("Pause") }
                        else OutlinedButton(onClick = { payments = payments.map { if (it.id == payment.id) it.copy(active = true) else it } }) { Text("Resume") }
                    }
                }
            }
        }
    }
}

private fun formatMoney(value: Double): String = String.format(Locale.US, "%.2f", value)
