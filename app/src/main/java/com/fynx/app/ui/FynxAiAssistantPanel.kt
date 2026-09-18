package com.fynx.app.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** User-facing FYNX AI assistant. Sensitive FYNX data is not exposed by this panel. */
@Composable
fun FynxAiAssistantPanel(onOpenDestination: (String) -> Unit = {}) {
    val welcome = remember { AiMessage("Hi, I'm FYNX AI. Ask me anything about your FYNX experience.", false) }
    var messages by remember { mutableStateOf(listOf(welcome)) }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var failedPrompt by remember { mutableStateOf<String?>(null) }
    var voiceConnected by remember { mutableStateOf(false) }
    var voiceConnecting by remember { mutableStateOf(false) }
    var voiceMuted by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val voiceEngine = remember { FynxAiWebRtcEngine(context) }

    val appendAssistantMessage: (String) -> Unit = { text ->
        if (text.isNotBlank()) messages = messages + AiMessage(text, false)
    }

    val connectVoice: () -> Unit = {
        scope.launch {
            voiceConnecting = true
            errorMessage = null
            voiceEngine.connect(
                onStateChanged = { state, error ->
                    voiceConnecting = state == FynxAiWebRtcEngine.State.CONNECTING
                    voiceConnected = state == FynxAiWebRtcEngine.State.CONNECTED
                    if (error != null) errorMessage = error
                },
                onEvent = { rawEvent -> parseRealtimeAssistantEvent(rawEvent)?.let(appendAssistantMessage) }
            )
            voiceConnecting = false
        }
    }

    val requestMicPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) connectVoice()
        else errorMessage = "Microphone permission is required for FYNX AI voice."
    }

    DisposableEffect(Unit) { onDispose { voiceEngine.close() } }

    val toolLinks = remember {
        listOf(
            "To-Do" to "Daily Planning",
            "Calendar" to "Calendar",
            "Money Tools" to "Money Planner",
            "Marketplace" to "Marketplace",
            "Chats" to "Messages",
            "AI Creation" to "AI Creation"
        )
    }
    val discoveryPrompts = remember {
        listOf(
            "People" to "Find people I may want to follow on FYNX",
            "Trending" to "Show me what's trending on FYNX",
            "Saved" to "Show me my saved FYNX posts"
        )
    }

    fun copyText(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("FYNX AI", text))
    }

    fun shareText(text: String) {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                "Share FYNX AI response"
            )
        )
    }

    fun toggleVoice() {
        if (voiceConnected) {
            voiceEngine.close()
            voiceConnected = false
            voiceMuted = false
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            connectVoice()
        } else {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(messages.size, loading, voiceConnecting) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .imePadding()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = FynxDesign.LargeCardShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = .96f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .5f)),
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("FYNX AI", style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (voiceConnected) "Voice is connected" else "Your intelligent FYNX assistant",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        enabled = !loading && messages.size > 1,
                        onClick = { messages = listOf(welcome); errorMessage = null; failedPrompt = null }
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear chat")
                    }
                    IconButton(enabled = !voiceConnecting, onClick = { toggleVoice() }) {
                        Icon(
                            if (voiceConnected) Icons.Default.StopCircle else Icons.Default.Mic,
                            contentDescription = if (voiceConnected) "Stop FYNX AI voice" else "Start FYNX AI voice"
                        )
                    }
                }
            }

            if (voiceConnected) {
                Spacer(Modifier.height(8.dp))
                AssistChip(
                    onClick = {
                        voiceMuted = !voiceMuted
                        voiceEngine.setMicrophoneEnabled(!voiceMuted)
                    },
                    label = { Text(if (voiceMuted) "Voice muted" else "Listening — tap to mute") },
                    leadingIcon = {
                        Icon(if (voiceMuted) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = null)
                    }
                )
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (messages.size == 1 && !loading && errorMessage == null) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = FynxDesign.LargeCardShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .62f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .35f))
                        ) {
                            Column(
                                Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Text("What can I help you with?", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Ask naturally. FYNX AI can use approved FYNX tools to find relevant information without exposing private data.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                items(messages) { message ->
                    AiChatBubble(
                        message = message,
                        onCopy = { copyText(message.text) },
                        onShare = { shareText(message.text) }
                    )
                }

                if (loading || voiceConnecting) {
                    item {
                        Surface(
                            shape = FynxDesign.CardShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .35f))
                        ) {
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    if (voiceConnecting) "Connecting to FYNX AI…" else "FYNX AI is thinking…",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (errorMessage != null) {
                    item {
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Column(
                                Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    errorMessage!!,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                if (failedPrompt != null) {
                                    TextButton(
                                        onClick = {
                                            input = failedPrompt.orEmpty()
                                            errorMessage = null
                                        }
                                    ) {
                                        Text("Retry")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            LazyRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(toolLinks) { (destination, label) ->
                    AssistChip(
                        onClick = { onOpenDestination(destination) },
                        label = { Text(label) },
                        leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) }
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            LazyRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(discoveryPrompts) { (label, prompt) ->
                    AssistChip(
                        onClick = { input = prompt; errorMessage = null },
                        label = { Text(label) },
                        leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = FynxDesign.LargeCardShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = .98f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .55f)),
                tonalElevation = 4.dp
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(FynxSecurityFoundation.MAX_AI_PROMPT_LENGTH); errorMessage = null },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp),
                    enabled = !loading,
                    minLines = 1,
                    maxLines = 5,
                    shape = FynxDesign.ControlShape,
                    placeholder = { Text("Message FYNX AI…") },
                    leadingIcon = {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    },
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
                                val history = messages.drop(1).filter { it.text.isNotBlank() }.takeLast(12)
                                messages = messages + AiMessage(prompt, true)
                                input = ""
                                loading = true
                                errorMessage = null
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        AiAssistantClient.sendMessage(context, prompt, history)
                                    }
                                    result.onSuccess { reply ->
                                            messages = messages + AiMessage(reply, false)
                                            failedPrompt = null
                                        }.onFailure {
                                            failedPrompt = prompt
                                            input = prompt
                                            errorMessage = "FYNX AI is temporarily unavailable. You can retry or edit your message."
                                        }
                                    loading = false
                                }
                            }
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Send")
                        }
                    )
                )
            }
        }
    }
}

@Composable
private fun AiChatBubble(
    message: AiMessage,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 340.dp),
            shape = if (message.fromUser) {
                RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 6.dp)
            } else {
                RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 6.dp, bottomEnd = 22.dp)
            },
            color = if (message.fromUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .4f)),
            tonalElevation = if (message.fromUser) 1.dp else 2.dp
        ) {
            Column(Modifier.padding(horizontal = 13.dp, vertical = 10.dp)) {
                if (!message.fromUser) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("FYNX AI", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(5.dp))
                }
                Text(
                    message.text,
                    color = if (message.fromUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                if (!message.fromUser && message.text.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = onCopy, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy response", modifier = Modifier.size(17.dp))
                        }
                        IconButton(onClick = onShare, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Share, contentDescription = "Share response", modifier = Modifier.size(17.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun parseRealtimeAssistantEvent(rawEvent: String): String? = runCatching {
    val event = JSONObject(rawEvent)
    when (event.optString("type")) {
        "response.output_text.done" -> event.optString("text").takeIf { it.isNotBlank() }
        "response.audio_transcript.done" -> event.optString("transcript").takeIf { it.isNotBlank() }
        "conversation.item.input_audio_transcription.completed" -> event.optString("transcript").takeIf { it.isNotBlank() }?.let { "You: $it" }
        else -> null
    }
}.getOrNull()