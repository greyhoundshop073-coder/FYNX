package com.fynx.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.roundToLong

@Composable
fun MoneyToolsPanel() {
    val context = LocalContext.current
    var balance by remember { mutableStateOf(0.0) }
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Income") }
    var entries by remember { mutableStateOf(FynxMoneyStore.loadTransactions(context)) }
    var pendingTransaction by remember { mutableStateOf<FynxMoneyTransaction?>(null) }

    val income = entries.filter { it.type == "Income" }.sumOf { it.amount }
    val expenses = entries.filter { it.type == "Expense" }.sumOf { it.amount }
    val net = income - expenses
    val accountsTotal = FynxMoneyStore.loadAccounts(context).sumOf { it.balance }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Money Tools", style = MaterialTheme.typography.headlineSmall)
        Text("Simple tools for tracking, planning, and calculating your money", color = FynxDesign.TextSecondary)

        MoneyCalculatorCard()
        CurrencyConverterCard()
        FynxMoneyTransferPanel()

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = FynxDesign.LargeCardShape,
            colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface),
            border = BorderStroke(1.dp, FynxDesign.Outline)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Money Dashboard 💰", style = MaterialTheme.typography.titleLarge)
                Text("Available tracked balance", color = FynxDesign.TextSecondary)
                Text(formatMoney(accountsTotal + net + balance), style = MaterialTheme.typography.headlineMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Income ${formatMoney(income)}", color = MaterialTheme.colorScheme.primary)
                    Text("Expenses ${formatMoney(expenses)}", color = MaterialTheme.colorScheme.error)
                }
                Text("${entries.size} recorded transaction(s) • ${FynxMoneyStore.loadAccounts(context).size} account(s)", color = FynxDesign.TextSecondary)
            }
        }

        BudgetCard(spent = expenses)
        SavingsGoalsCard()

        Text("Quick transaction", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(title, { title = it }, label = { Text("Description") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = FynxDesign.ControlShape)
        OutlinedTextField(amount, { amount = it }, label = { Text("Amount") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = FynxDesign.ControlShape)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = type == "Income", onClick = { type = "Income" }, label = { Text("Income") }, shape = FynxDesign.ControlShape)
            FilterChip(selected = type == "Expense", onClick = { type = "Expense" }, label = { Text("Expense") }, shape = FynxDesign.ControlShape)
            Spacer(Modifier.weight(1f))
            Button(onClick = {
                val value = amount.toDoubleOrNull()
                if (title.isNotBlank() && value != null && value > 0) {
                    pendingTransaction = FynxMoneyTransaction((entries.maxOfOrNull { it.id } ?: 0L) + 1L, title.trim(), value, type, "Today")
                }
            }, enabled = title.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0, shape = FynxDesign.ControlShape) { Text("Add") }
        }

        Text("Recent transactions", style = MaterialTheme.typography.titleMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            items(entries.asReversed().take(8), key = { it.id }) { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = FynxDesign.CardShape,
                    colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface),
                    border = BorderStroke(1.dp, FynxDesign.Outline)
                ) {
                    ListItem(
                        headlineContent = { Text(entry.title) },
                        supportingContent = { Text(entry.type, color = FynxDesign.TextSecondary) },
                        trailingContent = { Text("${if (entry.type == "Expense") "-" else "+"}${formatMoney(entry.amount)}", color = if (entry.type == "Expense") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(containerColor = FynxDesign.Surface)
                    )
                }
            }
        }
    }

    pendingTransaction?.let { transaction ->
        AlertDialog(
            onDismissRequest = { pendingTransaction = null },
            title = { Text("Confirm transaction") },
            text = { Text("Save ${transaction.type.lowercase()} of ${formatMoney(transaction.amount)} for ${transaction.title}? This is a local FYNX record and does not move real money.") },
            dismissButton = { TextButton(onClick = { pendingTransaction = null }) { Text("Cancel") } },
            confirmButton = { Button(onClick = { FynxMoneyStore.addTransaction(context, transaction); entries = FynxMoneyStore.loadTransactions(context); title = ""; amount = ""; pendingTransaction = null }, shape = FynxDesign.ControlShape) { Text("Confirm") } }
        )
    }
}

private fun formatMoney(value: Double): String = "${(value * 100).roundToLong() / 100.0}"

