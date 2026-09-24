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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Close
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

/** User-facing FYNX Assistant assistant. Sensitive FYNX data is not exposed by this panel. */
@Composable
fun FynxAiAssistantPanel(onOpenDestination: (String) -> Unit = {}) {
    var messages by remember { mutableStateOf(emptyList<AiMessage>()) }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var failedPrompt by remember { mutableStateOf<String?>(null) }
    var conversationSummary by remember { mutableStateOf("") }
    var currentTask by remember { mutableStateOf("") }
    var voiceConnected by remember { mutableStateOf(false) }
    var voiceConnecting by remember { mutableStateOf(false) }
    var voiceMuted by remember { mutableStateOf(false) }
    var showComposerTools by remember { mutableStateOf(false) }
    var conversationId by remember { mutableStateOf<String?>(null) }
    var conversationHistory by remember { mutableStateOf<List<FynxAiConversationSummary>>(emptyList()) }
    var showConversationHistory by remember { mutableStateOf(false) }
    var pendingMediaId by remember { mutableStateOf<String?>(null) }
    var pendingMediaUploading by remember { mutableStateOf(false) }
    var pendingMessageAction by remember { mutableStateOf<FynxAiPendingMessageAction?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val voiceEngine = remember { FynxAiWebRtcEngine(context) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                pendingMediaUploading = true
                errorMessage = null
                FynxAiConversationClient.uploadImage(context, uri)
                    .onSuccess { pendingMediaId = it }
                    .onFailure { errorMessage = it.message ?: "Image upload failed." }
                pendingMediaUploading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        FynxAiConversationClient.create(context)
            .onSuccess { conversationId = it.id }
            .onFailure { errorMessage = it.message ?: "Unable to start a new FYNX Assistant conversation." }
    }

    fun startNewConversation() {
        scope.launch {
            loading = false; errorMessage = null; failedPrompt = null; input = ""; pendingMediaId = null; pendingMessageAction = null
            conversationSummary = ""; currentTask = ""
            FynxAiConversationClient.create(context)
                .onSuccess { conversationId = it.id; messages = emptyList(); showConversationHistory = false }
                .onFailure { errorMessage = it.message ?: "Unable to start a new FYNX Assistant conversation." }
        }
    }

    fun loadConversation(id: String) {
        scope.launch {
            loading = true; errorMessage = null
            FynxAiConversationClient.get(context, id)
                .onSuccess { conversation ->
                    conversationId = conversation.id
                    messages = conversation.messages.map { AiMessage(it.text, it.role == "user") }
                    input = ""; pendingMediaId = null; pendingMessageAction = null; showConversationHistory = false
                }
                .onFailure { errorMessage = it.message ?: "Unable to load that conversation." }
            loading = false
        }
    }

    val appendAssistantMessage: (String) -> Unit = { text ->
        if (text.isNotBlank()) messages = messages + AiMessage(text, false)
    }

    fun confirmPendingMessage() {
        val action = pendingMessageAction ?: return
        scope.launch {
            FynxAiConversationClient.confirmMessage(context, action.actionId)
                .onSuccess {
                    pendingMessageAction = null
                    appendAssistantMessage("Message sent to ${action.recipientDisplayName.ifBlank { action.recipientUsername }}.")
                    if (voiceConnected) voiceEngine.sendEvent(JSONObject().put("type","conversation.item.create").put("item", JSONObject().put("type","message").put("role","system").put("content", org.json.JSONArray().put(JSONObject().put("type","input_text").put("text","The user explicitly confirmed the pending message. The FYNX backend has now sent it successfully. Do not send it again; acknowledge that it was sent.")))).toString())
                    if (voiceConnected) voiceEngine.sendEvent("{\"type\":\"response.create\"}")
                }
                .onFailure { errorMessage = it.message ?: "The message could not be sent." }
        }
    }

    fun cancelPendingMessage() {
        val action = pendingMessageAction ?: return
        scope.launch {
            FynxAiConversationClient.cancelMessage(context, action.actionId)
                .onSuccess { pendingMessageAction = null; appendAssistantMessage("Okay, I did not send the message.") }
                .onFailure { errorMessage = it.message ?: "The pending message could not be cancelled." }
        }
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
                onEvent = { rawEvent ->
                    parseRealtimeAssistantEvent(rawEvent)?.let { spoken ->
                        appendAssistantMessage(spoken)
                        val transcript = extractRealtimeUserTranscript(rawEvent)
                        if (transcript != null && pendingMessageAction != null) {
                            val normalized = transcript.trim().lowercase().replace(Regex("[^a-z0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim()
                            when {
                                normalized.matches(Regex("^(yes|yeah|yep|yup|sure|send|send it|yes send it|go ahead|do it)( please)?$")) -> confirmPendingMessage()
                                normalized.matches(Regex("^(no|nope|cancel|dont|do not|not now|stop)( please)?$")) -> cancelPendingMessage()
                            }
                        }
                    }
                },
                onToolResult = { name, output ->
                    if (name == "prepare_send_message") {
                        runCatching { JSONObject(output) }.getOrNull()?.takeIf { it.optBoolean("confirmationRequired") }?.let { action ->
                            val recipient = action.optJSONObject("recipient")
                            pendingMessageAction = FynxAiPendingMessageAction(action.optString("actionId"), recipient?.optString("username","") ?: "", recipient?.optString("displayName","") ?: "", action.optString("message"))
                        }
                    }
                }
            )
            voiceConnecting = false
        }
    }

    val requestMicPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) connectVoice()
        else errorMessage = "Microphone permission is required for FYNX Assistant voice."
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
        clipboard.setPrimaryClip(ClipData.newPlainText("FYNX Assistant", text))
    }

    fun shareText(text: String) {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                "Share FYNX Assistant response"
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

    fun sendPrompt(rawPrompt: String, appendUser: Boolean) {
        val prompt = rawPrompt.trim()
        if (pendingMessageAction != null) {
            val normalized = prompt.lowercase().replace(Regex("[^a-z0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim()
            if (normalized.matches(Regex("^(yes|yeah|yep|yup|sure|send|send it|yes send it|go ahead|do it)( please)?$"))) { confirmPendingMessage(); input = ""; return }
            if (normalized.matches(Regex("^(no|nope|cancel|dont|do not|not now|stop)( please)?$"))) { cancelPendingMessage(); input = ""; return@sendPrompt }
        }
        val activeConversation = conversationId
        if ((prompt.isEmpty() && pendingMediaId == null) || loading || activeConversation == null || pendingMediaUploading) return@sendPrompt
        val decision = FynxFutureIntelligencePolicy.authorize(
            permissions = listOf(FynxAiPermission(capability = FynxAiCapability.ASSISTANT, allowedScopes = setOf(FynxAiDataScope.NONE), enabled = true)),
            request = FynxAiRequest(capability = FynxAiCapability.ASSISTANT, prompt = prompt.ifBlank { "Analyze the attached image." }, requestedScopes = setOf(FynxAiDataScope.NONE))
        )
        if (!decision.allowed) { errorMessage = "I couldn't process that request safely."; return@sendPrompt }
        val attachment = pendingMediaId
        if (appendUser) messages = messages + AiMessage(prompt.ifBlank { "Analyze this image." }, true)
        input = ""; pendingMediaId = null; loading = true; errorMessage = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { FynxAiConversationClient.send(context, activeConversation, prompt, listOfNotNull(attachment)) }
            result.onSuccess { reply ->
                    messages = messages + AiMessage(reply.assistantMessage.text, false)
                    pendingMessageAction = reply.pendingAction
                    failedPrompt = null
                }
                .onFailure {
                    failedPrompt = prompt; input = prompt; if (attachment != null) pendingMediaId = attachment
                    errorMessage = "FYNX Assistant is temporarily unavailable. You can retry or edit your message."
                }
            loading = false
        }
    }

    LaunchedEffect(showConversationHistory) {
        if (showConversationHistory) {
            FynxAiConversationClient.list(context).onSuccess { conversationHistory = it }
                .onFailure { errorMessage = it.message ?: "Unable to load AI conversation history." }
        }
    }

    LaunchedEffect(messages.size, loading, voiceConnecting) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    FynxAiAssistantBackdrop(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("FYNX Assistant", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Box {
                    IconButton(enabled = !loading, onClick = { showConversationHistory = !showConversationHistory }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "FYNX Assistant menu")
                    }
                    DropdownMenu(expanded = showConversationHistory, onDismissRequest = { showConversationHistory = false }) {
                        DropdownMenuItem(text = { Text("New conversation") }, onClick = { startNewConversation() })
                        conversationHistory.forEach { conversation ->
                            DropdownMenuItem(
                                text = { Text(conversation.title.ifBlank { "New conversation" }, maxLines = 1) },
                                trailingIcon = {
                                    IconButton(onClick = {
                                        scope.launch {
                                            FynxAiConversationClient.delete(context, conversation.id)
                                                .onSuccess { conversationHistory = conversationHistory.filterNot { it.id == conversation.id }; if (conversationId == conversation.id) startNewConversation() }
                                                .onFailure { errorMessage = it.message ?: "Unable to delete conversation." }
                                        }
                                    }) { Icon(Icons.Default.Close, contentDescription = "Delete conversation") }
                                },
                                onClick = { loadConversation(conversation.id) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
                                    if (voiceConnecting) "Connecting to FYNX Assistant…" else "FYNX Assistant is thinking…",
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
                                        onClick = { failedPrompt?.let { sendPrompt(it, false) } }
                                    ) {
                                        Text("Retry")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (pendingMessageAction != null) {
                Spacer(Modifier.height(6.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("Ready to send", style = MaterialTheme.typography.titleSmall)
                        Text("To ${pendingMessageAction!!.recipientDisplayName.ifBlank { pendingMessageAction!!.recipientUsername }}: “${pendingMessageAction!!.message}”")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { confirmPendingMessage() }) { Text("Send") }
                            OutlinedButton(onClick = { cancelPendingMessage() }) { Text("Cancel") }
                        }
                    }
                }
            }

            if (pendingMediaId != null) {
                Spacer(Modifier.height(6.dp))
                Surface(modifier = Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .75f), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .35f))) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FynxRemoteMedia("/api/media/${pendingMediaId}", "image", Modifier.size(72.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Image attached", style = MaterialTheme.typography.labelLarge)
                            Text("It will be sent with your next AI message.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { pendingMediaId = null }) { Icon(Icons.Default.Close, contentDescription = "Remove image") }
                    }
                }
            }
            if (pendingMediaUploading) { Spacer(Modifier.height(6.dp)); LinearProgressIndicator(Modifier.fillMaxWidth()) }
            Spacer(Modifier.height(2.dp))
            Box {
                OutlinedTextField(
                        value = input,
                        onValueChange = {
                            input = it.take(FynxSecurityFoundation.MAX_AI_PROMPT_LENGTH)
                            errorMessage = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 3.dp),
                        enabled = !loading && !voiceConnected,
                        minLines = 1,
                        minLines = 1,
                        maxLines = 6,
                        shape = RoundedCornerShape(26.dp),
                        placeholder = { Text(if (voiceConnected) "Listening to you…" else "Message FYNX Assistant…") },
                        leadingIcon = {
                            Box {
                                IconButton(enabled = !loading, onClick = { showComposerTools = !showComposerTools }) {
                                    Icon(Icons.Default.Add, contentDescription = "Attach to FYNX Assistant")
                                }
                                DropdownMenu(expanded = showComposerTools, onDismissRequest = { showComposerTools = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Image") },
                                        leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                                        onClick = { showComposerTools = false; imagePicker.launch("image/*") }
                                    )
                                    toolLinks.forEach { (destination, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                                            onClick = { showComposerTools = false; onOpenDestination(destination) }
                                        )
                                    }
                                }
                            }
                        },
                        trailingIcon = {
                            when {
                                voiceConnected -> {
                                    VoiceListeningIndicator(
                                        muted = voiceMuted,
                                        onClick = {
                                            voiceMuted = !voiceMuted
                                            voiceEngine.setMicrophoneEnabled(!voiceMuted)
                                        }
                                    )
                                }
                                input.trim().isNotEmpty() -> {
                                    IconButton(
                                        enabled = !loading,
                                        onClick = { sendPrompt(input, true) }
                                    ) {
                                        Icon(Icons.Default.Send, contentDescription = "Send")
                                    }
                                }
                                else -> {
                                    IconButton(
                                        enabled = !voiceConnecting && !loading,
                                        onClick = { toggleVoice() }
                                    ) {
                                        Icon(Icons.Default.Mic, contentDescription = "Speak to FYNX Assistant")
                                    }
                                }
                            }
                        }
                    )
            }
        }
    }
}

@Composable
private fun VoiceListeningIndicator(
    muted: Boolean,
    onClick: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "fynxVoiceBubbles")
    val heights = (0..4).map { index ->
        val value by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(420 + (index * 70)),
                repeatMode = RepeatMode.Reverse
            ),
            label = "voiceBubble$index"
        )
        value
    }
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Row(
            modifier = Modifier.fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            heights.forEach { height ->
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height((16.dp * height).coerceAtLeast(5.dp))
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (muted) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.primary
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
                        Text("FYNX Assistant", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
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

private fun extractRealtimeUserTranscript(rawEvent: String): String? = runCatching {
    val event = JSONObject(rawEvent)
    if (event.optString("type") == "conversation.item.input_audio_transcription.completed") event.optString("transcript").takeIf { it.isNotBlank() } else null
}.getOrNull()

private fun parseRealtimeAssistantEvent(rawEvent: String): String? = runCatching {
    val event = JSONObject(rawEvent)
    when (event.optString("type")) {
        "response.output_text.done" -> event.optString("text").takeIf { it.isNotBlank() }
        "response.audio_transcript.done" -> event.optString("transcript").takeIf { it.isNotBlank() }
        "conversation.item.input_audio_transcription.completed" -> event.optString("transcript").takeIf { it.isNotBlank() }?.let { "You: $it" }
        else -> null
    }
}.getOrNull()