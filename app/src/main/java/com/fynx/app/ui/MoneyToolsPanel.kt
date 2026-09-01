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
        MoneyCalculatorCard()
        CurrencyConverterCard()
        Spacer(Modifier.height(12.dp))
        Spacer(Modifier.height(12.dp))
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


@Composable
private fun MoneyCalculatorCard() {
    var expression by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Quick Calculator", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(expression, { expression = it }, label = { Text("Example: 125 + 75") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Button(onClick = { result = calculateSimple(expression) }) { Text("Calculate") }
            if (result.isNotEmpty()) Text("Result: $result", style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun calculateSimple(value: String): String {
    val match = Regex("^\\s*(\\d+(?:\\.\\d+)?)\\s*([+\\-*/])\\s*(\\d+(?:\\.\\d+)?)\\s*$").matchEntire(value) ?: return "Invalid expression"
    val a = match.groupValues[1].toDouble(); val op = match.groupValues[2]; val b = match.groupValues[3].toDouble()
    if (op == "/" && b == 0.0) return "Cannot divide by zero"
    val answer = when (op) { "+" -> a + b; "-" -> a - b; "*" -> a * b; else -> a / b }
    return formatMoney(answer)
}


@Composable
private fun CurrencyConverterCard() {
    val currencies = listOf("USD", "EUR", "GBP", "NGN", "JPY")
    var from by remember { mutableStateOf("USD") }
    var to by remember { mutableStateOf("NGN") }
    var amount by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    val rates = mapOf("USD" to 1.0, "EUR" to 1.09, "GBP" to 1.27, "NGN" to 0.00064, "JPY" to 0.0068)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Currency Converter 💱", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(amount, { amount = it }, label = { Text("Amount") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CurrencyMenu("From", from, currencies) { from = it }
                CurrencyMenu("To", to, currencies) { to = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val value = amount.toDoubleOrNull()
                    result = if (value != null && value >= 0) formatMoney(value * (rates[to]!! / rates[from]!!)) else "Enter a valid amount"
                }) { Text("Convert") }
                OutlinedButton(onClick = { val old = from; from = to; to = old }) { Text("Swap") }
            }
            if (result.isNotEmpty()) Text("$amount $from = $result $to", style = MaterialTheme.typography.titleMedium)
            Text("Demo rates; connect a live provider for production.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CurrencyMenu(label: String, selected: String, values: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.weight(1f)) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("$label: $selected") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            values.forEach { value -> DropdownMenuItem(text = { Text(value) }, onClick = { onSelect(value); expanded = false }) }
        }
    }
}