@Composable
private fun FynxSectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface), border = BorderStroke(1.dp, FynxDesign.Outline)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun BudgetCard(spent: Double) {
    var budgetText by remember { mutableStateOf("") }; var budget by remember { mutableStateOf(0.0) }
    val remaining = budget - spent; val progress = if (budget > 0) (spent / budget).coerceIn(0.0, 1.0).toFloat() else 0f
    FynxSectionCard {
        Text("Budget & Spending Limits 🎯", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(budgetText, { budgetText = it }, label = { Text("Monthly budget") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = FynxDesign.ControlShape)
        Button(onClick = { budgetText.toDoubleOrNull()?.takeIf { it > 0 }?.let { budget = it } }, shape = FynxDesign.ControlShape) { Text("Set Budget") }
        if (budget > 0) { Text("Spent: ${formatMoney(spent)}"); Text(if (spent > budget) "Over budget: ${formatMoney(spent - budget)}" else "Remaining: ${formatMoney(remaining)}"); LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth()); if (spent > budget) Text("⚠️ Spending limit exceeded", color = MaterialTheme.colorScheme.error) else if (spent >= budget * 0.8) Text("⚠️ You are approaching your budget limit", color = FynxDesign.TextSecondary) }
    }
}

@Composable
private fun SavingsGoalsCard() {
    var name by remember { mutableStateOf("") }; var targetText by remember { mutableStateOf("") }; var addText by remember { mutableStateOf("") }; var goal by remember { mutableStateOf<EmbeddedSavingsGoal?>(null) }
    FynxSectionCard {
        Text("Savings Goals 🎯", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(name, { name = it }, label = { Text("Goal name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = FynxDesign.ControlShape)
        OutlinedTextField(targetText, { targetText = it }, label = { Text("Target amount") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = FynxDesign.ControlShape)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { val target = targetText.toDoubleOrNull(); if (!name.isBlank() && target != null && target > 0) goal = EmbeddedSavingsGoal(name.trim(), target, 0.0) }, shape = FynxDesign.ControlShape) { Text("Create Goal") }
            OutlinedTextField(addText, { addText = it }, label = { Text("Add saved") }, singleLine = true, modifier = Modifier.weight(1f), shape = FynxDesign.ControlShape)
            OutlinedButton(onClick = { val add = addText.toDoubleOrNull(); if (add != null && add > 0) goal?.let { goal = it.copy(saved = (it.saved + add).coerceAtMost(it.target)) } }, shape = FynxDesign.ControlShape) { Text("Add") }
        }
        goal?.let { val progress = (it.saved / it.target).coerceIn(0.0, 1.0).toFloat(); Text(it.name, style = MaterialTheme.typography.titleMedium); Text("Saved ${formatMoney(it.saved)} of ${formatMoney(it.target)}"); LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth()); if (it.saved >= it.target) Text("🏆 Goal completed!") else Text("Remaining ${formatMoney(it.target - it.saved)}", color = FynxDesign.TextSecondary) }
    }
}

private data class EmbeddedSavingsGoal(val name: String, val target: Double, val saved: Double)

@Composable
private fun MoneyCalculatorCard() {
    var expression by remember { mutableStateOf("") }; var result by remember { mutableStateOf("") }
    FynxSectionCard { Text("Quick Calculator", style = MaterialTheme.typography.titleMedium); OutlinedTextField(expression, { expression = it }, label = { Text("Example: 125 + 75") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = FynxDesign.ControlShape); Button(onClick = { result = calculateSimple(expression) }, shape = FynxDesign.ControlShape) { Text("Calculate") }; if (result.isNotEmpty()) Text("Result: $result", style = MaterialTheme.typography.titleMedium) }
}

private fun calculateSimple(value: String): String { val match = Regex("^\\s*(\\d+(?:\\.\\d+)?)\\s*([+\\-*/])\\s*(\\d+(?:\\.\\d+)?)\\s*$").matchEntire(value) ?: return "Invalid expression"; val a = match.groupValues[1].toDouble(); val op = match.groupValues[2]; val b = match.groupValues[3].toDouble(); if (op == "/" && b == 0.0) return "Cannot divide by zero"; return formatMoney(when (op) { "+" -> a + b; "-" -> a - b; "*" -> a * b; else -> a / b }) }

@Composable
private fun CurrencyConverterCard() {
    val currencies = listOf("USD", "EUR", "GBP", "NGN", "JPY"); var from by remember { mutableStateOf("USD") }; var to by remember { mutableStateOf("NGN") }; var amount by remember { mutableStateOf("") }; var result by remember { mutableStateOf("") }
    val rates = mapOf("USD" to 1.0, "EUR" to 1.09, "GBP" to 1.27, "NGN" to 0.00064, "JPY" to 0.0068)
    FynxSectionCard { Text("Currency Converter 💱", style = MaterialTheme.typography.titleMedium); OutlinedTextField(amount, { amount = it }, label = { Text("Amount") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = FynxDesign.ControlShape); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { CurrencyMenu("From", from, currencies) { from = it }; CurrencyMenu("To", to, currencies) { to = it } }; Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { val value = amount.toDoubleOrNull(); result = if (value != null && value >= 0) formatMoney(value * (rates[to]!! / rates[from]!!)) else "Enter a valid amount" }, shape = FynxDesign.ControlShape) { Text("Convert") }; OutlinedButton(onClick = { val old = from; from = to; to = old }, shape = FynxDesign.ControlShape) { Text("Swap") } }; if (result.isNotEmpty()) Text("$amount $from = $result $to", style = MaterialTheme.typography.titleMedium); Text("Demo rates; connect a live provider for production.", style = MaterialTheme.typography.bodySmall, color = FynxDesign.TextSecondary) }
}

@Composable
private fun RowScope.CurrencyMenu(label: String, selected: String, values: List<String>, onSelect: (String) -> Unit) { var expanded by remember { mutableStateOf(false) }; Box(Modifier.weight(1f)) { OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = FynxDesign.ControlShape) { Text("$label: $selected") }; DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { values.forEach { value -> DropdownMenuItem(text = { Text(value) }, onClick = { onSelect(value); expanded = false }) } } } }
