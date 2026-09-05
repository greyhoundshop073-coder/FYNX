package com.fynx.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Money-focused FYNX AI experience using the single existing authenticated AI backend. */
@Composable
fun FynxMoneyAiCoachPanel(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun ask() {
        val question = input.trim()
        if (question.isEmpty() || loading) return
        val prompt = """
            You are the FYNX Money Coach. Give practical educational guidance about budgeting,
            saving, spending, subscriptions and financial planning. Do not execute or authorize
            transactions. Never ask for passwords, PINs, card numbers or account credentials.
            Do not invent balances or personal financial facts. Use only information the user
            provides in this request. For financial decisions, clearly distinguish general
            guidance from personalized advice.
            
            User question:
            $question
        """.trimIndent()
        loading = true
        error = null
        scope.launch {
            val response = withContext(Dispatchers.IO) { AiAssistantClient.sendMessage(context, prompt) }
            response.onSuccess { result = it.trim() }.onFailure { error = "FYNX AI is temporarily unavailable. Please try again." }
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TextButton(onClick = onBack) { Text("← Money Center") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text("AI Money Coach", style = MaterialTheme.typography.headlineSmall)
                Text("Plan and learn with the existing FYNX AI.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Card(Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, border = BorderStroke(1.dp, FynxDesign.Outline)) {
            Text("Ask about budgets, saving goals, spending habits or planning. FYNX AI will not perform money transactions.", Modifier.padding(12.dp), color = FynxDesign.TextSecondary)
        }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it.take(FynxSecurityFoundation.MAX_AI_PROMPT_LENGTH) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            maxLines = 8,
            enabled = !loading,
            shape = FynxDesign.ControlShape,
            placeholder = { Text("Example: Help me build a monthly budget from the amounts I provide…") }
        )
        Button(onClick = ::ask, enabled = !loading && input.trim().isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.AutoAwesome, null)
            Spacer(Modifier.width(6.dp))
            Text(if (loading) "Thinking…" else "Ask FYNX AI")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (result.isNotBlank()) Card(Modifier.fillMaxWidth(), shape = FynxDesign.CardShape) { Text(result, Modifier.padding(14.dp)) }
    }
}
