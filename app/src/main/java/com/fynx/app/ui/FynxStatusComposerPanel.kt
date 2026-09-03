package com.fynx.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * FYNX Status composer: text, photo, video and voice, with 24-hour expiry,
 * styled text controls, real media pickers and a preview-before-publish flow.
 */
@Composable
fun FynxStatusComposerPanel(onClose: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = remember(context) { FynxAuthStore.load(context) }
    val username = auth.username?.removePrefix("@").orEmpty().ifBlank { "preview" }
    val displayName = username.ifBlank { "You" }
    var mode by remember { mutableStateOf(FynxStatusType.TEXT) }
    var text by remember { mutableStateOf("") }
    var background by remember { mutableLongStateOf(0xFF111111) }
    var foreground by remember { mutableLongStateOf(0xFFFFFFFF) }
    var font by remember { mutableStateOf(FynxStatusTextFont.CLASSIC) }
    var alignment by remember { mutableIntStateOf(1) }
    var mediaUri by remember { mutableStateOf<Uri?>(null) }
    var preview by remember { mutableStateOf(false) }
    var privateStatus by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var recordingStarted by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    var publishing by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { mediaUri = uri; mode = FynxStatusType.PHOTO; preview = false }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { mediaUri = uri; mode = FynxStatusType.VIDEO; preview = false }
    }
    val audioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startStatusRecording(context) { r, f -> recorder = r; recordingFile = f; recordingStarted = System.currentTimeMillis(); elapsed = 0L; recording = true }
        else error = "Microphone permission is required for voice Status."
    }

    LaunchedEffect(recording, recordingStarted) {
        while (recording) {
            elapsed = System.currentTimeMillis() - recordingStarted
            if (elapsed >= FYNX_STATUS_MAX_VOICE_DURATION_MS) stopStatusRecording(recorder, recordingFile) { uri -> mediaUri = uri; mode = FynxStatusType.VOICE; recorder = null; recordingFile = null; recording = false }
            delay(200L)
        }
    }

    DisposableEffect(Unit) { onDispose { runCatching { recorder?.stop() }; recorder?.release() } }

    fun publish() {
        if (publishing) return
        error = null
        if (mode == FynxStatusType.TEXT && text.isBlank()) { error = "Write something first."; return }
        publishing = true
        scope.launch {
            try {
                val sourceUri = mediaUri
                val persisted = if (mode == FynxStatusType.TEXT) null else sourceUri?.let { FynxStatusStore.persistMedia(context, it, mode) }
                if (mode != FynxStatusType.TEXT && persisted == null) { error = "FYNX could not save that media."; return@launch }
                val now = System.currentTimeMillis()
                val status = FynxStatus(
                    id = UUID.randomUUID().toString(), ownerUsername = username, ownerDisplayName = displayName,
                    type = mode, contentUri = persisted?.toString(), text = text.trim().ifBlank { null },
                    createdAtMillis = now, expiresAtMillis = now + FYNX_STATUS_EXPIRY_MS,
                    textStyle = FynxStatusTextStyle(background, foreground, font, alignment),
                    privateStatus = privateStatus, voiceDurationMs = if (mode == FynxStatusType.VOICE) elapsed else 0L
                )
                val mediaId = if (mode == FynxStatusType.TEXT) null else {
                    val mime = context.contentResolver.getType(sourceUri!!) ?: when (mode) {
                        FynxStatusType.PHOTO -> "image/jpeg"
                        FynxStatusType.VIDEO -> "video/mp4"
                        FynxStatusType.VOICE -> "audio/mp4"
                        FynxStatusType.TEXT -> "application/octet-stream"
                    }
                    FynxStatusClient.uploadMedia(context, sourceUri, mime).getOrElse {
                        error = it.message ?: "FYNX could not upload that media."
                        return@launch
                    }
                }
                FynxStatusClient.create(context, status, mediaId).getOrElse {
                    error = it.message ?: "FYNX could not publish the Status."
                    return@launch
                }
                FynxStatusStore.save(context, status.copy(contentUri = if (mediaId == null) status.contentUri else "/api/media/$mediaId"))
                savedMessage = "Status shared • expires in 24 hours"
                preview = false
                mediaUri = null
                text = ""
            } finally {
                publishing = false
            }
        }
    }

    if (preview) {
        FynxStatusPreview(statusType = mode, uri = mediaUri, text = text, background = background, foreground = foreground, font = font, alignment = alignment, voiceDurationMs = elapsed, onBack = { preview = false }, onPublish = ::publish)
        return
    }

    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Create Status", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Close") }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf(FynxStatusType.TEXT, FynxStatusType.PHOTO, FynxStatusType.VIDEO, FynxStatusType.VOICE)) { type ->
                FilterChip(selected = mode == type, onClick = { if (!recording) mode = type }, label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) })
            }
        }

        when (mode) {
            FynxStatusType.TEXT -> {
                StatusTextCanvas(text, background, foreground, font, alignment, Modifier.fillMaxWidth().height(280.dp))
                OutlinedTextField(value = text, onValueChange = { text = it.take(FYNX_STATUS_MAX_TEXT_LENGTH) }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 6, placeholder = { Text("Write a Status…") }, supportingText = { Text("${text.length}/$FYNX_STATUS_MAX_TEXT_LENGTH") })
                Text("Background", style = MaterialTheme.typography.labelLarge)
                ColorChoices(listOf(0xFF111111, 0xFF6A1B9A, 0xFF1565C0, 0xFF00695C, 0xFF2E7D32, 0xFFEF6C00, 0xFFC62828, 0xFFAD1457), background) { background = it }
                Text("Text color", style = MaterialTheme.typography.labelLarge)
                ColorChoices(listOf(0xFFFFFFFF, 0xFFFFEB3B, 0xFFFFCDD2, 0xFFB3E5FC, 0xFFC8E6C9), foreground) { foreground = it }
                Text("Font", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(FynxStatusTextFont.values().toList()) { f -> FilterChip(selected = font == f, onClick = { font = f }, label = { Text(f.name.lowercase().replaceFirstChar { it.uppercase() }) }) } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(0, 1, 2).forEach { a -> FilterChip(selected = alignment == a, onClick = { alignment = a }, label = { Text(listOf("Left", "Center", "Right")[a]) }) } }
            }
            FynxStatusType.PHOTO -> MediaChoiceCard("Photo Status", mediaUri, onChoose = { imagePicker.launch(arrayOf("image/*")) })
            FynxStatusType.VIDEO -> MediaChoiceCard("Video Status", mediaUri, onChoose = { videoPicker.launch(arrayOf("video/*")) })
            FynxStatusType.VOICE -> {
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (recording) "Recording ${formatStatusTime(elapsed)}" else "Voice Status • maximum 30 seconds", style = MaterialTheme.typography.titleMedium)
                    if (recording) {
                        LinearProgressIndicator(progress = { (elapsed.toFloat() / FYNX_STATUS_MAX_VOICE_DURATION_MS).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { repeat(24) { i -> Box(Modifier.width(3.dp).height((5 + ((elapsed / 80L + i * 5) % 24)).toInt().dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))) } }
                        Button(onClick = { stopStatusRecording(recorder, recordingFile) { uri -> mediaUri = uri; recorder = null; recordingFile = null; recording = false } }) { Text("Stop") }
                    } else {
                        Button(onClick = { if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startStatusRecording(context) { r, f -> recorder = r; recordingFile = f; recordingStarted = System.currentTimeMillis(); elapsed = 0L; recording = true } else audioPermission.launch(Manifest.permission.RECORD_AUDIO) }) { Text(if (mediaUri == null) "Record voice" else "Record again") }
                        if (mediaUri != null) Text("Voice ready for preview", color = MaterialTheme.colorScheme.primary)
                    }
                } }
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Private Status", style = MaterialTheme.typography.titleSmall); Text("Keep this Status owner-only until friend-audience sharing is selected.", style = MaterialTheme.typography.bodySmall) }
            Switch(checked = privateStatus, onCheckedChange = { privateStatus = it })
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        savedMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Button(onClick = { preview = true }, enabled = !recording && !publishing && (mode == FynxStatusType.TEXT && text.isNotBlank() || mode != FynxStatusType.TEXT && mediaUri != null), modifier = Modifier.fillMaxWidth()) { Text(if (publishing) "Publishing…" else "Preview Status") }
    }
}

