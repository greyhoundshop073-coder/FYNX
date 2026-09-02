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
import java.io.File

@Composable
fun ConversationPanel(chat: ChatPreview, onBack: () -> Unit, onOpenProfile: (String) -> Unit = {}, onVoiceCall: () -> Unit = {}, onVideoCall: () -> Unit = {}) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val fallbackMessage = remember(chat.lastMessage) { chat.lastMessage.takeIf { it.isNotBlank() }?.let { ChatMessage(it, false, id = "initial", delivered = true, read = true) } }
    var text by remember(chat.username) { mutableStateOf("") }
    var messages by remember(chat.username) { mutableStateOf(FynxChatStore.load(context, chat.username, fallbackMessage)) }
    var replyToId by remember { mutableStateOf<String?>(null) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var attachment by remember { mutableStateOf<Uri?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var recordingStartedAt by remember { mutableStateOf(0L) }
    var playingVoiceId by remember { mutableStateOf<String?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var menuMessageId by remember { mutableStateOf<String?>(null) }
    var showGifts by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { attachment = it }
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
                    recorder = this; recordingFile = file; recordingStartedAt = System.currentTimeMillis(); isRecording = true
                }
            }
        }
    }

    LaunchedEffect(chat.username, messages) {
        FynxChatStore.save(context, chat.username, messages)
        val latest = messages.maxByOrNull { it.timestamp }
        if (latest != null) {
            val previewText = when {
                latest.voiceUri != null -> "Voice message"
                latest.attachmentUri != null && latest.text.isBlank() -> "Photo"
                else -> latest.text
            }
            FynxChatStore.savePreview(context, chat.copy(lastMessage = previewText, time = formatChatTime(latest.timestamp)))
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { recorder?.stop() }
            recorder?.release(); player?.release()
        }
    }

    fun startRecording() = microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
    fun cancelRecording() {
        recorder?.release(); recorder = null; recordingFile?.delete(); recordingFile = null; isRecording = false
    }
    fun stopRecording() {
        val r = recorder ?: return
        val file = recordingFile
        val duration = System.currentTimeMillis() - recordingStartedAt
        runCatching { r.stop() }; r.release(); recorder = null; isRecording = false; recordingFile = null
        if (file != null && file.exists() && file.length() > 0L && duration >= 300L) {
            messages = messages + ChatMessage("Voice message", true, id = System.currentTimeMillis().toString(), delivered = true, voiceUri = file.absolutePath, voiceDurationMs = duration)
        } else file?.delete()
    }
    fun playVoice(message: ChatMessage) {
        player?.release()
        player = runCatching {
            MediaPlayer().apply { setDataSource(message.voiceUri); prepare(); setOnCompletionListener { playingVoiceId = null }; start() }
        }.getOrNull()
        playingVoiceId = if (player != null) message.id else null
    }

    val visibleMessages = if (searchQuery.isBlank()) messages else messages.filter { it.text.contains(searchQuery, ignoreCase = true) }

    Column(Modifier.fillMaxSize().background(FynxDesign.Background)) {
        Surface(color = FynxDesign.Surface, tonalElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { Text("‹", style = MaterialTheme.typography.headlineSmall) }
                    IconButton(onClick = { onOpenProfile(chat.username) }) {
                        FynxAvatar(chat.name, modifier = Modifier.size(46.dp))
                    }
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        TextButton(onClick = { onOpenProfile(chat.username) }, contentPadding = PaddingValues(0.dp)) {
                            Text(chat.name, style = MaterialTheme.typography.titleMedium)
                        }
                        Text(if (chat.online) "● Online" else chat.username, style = MaterialTheme.typography.bodySmall, color = FynxDesign.TextSecondary)
                    }
                    IconButton(onClick = onVoiceCall) { Icon(Icons.Default.Call, "Voice call") }
                    IconButton(onClick = onVideoCall) { Icon(Icons.Default.Videocam, "Video call") }
                    IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) searchQuery = "" }) {
                        Icon(if (searchOpen) Icons.Default.Close else Icons.Default.Search, "Search")
                    }
                    IconButton(onClick = { showGifts = true }) { Icon(Icons.Default.CardGiftcard, "Send gift") }
                }
            }
        }

        if (searchOpen) OutlinedTextField(searchQuery, { searchQuery = it }, Modifier.fillMaxWidth().padding(10.dp), singleLine = true, placeholder = { Text("Search messages…") })

        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(visibleMessages, key = { it.id }) { message ->
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromMe) Arrangement.End else Arrangement.Start) {
                        Surface(color = if (message.fromMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(18.dp), tonalElevation = 1.dp, modifier = Modifier.widthIn(max = 320.dp)) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                                if (message.voiceUri != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { playVoice(message) }) { Text(if (playingVoiceId == message.id) "Ⅱ" else "▶") }
                                        Column(Modifier.weight(1f)) {
                                            LinearProgressIndicator(progress = { if (playingVoiceId == message.id) 0.45f else 0f })
                                            Text((message.voiceDurationMs / 1000).toString() + "s", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                } else SelectionContainer { Text(message.text) }
                                if (message.edited) Text("Edited", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                message.reaction?.let { Text(it) }
                                if (message.fromMe) Text(if (message.read) "Read" else if (message.delivered) "Delivered" else "Sent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromMe) Arrangement.End else Arrangement.Start) {
                        TextButton(onClick = { menuMessageId = if (menuMessageId == message.id) null else message.id }) { Text("More") }
                    }
                    if (menuMessageId == message.id) {
                        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = if (message.fromMe) Arrangement.End else Arrangement.Start) {
                            if (message.voiceUri == null) IconButton(onClick = { clipboardManager.setText(AnnotatedString(message.text)); menuMessageId = null }) { Icon(Icons.Default.ContentCopy, "Copy") }
                            IconButton(onClick = { replyToId = message.id; menuMessageId = null }) { Icon(Icons.Default.Reply, "Reply") }
                            if (message.fromMe && message.voiceUri == null) {
                                IconButton(onClick = { editingId = message.id; text = message.text; menuMessageId = null }) { Icon(Icons.Default.Edit, "Edit") }
                                IconButton(onClick = { messages = messages.filterNot { it.id == message.id }; menuMessageId = null }) { Icon(Icons.Default.Delete, "Delete") }
                            }
                        }
                    }
                }
            }
        }

        Surface(color = FynxDesign.Surface, tonalElevation = 3.dp, modifier = Modifier.navigationBarsPadding().imePadding()) {
            Column(Modifier.fillMaxWidth().padding(8.dp)) {
                if (attachment != null) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Image, null)
                        Text("Photo attached", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { attachment = null }) { Icon(Icons.Default.Close, "Remove attachment") }
                    }
                }
                if (replyToId != null) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Replying to message", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { replyToId = null }) { Icon(Icons.Default.Close, "Cancel reply") }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    IconButton(onClick = { imagePicker.launch("image/*") }) { Icon(Icons.Default.AttachFile, "Attach") }
                    OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(22.dp), placeholder = { Text(if (editingId == null) "Message…" else "Edit message…") }, maxLines = 5)
                    if (isRecording) {
                        IconButton(onClick = { stopRecording() }) { Icon(Icons.Default.Stop, "Stop recording") }
                        IconButton(onClick = { cancelRecording() }) { Icon(Icons.Default.Close, "Cancel recording") }
                    } else if (text.isBlank()) {
                        IconButton(onClick = { startRecording() }) { Icon(Icons.Default.Mic, "Voice note") }
                    } else {
                        IconButton(onClick = {
                            val value = text.trim()
                            if (value.isNotEmpty()) {
                                if (editingId != null) messages = messages.map { if (it.id == editingId) it.copy(text = value, edited = true) else it }
                                else messages = messages + ChatMessage(value, true, id = System.currentTimeMillis().toString(), delivered = true, replyToId = replyToId, attachmentUri = attachment?.toString(), attachmentType = if (attachment != null) "image" else null)
                                text = ""; editingId = null; replyToId = null; attachment = null
                            }
                        }) { Icon(if (editingId == null) Icons.Default.Send else Icons.Default.Edit, if (editingId == null) "Send" else "Save") }
                    }
                }
            }
        }
    }

    if (showGifts) {
        AlertDialog(onDismissRequest = { showGifts = false }, title = { Text("Send a gift") }, text = { Column(Modifier.fillMaxWidth().heightIn(max = 420.dp)) { GiftsPanel(recipientName = chat.name, onGiftSelected = { showGifts = false }) } }, confirmButton = { TextButton(onClick = { showGifts = false }) { Text("Close") } })
    }
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
