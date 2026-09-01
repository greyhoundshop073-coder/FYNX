package com.fynx.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.AnnotatedString
import androidx.core.content.ContextCompat
import java.io.File

@Composable
fun ConversationPanel(chat: ChatPreview, onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var text by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf(ChatMessage(chat.lastMessage, false, id = "initial", delivered = true, read = true))) }
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
                    prepare()
                    start()
                    recorder = this
                    recordingFile = file
                    recordingStartedAt = System.currentTimeMillis()
                    isRecording = true
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { recorder?.stop() }
            recorder?.release()
            player?.release()
        }
    }

    fun startRecording() = microphonePermission.launch(Manifest.permission.RECORD_AUDIO)

    fun cancelRecording() {
        recorder?.release()
        recorder = null
        recordingFile?.delete()
        recordingFile = null
        isRecording = false
    }

    fun stopRecording() {
        val r = recorder ?: return
        val file = recordingFile
        val duration = System.currentTimeMillis() - recordingStartedAt
        runCatching { r.stop() }
        r.release()
        recorder = null
        isRecording = false
        recordingFile = null
        if (file != null && file.exists() && file.length() > 0L && duration >= 300L) {
            messages = messages + ChatMessage(
                "Voice message", true,
                id = System.currentTimeMillis().toString(),
                delivered = true,
                voiceUri = file.absolutePath,
                voiceDurationMs = duration
            )
        } else file?.delete()
    }

    fun playVoice(message: ChatMessage) {
        player?.release()
        player = runCatching {
            MediaPlayer().apply {
                setDataSource(message.voiceUri)
                prepare()
                setOnCompletionListener { playingVoiceId = null }
                start()
            }
        }.getOrNull()
        playingVoiceId = if (player != null) message.id else null
    }

    val visibleMessages = if (searchQuery.isBlank()) messages else messages.filter { it.text.contains(searchQuery, ignoreCase = true) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Column(Modifier.padding(start = 4.dp).weight(1f)) {
                Text(chat.name, style = MaterialTheme.typography.titleMedium)
                Text(if (chat.online) "Online" else chat.username, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) searchQuery = "" }) { Text(if (searchOpen) "Close" else "Search") }
        }
        if (searchOpen) OutlinedTextField(searchQuery, { searchQuery = it }, Modifier.fillMaxWidth().padding(horizontal = 10.dp), singleLine = true, placeholder = { Text("Search messages…") })
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visibleMessages) { message ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromMe) Arrangement.End else Arrangement.Start) {
                    Surface(tonalElevation = 1.dp, shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            if (message.voiceUri != null) {
                                TextButton(onClick = { playVoice(message) }) { Text(if (playingVoiceId == message.id) "Playing…" else "▶ Voice") }
                                Text("${message.voiceDurationMs / 1000}s", style = MaterialTheme.typography.labelSmall)
                            } else {
                                SelectionContainer { Text(message.text) }
                            }
                            if (message.edited) Text("Edited", style = MaterialTheme.typography.labelSmall)
                            message.reaction?.let { Text(it) }
                            if (message.fromMe) Text(if (message.read) "Read" else if (message.delivered) "Delivered" else "Sent", style = MaterialTheme.typography.labelSmall)
                            Row {
                                if (message.voiceUri == null) {
                                    TextButton(onClick = { clipboardManager.setText(AnnotatedString(message.text)) }) { Text("Copy") }
                                }
                                TextButton(onClick = { replyToId = message.id }) { Text("Reply") }
                                TextButton(onClick = { messages = messages.map { if (it.id == message.id) it.copy(reaction = if (it.reaction == "❤️") null else "❤️") else it } }) { Text("❤️") }
                                if (message.fromMe && message.voiceUri == null) {
                                    TextButton(onClick = { editingId = message.id; text = message.text }) { Text("Edit") }
                                    TextButton(onClick = { messages = messages.filterNot { it.id == message.id } }) { Text("Delete") }
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { imagePicker.launch("image/*") }) { Text("Photo") }
            TextButton(onClick = { imagePicker.launch("video/*") }) { Text("Video") }
            TextButton(onClick = { documentPicker.launch("*/*") }) { Text("File") }
            if (isRecording) {
                TextButton(onClick = { stopRecording() }) { Text("⏹ Stop") }
                TextButton(onClick = { cancelRecording() }) { Text("Cancel") }
            } else TextButton(onClick = { startRecording() }) { Text("🎤 Voice") }
            OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text(if (editingId == null) "Message…" else "Edit message…") })
            TextButton(onClick = {
                val value = text.trim()
                if (value.isNotEmpty()) {
                    if (editingId != null) messages = messages.map { if (it.id == editingId) it.copy(text = value, edited = true) else it }
                    else messages = messages + ChatMessage(value, true, id = System.currentTimeMillis().toString(), delivered = true, replyToId = replyToId)
                    text = ""; editingId = null; replyToId = null
                }
            }) { Text(if (editingId == null) "Send" else "Save") }
        }
    }
}