@Composable
private fun FynxStatusPreview(statusType: FynxStatusType, uri: Uri?, text: String, background: Long, foreground: Long, font: FynxStatusTextFont, alignment: Int, voiceDurationMs: Long, onBack: () -> Unit, onPublish: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Preview", style = MaterialTheme.typography.headlineSmall)
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            when (statusType) {
                FynxStatusType.TEXT -> StatusTextCanvas(text, background, foreground, font, alignment, Modifier.fillMaxSize())
                FynxStatusType.PHOTO -> Text("Photo ready • ${uri?.lastPathSegment ?: "media"}")
                FynxStatusType.VIDEO -> Text("Video ready • ${uri?.lastPathSegment ?: "media"}")
                FynxStatusType.VOICE -> VoicePreview(uri, voiceDurationMs)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Edit") }; Button(onClick = onPublish, enabled = true, modifier = Modifier.weight(1f)) { Text("Share") } }
    }
}

@Composable
private fun StatusTextCanvas(text: String, background: Long, foreground: Long, font: FynxStatusTextFont, alignment: Int, modifier: Modifier) {
    val family = when (font) { FynxStatusTextFont.SERIF -> FontFamily.Serif; FynxStatusTextFont.TYPEWRITER -> FontFamily.Monospace; else -> FontFamily.Default }
    val weight = if (font == FynxStatusTextFont.BOLD) FontWeight.Bold else FontWeight.Normal
    Box(modifier.background(Color(background), RoundedCornerShape(18.dp)).padding(24.dp), contentAlignment = Alignment.Center) { Text(text.ifBlank { "Your Status" }, color = Color(foreground), style = TextStyle(fontFamily = family, fontWeight = weight, fontSize = 24.sp, textAlign = listOf(TextAlign.Start, TextAlign.Center, TextAlign.End)[alignment]), modifier = Modifier.fillMaxWidth()) }
}

