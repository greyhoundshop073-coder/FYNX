package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MoneyInsightsPanel() {
    var incomeText by remember { mutableStateOf("") }
    var expenseText by remember { mutableStateOf("") }
    var savingsText by remember { mutableStateOf("") }
    var categoryText by remember { mutableStateOf("") }
    var showReport by remember { mutableStateOf(false) }

    val income = incomeText.toDoubleOrNull() ?: 0.0
    val expenses = expenseText.toDoubleOrNull() ?: 0.0
    val savings = savingsText.toDoubleOrNull() ?: 0.0
    val net = income - expenses
    val savingsRate = if (income > 0) (savings / income * 100).coerceIn(0.0, 100.0) else 0.0

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Money Insights & Reports 📊", style = MaterialTheme.typography.headlineSmall)
        Text("A simple view of your money patterns.", color = MaterialTheme.colorScheme.onSurfaceVariant)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Report inputs", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(incomeText, { incomeText = it }, label = { Text("Income") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(expenseText, { expenseText = it }, label = { Text("Expenses") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(savingsText, { savingsText = it }, label = { Text("Savings") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(categoryText, { categoryText = it }, label = { Text("Top spending category (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = { showReport = true }) { Text("Generate Report") }
            }
        }

        if (showReport) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Financial Summary", style = MaterialTheme.typography.titleMedium)
                    Text("Income: ${money(income)}")
                    Text("Expenses: ${money(expenses)}")
                    Text("Net: ${money(net)}")
                    Text("Savings: ${money(savings)}")
                    Text("Savings rate: ${money(savingsRate)}%")
                    if (categoryText.isNotBlank()) Text("Top spending category: ${categoryText.trim()}")
                    Text(
                        when {
                            income <= 0.0 -> "💡 Add income to generate a more useful insight."
                            expenses > income -> "⚠️ Your expenses are higher than your income. Consider reviewing your spending."
                            savingsRate >= 20.0 -> "🌟 Good progress: your savings rate is at least 20%."
                            savings > 0.0 -> "💡 You are saving, but there may be room to increase your savings rate."
                            else -> "💡 Consider setting a small savings target for the next period."
                        }
                    )
                    Text("This report only analyzes information entered in FYNX; it does not move money or access a bank account.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun money(value: Double): String = String.format(java.util.Locale.US, "%.2f", value)
