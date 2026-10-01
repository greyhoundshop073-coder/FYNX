package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MoneyInsightsPanel() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val transactions = remember { mutableStateOf(FynxMoneyStore.loadTransactions(context)) }
    var selectedType by remember { mutableStateOf("All") }

    val visible = transactions.value.filter { selectedType == "All" || it.type == selectedType }
    val income = transactions.value.filter { it.type == "Income" }.sumOf { it.amount }
    val expenses = transactions.value.filter { it.type == "Expense" }.sumOf { it.amount }
    val net = income - expenses
    val savingsRate = if (income > 0) ((net / income) * 100).coerceIn(0.0, 100.0) else 0.0

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Money Insights & Reports", style = MaterialTheme.typography.headlineSmall)
        Text("Insights are calculated from your saved FYNX money activity.", color = MaterialTheme.colorScheme.onSurfaceVariant)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Financial Summary", style = MaterialTheme.typography.titleMedium)
                Text("Income: ${money(income)}")
                Text("Expenses: ${money(expenses)}")
                Text("Net: ${money(net)}")
                Text("Savings rate: ${money(savingsRate)}%")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = selectedType == "All", onClick = { selectedType = "All" }, label = { Text("All") })
            FilterChip(selected = selectedType == "Income", onClick = { selectedType = "Income" }, label = { Text("Income") })
            FilterChip(selected = selectedType == "Expense", onClick = { selectedType = "Expense" }, label = { Text("Expenses") })
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Activity", style = MaterialTheme.typography.titleMedium)
                if (visible.isEmpty()) {
                    Text("No saved transactions yet. Add activity from Money Tools.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    visible.takeLast(8).reversed().forEach { item ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(item.title)
                                Text(item.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("${if (item.type == "Income") "+" else "-"}${money(item.amount)}")
                        }
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Text(
                when {
                    transactions.value.isEmpty() -> "Add a few transactions to unlock useful spending and income patterns."
                    expenses > income -> "Your saved expenses currently exceed your saved income. Review recent spending."
                    savingsRate >= 20.0 -> "Your saved activity shows a savings rate of at least 20%. Keep it consistent."
                    net > 0.0 -> "Your saved activity is currently positive. Consider assigning part of the surplus to a savings goal."
                    else -> "Keep recording transactions to make these insights more useful."
                },
                modifier = Modifier.padding(16.dp)
            )
        }

        Text("FYNX only analyzes locally saved activity here. It does not access bank accounts or move real money.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun money(value: Double): String = String.format(java.util.Locale.US, "%.2f", value)
