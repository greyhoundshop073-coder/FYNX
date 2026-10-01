package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

/** Read-only spending insights foundation. It accepts existing totals instead of creating duplicate transaction storage. */
@Composable
fun SpendingInsightsPanel(
    income: Double = 0.0,
    expenses: Double = 0.0,
    budget: Double = 0.0,
    recurringMonthly: Double = 0.0
) {
    val net = income - expenses
    val budgetUsed = if (budget > 0) (expenses / budget).coerceIn(0.0, 1.0).toFloat() else 0f
    val recurringShare = if (expenses > 0) (recurringMonthly / expenses).coerceIn(0.0, 1.0).toFloat() else 0f

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Spending & Expense Insights 📊", style = MaterialTheme.typography.headlineSmall)
        Text("A safe overview built from existing money totals.", color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InsightCard("Income", income, Modifier.weight(1f))
            InsightCard("Expenses", expenses, Modifier.weight(1f))
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Net activity", style = MaterialTheme.typography.titleMedium)
                Text(money(net), style = MaterialTheme.typography.headlineSmall)
                Text(if (net >= 0) "You are currently spending less than your income." else "Your recorded expenses are above your recorded income.")
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Budget health", style = MaterialTheme.typography.titleMedium)
                if (budget > 0) {
                    Text("${percent(budgetUsed)} of budget used")
                    LinearProgressIndicator(progress = { budgetUsed }, modifier = Modifier.fillMaxWidth())
                    Text(if (expenses > budget) "⚠️ Over budget by ${money(expenses - budget)}" else "Remaining: ${money(budget - expenses)}")
                } else Text("No budget has been supplied yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Recurring-cost impact", style = MaterialTheme.typography.titleMedium)
                if (expenses > 0) {
                    Text("Recurring payments: ${money(recurringMonthly)} per month")
                    LinearProgressIndicator(progress = { recurringShare }, modifier = Modifier.fillMaxWidth())
                    Text("${percent(recurringShare)} of recorded expenses")
                } else Text("Add expenses to see recurring-cost impact.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Text("Category-level charts will be connected when FYNX has a single shared transaction source, avoiding duplicate financial records.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InsightCard(title: String, value: Double, modifier: Modifier) {
    Card(modifier) { Column(Modifier.padding(14.dp)) { Text(title, style = MaterialTheme.typography.titleSmall); Text(money(value), style = MaterialTheme.typography.titleLarge) } }
}

private fun money(value: Double): String = String.format(Locale.US, "%.2f", value)
private fun percent(value: Float): String = "${(value * 100).toInt()}%"
