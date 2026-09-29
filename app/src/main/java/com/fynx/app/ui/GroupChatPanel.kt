package com.fynx.app.ui

import android.Manifest
import android.content.Context
import android.media.AudioManager
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.io.File
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun GroupChatPanel(
    group: GroupChat,
    currentUsername: String,
    onBack: () -> Unit,
    onGroupChanged: (GroupChat) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val groupAppearance = FynxConversationPreferences.groupAppearance(context, group.id)
    val glassThemeId = FynxGlassThemeId.entries.firstOrNull { it.label == groupAppearance } ?: FynxGlassThemeId.PURE_BLACK
    val glassPalette = fynxGlassPalette(glassThemeId)
    val isAdmin = group.isAdmin(currentUsername)
    var newMember by remember { mutableStateOf("") }
    var description by remember { mutableStateOf(group.description) }
    var text by remember { mutableStateOf("") }
    var messages by remember(group.id) { mutableStateOf(loadGroupMessages(context, group.id)) }
    var isNewGroupConversation by remember(group.id) { mutableStateOf(false) }
    var attachment by remember { mutableStateOf<Uri?>(null) }
    var attachmentType by remember { mutableStateOf("image") }
    var attachmentName by remember { mutableStateOf<String?>(null) }
    var showCamera by remember { mutableStateOf(false) }
    var showWallpaper by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var recordingStarted by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var isRecording by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var actionMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var editMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var editText by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<ChatMessage?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showGroupMenu by remember { mutableStateOf(false) }

    fun feedback() {
        if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.vibrate(35)
        }
        runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60).startTone(ToneGenerator.TONE_PROP_ACK, 70) }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            attachment = uri
            attachmentType = if (context.contentResolver.getType(uri)?.startsWith("video/") == true) "video" else "image"
            attachmentName = null
        }
    }

    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            attachment = uri
            attachmentType = "document"
            attachmentName = uri.lastPathSegment?.substringAfterLast("/")?.takeIf { it.isNotBlank() } ?: "Document"
        }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val file = File(context.cacheDir, "fynx_group_voice_${System.currentTimeMillis()}.m4a")
            runCatching {
                createCompatibleMediaRecorder(context).apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setOutputFile(file.absolutePath)
                    prepare()
                    start()
                    recording = this
                    recordingFile = file
                    recordingStarted = System.currentTimeMillis()
                    elapsed = 0
                    isRecording = true
                }
            }.onFailure { error = it.message ?: "Microphone unavailable" }
        }
    }

    LaunchedEffect(isRecording, recordingStarted) {
        while (isRecording) {
            elapsed = (System.currentTimeMillis() - recordingStarted).coerceAtLeast(0)
            kotlinx.coroutines.delay(150)
        }
    }

    LaunchedEffect(group.id) {
        if (!FynxBackendClient.hasAccessToken(context)) return@LaunchedEffect

        // Group chat does not have a websocket channel yet. Keep the existing
        // authoritative REST path, but refresh it while this screen is alive so
        // a group opened on one device can receive messages sent from another
        // without requiring the user to leave and re-enter the group.
        syncing = true
        if (isAdmin) {
            val remoteGroup = FynxGroup(
                group.id,
                group.name,
                group.description,
                FynxGroupVisibility.PRIVATE,
                group.adminUsernames.firstOrNull() ?: currentUsername,
                group.memberUsernames.map {
                    FynxGroupMember(it, if (it in group.adminUsernames) FynxGroupRole.ADMIN else FynxGroupRole.MEMBER)
                }
            )
            FynxGroupRemoteClient.syncGroup(context, remoteGroup).onFailure { error = it.message }
        }

        suspend fun refreshGroupMessages(showError: Boolean) {
            FynxGroupRemoteClient.loadMessages(context, group.id)
                .onSuccess { remote ->
                    val remoteMessages = remote.map {
                        FynxGroupRemoteClient.toChatMessage(
                            it,
                            currentUsername,
                            FynxBackendClient.baseUrl(context)
                        )
                    }
                    // Preserve an optimistic message until the server can see it,
                    // while replacing any message whose authoritative server row exists.
                    val remoteIds = remoteMessages.asSequence().map { it.id }.toSet()
                    val pendingLocal = messages.filter { it.id !in remoteIds }
                    messages = (remoteMessages + pendingLocal)
                        .distinctBy { it.id }
                        .sortedBy { it.timestamp }
                    saveGroupMessages(context, group.id, messages)
                    isNewGroupConversation = remoteMessages.isEmpty() && messages.isEmpty()
                    if (showError) error = null
                }
                .onFailure {
                    if (showError) {
                        error = it.message
                        isNewGroupConversation = false
                    }
                }
        }

        refreshGroupMessages(showError = true)
        syncing = false

        while (true) {
            kotlinx.coroutines.delay(10_000)
            refreshGroupMessages(showError = false)
        }
    }

    fun sendMessage(message: ChatMessage) {
        isNewGroupConversation = false
        messages = messages + message
        saveGroupMessages(context, group.id, messages)
        feedback()
        scope.launch {
            FynxGroupRemoteClient.sendMessage(context, group.id, message)
                .onSuccess { server ->
                    messages = messages.map {
                        if (it.id == message.id) FynxGroupRemoteClient.toChatMessage(server, currentUsername, FynxBackendClient.baseUrl(context)) else it
                    }
                    saveGroupMessages(context, group.id, messages)
                    error = null
                }
                .onFailure { e ->
                    messages = messages.filterNot { it.id == message.id }
                    saveGroupMessages(context, group.id, messages)
                    error = e.message ?: "Message could not be sent."
                }
        }
    }

    fun stopVoice() {
        val recorder = recording ?: return
        val file = recordingFile
        val duration = System.currentTimeMillis() - recordingStarted
        runCatching { recorder.stop() }
        recorder.release()
        recording = null
        recordingFile = null
        isRecording = false
        elapsed = 0
        if (file != null && file.exists() && file.length() > 0 && duration >= 300) {
            sendMessage(
                ChatMessage(
                    "Voice message",
                    true,
                    UUID.randomUUID().toString(),
                    delivered = true,
                    read = true,
                    replyToId = replyTo?.id,
                    attachmentUri = Uri.fromFile(file).toString(),
                    attachmentType = "audio"
                )
            )
        } else {
            file?.delete()
        }
        replyTo = null
    }

    fun send() {
        if (text.isBlank() && attachment == null) return
        val selected = attachment
        val message = ChatMessage(
            text.trim().ifBlank { if (attachmentType == "video") "Video" else "Photo" },
            true,
            UUID.randomUUID().toString(),
            delivered = true,
            read = true,
            replyToId = replyTo?.id,
            attachmentUri = selected?.toString(),
            attachmentType = if (selected == null) null else attachmentType
        )
        text = ""
        attachment = null
        replyTo = null
        sendMessage(message)
    }

    fun applyServer(updated: FynxGroupRemoteClient.RemoteMessage) {
        val mapped = FynxGroupRemoteClient.toChatMessage(updated, currentUsername, FynxBackendClient.baseUrl(context))
        messages = messages.map { if (it.id == updated.id) mapped else it }
        saveGroupMessages(context, group.id, messages)
    }

    val visibleMessages = remember(messages, searchQuery) {
        val query = searchQuery.trim().lowercase()
        if (query.isBlank()) messages else messages.filter { message ->
            message.text.lowercase().contains(query) || message.senderUsername.orEmpty().lowercase().contains(query) || message.senderName.orEmpty().lowercase().contains(query)
        }
    }

    FynxGroupWallpaperBackground(group.id, Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Surface(color = glassPalette.backgroundMid.copy(alpha = 0.98f), contentColor = glassPalette.messageText, tonalElevation = 0.dp, modifier = Modifier.statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.ArrowBack, "Back", tint = glassPalette.messageText) }
                    if (group.groupPhotoMediaId.isNullOrBlank()) FynxAvatar(group.name, Modifier.size(40.dp)) else FynxRemoteProfileAvatar(group.groupPhotoMediaId, group.name, Modifier.size(40.dp))
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        if (searchQuery.isBlank()) {
                            Text(group.name, style = MaterialTheme.typography.titleMedium, color = glassPalette.messageText)
                            Text(
                                "${group.memberUsernames.size} members${if (syncing) " • Syncing…" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = glassPalette.messageMuted
                            )
                        } else {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = { Text("Search messages…") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedTextColor = glassPalette.messageText,
                                    unfocusedTextColor = glassPalette.messageText,
                                    focusedPlaceholderColor = glassPalette.messageMuted,
                                    unfocusedPlaceholderColor = glassPalette.messageMuted,
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent
                                )
                            )
                        }
                    }
                    if (searchQuery.isBlank()) {
                        IconButton(onClick = { searchQuery = " " }) { Icon(Icons.Default.Search, "Search messages", tint = glassPalette.messageText) }
                    } else {
                        IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, "Close message search", tint = glassPalette.messageText) }
                    }
                    Box {
                        IconButton(onClick = { showGroupMenu = true }) { Icon(Icons.Default.MoreVert, "Group menu", tint = glassPalette.messageText) }
                        DropdownMenu(expanded = showGroupMenu, onDismissRequest = { showGroupMenu = false }) {
                            DropdownMenuItem(text = { Text("Search messages") }, onClick = { searchQuery = " "; showGroupMenu = false })
                            DropdownMenuItem(text = { Text("Group wallpaper") }, onClick = { showWallpaper = true; showGroupMenu = false })
                            DropdownMenuItem(text = { Text("Group info") }, onClick = { showGroupMenu = false; error = "${group.memberUsernames.size} members • ${if (group.description.isBlank()) "No description" else group.description}" })
                        }
                    }
                }
            }

            error?.let {
                Text(
                    it,
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
                contentPadding = PaddingValues(bottom = 10.dp)
            ) {
                if (isNewGroupConversation) {
                    item(key = "fynx-first-group-intro") {
                        FynxFirstGroupContactIntro(group)
                    }
                }
                items(visibleMessages, key = { it.id }) { message ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (message.fromMe) Arrangement.End else Arrangement.Start
                    ) {
                        Box {
                            Surface(
                                color = if (message.fromMe) glassPalette.outgoingStart.copy(alpha = 0.92f) else glassPalette.incomingGlass.copy(alpha = 0.96f),
                                contentColor = glassPalette.messageText,
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.widthIn(max = 330.dp).combinedClickable(
                                    onClick = { actionMessage = message },
                                    onLongClick = { actionMessage = message }
                                )
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    if (message.replyToId != null) {
                                        val parent = messages.firstOrNull { it.id == message.replyToId }
                                        Text(
                                            "Reply to ${parent?.senderUsername?.let { "@$it" } ?: "message"}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (message.attachmentUri != null && message.attachmentType == "document") {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Description, null, Modifier.size(28.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Document", maxLines = 2)
                                        }
                                    } else if (message.attachmentUri != null && message.attachmentType != "audio") {
                                        FynxRemoteMedia(
                                            message.attachmentUri,
                                            message.attachmentType ?: "image",
                                            Modifier.sizeIn(maxWidth = 290.dp, maxHeight = 240.dp)
                                        )
                                    }
                                    if (message.attachmentType == "audio") {
                                        FynxRemoteAudio(message.attachmentUri ?: message.voiceUri.orEmpty(), Modifier.fillMaxWidth())
                                    }
                                    if (message.text.isNotBlank() && message.attachmentType != "audio") Text(message.text)
                                    if (message.edited) Text("edited", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (message.reaction != null) Text(message.reaction!!, style = MaterialTheme.typography.labelSmall)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (message.pinned) Icon(Icons.Default.PushPin, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                        if (message.fromMe) Text("✓✓", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Surface(color = glassPalette.backgroundMid.copy(alpha = 0.98f), contentColor = glassPalette.messageText, tonalElevation = 0.dp, modifier = Modifier.navigationBarsPadding().imePadding()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)) {
                    replyTo?.let { replying ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Replying to @${replying.senderUsername ?: "member"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(replying.text.ifBlank { "Media message" }, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                            IconButton(onClick = { replyTo = null }) { Icon(Icons.Default.Close, "Cancel reply") }
                        }
                    }
                    if (attachment != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp)) {
                            Icon(if (attachmentType == "video") Icons.Default.Videocam else Icons.Default.Image, null)
                            Text(if (attachmentType == "video") "Video ready" else if (attachmentType == "document") (attachmentName ?: "Document ready") else "Photo ready", Modifier.weight(1f))
                            IconButton(onClick = { attachment = null }) { Icon(Icons.Default.Close, "Remove") }
                        }
                    }
                    if (isRecording) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("●", color = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(8.dp))
                                Text("Recording ${elapsed / 1000}s", Modifier.weight(1f))
                                TextButton(onClick = {
                                    recording?.release()
                                    recording = null
                                    recordingFile?.delete()
                                    recordingFile = null
                                    isRecording = false
                                }) { Text("Cancel") }
                                Button(onClick = { stopVoice() }) { Text("Send") }
                            }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { picker.launch("image/* video/*") }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.Add, "Attachments", tint = glassPalette.messageText)
                            }
                            IconButton(onClick = { documentPicker.launch(arrayOf("application/pdf", "text/plain", "application/zip", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.presentationml.presentation")) }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.Description, "Document", tint = glassPalette.messageText)
                            }
                            IconButton(onClick = { showCamera = true }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.CameraAlt, "Camera", tint = glassPalette.messageText)
                            }
                            OutlinedTextField(
                                value = text,
                                onValueChange = { text = it.take(4000) },
                                modifier = Modifier.weight(1f),
                                minLines = 1,
                                maxLines = 6,
                                shape = RoundedCornerShape(24.dp),
                                placeholder = { Text("Message group…") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = glassPalette.incomingGlass,
                                    unfocusedContainerColor = glassPalette.incomingGlass,
                                    focusedTextColor = glassPalette.messageText,
                                    unfocusedTextColor = glassPalette.messageText,
                                    focusedPlaceholderColor = glassPalette.messageMuted,
                                    unfocusedPlaceholderColor = glassPalette.messageMuted,
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent
                                ),
                                trailingIcon = {
                                    val canSend = text.isNotBlank() || attachment != null
                                    IconButton(
                                        onClick = {
                                            if (canSend) send() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                        },
                                        enabled = !sending,
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(
                                                if (canSend) glassPalette.outgoingStart
                                                else glassPalette.doodleSecondary
                                            )
                                            .semantics {
                                                contentDescription = if (canSend) "Send message" else "Hold to record voice message"
                                            }
                                    ) {
                                        Icon(
                                            if (canSend) Icons.Default.Send else Icons.Default.Mic,
                                            if (canSend) "Send message" else "Record voice",
                                            tint = Color.White
                                        )
                                    }
                                },
                                singleLine = false
                            )
                        }
                    }
                }
            }

            if (isAdmin) {
                Surface(tonalElevation = 1.dp) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(description, { description = it; onGroupChanged(group.copy(description = it)) }, Modifier.weight(1f), singleLine = true, label = { Text("Group description") })
                        OutlinedTextField(newMember, { newMember = it }, Modifier.weight(1f), singleLine = true, label = { Text("Add username") })
                        Button(enabled = newMember.isNotBlank(), onClick = { onGroupChanged(group.addMember(newMember.trim())); newMember = "" }) { Text("Add") }
                    }
                }
            }
        }
    }

    actionMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { actionMessage = null },
            title = { Text("Message actions") },
            text = {
                Column {
                    if (message.fromMe) {
                        TextButton(onClick = { editMessage = message; editText = message.text; actionMessage = null }, modifier = Modifier.fillMaxWidth()) { Text("Edit message") }
                    }
                    TextButton(onClick = { replyTo = message; actionMessage = null }, modifier = Modifier.fillMaxWidth()) { Text("Reply") }
                    TextButton(
                        enabled = message.text.isNotBlank(),
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("FYNX message", message.text))
                            actionMessage = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Copy") }
                    TextButton(
                        onClick = {
                            scope.launch {
                                val next = if (message.reaction == "👍") null else "👍"
                                FynxGroupRemoteClient.reactToMessage(context, group.id, message.id, next)
                                    .onSuccess { applyServer(it) }
                                    .onFailure { error = it.message ?: "Reaction failed." }
                                actionMessage = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (message.reaction == "👍") "Remove reaction" else "React 👍") }
                    if (isAdmin) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    FynxGroupRemoteClient.setPinned(context, group.id, message.id, !message.pinned)
                                        .onSuccess { applyServer(it) }
                                        .onFailure { error = it.message ?: "Pin failed." }
                                    actionMessage = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (message.pinned) "Unpin message" else "Pin message") }
                    }
                    if (message.fromMe || isAdmin) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    FynxGroupRemoteClient.deleteMessage(context, group.id, message.id)
                                        .onSuccess { applyServer(it) }
                                        .onFailure { error = it.message ?: "Delete failed." }
                                    actionMessage = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Delete message") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { actionMessage = null }) { Text("Close") } }
        )
    }

    editMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { editMessage = null },
            title = { Text("Edit message") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it.take(4000) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            FynxGroupRemoteClient.editMessage(context, group.id, message.id, editText.trim())
                                .onSuccess { applyServer(it); editMessage = null }
                                .onFailure { error = it.message ?: "Edit failed." }
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editMessage = null }) { Text("Cancel") } }
        )
    }

    if (showCamera) {
        Dialog(
            onDismissRequest = { showCamera = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                FynxCameraCapturePanel(
                    onCaptured = { uri, type -> attachment = uri; attachmentType = type; showCamera = false },
                    onDismiss = { showCamera = false }
                )
                }
            }
        }
    }
    if (showWallpaper) FynxGroupWallpaperDialog(group.id) { showWallpaper = false }
}

