package com.fynx.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class MoneySection(val key: String, val title: String, val description: String, val icon: String)

@Composable
fun MoneyCenterPanel() {
    var selectedTool by remember { mutableStateOf<String?>(null) }
    val sections = listOf(
        MoneySection("Dashboard", "Money Dashboard", "Balances, income, expenses and your overall money picture", "💰"),
        MoneySection("Transfer", "Send & Transfer Money", "Money transfers and payment movement", "↔️"),
        MoneySection("Bills", "Bills & Payments", "Bills, payment reminders and due amounts", "🧾"),
        MoneySection("Transactions", "Transactions", "Your recorded money activity", "📜"),
        MoneySection("Accounts", "Accounts & Wallets", "Keep your tracked cash, bank and wallet accounts together", "🏦"),
        MoneySection("Budget", "Budget Planner", "Budgets and spending limits", "🎯"),
        MoneySection("Savings", "Savings Goals", "Targets and savings progress", "🐷"),
        MoneySection("Subscriptions", "Subscriptions", "Recurring payments and subscriptions", "🔁"),
        MoneySection("Receipts", "Receipts & Expenses", "Receipts and expense records", "🧾"),
        MoneySection("Insights", "Money Insights", "Patterns and useful financial summaries", "📊"),
        MoneySection("Spending Insights", "Spending Insights", "Where your money is going", "📈"),
        MoneySection("Alerts", "Money Alerts", "Important money warnings and reminders", "🔔"),
        MoneySection("Vault", "Secure Money Vault", "Protected money information", "🔐"),
        MoneySection("Currency", "Currency Converter", "Convert between currencies", "💱")
    )

    if (selectedTool != null) {
        Column(Modifier.fillMaxSize()) {
            TextButton(onClick = { selectedTool = null }) { Text("← Money Center") }
            when (selectedTool) {
                "Dashboard" -> MoneyToolsPanel()
                "Transfer" -> FynxMoneyTransferPanel()
                "Bills" -> BillsPaymentPanel()
                "Transactions" -> TransactionHistoryPanel()
                "Accounts" -> AccountsWalletsPanel()
                "Budget" -> BudgetPlannerPanel()
                "Savings" -> SavingsGoalsPanel()
                "Subscriptions" -> SubscriptionsPanel()
                "Receipts" -> ReceiptsExpensePanel()
                "Insights" -> MoneyInsightsPanel()
                "Spending Insights" -> SpendingInsightsPanel()
                "Alerts" -> MoneyAlertsPanel()
                "Vault" -> SecureMoneyVaultPanel()
                "Currency" -> CurrencyConverterPanel()
            }
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(16.dp)) {
        item {
            Text("Money Center 💰", style = MaterialTheme.typography.headlineSmall)
            Text("Everything related to your money is organized here instead of being scattered across the FYNX menu.", color = FynxDesign.TextSecondary)
            Spacer(Modifier.height(4.dp))
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = FynxDesign.LargeCardShape, colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface), border = BorderStroke(1.dp, FynxDesign.Outline)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("One place for your money", style = MaterialTheme.typography.titleLarge)
                    Text("Accounts • transfers • bills • transactions • budgets • savings • subscriptions • insights", color = FynxDesign.TextSecondary)
                    Text("Real bank and payment connections will be added later; current tracking tools remain local.", style = MaterialTheme.typography.bodySmall, color = FynxDesign.TextSecondary)
                }
            }
        }
        items(sections, key = { it.key }) { section ->
            Card(onClick = { selectedTool = section.key }, modifier = Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface), border = BorderStroke(1.dp, FynxDesign.Outline)) {
                Row(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(section.icon, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(section.title, style = MaterialTheme.typography.titleMedium)
                        Text(section.description, color = FynxDesign.TextSecondary)
                    }
                    Text("›", style = MaterialTheme.typography.headlineSmall, color = FynxDesign.TextSecondary)
                }
            }
        }
    }
}
