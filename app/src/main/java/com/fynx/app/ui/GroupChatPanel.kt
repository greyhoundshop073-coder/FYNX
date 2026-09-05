package com.fynx.app.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import java.util.UUID

@Composable
fun GroupChatPanel(
    group: GroupChat,
    currentUsername: String,
    onBack: () -> Unit,
    onGroupChanged: (GroupChat) -> Unit = {}
) {
    val context = LocalContext.current
    val isAdmin = group.isAdmin(currentUsername)
    var newMember by remember { mutableStateOf("") }
    var description by remember { mutableStateOf(group.description) }
    var text by remember { mutableStateOf("") }
    var messages by remember(group.name) { mutableStateOf(loadGroupMessages(context, group.id)) }
    var attachment by remember { mutableStateOf<Uri?>(null) }
    var attachmentType by remember { mutableStateOf("image") }
    var showFynxCamera by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var recordingStartedAt by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableLongStateOf(0L) }

    fun feedback() {
        if (Build.VERSION.SDK_INT >= 31) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(35L, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.vibrate(35L)
        }
        runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60).startTone(ToneGenerator.TONE_PROP_ACK, 70) }
    }

    fun addMessage(message: ChatMessage) {
        messages = messages + message
        saveGroupMessages(context, group.id, messages)
        feedback()
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { attachment = uri; attachmentType = "image" }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { attachment = uri; attachmentType = "video" }
    }
    // Keep legacy capture contracts available, but use the shared FYNX camera for the
    // group-chat experience so preview, front/back switching and editing are consistent.
    val cameraPhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok && attachment != null) attachmentType = "image" else if (!ok) attachment = null
    }
    val cameraVideo = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok ->
        if (ok && attachment != null) attachmentType = "video" else if (!ok) attachment = null
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val uri = createCaptureUri(context, "image")
            attachment = uri
            attachmentType = "image"
            cameraPhoto.launch(uri)
        }
    }
    val videoCameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val uri = createCaptureUri(context, "video")
            attachment = uri
            attachmentType = "video"
            cameraVideo.launch(uri)
        }
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) return@rememberLauncherForActivityResult
        val file = File(context.cacheDir, "group_voice_${System.currentTimeMillis()}.m4a")
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
                elapsed = 0L
                isRecording = true
            }
        }
    }

    LaunchedEffect(isRecording, recordingStartedAt) {
        while (isRecording) {
            elapsed = (System.currentTimeMillis() - recordingStartedAt).coerceAtLeast(0L)
            kotlinx.coroutines.delay(150L)
        }
    }

    fun stopVoice() {
        val r = recorder ?: return
        val file = recordingFile
        val duration = System.currentTimeMillis() - recordingStartedAt
        runCatching { r.stop() }
        r.release(); recorder = null; recordingFile = null; isRecording = false; elapsed = 0L
        if (file != null && file.exists() && file.length() > 0L && duration >= 300L) {
            addMessage(ChatMessage("Voice message", true, UUID.randomUUID().toString(), delivered = true, read = true, voiceUri = file.absolutePath, voiceDurationMs = duration))
        } else file?.delete()
    }

    fun send() {
        if (text.isBlank() && attachment == null) return
        addMessage(ChatMessage(text.trim().ifBlank { if (attachmentType == "video") "Video" else "Photo" }, true, UUID.randomUUID().toString(), delivered = true, read = true, attachmentUri = attachment?.toString(), attachmentType = attachmentType))
        text = ""
        attachment = null
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Surface(tonalElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹") }
                FynxAvatar(group.name, Modifier.size(42.dp))
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(group.name, style = MaterialTheme.typography.titleMedium)
                    Text("${group.memberUsernames.size} members", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { }) { Icon(Icons.Default.Groups, "Group") }
            }
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(bottom = 10.dp)) {
            items(messages, key = { it.id }) { message ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromMe) Arrangement.End else Arrangement.Start) {
                    Surface(color = if (message.fromMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(18.dp), modifier = Modifier.widthIn(max = 330.dp)) {
                        Column(Modifier.padding(10.dp)) {
                            if (message.attachmentUri != null) {
                                AndroidView(factory = { ImageViewWithUri(context, message.attachmentUri!!, message.attachmentType ?: "image") }, modifier = Modifier.sizeIn(maxWidth = 290.dp, maxHeight = 240.dp))
                            }
                            if (message.voiceUri != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.GraphicEq, "Voice")
                                    Spacer(Modifier.width(8.dp))
                                    Text("Voice • ${message.voiceDurationMs / 1000}s")
                                }
                            }
                            if (message.text.isNotBlank()) Text(message.text)
                            if (message.fromMe) Text("✓✓", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        Surface(tonalElevation = 3.dp, modifier = Modifier.navigationBarsPadding().imePadding()) {
            Column(Modifier.fillMaxWidth().padding(8.dp)) {
                if (attachment != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp)) {
                        Icon(if (attachmentType == "video") Icons.Default.Videocam else Icons.Default.Image, null)
                        Text(if (attachmentType == "video") "Video ready" else "Photo ready", Modifier.weight(1f))
                        IconButton(onClick = { attachment = null }) { Icon(Icons.Default.Close, "Remove") }
                    }
                }
                if (isRecording) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("●", color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text("Recording ${elapsed / 1000}s", Modifier.weight(1f))
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
                                repeat(10) { i -> Box(Modifier.width(4.dp).height((8 + ((elapsed / 120 + i * 5) % 18)).toInt().dp).background(MaterialTheme.colorScheme.primary, CircleShape)) }
                            }
                            TextButton(onClick = { recorder?.release(); recorder = null; recordingFile?.delete(); recordingFile = null; isRecording = false; elapsed = 0L }) { Text("Cancel") }
                            Button(onClick = { stopVoice() }) { Text("Send") }
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { imagePicker.launch("image/*") }) { Icon(Icons.Default.PhotoLibrary, "Choose photo") }
                        IconButton(onClick = { showFynxCamera = true }) { Icon(Icons.Default.PhotoCamera, "Open FYNX camera") }
                        IconButton(onClick = { videoPicker.launch("video/*") }) { Icon(Icons.Default.VideoLibrary, "Choose video") }
                        IconButton(onClick = { videoCameraPermission.launch(Manifest.permission.CAMERA) }) { Icon(Icons.Default.Videocam, "Record video") }
                        OutlinedTextField(text, { text = it.take(4000) }, Modifier.weight(1f), placeholder = { Text("Message group…") }, singleLine = true)
                        IconButton(onClick = { microphonePermission.launch(Manifest.permission.RECORD_AUDIO) }) { Icon(Icons.Default.Mic, "Record voice") }
                        IconButton(onClick = { send() }, enabled = text.isNotBlank() || attachment != null) { Icon(Icons.Default.Send, "Send") }
                    }
                }
            }
        }

        if (showFynxCamera) {
            Dialog(
                onDismissRequest = { showFynxCamera = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    FynxCameraCapturePanel(
                        onCaptured = { uri, type ->
                            attachment = uri
                            attachmentType = type
                            showFynxCamera = false
                        },
                        onDismiss = { showFynxCamera = false }
                    )
                }
            }
        }

        if (isAdmin) {
            Surface(tonalElevation = 1.dp) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(description, { description = it; onGroupChanged(group.copy(description = it)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Group description") })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(newMember, { newMember = it }, Modifier.weight(1f), singleLine = true, label = { Text("Username to add") })
                        Button(enabled = newMember.isNotBlank(), onClick = { onGroupChanged(group.addMember(newMember.trim())); newMember = "" }) { Text("Add") }
                    }
                }
            }
        }
    }
}

