package com.fynx.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.widget.ImageView
import android.widget.VideoView
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

@Composable
fun FynxStatusComposerPanel(onClose: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = remember(context) { FynxAuthStore.load(context) }
    val username = auth.username?.removePrefix("@").orEmpty().ifBlank { "preview" }
    val displayName = username.ifBlank { "You" }
    var type by remember { mutableStateOf(FynxStatusType.TEXT) }
    var text by remember { mutableStateOf("") }
    var mediaUri by remember { mutableStateOf<Uri?>(null) }
    var background by remember { mutableLongStateOf(0xFF111111) }
    var foreground by remember { mutableLongStateOf(0xFFFFFFFF) }
    var audience by remember { mutableStateOf(FynxStatusAudience.EVERYONE) }
    var preview by remember { mutableStateOf(false) }
    var publishing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var recording by remember { mutableStateOf(false) }
    var recordingStarted by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { uri -> mediaUri = uri; type = FynxStatusType.PHOTO; preview = false } }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { uri -> mediaUri = uri; type = FynxStatusType.VIDEO; preview = false } }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startStatusRecording(context) { r, f -> recorder = r; recordingFile = f; recordingStarted = System.currentTimeMillis(); recording = true }
        else error = "Microphone permission is required for a voice Status."
    }

    LaunchedEffect(recording, recordingStarted) {
        while (recording) {
            elapsed = System.currentTimeMillis() - recordingStarted
            if (elapsed >= FYNX_STATUS_MAX_VOICE_DURATION_MS) stopStatusRecording(recorder, recordingFile) { uri -> mediaUri = uri; recorder = null; recordingFile = null; recording = false; type = FynxStatusType.VOICE }
            delay(200)
        }
    }
    DisposableEffect(Unit) { onDispose { runCatching { recorder?.stop() }; recorder?.release() } }

    fun publish() {
        if (publishing) return
        if (type == FynxStatusType.TEXT && text.isBlank()) { error = "Write something first."; return }
        if (type != FynxStatusType.TEXT && mediaUri == null) { error = "Choose media first."; return }
        publishing = true; error = null
        scope.launch {
            try {
                val source = mediaUri
                val mediaId = if (type == FynxStatusType.TEXT) null else {
                    val mime = context.contentResolver.getType(source!!) ?: when (type) {
                        FynxStatusType.PHOTO -> "image/jpeg"
                        FynxStatusType.VIDEO -> "video/mp4"
                        FynxStatusType.VOICE -> "audio/mp4"
                        FynxStatusType.TEXT -> "text/plain"
                    }
                    FynxStatusClient.uploadMedia(context, source, mime).getOrElse { error = it.message ?: "Media upload failed."; return@launch }
                }
                val now = System.currentTimeMillis()
                val status = FynxStatus(
                    id = UUID.randomUUID().toString(), ownerUsername = username, ownerDisplayName = displayName,
                    type = type, text = text.trim().ifBlank { null }, createdAtMillis = now,
                    expiresAtMillis = now + FYNX_STATUS_EXPIRY_MS,
                    textStyle = FynxStatusTextStyle(background, foreground),
                    privateStatus = audience == FynxStatusAudience.FRIENDS,
                    voiceDurationMs = if (type == FynxStatusType.VOICE) elapsed else 0L,
                    audience = audience
                )
                FynxStatusClient.create(context, status, mediaId).getOrElse { error = it.message ?: "Status publishing failed."; return@launch }
                FynxStatusStore.save(context, status.copy(contentUri = mediaId?.let { "/api/media/$it" }))
                mediaUri = null; text = ""; preview = false
            } finally { publishing = false }
        }
    }

    if (preview) {
        FynxStatusPreview(type, mediaUri, text, background, foreground, elapsed, { preview = false }, ::publish)
        return
    }

    Column(Modifier.fillMaxSize().padding(14.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Create Status", style = MaterialTheme.typography.headlineSmall)
                Text("Photo, video, text or voice • expires after 24 hours", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onClose) { Text("Close") }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(FynxStatusType.values().toList()) { option -> FilterChip(selected = type == option, onClick = { if (!recording) type = option }, label = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) }) }
        }
        when (type) {
            FynxStatusType.TEXT -> {
                StatusTextCanvas(text, background, foreground, Modifier.fillMaxWidth().height(300.dp))
                OutlinedTextField(value = text, onValueChange = { text = it.take(FYNX_STATUS_MAX_TEXT_LENGTH) }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 6, placeholder = { Text("Write a Status…") }, supportingText = { Text("${text.length}/$FYNX_STATUS_MAX_TEXT_LENGTH") })
                Text("Background", style = MaterialTheme.typography.labelLarge)
                ColorChoices(listOf(0xFF111111,0xFF6A1B9A,0xFF1565C0,0xFF00695C,0xFF2E7D32,0xFFEF6C00,0xFFC62828,0xFFAD1457), background) { background = it }
            }
            FynxStatusType.PHOTO -> MediaPickerCard("Photo", mediaUri) { pickImage.launch(arrayOf("image/*")) }
            FynxStatusType.VIDEO -> MediaPickerCard("Video", mediaUri) { pickVideo.launch(arrayOf("video/*")) }
            FynxStatusType.VOICE -> VoiceRecorderCard(recording, elapsed, mediaUri != null, {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startStatusRecording(context) { r, f -> recorder = r; recordingFile = f; recordingStarted = System.currentTimeMillis(); elapsed = 0L; recording = true } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }, { stopStatusRecording(recorder, recordingFile) { uri -> mediaUri = uri; recorder = null; recordingFile = null; recording = false; type = FynxStatusType.VOICE } })
        }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Who can see this Status?", style = MaterialTheme.typography.titleSmall)
                AudienceOption("Everyone", FynxStatusAudience.EVERYONE, audience) { audience = it }
                AudienceOption("Friends only", FynxStatusAudience.FRIENDS, audience) { audience = it }
                Text("FYNX enforces the selected audience on the server.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { preview = true }, enabled = !recording && !publishing && ((type == FynxStatusType.TEXT && text.isNotBlank()) || (type != FynxStatusType.TEXT && mediaUri != null)), modifier = Modifier.fillMaxWidth()) { Text(if (publishing) "Publishing…" else "Preview Status") }
    }
}