@Composable
private fun ColorChoices(colors: List<Long>, selected: Long, onSelected: (Long) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(colors) { color -> FilterChip(selected = selected == color, onClick = { onSelected(color) }, label = { Text("●", color = Color(color)) }) } }
}

@Composable
private fun MediaChoiceCard(title: String, uri: Uri?, onChoose: () -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(uri?.lastPathSegment ?: "No media selected", color = MaterialTheme.colorScheme.onSurfaceVariant); Button(onClick = onChoose) { Text(if (uri == null) "Choose media" else "Choose another") } } }
}

@Composable
private fun VoicePreview(uri: Uri?, duration: Long) {
    var player by remember(uri) { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(uri) { onDispose { player?.release() } }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) { Button(onClick = { player?.release(); player = runCatching { MediaPlayer().apply { setDataSource(uri.toString()); prepare(); start() } }.getOrNull() }) { Text("Play") }; Text("${duration / 1000}s voice") }
}

private fun startStatusRecording(context: Context, onStarted: (MediaRecorder, File) -> Unit) {
    val file = File(context.cacheDir, "fynx_status_voice_${System.currentTimeMillis()}.m4a")
    runCatching {
        val recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        recorder.apply { setAudioSource(MediaRecorder.AudioSource.MIC); setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); setAudioEncoder(MediaRecorder.AudioEncoder.AAC); setOutputFile(file.absolutePath); prepare(); start() }
        onStarted(recorder, file)
    }
}

private fun stopStatusRecording(recorder: MediaRecorder?, file: File?, onStopped: (Uri?) -> Unit) {
    runCatching { recorder?.stop() }
    recorder?.release()
    onStopped(file?.takeIf { it.exists() && it.length() > 0L }?.let(Uri::fromFile))
}

private fun formatStatusTime(milliseconds: Long): String = "%02d:%02d".format(milliseconds / 60_000L, (milliseconds / 1000L) % 60L)