private fun createCaptureUri(context: Context, type: String): Uri {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "FYNX_${System.currentTimeMillis()}.${if (type == "video") "mp4" else "jpg"}")
        put(MediaStore.MediaColumns.MIME_TYPE, if (type == "video") "video/mp4" else "image/jpeg")
        if (Build.VERSION.SDK_INT >= 29) put(MediaStore.MediaColumns.RELATIVE_PATH, if (type == "video") "Movies/FYNX" else "Pictures/FYNX")
    }
    return resolver.insert(if (type == "video") MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: error("Unable to create capture destination")
}

private fun saveGroupMessages(context: Context, groupId: String, messages: List<ChatMessage>) {
    val array = org.json.JSONArray()
    messages.forEach { m ->
        array.put(org.json.JSONObject().apply {
            put("id", m.id); put("text", m.text); put("fromMe", m.fromMe); put("timestamp", m.timestamp)
            put("delivered", m.delivered); put("read", m.read); put("attachmentUri", m.attachmentUri ?: "")
            put("attachmentType", m.attachmentType); put("voiceUri", m.voiceUri ?: ""); put("voiceDurationMs", m.voiceDurationMs)
        })
    }
    context.getSharedPreferences("fynx_group_messages", Context.MODE_PRIVATE).edit().putString(groupId, array.toString()).apply()
}

private fun loadGroupMessages(context: Context, groupId: String): List<ChatMessage> {
    val raw = context.getSharedPreferences("fynx_group_messages", Context.MODE_PRIVATE).getString(groupId, null) ?: return emptyList()
    return runCatching {
        val array = org.json.JSONArray(raw)
        List(array.length()) { i ->
            val o = array.getJSONObject(i)
            ChatMessage(o.optString("text"), o.optBoolean("fromMe"), o.optString("id"), o.optLong("timestamp"), o.optBoolean("delivered"), o.optBoolean("read"), attachmentUri = o.optString("attachmentUri").ifBlank { null }, attachmentType = o.optString("attachmentType", "image"), voiceUri = o.optString("voiceUri").ifBlank { null }, voiceDurationMs = o.optLong("voiceDurationMs"))
        }
    }.getOrElse { emptyList() }
}

private fun ImageViewWithUri(context: Context, uriString: String, type: String): android.widget.ImageView {
    return android.widget.ImageView(context).apply {
        scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        if (type == "image") setImageURI(runCatching { Uri.parse(uriString) }.getOrNull())
        else setImageResource(android.R.drawable.ic_media_play)
    }
}

private fun createCompatibleMediaRecorder(context: Context): MediaRecorder =
    if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