@Composable private fun AudienceOption(label: String, value: FynxStatusAudience, selected: FynxStatusAudience, onSelect: (FynxStatusAudience) -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected == value, { onSelect(value) }); Text(label) } }

@Composable private fun MediaPickerCard(label: String, uri: Uri?, onPick: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("$label Status", style = MaterialTheme.typography.titleMedium)
        if (uri != null) LocalStatusMediaPreview(uri, label.lowercase(), Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 420.dp))
        Text(uri?.lastPathSegment ?: "No $label selected", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Button(onClick = onPick, modifier = Modifier.fillMaxWidth()) { Text(if (uri == null) "Choose $label" else "Choose another") }
    } }
}

@Composable private fun VoiceRecorderCard(recording: Boolean, elapsed: Long, hasVoice: Boolean, onRecord: () -> Unit, onStop: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (recording) "Recording voice Status" else "Voice Status", style = MaterialTheme.typography.titleMedium)
        Text(if (recording) formatStatusTime(elapsed) else if (hasVoice) "Voice ready" else "Up to 30 seconds")
        if (recording) LinearProgressIndicator(progress = { (elapsed.toFloat() / FYNX_STATUS_MAX_VOICE_DURATION_MS).coerceIn(0f,1f) }, Modifier.fillMaxWidth())
        Button(onClick = if (recording) onStop else onRecord, modifier = Modifier.fillMaxWidth()) { Text(if (recording) "Stop recording" else if (hasVoice) "Record again" else "Record voice") }
    } }
}

@Composable private fun LocalStatusMediaPreview(uri: Uri, kind: String, modifier: Modifier) {
    if (kind == "video") AndroidView(modifier = modifier, factory = { context -> VideoView(context).apply { setVideoURI(uri); setMediaController(android.widget.MediaController(context)); setOnPreparedListener { it.isLooping = true; start() } } }, update = { it.setVideoURI(uri) })
    else AndroidView(modifier = modifier, factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setImageURI(uri) } }, update = { it.setImageURI(uri) })
}

@Composable private fun FynxStatusPreview(type: FynxStatusType, uri: Uri?, text: String, background: Long, foreground: Long, durationMs: Long, onBack: () -> Unit, onPublish: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Status preview", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f)); TextButton(onClick = onBack) { Text("Edit") } }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            when (type) {
                FynxStatusType.TEXT -> StatusTextCanvas(text, background, foreground, Modifier.fillMaxSize())
                FynxStatusType.PHOTO -> uri?.let { LocalStatusMediaPreview(it, "image", Modifier.fillMaxWidth().heightIn(min=320.dp, max=620.dp)) }
                FynxStatusType.VIDEO -> uri?.let { LocalStatusMediaPreview(it, "video", Modifier.fillMaxWidth().heightIn(min=320.dp, max=620.dp)) }
                FynxStatusType.VOICE -> Card(Modifier.fillMaxWidth()) { Text("Voice Status • ${formatStatusTime(durationMs)}", Modifier.padding(20.dp)) }
            }
        }
        Button(onClick = onPublish, modifier = Modifier.fillMaxWidth()) { Text("Share Status") }
    }
}

@Composable private fun StatusTextCanvas(text: String, background: Long, foreground: Long, modifier: Modifier) { Box(modifier.background(Color(background), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) { Text(text.ifBlank { "Your Status" }, color = Color(foreground), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(28.dp)) } }
@Composable private fun ColorChoices(colors: List<Long>, selected: Long, onSelected: (Long) -> Unit) { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(colors) { color -> FilterChip(selected == color, { onSelected(color) }, label = { Text("●", color = Color(color)) }) } } }
private fun startStatusRecording(context: Context, onStarted: (MediaRecorder, File) -> Unit) { val file = File(context.cacheDir, "fynx_status_voice_${System.currentTimeMillis()}.m4a"); runCatching { val r = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder(); r.apply { setAudioSource(MediaRecorder.AudioSource.MIC); setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); setAudioEncoder(MediaRecorder.AudioEncoder.AAC); setOutputFile(file.absolutePath); prepare(); start() }; onStarted(r, file) } }
private fun stopStatusRecording(recorder: MediaRecorder?, file: File?, onStopped: (Uri?) -> Unit) { runCatching { recorder?.stop() }; recorder?.release(); onStopped(file?.takeIf { it.exists() && it.length() > 0L }?.let(Uri::fromFile)) }
private fun formatStatusTime(milliseconds: Long): String = "%02d:%02d".format(milliseconds / 60_000L, (milliseconds / 1000L) % 60L)
