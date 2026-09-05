package com.fynx.app.ui

import android.Manifest
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ConversationPanel(chat: ChatPreview, onBack: () -> Unit, onOpenProfile: (String) -> Unit = {}, onVoiceCall: () -> Unit = {}, onVideoCall: () -> Unit = {}) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val fallbackMessage = remember(chat.lastMessage) { chat.lastMessage.takeIf { it.isNotBlank() }?.let { ChatMessage(it, false, id = "initial", delivered = true, read = true) } }
    var text by remember(chat.username) { mutableStateOf("") }
    var messages by remember(chat.username) { mutableStateOf(FynxChatStore.load(context, chat.username, fallbackMessage)) }
    var replyToId by remember { mutableStateOf<String?>(null) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var attachment by remember { mutableStateOf<Uri?>(null) }
    var attachmentType by remember { mutableStateOf<String?>(null) }
    var showCamera by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var isRecordingPaused by remember { mutableStateOf(false) }
    var recordingElapsed by remember { mutableLongStateOf(0L) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var recordingStartedAt by remember { mutableStateOf(0L) }
    var playingVoiceId by remember { mutableStateOf<String?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var menuMessageId by remember { mutableStateOf<String?>(null) }
    var showGifts by remember { mutableStateOf(false) }
    var currentUserId by remember { mutableStateOf<String?>(null) }
    var recipientUserId by remember { mutableStateOf<String?>(null) }
    var isOnline by remember(chat.username) { mutableStateOf(chat.online) }
    var otherIsTyping by remember(chat.username) { mutableStateOf(false) }
    var networkError by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var typingSent by remember { mutableStateOf(false) }

    val realtimeClient = remember(chat.username) {
        FynxRealtimeClient(
            context = context,
            onMessage = { remote ->
                val myId = currentUserId ?: return@FynxRealtimeClient
                if (remote.senderId != myId && remote.recipientId != myId) return@FynxRealtimeClient
                val converted = FynxProductionMessaging.toChatMessage(remote, myId)
                messages = (messages.filterNot { it.id == remote.id } + converted).sortedBy { it.timestamp }
                if (remote.recipientId == myId) {
                    FynxInChatSound.play(context)
                    realtimeClient.acknowledgeMessage(remote.id)
                    scope.launch { FynxProductionMessaging.markRead(context, listOf(remote.id)) }
                }
            },
            onEvent = { event ->
                when (event) {
                    is FynxRealtimeClient.Event.MessageStatus -> {
                        messages = messages.map { message ->
                            if (message.id != event.messageId) message else when (event.status) {
                                FynxRealtimeClient.Status.READ -> message.copy(delivered = true, read = true)
                                FynxRealtimeClient.Status.DELIVERED -> message.copy(delivered = true)
                                FynxRealtimeClient.Status.SENT -> message
                            }
                        }
                    }
                    is FynxRealtimeClient.Event.Typing -> if (event.userId == recipientUserId) otherIsTyping = event.isTyping
                    is FynxRealtimeClient.Event.Presence -> if (event.userId == recipientUserId) isOnline = event.online
                }
            }
        )
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        attachment = it
        attachmentType = if (it == null) null else "image"
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && !isRecording) {
            val file = File(context.cacheDir, "voice_" + System.currentTimeMillis() + ".m4a")
            runCatching {
                createCompatibleMediaRecorder(context).apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setOutputFile(file.absolutePath)
                    prepare(); start()
                    recorder = this
                    recordingFile = file
                    recordingStartedAt = System.currentTimeMillis()
                    recordingElapsed = 0L
                    isRecordingPaused = false
                    isRecording = true
                }
            }.onFailure { networkError = it.message ?: "Unable to start recording" }
        }
    }

    LaunchedEffect(chat.username) {
        currentUserId = FynxBackendClient.currentUserId(context).getOrNull()
        recipientUserId = FynxSocialClient.searchUsers(context, chat.username.removePrefix("@"))
            .getOrNull()?.firstOrNull { it.username.equals(chat.username.removePrefix("@"), true) }?.id
        FynxProductionMessaging.history(context, chat.username.removePrefix("@"))
            .onSuccess { remoteMessages ->
                val myId = currentUserId
                if (myId != null) messages = remoteMessages.map { FynxProductionMessaging.toChatMessage(it, myId) }
                val unread = remoteMessages.filter { it.recipientId == myId && !it.read }.map { it.id }
                if (unread.isNotEmpty()) {
                    realtimeClient.sendRead(unread)
                    scope.launch { FynxProductionMessaging.markRead(context, unread) }
                }
            }
            .onFailure { networkError = it.message ?: "Unable to load messages" }
        realtimeClient.connect()
    }

    LaunchedEffect(text, recipientUserId) {
        val recipient = recipientUserId ?: return@LaunchedEffect
        if (text.isBlank()) {
            if (typingSent) { realtimeClient.sendTyping(recipient, false); typingSent = false }
            return@LaunchedEffect
        }
        if (!typingSent) { realtimeClient.sendTyping(recipient, true); typingSent = true }
        delay(1800L)
        if (typingSent) { realtimeClient.sendTyping(recipient, false); typingSent = false }
    }

    LaunchedEffect(isRecording, recordingStartedAt) {
        while (isRecording) {
            if (!isRecordingPaused) recordingElapsed = (System.currentTimeMillis() - recordingStartedAt).coerceAtLeast(0L)
            delay(200L)
        }
    }

    LaunchedEffect(messages) {
        FynxChatStore.save(context, chat.username, messages)
        val latest = messages.maxByOrNull { it.timestamp }
        if (latest != null) {
            val previewText = when {
                latest.voiceUri != null -> "Voice message"
                latest.attachmentUri != null && latest.text.isBlank() -> if (latest.attachmentType == "video") "Video" else "Photo"
                else -> latest.text
            }
            FynxChatStore.savePreview(context, chat.copy(lastMessage = previewText, time = formatChatTime(latest.timestamp)))
        }
    }

    DisposableEffect(realtimeClient) {
        onDispose {
            if (typingSent) realtimeClient.sendTyping(recipientUserId ?: "", false)
            realtimeClient.close()
            runCatching { recorder?.stop() }
            recorder?.release(); player?.release()
        }
    }

    fun startRecording() = microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
    fun cancelRecording() {
        recorder?.release(); recorder = null; recordingFile?.delete(); recordingFile = null; recordingElapsed = 0L; isRecording = false
    }
    fun stopRecording() {
        val r = recorder ?: return
        val file = recordingFile
        val duration = System.currentTimeMillis() - recordingStartedAt
        runCatching { r.stop() }; r.release(); recorder = null; isRecordingPaused = false; isRecording = false; recordingFile = null; recordingElapsed = 0L
        if (file != null && file.exists() && file.length() > 0L && duration >= 300L) {
            val pendingFile = file
            scope.launch {
                sending = true
                networkError = null
                FynxProductionMessaging.uploadMedia(context, Uri.fromFile(pendingFile), "audio/mp4")
                    .onSuccess { media ->
                        FynxProductionMessaging.sendText(context, chat.username.removePrefix("@"), "", mediaId = media.id, mediaType = "audio", voiceDurationMs = duration)
                            .onSuccess { remote ->
                                currentUserId?.let { myId -> messages = (messages.filterNot { it.id == remote.id } + FynxProductionMessaging.toChatMessage(remote, myId)).sortedBy { it.timestamp } }
                                pendingFile.delete()
                            }.onFailure { networkError = it.message ?: "Voice message could not be sent" }
                    }.onFailure { networkError = it.message ?: "Voice recording upload failed" }
                sending = false
            }
        } else file?.delete()
    }
    fun playVoice(message: ChatMessage) {
        val voiceUrl = message.voiceUri ?: return
        player?.release()
        player = null
        playingVoiceId = null
        scope.launch {
            val localUri = if (voiceUrl.startsWith("http://") || voiceUrl.startsWith("https://") || voiceUrl.startsWith("/api/")) {
                val mediaId = message.mediaId ?: voiceUrl.substringAfterLast('/').takeIf { it.isNotBlank() }
                if (mediaId == null) {
                    networkError = "Voice message media is unavailable"
                    return@launch
                }
                FynxProductionMessaging.cacheRemoteMedia(context, mediaId, voiceUrl).getOrElse {
                    networkError = it.message ?: "Voice message could not be loaded"
                    return@launch
                }
            } else Uri.parse(voiceUrl)
            val preparedPlayer = runCatching {
                MediaPlayer().apply {
                    setDataSource(context, localUri)
                    setOnCompletionListener {
                        playingVoiceId = null
                        release()
                        player = null
                    }
                    setOnErrorListener { _, _, _ ->
                        playingVoiceId = null
                        release()
                        player = null
                        true
                    }
                    prepare()
                    start()
                }
            }.getOrElse {
                networkError = it.message ?: "Voice message could not be played"
                null
            }
            if (preparedPlayer != null) {
                player = preparedPlayer
                playingVoiceId = message.id
            }
        }
    }

    val visibleMessages = if (searchQuery.isBlank()) messages else messages.filter { it.text.contains(searchQuery, ignoreCase = true) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { Text("‹", style = MaterialTheme.typography.headlineSmall) }
                    IconButton(onClick = { onOpenProfile(chat.username) }) { FynxAvatar(chat.name, modifier = Modifier.size(46.dp)) }
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        TextButton(onClick = { onOpenProfile(chat.username) }, contentPadding = PaddingValues(0.dp)) { Text(chat.name, style = MaterialTheme.typography.titleMedium) }
                        Text(when { otherIsTyping -> "typing…"; isOnline -> "● Online"; else -> chat.username }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onVoiceCall) { Icon(Icons.Default.Call, "Voice call") }
                    IconButton(onClick = onVideoCall) { Icon(Icons.Default.Videocam, "Video call") }
                    IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) searchQuery = "" }) { Icon(if (searchOpen) Icons.Default.Close else Icons.Default.Search, "Search") }
                    IconButton(onClick = { showGifts = true }) { Icon(Icons.Default.CardGiftcard, "Send gift") }
                }
            }
        }

        if (searchOpen) OutlinedTextField(searchQuery, { searchQuery = it }, Modifier.fillMaxWidth().padding(10.dp), singleLine = true, placeholder = { Text("Search messages…") })
        networkError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)) }

        LazyColumn(Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(visibleMessages, key = { it.id }) { message ->
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromMe) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom) {
                        if (!message.fromMe) {
                            FynxAvatar(message.senderName ?: chat.name, modifier = Modifier.size(30.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                        Surface(
                            color = if (message.fromMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (message.fromMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            shape = RoundedCornerShape(18.dp),
                            tonalElevation = 1.dp,
                            modifier = Modifier.widthIn(max = 320.dp)
                        ) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                                if (!message.fromMe && !message.senderName.isNullOrBlank()) Text(message.senderName!!, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 3.dp))
                                if (message.replyToId != null) {
                                    val replied = messages.firstOrNull { it.id == message.replyToId }
                                    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp)) {
                                        Text("↳ ${replied?.text?.take(80) ?: "Original message"}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(8.dp))
                                    }
                                }
                                if (message.voiceUri != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { playVoice(message) }) { Text(if (playingVoiceId == message.id) "Ⅱ" else "▶") }
                                        Column(Modifier.weight(1f)) {
                                            LinearProgressIndicator(progress = { if (playingVoiceId == message.id) 0.45f else 0f })
                                            Text((message.voiceDurationMs / 1000).toString() + "s", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                } else {
                                    if (message.attachmentUri != null) {
                                        Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().padding(bottom = if (message.text.isBlank()) 0.dp else 7.dp)) {
                                            Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(if (message.attachmentType == "video") Icons.Default.Videocam else Icons.Default.Image, "Media attachment")
                                                Spacer(Modifier.width(8.dp))
                                                Text(if (message.attachmentType == "video") "Video attached" else "Photo attached", style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                    if (message.text.isNotBlank()) SelectionContainer { Text(message.text) }
                                }
                                if (message.edited) Text("Edited", style = MaterialTheme.typography.labelSmall)
                                message.reaction?.let { Text(it) }
                                if (message.fromMe) Text(if (message.read) "✓✓ Read" else if (message.delivered) "✓✓ Delivered" else "✓ Sent", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromMe) Arrangement.End else Arrangement.Start) {
                        TextButton(onClick = { menuMessageId = if (menuMessageId == message.id) null else message.id }) { Text("More") }
                    }
                    if (menuMessageId == message.id) {
                        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = if (message.fromMe) Arrangement.End else Arrangement.Start) {
                            if (message.voiceUri == null && message.text.isNotBlank()) IconButton(onClick = { clipboardManager.setText(AnnotatedString(message.text)); menuMessageId = null }) { Icon(Icons.Default.ContentCopy, "Copy") }
                            IconButton(onClick = { replyToId = message.id; menuMessageId = null }) { Icon(Icons.Default.Reply, "Reply") }
                            if (message.fromMe && message.voiceUri == null && message.text.isNotBlank()) {
                                IconButton(onClick = { editingId = message.id; text = message.text; menuMessageId = null }) { Icon(Icons.Default.Edit, "Edit") }
                                IconButton(onClick = { messages = messages.filterNot { it.id == message.id }; menuMessageId = null }) { Icon(Icons.Default.Delete, "Delete") }
                            }
                        }
                    }
                }
            }
        }

        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp, modifier = Modifier.navigationBarsPadding().imePadding()) {
            Column(Modifier.fillMaxWidth().padding(8.dp)) {
                if (attachment != null) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (attachmentType == "video") Icons.Default.Videocam else Icons.Default.Image, null)
                        Text(if (attachmentType == "video") "Video ready to send" else "Photo ready to send", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { attachment = null; attachmentType = null }) { Icon(Icons.Default.Close, "Remove attachment") }
                    }
                }
                if (replyToId != null) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Replying to message", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); IconButton(onClick = { replyToId = null }) { Icon(Icons.Default.Close, "Cancel reply") }
                    }
                }
                if (isRecording) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(9.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)))
                            Spacer(Modifier.width(8.dp)); Text(if (isRecordingPaused) "Paused" else "Recording", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.width(8.dp)); Text(formatRecordingTime(recordingElapsed), style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.width(10.dp))
                            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                                repeat(18) { index ->
                                    val height = 5.dp + (((recordingElapsed / 100L + index * 7L) % 20L).toInt()).dp
                                    Box(Modifier.width(3.dp).height(height).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    IconButton(onClick = { showCamera = true }, enabled = !isRecording) { Icon(Icons.Default.CameraAlt, "Camera") }
                    IconButton(onClick = { imagePicker.launch("image/*") }, enabled = !isRecording) { Icon(Icons.Default.AttachFile, "Attach") }
                    OutlinedTextField(value = text, onValueChange = { value ->
                        val wasBlank = text.isBlank(); text = value
                        if (value.isBlank() && typingSent) { recipientUserId?.let { realtimeClient.sendTyping(it, false) }; typingSent = false }
                        else if (wasBlank && value.isNotBlank()) recipientUserId?.let { realtimeClient.sendTyping(it, true); typingSent = true }
                    }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(22.dp), placeholder = { Text(if (editingId == null) "Message…" else "Edit message…") }, maxLines = 5, enabled = !isRecording)
                    if (isRecording) {
                        IconButton(onClick = { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) runCatching { if (isRecordingPaused) { recorder?.resume(); isRecordingPaused = false } else { recorder?.pause(); isRecordingPaused = true } } }) { Icon(if (isRecordingPaused) Icons.Default.PlayArrow else Icons.Default.Pause, if (isRecordingPaused) "Resume recording" else "Pause recording") }
                        IconButton(onClick = { stopRecording() }) { Icon(Icons.Default.Stop, "Stop recording") }
                        IconButton(onClick = { cancelRecording() }) { Icon(Icons.Default.Close, "Cancel recording") }
                    } else if (text.isBlank() && attachment == null) {
                        IconButton(onClick = { startRecording() }) { Icon(Icons.Default.Mic, "Voice note") }
                    } else {
                        IconButton(onClick = {
                            val value = text.trim()
                            if (value.isNotEmpty() || attachment != null) {
                                if (editingId != null) {
                                    messages = messages.map { if (it.id == editingId) it.copy(text = value, edited = true) else it }
                                    text = ""; editingId = null; replyToId = null
                                } else {
                                    val recipient = recipientUserId
                                    if (recipient == null) networkError = "Unable to find this FYNX user."
                                    else {
                                        sending = true; networkError = null
                                        scope.launch {
                                            val selectedAttachment = attachment
                                            if (selectedAttachment != null) {
                                                val selectedType = attachmentType ?: "image"
                                                FynxProductionMessaging.uploadMedia(context, selectedAttachment)
                                                    .mapCatching { media -> FynxProductionMessaging.sendText(context, chat.username.removePrefix("@"), value, replyToId, media.id, selectedType, 0L).getOrThrow() }
                                            } else FynxProductionMessaging.sendText(context, chat.username.removePrefix("@"), value, replyToId)
                                                .onSuccess { remote ->
                                                    currentUserId?.let { myId -> messages = (messages.filterNot { it.id == remote.id } + FynxProductionMessaging.toChatMessage(remote, myId)).sortedBy { it.timestamp } }
                                                    realtimeClient.sendTyping(recipient, false); typingSent = false
                                                    text = ""; editingId = null; replyToId = null; attachment = null; attachmentType = null
                                                }
                                                .onFailure { networkError = it.message ?: "Message could not be sent" }
                                            sending = false
                                        }
                                    }
                                }
                            }
                        }, enabled = !sending) { Icon(if (editingId == null) Icons.Default.Send else Icons.Default.Edit, if (editingId == null) "Send" else "Save") }
                    }
                }
            }
        }
    }

    if (showCamera) {
        Dialog(onDismissRequest = { showCamera = false }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize()) {
                FynxCameraCapturePanel(onCaptured = { uri, type -> attachment = uri; attachmentType = type; showCamera = false })
            }
        }
    }

    if (showGifts) {
        AlertDialog(onDismissRequest = { showGifts = false }, title = { Text("Send a gift") }, text = { Column(Modifier.fillMaxWidth().heightIn(max = 420.dp)) { GiftsPanel(recipientName = chat.name, onGiftSelected = { showGifts = false }) } }, confirmButton = { TextButton(onClick = { showGifts = false }) { Text("Close") } })
    }
}

private fun formatRecordingTime(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000L
    return "%02d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

private fun formatChatTime(timestamp: Long): String {
    val elapsed = System.currentTimeMillis() - timestamp
    return when {
        elapsed < 60_000L -> "Now"
        elapsed < 3_600_000L -> "${elapsed / 60_000L}m"
        elapsed < 86_400_000L -> "${elapsed / 3_600_000L}h"
        else -> "${elapsed / 86_400_000L}d"
    }
}

@Suppress("DEPRECATION")
private fun createCompatibleMediaRecorder(context: android.content.Context): MediaRecorder =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
