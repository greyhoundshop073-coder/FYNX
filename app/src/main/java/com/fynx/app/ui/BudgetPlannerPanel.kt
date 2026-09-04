package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private data class BudgetItem(val id: Long, val category: String, val limit: Double, val spent: Double)

@Composable
fun BudgetPlannerPanel() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var category by remember { mutableStateOf("") }
    var limitText by remember { mutableStateOf("") }
    var spentText by remember { mutableStateOf("") }
    var period by remember { mutableStateOf("Monthly") }
    var nextId by remember { mutableLongStateOf(1L) }
    var budgets by remember { mutableStateOf(emptyList<BudgetItem>()) }
    var aiResult by remember { mutableStateOf("") }
    var aiLoading by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf<String?>(null) }

    val totalLimit = budgets.sumOf { it.limit }
    val totalSpent = budgets.sumOf { it.spent }
    val remaining = totalLimit - totalSpent

    fun askMoneyAi() {
        if (aiLoading) return
        val summary = if (budgets.isEmpty()) {
            "The user has not added any $period budget categories yet."
        } else {
            budgets.joinToString("\n") { "${it.category}: limit=${money(it.limit)}, spent=${money(it.spent)}, remaining=${money(it.limit - it.spent)}" }
        }
        val prompt = "You are FYNX Money Assistant. Give practical, educational budgeting guidance from the user's recorded $period budget only. Do not invent income, balances, transactions, debts or financial facts. Do not recommend specific investments or guarantee returns. Be concise and clear. Total budget=${money(totalLimit)}, total spent=${money(totalSpent)}, total remaining=${money(remaining)}. Categories:\n$summary"
        val decision = FynxFutureIntelligencePolicy.authorize(
            permissions = listOf(FynxAiPermission(FynxAiCapability.ASSISTANT, setOf(FynxAiDataScope.NONE), true)),
            request = FynxAiRequest(FynxAiCapability.ASSISTANT, prompt, setOf(FynxAiDataScope.NONE))
        )
        if (!decision.allowed) {
            aiError = "FYNX could not safely process this money request."
            return
        }
        aiLoading = true
        aiError = null
        scope.launch {
            val response = withContext(Dispatchers.IO) { AiAssistantClient.sendMessage(context, prompt) }
            response.onSuccess { aiResult = it.trim() }
                .onFailure { aiError = "FYNX Money AI is temporarily unavailable. Please try again." }
            aiLoading = false
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Budget Planner 💸", style = MaterialTheme.typography.headlineSmall)
        Text("Set limits and see how much remains in each category.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("${period} Budget", style = MaterialTheme.typography.titleMedium)
            Text("Budget: ${money(totalLimit)}")
            Text("Spent: ${money(totalSpent)}")
            Text("Remaining: ${money(remaining)}", color = if (remaining >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        } }
        Spacer(Modifier.height(10.dp))
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("FYNX Money AI 🤖", style = MaterialTheme.typography.titleMedium)
            Text("Get guidance from the budget information you entered.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = ::askMoneyAi, enabled = !aiLoading) { Text(if (aiLoading) "Thinking…" else "Ask FYNX AI") }
            aiError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (aiResult.isNotBlank()) Text(aiResult)
        } }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Weekly", "Monthly").forEach { option -> FilterChip(selected = period == option, onClick = { period = option; aiResult = ""; aiError = null }, label = { Text(option) }) }
        }
        OutlinedTextField(category, { category = it }, label = { Text("Budget category") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(limitText, { limitText = it }, label = { Text("Category limit") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(spentText, { spentText = it }, label = { Text("Amount spent") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            val limit = limitText.toDoubleOrNull()
            val spent = spentText.toDoubleOrNull() ?: 0.0
            if (category.isNotBlank() && limit != null && limit > 0 && spent >= 0) {
                budgets = budgets + BudgetItem(nextId++, category.trim(), limit, spent)
                category = ""; limitText = ""; spentText = ""
            }
        }) { Text("Add Budget") }
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(budgets, key = { it.id }) { budget ->
                val progress = if (budget.limit > 0) (budget.spent / budget.limit).coerceIn(0.0, 1.0) else 0.0
                val over = budget.spent > budget.limit
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(budget.category, style = MaterialTheme.typography.titleMedium); Text(money(budget.limit)) }
                    LinearProgressIndicator(progress = { progress.toFloat() }, modifier = Modifier.fillMaxWidth())
                    Text("Spent ${money(budget.spent)} • Remaining ${money(budget.limit - budget.spent)}")
                    if (over) Text("⚠️ Over budget", color = MaterialTheme.colorScheme.error)
                    else if (progress >= 0.8) Text("🔔 Close to limit", color = MaterialTheme.colorScheme.error)
                    else Text("🟢 Within budget", color = MaterialTheme.colorScheme.primary)
                } }
            }
        }
    }
}

private fun money(value: Double): String = String.format(Locale.US, "%.2f", value)
