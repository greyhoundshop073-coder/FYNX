package com.fynx.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** User-facing FYNX AI assistant. Sensitive FYNX data is not exposed by this panel. */
@Composable
fun FynxAiAssistantPanel() {
    var messages by remember {
        mutableStateOf(
            listOf(AiMessage("Hi, I'm FYNX AI. Ask me a question and I'll help you.", false))
        )
    }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = FynxDesign.ControlShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(9.dp).size(22.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("FYNX AI", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Private-by-default assistant",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { message ->
                Card(
                    Modifier.fillMaxWidth(),
                    shape = FynxDesign.CardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = if (message.fromUser) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .45f))
                ) {
                    Text(message.text, Modifier.padding(14.dp))
                }
            }
            if (loading) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it.take(FynxSecurityFoundation.MAX_AI_PROMPT_LENGTH) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading,
            minLines = 2,
            maxLines = 5,
            shape = FynxDesign.ControlShape,
            placeholder = { Text("Ask FYNX AI…") },
            trailingIcon = {
                IconButton(
                    enabled = !loading && input.trim().isNotEmpty(),
                    onClick = {
                        val prompt = input.trim()
                        if (prompt.isEmpty()) return@IconButton

                        val decision = FynxFutureIntelligencePolicy.authorize(
                            permissions = listOf(
                                FynxAiPermission(
                                    capability = FynxAiCapability.ASSISTANT,
                                    allowedScopes = setOf(FynxAiDataScope.NONE),
                                    enabled = true
                                )
                            ),
                            request = FynxAiRequest(
                                capability = FynxAiCapability.ASSISTANT,
                                prompt = prompt,
                                requestedScopes = setOf(FynxAiDataScope.NONE)
                            )
                        )
                        if (!decision.allowed) {
                            messages = messages + AiMessage("I couldn't process that request safely.", false)
                            return@IconButton
                        }

                        messages = messages + AiMessage(prompt, true)
                        input = ""
                        loading = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                AiAssistantClient.sendMessage(context, prompt)
                            }
                            messages = messages + AiMessage(
                                result.getOrElse { "FYNX AI is temporarily unavailable. Please try again." },
                                false
                            )
                            loading = false
                        }
                    }
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send")
                }
            }
        )
    }
}
