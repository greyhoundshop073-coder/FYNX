package com.fynx.app.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

private fun friendlyFynxAiError(error: Throwable, fallback: String): String {
    val detail = error.message.orEmpty()
    val normalized = detail.lowercase()
    val serviceUnavailable = listOf(
        "http 404",
        "application not found",
        "failed to connect",
        "unable to resolve host",
        "connection refused",
        "timed out",
        "timeout"
    ).any(normalized::contains)
    return if (serviceUnavailable || detail.isBlank()) fallback else detail
}

private data class FynxAiUiMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val fromUser: Boolean,
    val attachmentIds: List<String> = emptyList(),
    val streaming: Boolean = false,
    val interrupted: Boolean = false
)

private data class FynxAiDiscoveryPrompt(
    val title: String,
    val description: String,
    val prompt: String,
    val icon: ImageVector,
    val accentColor: Color
)

/** User-facing FYNX Assistant. Private FYNX data stays inside its protected screens. */
@Composable
fun FynxAiAssistantPanel(
    onOpenDestination: (String) -> Unit = {},
    initialMarketplaceListingId: String? = null,
    currentUserDisplayName: String = "there",
    currentUsername: String? = null,
    currentUserPhotoUri: String? = null
) {
    var messages by remember { mutableStateOf(emptyList<FynxAiUiMessage>()) }
    var input by remember { mutableStateOf("") }
    var hasStartedInteraction by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var creatingConversation by remember { mutableStateOf(false) }
    var historyLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var failedPrompt by remember { mutableStateOf<String?>(null) }
    var voiceConnected by remember { mutableStateOf(false) }
    var voiceConnecting by remember { mutableStateOf(false) }
    var voiceMuted by remember { mutableStateOf(false) }
    var showComposerTools by remember { mutableStateOf(false) }
    var showTranslationDialog by remember { mutableStateOf(false) }
    var translationSourceText by remember { mutableStateOf("") }
    var translationTargetLanguage by remember { mutableStateOf("English") }
    var conversationId by remember { mutableStateOf<String?>(null) }
    var conversationHistory by remember { mutableStateOf<List<FynxAiConversationSummary>>(emptyList()) }
    var showConversationHistory by remember { mutableStateOf(false) }
    var historyQuery by remember { mutableStateOf("") }
    var conversationPendingDelete by remember { mutableStateOf<FynxAiConversationSummary?>(null) }
    var deletingConversationId by remember { mutableStateOf<String?>(null) }
    var pendingMediaId by remember { mutableStateOf<String?>(null) }
    var pendingMediaUploading by remember { mutableStateOf(false) }
    var pendingMessageAction by remember { mutableStateOf<FynxAiPendingMessageAction?>(null) }
    var generationJob by remember { mutableStateOf<Job?>(null) }
    var followingLatest by remember { mutableStateOf(true) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var welcomeDisplayName by remember(currentUsername, currentUserDisplayName) {
        mutableStateOf(currentUserDisplayName.trim().takeUnless { it.isBlank() || it.equals("Your name", ignoreCase = true) }
            ?: currentUsername?.removePrefix("@")?.trim().orEmpty().ifBlank { "there" })
    }
    LaunchedEffect(currentUsername, currentUserDisplayName) {
        val username = currentUsername?.removePrefix("@")?.trim().orEmpty()
        if (username.isNotBlank() && FynxBackendClient.hasAccessToken(context)) {
            FynxProfileRemoteClient.get(context, username).onSuccess { profile ->
                welcomeDisplayName = profile.displayName.trim()
                    .takeUnless { it.isBlank() || it.equals("Your name", ignoreCase = true) }
                    ?: profile.username.removePrefix("@").ifBlank { username }
            }
        }
    }
    val listState = rememberLazyListState()
    val voiceEngine = remember { FynxAiWebRtcEngine(context) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            hasStartedInteraction = true
            scope.launch {
                pendingMediaUploading = true
                errorMessage = null
                FynxAiConversationClient.uploadImage(context, uri)
                    .onSuccess {
                        pendingMediaId = it
                        hasStartedInteraction = true
                    }
                    .onFailure { errorMessage = friendlyFynxAiError(it, "Image upload is unavailable right now. Please try again when your connection is restored.") }
                pendingMediaUploading = false
            }
        }
    }

    // Navigation shortcuts must lead to an actual FYNX destination. Contextual
    // AI starters are kept separately below so they begin a conversation instead.
    val toolLinks = remember {
        listOf(
            "To-Do" to "Daily Planning",
            "Calendar" to "Calendar",
            "Money Tools" to "Money Planner",
            "Wallet" to "Wallet",
            "Marketplace" to "Marketplace",
            "Chats" to "Messages",
            "Stories" to "Status & Stories"
        )
    }
    // Each discovery card starts a focused conversation; it is not a navigation alias.
    // Keep short, user-facing descriptions separate from the full prompts sent to AI.
    val discoveryPrompts = remember {
        listOf(
            FynxAiDiscoveryPrompt(
                title = "Marketplace guide",
                description = "Discover products, compare sellers and grow your My Shop.",
                prompt = "Explain how the FYNX Marketplace works. Walk me through discovering products, checking sellers and listings, buying safely, and how selling through My Shop works. Ask whether I want help as a buyer or seller.",
                icon = Icons.Default.Store,
                accentColor = Color(0xFF007FFF)
            ),
            FynxAiDiscoveryPrompt(
                title = "Social discovery",
                description = "Explore public profiles, posts and conversations.",
                prompt = "Help me discover FYNX as a social community. Explain how to find public profiles, explore posts and trending conversations, and interact safely. Ask what kind of people or content I want to discover.",
                icon = Icons.Default.Group,
                accentColor = Color(0xFF008000)
            ),
            FynxAiDiscoveryPrompt(
                title = "Daily planning",
                description = "Turn your goals into a clear, realistic day plan.",
                prompt = "Help me organize my day and break goals into manageable tasks. Use only the details I share in this conversation. You cannot see or change my private To-Do or Calendar entries; when I am ready, guide me to the Daily Planning or Calendar shortcuts so I can add or edit items myself.",
                icon = Icons.Default.CalendarToday,
                accentColor = Color(0xFFE3A008)
            ),
            FynxAiDiscoveryPrompt(
                title = "Writing assistant",
                description = "Polish captions, product copy, bios and replies.",
                prompt = "Explain FYNX's inline AI writing assistant. It can help improve feed post captions, Marketplace product names and descriptions, Status text, profile bios, My Shop descriptions, comments and replies. Show me how to tap Improve with FYNX AI, review the suggested draft, and choose whether to use it or keep my original. Remind me that AI never posts or sends content automatically.",
                icon = Icons.Default.Edit,
                accentColor = Color(0xFF7C3AED)
            ),
            FynxAiDiscoveryPrompt(
                title = "Image understanding",
                description = "Ask questions about a photo you share.",
                prompt = "Show me how I can use FYNX AI to understand an image. Explain that I can attach a photo and ask questions about what is visible, then invite me to attach an image or describe what I need help with.",
                icon = Icons.Default.Image,
                accentColor = Color(0xFF0891B2)
            ),
            FynxAiDiscoveryPrompt(
                title = "Voice assistant",
                description = "Talk naturally with your FYNX AI assistant.",
                prompt = "Explain how to talk with FYNX AI using the voice assistant. Guide me through starting a voice conversation, speaking naturally, muting the microphone, and ending the session. Ask what I would like help with by voice.",
                icon = Icons.Default.Mic,
                accentColor = Color(0xFFDB2777)
            ),
            FynxAiDiscoveryPrompt(
                title = "Wallet guidance",
                description = "Understand Wallet tools and safe next steps.",
                prompt = "Explain FYNX Wallet and Money Tools in general terms. Never ask for or inspect my wallet PIN, bank details, balance, or private transaction history. For account-specific information, guide me to open the protected Wallet screen. You cannot move money, make payments, withdraw funds, or issue refunds.",
                icon = Icons.Default.AccountBalanceWallet,
                accentColor = Color(0xFFDC143C)
            )
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

    fun endVoiceSession() {
        voiceEngine.close()
        voiceConnected = false
        voiceConnecting = false
        voiceMuted = false
    }

    fun stopGeneration() {
        val activeJob = generationJob ?: return
        generationJob = null
        loading = false
        val streamingIndex = messages.indexOfLast { it.streaming && !it.fromUser }
        if (streamingIndex >= 0) {
            val updated = messages.toMutableList()
            val current = updated[streamingIndex]
            if (current.text.isBlank()) updated.removeAt(streamingIndex)
            else updated[streamingIndex] = current.copy(streaming = false, interrupted = true)
            messages = updated
        }
        statusMessage = "Stopped receiving this answer. The server may already have saved it."
        activeJob.cancel()
    }

    fun startNewConversation() {
        if (creatingConversation) return
        stopGeneration()
        endVoiceSession()
        // Keep the first-use dashboard and new-chat action independent of the
        // backend. Create the persisted conversation only when the user sends
        // their first prompt, so an offline service does not surface a raw 404
        // or block access to the assistant composer.
        conversationId = null
        messages = emptyList()
        errorMessage = null
        statusMessage = null
        failedPrompt = null
        input = ""
        hasStartedInteraction = false
        pendingMediaId = null
        pendingMessageAction = null
        historyQuery = ""
        showConversationHistory = false
    }

    fun loadConversation(id: String) {
        stopGeneration()
        endVoiceSession()
        historyLoading = true
        errorMessage = null
        statusMessage = null
        scope.launch {
            FynxAiConversationClient.get(context, id)
                .onSuccess { conversation ->
                    conversationId = conversation.id
                    hasStartedInteraction = true
                    messages = conversation.messages.map {
                        FynxAiUiMessage(
                            id = it.id,
                            text = it.text,
                            fromUser = it.role == "user",
                            attachmentIds = it.attachmentIds
                        )
                    }
                    input = ""
                    pendingMediaId = null
                    pendingMessageAction = null
                    failedPrompt = null
                    showConversationHistory = false
                }
                .onFailure { errorMessage = friendlyFynxAiError(it, "That conversation is unavailable right now. Please try again when FYNX services are restored.") }
            historyLoading = false
        }
    }

    fun deleteConversation(conversation: FynxAiConversationSummary) {
        deletingConversationId = conversation.id
        scope.launch {
            FynxAiConversationClient.delete(context, conversation.id)
                .onSuccess {
                    conversationHistory = conversationHistory.filterNot { it.id == conversation.id }
                    if (conversationId == conversation.id) {
                        conversationId = null
                        messages = emptyList()
                        startNewConversation()
                    }
                }
                .onFailure { errorMessage = friendlyFynxAiError(it, "That conversation could not be deleted right now. Please try again when FYNX services are restored.") }
            deletingConversationId = null
        }
    }

    val appendAssistantMessage: (String) -> Unit = { text ->
        if (text.isNotBlank()) {
            messages = messages + FynxAiUiMessage(text = text, fromUser = false)
        }
    }

    fun confirmPendingMessage() {
        val action = pendingMessageAction ?: return
        scope.launch {
            FynxAiConversationClient.confirmMessage(context, action.actionId)
                .onSuccess {
                    pendingMessageAction = null
                    appendAssistantMessage("Message sent to " + action.recipientDisplayName.ifBlank { action.recipientUsername } + ".")
                    if (voiceConnected) {
                        voiceEngine.sendEvent(
                            JSONObject()
                                .put("type", "conversation.item.create")
                                .put("item", JSONObject().put("type", "message").put("role", "system")
                                    .put("content", org.json.JSONArray().put(JSONObject().put("type", "input_text")
                                        .put("text", "The user explicitly confirmed the pending message. The FYNX backend has now sent it successfully. Do not send it again; acknowledge that it was sent."))))
                                .toString()
                        )
                        voiceEngine.sendEvent("{\"type\":\"response.create\"}")
                    }
                }
                .onFailure { errorMessage = it.message ?: "The message could not be sent." }
        }
    }

    fun cancelPendingMessage() {
        val action = pendingMessageAction ?: return
        scope.launch {
            FynxAiConversationClient.cancelMessage(context, action.actionId)
                .onSuccess {
                    pendingMessageAction = null
                    appendAssistantMessage("Okay, I did not send the message.")
                }
                .onFailure { errorMessage = friendlyFynxAiError(it, "The pending message could not be cancelled because FYNX services are unavailable.") }
        }
    }

    fun connectVoice() {
        hasStartedInteraction = true
        scope.launch {
            voiceConnecting = true
            errorMessage = null
            voiceEngine.connect(
                onStateChanged = { state, error ->
                    voiceConnecting = state == FynxAiWebRtcEngine.State.CONNECTING
                    voiceConnected = state == FynxAiWebRtcEngine.State.CONNECTED
                    if (state == FynxAiWebRtcEngine.State.CONNECTED) voiceMuted = false
                    if (error != null) errorMessage = error
                },
                onEvent = { rawEvent ->
                    parseRealtimeAssistantEvent(rawEvent)?.let { spoken ->
                        appendAssistantMessage(spoken)
                        val transcript = extractRealtimeUserTranscript(rawEvent)
                        if (transcript != null && pendingMessageAction != null) {
                            val normalized = transcript.trim().lowercase()
                                .replace(Regex("[^a-z0-9\\s]"), " ")
                                .replace(Regex("\\s+"), " ")
                                .trim()
                            when {
                                normalized.matches(Regex("^(yes|yeah|yep|yup|sure|send|send it|yes send it|go ahead|do it)( please)?$")) -> confirmPendingMessage()
                                normalized.matches(Regex("^(no|nope|cancel|dont|do not|not now|stop)( please)?$")) -> cancelPendingMessage()
                            }
                        }
                    }
                },
                onToolResult = { name, output ->
                    if (name == "prepare_send_message") {
                        runCatching { JSONObject(output) }.getOrNull()
                            ?.takeIf { it.optBoolean("confirmationRequired") }
                            ?.let { action ->
                                val recipient = action.optJSONObject("recipient")
                                pendingMessageAction = FynxAiPendingMessageAction(
                                    action.optString("actionId"),
                                    recipient?.optString("username", "") ?: "",
                                    recipient?.optString("displayName", "") ?: "",
                                    action.optString("message")
                                )
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

    DisposableEffect(Unit) {
        onDispose {
            generationJob?.cancel()
            voiceEngine.close()
        }
    }

    LaunchedEffect(initialMarketplaceListingId) {
        val listingId = initialMarketplaceListingId?.trim().orEmpty()
        if (listingId.isNotBlank()) {
            hasStartedInteraction = true
            input = "Help me evaluate Marketplace listing #" + listingId + ". Review its public listing details, risks, and what I should check before buying."
        }
    }

    LaunchedEffect(showConversationHistory) {
        if (showConversationHistory) {
            historyLoading = true
            FynxAiConversationClient.list(context)
                .onSuccess { conversationHistory = it }
                .onFailure { errorMessage = friendlyFynxAiError(it, "Conversation history is unavailable right now. Please try again when FYNX services are restored.") }
            historyLoading = false
        }
    }

    fun sendPrompt(rawPrompt: String, appendUser: Boolean) {
        val prompt = rawPrompt.trim()
        if (prompt.isNotEmpty() || pendingMediaId != null) hasStartedInteraction = true
        if (pendingMessageAction != null) {
            val normalized = prompt.lowercase()
                .replace(Regex("[^a-z0-9\\s]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            if (normalized.matches(Regex("^(yes|yeah|yep|yup|sure|send|send it|yes send it|go ahead|do it)( please)?$"))) {
                confirmPendingMessage()
                input = ""
                return
            }
            if (normalized.matches(Regex("^(no|nope|cancel|dont|do not|not now|stop)( please)?$"))) {
                cancelPendingMessage()
                input = ""
                return
            }
        }

        val activeConversation = conversationId
        if ((prompt.isEmpty() && pendingMediaId == null) || loading || creatingConversation || pendingMediaUploading) return
        val decision = FynxFutureIntelligencePolicy.authorize(
            permissions = listOf(FynxAiPermission(capability = FynxAiCapability.ASSISTANT, allowedScopes = setOf(FynxAiDataScope.NONE), enabled = true)),
            request = FynxAiRequest(
                capability = FynxAiCapability.ASSISTANT,
                prompt = prompt.ifBlank { "Analyze the attached image." },
                requestedScopes = setOf(FynxAiDataScope.NONE)
            )
        )
        if (!decision.allowed) {
            errorMessage = "I couldn't process that request safely."
            return
        }

        val attachment = pendingMediaId
        val requestMessage = prompt.ifBlank { "Analyze the attached image." }
        if (appendUser) {
            messages = messages + FynxAiUiMessage(
                text = requestMessage,
                fromUser = true,
                attachmentIds = listOfNotNull(attachment)
            )
        }
        val assistantMessageIndex = messages.size
        messages = messages + FynxAiUiMessage(text = "", fromUser = false, streaming = true)
        input = ""
        pendingMediaId = null
        loading = true
        errorMessage = null
        statusMessage = null
        failedPrompt = null

        val activeJob = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val ensuredConversationId = activeConversation
                    ?: FynxAiConversationClient.create(context).getOrThrow().id.also { conversationId = it }
                val result = FynxAiConversationClient.sendStreaming(
                    context,
                    ensuredConversationId,
                    requestMessage,
                    listOfNotNull(attachment)
                ) { delta ->
                    withContext(Dispatchers.Main.immediate) {
                        val current = messages.getOrNull(assistantMessageIndex)
                        if (current != null && !current.fromUser && delta.isNotEmpty()) {
                            val updated = messages.toMutableList()
                            updated[assistantMessageIndex] = current.copy(text = current.text + delta)
                            messages = updated
                        }
                    }
                }
                val reply = result.getOrThrow()
                val current = messages.getOrNull(assistantMessageIndex)
                if (current != null && !current.fromUser) {
                    val updated = messages.toMutableList()
                    updated[assistantMessageIndex] = current.copy(text = reply.assistantMessage.text, streaming = false)
                    messages = updated
                } else {
                    messages = messages + FynxAiUiMessage(text = reply.assistantMessage.text, fromUser = false)
                }
                pendingMessageAction = reply.pendingAction
                failedPrompt = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (assistantMessageIndex < messages.size) {
                    messages = messages.toMutableList().also { list ->
                        if (assistantMessageIndex < list.size) list.removeAt(assistantMessageIndex)
                    }
                }
                failedPrompt = requestMessage
                input = prompt
                if (attachment != null) pendingMediaId = attachment
                errorMessage = "FYNX Assistant is temporarily unavailable. You can retry or edit your message."
            } finally {
                val completedJob = currentCoroutineContext()[Job]
                withContext(NonCancellable) {
                    if (generationJob === completedJob) {
                        loading = false
                        generationJob = null
                    }
                }
            }
        }
        generationJob = activeJob
        activeJob.start()
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            layout.totalItemsCount == 0 || lastVisible >= layout.totalItemsCount - 2
        }.distinctUntilChanged().collect { followingLatest = it }
    }

    LaunchedEffect(messages.size, messages.lastOrNull()?.text, loading, creatingConversation, errorMessage, statusMessage, voiceConnecting) {
        val lastIndex = listState.layoutInfo.totalItemsCount - 1
        if (followingLatest && lastIndex >= 0) listState.animateScrollToItem(lastIndex)
    }

    val filteredHistory = remember(conversationHistory, historyQuery) {
        val query = historyQuery.trim()
        if (query.isEmpty()) conversationHistory.take(12)
        else conversationHistory.filter { it.title.contains(query, ignoreCase = true) }.take(12)
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .25f))
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(10.dp).size(22.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("FYNX Assistant", style = MaterialTheme.typography.titleLarge)
                    Text(
                        when {
                            voiceConnected -> "Voice session connected"
                            creatingConversation -> "Preparing a new conversation"
                            else -> "Ask  ·  Create  ·  Discover"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(enabled = !creatingConversation, onClick = { startNewConversation() }) {
                    Icon(Icons.Default.Add, contentDescription = "Start new conversation")
                }
                Box {
                    IconButton(
                        enabled = !creatingConversation,
                        onClick = { showConversationHistory = !showConversationHistory }
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Conversation history")
                    }
                    DropdownMenu(
                        expanded = showConversationHistory,
                        onDismissRequest = { showConversationHistory = false },
                        modifier = Modifier.widthIn(min = 280.dp, max = 340.dp).heightIn(max = 480.dp)
                    ) {
                        Column(
                            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Your conversations", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 6.dp))
                            OutlinedTextField(
                                value = historyQuery,
                                onValueChange = { historyQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = FynxDesign.ControlShape,
                                placeholder = { Text("Search conversation titles") }
                            )
                            if (historyLoading) {
                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Loading history…", style = MaterialTheme.typography.bodySmall)
                                }
                            } else if (filteredHistory.isEmpty()) {
                                Text(
                                    if (historyQuery.isBlank()) "No saved conversations yet." else "No matching conversations.",
                                    modifier = Modifier.padding(10.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                filteredHistory.forEach { conversation ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                conversation.title.ifBlank { "New conversation" },
                                                maxLines = 1
                                            )
                                        },
                                        trailingIcon = {
                                            IconButton(
                                                enabled = deletingConversationId != conversation.id,
                                                onClick = { conversationPendingDelete = conversation; showConversationHistory = false }
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Delete conversation")
                                            }
                                        },
                                        onClick = { loadConversation(conversation.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Box(
                Modifier.weight(1f).fillMaxWidth()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (messages.isEmpty() && !hasStartedInteraction) {
                        item(key = "fynx-ai-welcome") {
                            FynxAiWelcomeContent(
                                discoveryPrompts = discoveryPrompts,
                                currentUserDisplayName = welcomeDisplayName,
                                currentUsername = currentUsername,
                                currentUserPhotoUri = currentUserPhotoUri,
                                onPrompt = { sendPrompt(it, true) }
                            )
                        }
                    } else {
                        items(messages, key = { it.id }) { message ->
                            AiChatBubble(
                                message = message,
                                onCopy = { copyText(message.text) },
                                onShare = { shareText(message.text) }
                            )
                        }
                    }

                    if (loading || voiceConnecting || creatingConversation) {
                        item(key = "fynx-ai-activity") {
                            Surface(
                                shape = FynxDesign.CardShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .35f))
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        when {
                                            creatingConversation -> "Preparing your conversation…"
                                            voiceConnecting -> "Connecting to FYNX Assistant…"
                                            messages.lastOrNull()?.text?.isNotBlank() == true -> "Writing your response…"
                                            else -> "FYNX Assistant is thinking…"
                                        },
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (statusMessage != null) {
                        item(key = "fynx-ai-status") {
                            Surface(
                                shape = FynxDesign.CardShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .25f))
                            ) {
                                Text(
                                    statusMessage.orEmpty(),
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    if (errorMessage != null) {
                        item(key = "fynx-ai-error") {
                            Card(
                                Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                            ) {
                                Column(
                                    Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(errorMessage.orEmpty(), color = MaterialTheme.colorScheme.onErrorContainer)
                                    if (failedPrompt != null) {
                                        TextButton(onClick = { failedPrompt?.let { sendPrompt(it, false) } }) {
                                            Text("Retry")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (!followingLatest && messages.isNotEmpty()) {
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                val lastIndex = listState.layoutInfo.totalItemsCount - 1
                                if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
                                followingLatest = true
                            }
                        },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp)
                    ) {
                        Text("Latest ↓")
                    }
                }
            }

            if (pendingMessageAction != null) {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                    shape = FynxDesign.CardShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .35f))
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("Ready to send", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "To " + pendingMessageAction!!.recipientDisplayName.ifBlank { pendingMessageAction!!.recipientUsername } +
                                ": “" + pendingMessageAction!!.message + "”"
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { confirmPendingMessage() }) { Text("Send") }
                            OutlinedButton(onClick = { cancelPendingMessage() }) { Text("Cancel") }
                        }
                    }
                }
            }

            if (pendingMediaId != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                    shape = FynxDesign.CardShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .35f))
                ) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FynxRemoteMedia("/api/media/" + pendingMediaId, "image", Modifier.size(72.dp).clip(FynxDesign.ControlShape))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Image attached", style = MaterialTheme.typography.labelLarge)
                            Text(
                                "It will be included with your next message.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { pendingMediaId = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove attached image")
                        }
                    }
                }
            }

            if (pendingMediaUploading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 10.dp))
            }

            Column(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .65f)),
                    tonalElevation = 2.dp
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = {
                            input = it.take(FynxSecurityFoundation.MAX_AI_PROMPT_LENGTH)
                            if (input.isNotBlank()) hasStartedInteraction = true
                            errorMessage = null
                            statusMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading && !voiceConnected && !creatingConversation,
                        minLines = 1,
                        maxLines = 6,
                        shape = RoundedCornerShape(28.dp),
                        placeholder = {
                            Text(
                                when {
                                    voiceConnected -> "Listening to you…"
                                    creatingConversation -> "Preparing your conversation…"
                                    else -> "Message FYNX Assistant…"
                                }
                            )
                        },
                        leadingIcon = {
                            Box {
                                IconButton(
                                    enabled = !loading && !creatingConversation && !pendingMediaUploading,
                                    onClick = { showComposerTools = !showComposerTools }
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Attach to FYNX Assistant")
                                }
                                DropdownMenu(
                                    expanded = showComposerTools,
                                    onDismissRequest = { showComposerTools = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Add an image") },
                                        leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                                        onClick = {
                                            showComposerTools = false
                                            imagePicker.launch("image/*")
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Translate text") },
                                        leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                                        onClick = {
                                            translationSourceText = input
                                            showTranslationDialog = true
                                            showComposerTools = false
                                        }
                                    )
                                    toolLinks.forEach { (destination, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                                            onClick = {
                                                showComposerTools = false
                                                onOpenDestination(destination)
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        trailingIcon = {
                            when {
                                loading -> {
                                    IconButton(onClick = { stopGeneration() }) {
                                        Icon(Icons.Default.StopCircle, contentDescription = "Stop receiving response")
                                    }
                                }
                                voiceConnected -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { endVoiceSession() }) {
                                            Icon(Icons.Default.Close, contentDescription = "End voice session")
                                        }
                                        VoiceListeningIndicator(
                                            muted = voiceMuted,
                                            onClick = {
                                                voiceMuted = !voiceMuted
                                                voiceEngine.setMicrophoneEnabled(!voiceMuted)
                                            }
                                        )
                                    }
                                }
                                voiceConnecting -> {
                                    IconButton(onClick = { endVoiceSession() }) {
                                        Icon(Icons.Default.StopCircle, contentDescription = "Cancel voice connection")
                                    }
                                }
                                input.trim().isNotEmpty() || pendingMediaId != null -> {
                                    IconButton(
                                        enabled = !pendingMediaUploading && !creatingConversation,
                                        onClick = { sendPrompt(input, true) }
                                    ) {
                                        Icon(Icons.Default.Send, contentDescription = "Send message")
                                    }
                                }
                                else -> {
                                    IconButton(
                                        enabled = !creatingConversation && !pendingMediaUploading,
                                        onClick = {
                                            hasStartedInteraction = true
                                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                                connectVoice()
                                            } else {
                                                requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Mic, contentDescription = "Speak to FYNX Assistant")
                                    }
                                }
                            }
                        }
                    )
                }
                Text(
                    "FYNX Assistant can make mistakes. Check important information.",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showTranslationDialog) {
        AlertDialog(
            onDismissRequest = { showTranslationDialog = false },
            title = { Text("Translate text") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Choose the language and add the text you want FYNX AI to translate.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = translationTargetLanguage,
                        onValueChange = { translationTargetLanguage = it.take(FynxAiTranslation.MAX_LANGUAGE_LENGTH) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Translate into") },
                        placeholder = { Text("e.g. English, Hausa, Yoruba or French") },
                        shape = FynxDesign.ControlShape
                    )
                    OutlinedTextField(
                        value = translationSourceText,
                        onValueChange = { translationSourceText = it.take(FynxAiTranslation.MAX_SOURCE_LENGTH) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 6,
                        label = { Text("Text to translate") },
                        placeholder = { Text("Type or paste your text here…") },
                        supportingText = {
                            Text("${translationSourceText.length}/${FynxAiTranslation.MAX_SOURCE_LENGTH}")
                        },
                        shape = FynxDesign.ControlShape
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = translationSourceText.isNotBlank() &&
                        translationTargetLanguage.isNotBlank() &&
                        !loading && !creatingConversation && !pendingMediaUploading,
                    onClick = {
                        val prompt = runCatching {
                            FynxAiTranslation.buildPrompt(translationSourceText, translationTargetLanguage)
                        }.getOrElse {
                            errorMessage = it.message ?: "Check the translation text and target language."
                            return@TextButton
                        }
                        showTranslationDialog = false
                        translationSourceText = ""
                        sendPrompt(prompt, true)
                    }
                ) {
                    Text("Translate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTranslationDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (conversationPendingDelete != null) {
        val target = conversationPendingDelete!!
        AlertDialog(
            onDismissRequest = { conversationPendingDelete = null },
            title = { Text("Delete conversation?") },
            text = { Text("This permanently removes “" + target.title.ifBlank { "New conversation" } + "” and its saved messages.") },
            confirmButton = {
                TextButton(
                    enabled = deletingConversationId == null,
                    onClick = {
                        conversationPendingDelete = null
                        deleteConversation(target)
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { conversationPendingDelete = null }) { Text("Keep conversation") }
            }
        )
    }
}

@Composable
private fun FynxAiWelcomeContent(
    discoveryPrompts: List<FynxAiDiscoveryPrompt>,
    currentUserDisplayName: String,
    currentUsername: String?,
    currentUserPhotoUri: String?,
    onPrompt: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
            border = BorderStroke(1.dp, Color(0xFFBED7FF)),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFF5F9FF), Color(0xFFE7F1FF))
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FynxProfileImage(
                    name = currentUserDisplayName,
                    uriString = currentUserPhotoUri,
                    modifier = Modifier.size(46.dp).clip(CircleShape),
                    ownerUsername = currentUsername
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Hello, " + currentUserDisplayName.trim().ifBlank { "there" } + "!",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF1C2434),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        "How can I help today?",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF526174)
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Try asking", style = MaterialTheme.typography.titleMedium)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 4.dp)
            ) {
                items(discoveryPrompts, key = { it.title }) { starter ->
                    Surface(
                        modifier = Modifier.clickable(onClickLabel = starter.description) { onPrompt(starter.prompt) },
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, starter.accentColor.copy(alpha = .28f)),
                        tonalElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = starter.accentColor.copy(alpha = .12f)
                            ) {
                                Icon(
                                    starter.icon,
                                    contentDescription = null,
                                    tint = starter.accentColor,
                                    modifier = Modifier.padding(7.dp).size(17.dp)
                                )
                            }
                            Text(
                                starter.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceListeningIndicator(muted: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "fynxVoiceBubbles")
    val heights = (0..4).map { index ->
        val value by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = androidx.compose.animation.core.tween(420 + (index * 70)),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "voiceBubble" + index
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
    message: FynxAiUiMessage,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 360.dp),
            shape = if (message.fromUser) {
                RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 6.dp)
            } else {
                RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 6.dp, bottomEnd = 22.dp)
            },
            color = if (message.fromUser) Color(0xFF007FFF) else MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                1.dp,
                if (message.fromUser) Color(0xFF007FFF).copy(alpha = .72f)
                else MaterialTheme.colorScheme.outline.copy(alpha = .38f)
            ),
            tonalElevation = if (message.fromUser) 1.dp else 2.dp
        ) {
            Column(
                Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
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
                        if (message.streaming) {
                            Spacer(Modifier.width(8.dp))
                            Text("Writing…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                message.attachmentIds.forEach { attachmentId ->
                    FynxRemoteMedia(
                        "/api/media/" + attachmentId,
                        "image",
                        Modifier.fillMaxWidth().heightIn(max = 190.dp).clip(FynxDesign.ControlShape)
                    )
                }

                if (message.text.isNotBlank()) {
                    SelectionContainer {
                        Text(
                            text = buildFynxAiMarkdown(
                                message.text,
                                if (message.fromUser) Color.White else MaterialTheme.colorScheme.onSurface,
                                if (message.fromUser) Color.White.copy(alpha = .16f) else MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            color = if (message.fromUser) Color.White else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                if (message.interrupted) {
                    Text(
                        "Response stopped early",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (!message.fromUser && message.text.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
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

private fun buildFynxAiMarkdown(text: String, textColor: Color, codeBackground: Color): AnnotatedString =
    buildAnnotatedString {
        var insideCodeBlock = false
        val codeFence = 96.toChar().toString().repeat(3)
        val lines = text.split('\n')
        lines.forEachIndexed { index, line ->
            if (line.trimStart().startsWith(codeFence)) {
                insideCodeBlock = !insideCodeBlock
            } else if (insideCodeBlock) {
                pushStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        color = textColor,
                        background = codeBackground
                    )
                )
                append(line)
                pop()
            } else {
                val heading = Regex("^(#{1,3})\\s+(.+)$").matchEntire(line.trimStart())
                val bullet = Regex("^(\\s*)[-*]\\s+(.+)$").matchEntire(line)
                when {
                    heading != null -> {
                        val size = when (heading.groupValues[1].length) {
                            1 -> 20.sp
                            2 -> 18.sp
                            else -> 16.sp
                        }
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = size))
                        appendFynxInlineMarkdown(heading.groupValues[2], textColor, codeBackground)
                        pop()
                    }
                    bullet != null -> {
                        append(bullet.groupValues[1])
                        append("•  ")
                        appendFynxInlineMarkdown(bullet.groupValues[2], textColor, codeBackground)
                    }
                    else -> appendFynxInlineMarkdown(line, textColor, codeBackground)
                }
            }
            if (index < lines.lastIndex) append("\n")
        }
    }

private fun AnnotatedString.Builder.appendFynxInlineMarkdown(value: String, textColor: Color, codeBackground: Color) {
    val tokenPattern = Regex("(\\*\\*[^*]+\\*\\*|\\*[^*]+\\*)")
    var cursor = 0
    tokenPattern.findAll(value).forEach { match ->
        append(value.substring(cursor, match.range.first))
        val token = match.value
        when {
            token.startsWith("**") && token.endsWith("**") -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                append(token.substring(2, token.length - 2))
                pop()
            }
            token.startsWith("*") && token.endsWith("*") -> {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                append(token.substring(1, token.length - 1))
                pop()
            }
        }
        cursor = match.range.last + 1
    }
    append(value.substring(cursor))
}

private fun extractRealtimeUserTranscript(rawEvent: String): String? = runCatching {
    val event = JSONObject(rawEvent)
    if (event.optString("type") == "conversation.item.input_audio_transcription.completed") {
        event.optString("transcript").takeIf { it.isNotBlank() }
    } else null
}.getOrNull()

private fun parseRealtimeAssistantEvent(rawEvent: String): String? = runCatching {
    val event = JSONObject(rawEvent)
    when (event.optString("type")) {
        "response.output_text.done" -> event.optString("text").takeIf { it.isNotBlank() }
        "response.audio_transcript.done" -> event.optString("transcript").takeIf { it.isNotBlank() }
        "conversation.item.input_audio_transcription.completed" -> event.optString("transcript").takeIf { it.isNotBlank() }?.let { "You: " + it }
        else -> null
    }
}.getOrNull()
