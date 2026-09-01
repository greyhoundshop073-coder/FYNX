package com.fynx.app.ui

import android.Manifest
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import java.io.File

@Composable
fun ConversationPanel(
    chat: ChatPreview,
    onBack: () -> Unit,
    onVoiceCall: () -> Unit = {},
    onVideoCall: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val fallbackMessage = remember(chat.lastMessage) {
        ChatMessage(chat.lastMessage, false, id = "initial", delivered = true, read = true)
    }
    var text by remember(chat.username) {
        mutableStateOf("")
    }
    var messages by remember(chat.username) {
        mutableStateOf(FynxChatStore.load(context, chat.username, fallbackMessage))
    }
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

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { attachment = it }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { attachment = it }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && !isRecording) {
            val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
            runCatching {
                MediaRecorder(context).apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setOutputFile(file.absolutePath)
                    prepare(); start()
                    recorder = this
                    recordingFile = file
                    recordingStartedAt = System.currentTimeMillis()
                    isRecording = true
                }
            }
        }
    }

    LaunchedEffect(chat.username, messages) {
        FynxChatStore.save(context, chat.username, messages)
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { recorder?.stop() }
            recorder?.release(); player?.release()
        }
    }

    fun startRecording() = microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
    fun cancelRecording() {
        recorder?.release(); recorder = null
        recordingFile?.delete(); recordingFile = null; isRecording = false
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

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹") }
                Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Text(chat.name, style = MaterialTheme.typography.titleMedium)
                    Text(if (chat.online) "online" else chat.username, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onVoiceCall) { Icon(Icons.Default.Call, "Voice call") }
                IconButton(onClick = onVideoCall) { Icon(Icons.Default.Videocam, "Video call") }
                IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) searchQuery = "" }) { Icon(if (searchOpen) Icons.Default.Close else Icons.Default.Search, "Search") }
                IconButton(onClick = { }) { Icon(Icons.Default.MoreVert, "More") }
            }
        }

        if (searchOpen) {
            OutlinedTextField(searchQuery, { searchQuery = it }, Modifier.fillMaxWidth().padding(10.dp), singleLine = true, placeholder = { Text("Search messages…") })
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(visibleMessages, key = { it.id }) { message ->
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromMe) Arrangement.End else Arrangement.Start) {
                        Surface(
                            color = if (message.fromMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(18.dp),
                            tonalElevation = 1.dp,
                            modifier = Modifier.widthIn(max = 320.dp)
                        ) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                                if (message.voiceUri != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { playVoice(message) }) { Text(if (playingVoiceId == message.id) "Ⅱ" else "▶") }
                                        Column(Modifier.weight(1f)) {
                                            LinearProgressIndicator(progress = { if (playingVoiceId == message.id) 0.45f else 0f })
                                            Text("${message.voiceDurationMs / 1000}s", style = MaterialTheme.typography.labelSmall)
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

        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().padding(8.dp)) {
                if (replyToId != null) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Replying to message", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { replyToId = null }) { Icon(Icons.Default.Close, "Cancel reply") }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    IconButton(onClick = { imagePicker.launch("image/*") }) { Icon(Icons.Default.AttachFile, "Attach") }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(22.dp),
                        placeholder = { Text(if (editingId == null) "Message…" else "Edit message…") },
                        maxLines = 5
                    )
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
                                else messages = messages + ChatMessage(value, true, id = System.currentTimeMillis().toString(), delivered = true, replyToId = replyToId)
                                text = ""; editingId = null; replyToId = null
                            }
                        }) { Icon(if (editingId == null) Icons.Default.Send else Icons.Default.Edit, if (editingId == null) "Send" else "Save") }
                    }
                }
            }
        }
    }
}
