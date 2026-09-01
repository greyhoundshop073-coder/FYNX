package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

private val currencyRates = linkedMapOf(
    "USD" to 1.0, "NGN" to 1650.0, "EUR" to 0.86, "GBP" to 0.75,
    "AED" to 3.67, "JPY" to 147.0, "CAD" to 1.38, "AUD" to 1.53
)

@Composable
fun CurrencyConverterPanel() {
    var amountText by remember { mutableStateOf("") }
    var from by remember { mutableStateOf("USD") }
    var to by remember { mutableStateOf("NGN") }
    var converted by remember { mutableStateOf<Double?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Currency Converter 💱", style = MaterialTheme.typography.headlineSmall)
        Text("Convert using the built-in reference rates. Live rates can be connected later.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(amountText, { amountText = it }, label = { Text("Amount") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        CurrencyChoice("From", from) { from = it; converted = null }
        CurrencyChoice("To", to) { to = it; converted = null }
        Button(onClick = {
            val amount = amountText.toDoubleOrNull()
            if (amount != null && amount >= 0) {
                converted = amount / (currencyRates[from] ?: 1.0) * (currencyRates[to] ?: 1.0)
            }
        }) { Text("Convert") }
        converted?.let { value ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Text("Converted amount", style = MaterialTheme.typography.titleMedium)
                Text("${money(value)} $to", style = MaterialTheme.typography.headlineMedium)
                Text("Reference: 1 $from ≈ ${money((currencyRates[to] ?: 1.0) / (currencyRates[from] ?: 1.0))} $to", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
        }
    }
}

@Composable
private fun CurrencyChoice(label: String, selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("$label: $selected") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            currencyRates.keys.forEach { code -> DropdownMenuItem(text = { Text(code) }, onClick = { onSelected(code); expanded = false }) }
        }
    }
}

private fun money(value: Double): String = String.format(Locale.US, "%.2f", value)