@Composable
private fun FynxFirstGroupContactIntro(group: GroupChat) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (group.groupPhotoMediaId.isNullOrBlank()) FynxAvatar(group.name, Modifier.size(58.dp)) else FynxRemoteProfileAvatar(group.groupPhotoMediaId, group.name, Modifier.size(58.dp))
            Spacer(Modifier.height(8.dp))
            Text(group.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "${group.memberUsernames.size} members",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (group.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    group.description.trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3
                )
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(Modifier.height(10.dp))
            Text(
                "You're starting a new group conversation",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun groupMessagesPrefs(context: Context) = context.getSharedPreferences(
    "fynx_group_messages_${FynxAuthStore.accountStorageKey(context)?.trim()?.lowercase()?.map { c -> if (c.isLetterOrDigit()) c else '_' }?.joinToString("")?.take(80)?.ifBlank { "account" } ?: "signed_out"}",
    Context.MODE_PRIVATE
)

private fun saveGroupMessages(context: Context, groupId: String, messages: List<ChatMessage>) {
    val array = org.json.JSONArray()
    messages.takeLast(100).forEach { message ->
        array.put(org.json.JSONObject().apply {
            put("id", message.id)
            put("text", message.text)
            put("fromMe", message.fromMe)
            put("timestamp", message.timestamp)
            put("delivered", message.delivered)
            put("read", message.read)
            put("replyToId", message.replyToId ?: "")
            put("reaction", message.reaction ?: "")
            put("edited", message.edited)
            put("pinned", message.pinned)
            put("attachmentUri", message.attachmentUri ?: "")
            put("attachmentType", message.attachmentType ?: "")
            put("voiceUri", message.voiceUri ?: "")
            put("voiceDurationMs", message.voiceDurationMs)
            put("senderName", message.senderName ?: "")
            put("senderUsername", message.senderUsername ?: "")
        })
    }
    groupMessagesPrefs(context).edit().putString(groupId, array.toString()).apply()
}

private fun loadGroupMessages(context: Context, groupId: String): List<ChatMessage> {
    val raw = groupMessagesPrefs(context).getString(groupId, null) ?: return emptyList()
    return runCatching {
        val array = org.json.JSONArray(raw)
        List(array.length()) { index ->
            val objectValue = array.getJSONObject(index)
            ChatMessage(
                objectValue.optString("text"),
                objectValue.optBoolean("fromMe"),
                objectValue.optString("id"),
                objectValue.optLong("timestamp"),
                objectValue.optBoolean("delivered"),
                objectValue.optBoolean("read"),
                replyToId = objectValue.optString("replyToId").ifBlank { null },
                reaction = objectValue.optString("reaction").ifBlank { null },
                edited = objectValue.optBoolean("edited"),
                attachmentUri = objectValue.optString("attachmentUri").ifBlank { null },
                attachmentType = objectValue.optString("attachmentType").ifBlank { null },
                voiceUri = objectValue.optString("voiceUri").ifBlank { null },
                voiceDurationMs = objectValue.optLong("voiceDurationMs"),
                senderName = objectValue.optString("senderName").ifBlank { null },
                senderUsername = objectValue.optString("senderUsername").ifBlank { null },
                pinned = objectValue.optBoolean("pinned")
            )
        }
    }.getOrElse { emptyList() }
}

private fun createCompatibleMediaRecorder(context: Context): MediaRecorder =
    if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()