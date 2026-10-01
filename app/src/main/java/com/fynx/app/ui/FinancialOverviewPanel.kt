package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToLong

/** A lightweight read-only overview that does not create a second source of financial data. */
@Composable
fun FinancialOverviewPanel(
    balance: Double = 0.0,
    income: Double = 0.0,
    expenses: Double = 0.0,
    budget: Double = 0.0,
    savingsSaved: Double = 0.0,
    savingsTarget: Double = 0.0,
    outstandingBills: Double = 0.0
) {
    val net = income - expenses
    val budgetProgress = if (budget > 0) (expenses / budget).coerceIn(0.0, 1.0).toFloat() else 0f
    val savingsProgress = if (savingsTarget > 0) (savingsSaved / savingsTarget).coerceIn(0.0, 1.0).toFloat() else 0f

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Financial Overview 📊", style = MaterialTheme.typography.headlineSmall)
        Text("A simple snapshot of your FYNX money activity.", color = MaterialTheme.colorScheme.onSurfaceVariant)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Current balance", style = MaterialTheme.typography.titleMedium)
                Text(formatFinancial(balance), style = MaterialTheme.typography.headlineMedium)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCard("Income", income, Modifier.weight(1f))
            SummaryCard("Expenses", expenses, Modifier.weight(1f))
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Net activity", style = MaterialTheme.typography.titleMedium)
                Text(formatFinancial(net))
                if (budget > 0) {
                    Text("Budget used: ${formatPercent(budgetProgress)}")
                    LinearProgressIndicator(progress = { budgetProgress }, modifier = Modifier.fillMaxWidth())
                } else Text("Set a budget to see spending progress.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Savings", style = MaterialTheme.typography.titleMedium)
                if (savingsTarget > 0) {
                    Text("${formatFinancial(savingsSaved)} of ${formatFinancial(savingsTarget)}")
                    LinearProgressIndicator(progress = { savingsProgress }, modifier = Modifier.fillMaxWidth())
                    Text("${formatPercent(savingsProgress)} complete")
                } else Text("Create a savings goal to track progress.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Bills", style = MaterialTheme.typography.titleMedium)
                Text("Outstanding: ${formatFinancial(outstandingBills)}")
            }
        }

        Text("This overview is intentionally read-only for now, so existing money tools remain the source of truth.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SummaryCard(title: String, value: Double, modifier: Modifier) {
    Card(modifier) { Column(Modifier.padding(14.dp)) { Text(title, style = MaterialTheme.typography.titleSmall); Text(formatFinancial(value)) } }
}

private fun formatFinancial(value: Double): String = "${(value * 100).roundToLong() / 100.0}"
private fun formatPercent(value: Float): String = "${(value * 100).roundToLong()}%"
