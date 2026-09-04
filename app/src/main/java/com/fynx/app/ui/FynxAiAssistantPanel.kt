package com.fynx.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** User-facing FYNX AI assistant. Sensitive FYNX data is not exposed by this panel. */
@Composable
fun FynxAiAssistantPanel(onOpenDestination: (String) -> Unit = {}) {
    val welcome = remember { AiMessage("Hi, I'm FYNX AI. Ask me a question and I'll help you.", false) }
    var messages by remember { mutableStateOf(listOf(welcome)) }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val toolLinks = remember {
        listOf(
            "To-Do" to "Daily Planning",
            "Calendar" to "Calendar",
            "Money Tools" to "Money Planner",
            "Marketplace" to "Marketplace",
            "Chats" to "Messages"
        )
    }

    fun copyText(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("FYNX AI", text))
    }

    fun shareText(text: String) {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }, "Share FYNX AI response"))
    }

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
            Column(Modifier.weight(1f)) {
                Text("FYNX AI", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Connected to your FYNX tools",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                enabled = !loading && messages.size > 1,
                onClick = { messages = listOf(welcome); errorMessage = null }
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear chat")
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(toolLinks) { (destination, label) ->
                AssistChip(
                    onClick = { onOpenDestination(destination) },
                    label = { Text(label) },
                    leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) }
                )
            }
        }

        if (errorMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    errorMessage!!,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
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
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(message.text, Modifier.padding(2.dp))
                        if (!message.fromUser) {
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                IconButton(onClick = { copyText(message.text) }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy response")
                                }
                                IconButton(onClick = { shareText(message.text) }) {
                                    Icon(Icons.Default.Share, contentDescription = "Share response")
                                }
                            }
                        }
                    }
                }
            }
            if (loading) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = {
                input = it.take(FynxSecurityFoundation.MAX_AI_PROMPT_LENGTH)
                errorMessage = null
            },
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
                            errorMessage = "I couldn't process that request safely."
                            return@IconButton
                        }

                        messages = messages + AiMessage(prompt, true)
                        input = ""
                        loading = true
                        errorMessage = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                AiAssistantClient.sendMessage(context, prompt)
                            }
                            result.onSuccess { reply ->
                                messages = messages + AiMessage(reply, false)
                            }.onFailure {
                                errorMessage = "FYNX AI is temporarily unavailable. Please try again."
                            }
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
