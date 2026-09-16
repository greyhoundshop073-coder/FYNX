package com.fynx.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

private val MATURE_STATUS_BACKGROUNDS = listOf(0xFF111111, 0xFF4527A0, 0xFF1565C0, 0xFF00695C, 0xFF2E7D32, 0xFFEF6C00, 0xFFC62828, 0xFFAD1457, 0xFF37474F, 0xFF455A64)
private val MATURE_STATUS_TEXT_COLORS = listOf(0xFFFFFFFF, 0xFF000000, 0xFFFFEB3B, 0xFFFFCDD2, 0xFFB3E5FC)

@Composable
fun FynxMatureStatusComposerPanel(onClose: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = remember(context) { FynxAuthStore.load(context) }
    val username = auth.username?.removePrefix("@").orEmpty().ifBlank { "preview" }
    val displayName = username.ifBlank { "You" }
    var type by remember { mutableStateOf(FynxStatusType.TEXT) }
    var text by remember { mutableStateOf("") }
    var mediaUri by remember { mutableStateOf<Uri?>(null) }
    var background by remember { mutableLongStateOf(MATURE_STATUS_BACKGROUNDS.first()) }
    var foreground by remember { mutableLongStateOf(0xFFFFFFFF) }
    var font by remember { mutableStateOf(FynxStatusTextFont.CLASSIC) }
    var audience by remember { mutableStateOf(FynxStatusAudience.EVERYONE) }
    var publishing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var recording by remember { mutableStateOf(false) }
    var recordingStarted by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var showColors by remember { mutableStateOf(false) }
    var showTools by remember { mutableStateOf(false) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { mediaUri = it; type = FynxStatusType.PHOTO; showColors = false; showTools = false; error = null } }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { mediaUri = it; type = FynxStatusType.VIDEO; showColors = false; showTools = false; error = null } }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) beginMatureVoiceRecording(context, onStarted = { r, f -> recorder = r; recordingFile = f; recordingStarted = System.currentTimeMillis(); elapsed = 0L; recording = true; error = null }, onError = { message -> error = message }) else error = "Microphone permission is required for a voice Status."
    }
    LaunchedEffect(recording, recordingStarted) {
        while (recording) {
            elapsed = System.currentTimeMillis() - recordingStarted
            if (elapsed >= FYNX_STATUS_MAX_VOICE_DURATION_MS) {
                stopMatureVoiceRecording(recorder, recordingFile) { uri, message -> if (uri != null) { mediaUri = uri; error = null } else error = message ?: "Voice recording could not be saved."; recorder = null; recordingFile = null; recording = false; type = FynxStatusType.VOICE }
            }
            delay(200)
        }
    }
    DisposableEffect(Unit) { onDispose { recorder?.let { runCatching { it.stop() }; runCatching { it.release() } }; recordingFile?.let { if (it.exists()) runCatching { it.delete() } } } }

    fun clearDraft() {
        if (publishing || recording) return
        mediaUri = null; text = ""; type = FynxStatusType.TEXT; background = MATURE_STATUS_BACKGROUNDS.first(); foreground = 0xFFFFFFFF; font = FynxStatusTextFont.CLASSIC; showColors = false; showTools = false; error = null
    }
    fun publish() {
        if (publishing || recording) return
        if (type == FynxStatusType.TEXT && text.isBlank()) { error = "Write something first."; return }
        if (type != FynxStatusType.TEXT && mediaUri == null) { error = "Add your media first."; return }
        publishing = true; error = null; showTools = false
        scope.launch {
            try {
                val source = mediaUri
                val mediaId = if (type == FynxStatusType.TEXT) null else {
                    val mime = context.contentResolver.getType(source!!) ?: when (type) { FynxStatusType.PHOTO -> "image/jpeg"; FynxStatusType.VIDEO -> "video/mp4"; FynxStatusType.VOICE -> "audio/mp4"; FynxStatusType.TEXT -> "text/plain" }
                    FynxStatusClient.uploadMedia(context, source, mime).getOrElse { error = it.message ?: "Media upload failed."; return@launch }
                }
                val now = System.currentTimeMillis()
                val status = FynxStatus(UUID.randomUUID().toString(), username, displayName, type, text.trim().ifBlank { null }, now, now + FYNX_STATUS_EXPIRY_MS, FynxStatusTextStyle(background, foreground, font, 1), audience == FynxStatusAudience.FRIENDS, if (type == FynxStatusType.VOICE) elapsed else 0L, audience)
                FynxStatusClient.create(context, status, mediaId).getOrElse { error = it.message ?: "Status publishing failed."; return@launch }
                FynxStatusStore.save(context, status.copy(contentUri = mediaId?.let { "/api/media/$it" }))
                onClose()
            } finally { publishing = false }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (type) {
            FynxStatusType.TEXT -> Box(Modifier.fillMaxSize().background(Color(background)), contentAlignment = Alignment.Center) {
                OutlinedTextField(value = text, onValueChange = { text = it.take(FYNX_STATUS_MAX_TEXT_LENGTH) }, placeholder = { Text("Type a Status", color = Color(foreground).copy(alpha = .6f)) }, textStyle = LocalTextStyle.current.copy(color = Color(foreground), textAlign = TextAlign.Center, fontFamily = matureStatusFont(font), fontWeight = if (font == FynxStatusTextFont.BOLD) FontWeight.Bold else FontWeight.Normal), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent, cursorColor = Color(foreground), focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent), modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp), minLines = 1, maxLines = 8)
            }
            FynxStatusType.PHOTO -> mediaUri?.let { MatureStatusMedia(it, false, Modifier.fillMaxSize()) }
            FynxStatusType.VIDEO -> mediaUri?.let { MatureStatusMedia(it, true, Modifier.fillMaxSize()) }
            FynxStatusType.VOICE -> Box(Modifier.fillMaxSize().background(Color(background)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Surface(shape = CircleShape, color = Color(foreground).copy(alpha = .14f), modifier = Modifier.size(112.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Mic, null, tint = Color(foreground), modifier = Modifier.size(48.dp)) } }
                    Text(if (recording) formatMatureTime(elapsed) else if (mediaUri != null) "Voice Status ready" else "Press the microphone to record", color = Color(foreground), style = MaterialTheme.typography.titleMedium)
                    if (recording) LinearProgressIndicator(progress = { (elapsed.toFloat() / FYNX_STATUS_MAX_VOICE_DURATION_MS).coerceIn(0f, 1f) }, modifier = Modifier.width(220.dp))
                }
            }
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose, enabled = !publishing && !recording) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                Text("Create Status", color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Box {
                    IconButton(onClick = { showTools = !showTools; showColors = false }, enabled = !recording && !publishing) { Icon(Icons.Default.MoreVert, "Status tools", tint = Color.White) }
                    DropdownMenu(expanded = showTools, onDismissRequest = { showTools = false }) {
                        if (type == FynxStatusType.TEXT) DropdownMenuItem(text = { Text("Colors & text") }, onClick = { showColors = true; showTools = false })
                        DropdownMenuItem(text = { Text("Clear") }, onClick = ::clearDraft)
                        DropdownMenuItem(text = { Text("Share") }, enabled = !publishing && !recording && ((type == FynxStatusType.TEXT && text.isNotBlank()) || (type != FynxStatusType.TEXT && mediaUri != null)), onClick = ::publish)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (showColors && type == FynxStatusType.TEXT) Surface(color = Color.Black.copy(alpha = .72f), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(MATURE_STATUS_BACKGROUNDS) { value -> Surface(shape = CircleShape, color = Color(value), modifier = Modifier.size(34.dp).clickable(enabled = !recording && !publishing) { background = value }) {} } }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(MATURE_STATUS_TEXT_COLORS) { value -> Surface(shape = CircleShape, color = Color(value), modifier = Modifier.size(30.dp).clickable(enabled = !recording && !publishing) { foreground = value }) {} } }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(FynxStatusTextFont.values().toList()) { option -> FilterChip(selected = font == option, onClick = { font = option }, enabled = !recording && !publishing, label = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) }) } }
                }
            }
            Surface(color = Color.Black.copy(alpha = .78f), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (type == FynxStatusType.TEXT) Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.TextFields, null, tint = Color.White); Text("${text.length}/$FYNX_STATUS_MAX_TEXT_LENGTH", color = Color.White, modifier = Modifier.padding(start = 8.dp)) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        MatureStatusModeButton(Icons.Default.TextFields, "Text", type == FynxStatusType.TEXT, !recording && !publishing) { type = FynxStatusType.TEXT; mediaUri = null; showColors = false; error = null }
                        MatureStatusModeButton(Icons.Default.Photo, "Photo", type == FynxStatusType.PHOTO, !recording && !publishing) { pickImage.launch(arrayOf("image/*")) }
                        MatureStatusModeButton(Icons.Default.Videocam, "Video", type == FynxStatusType.VIDEO, !recording && !publishing) { pickVideo.launch(arrayOf("video/*")) }
                        MatureStatusModeButton(Icons.Default.Mic, "Voice", type == FynxStatusType.VOICE, !publishing && !recording) {
                            type = FynxStatusType.VOICE; showColors = false; error = null
                            if (mediaUri == null) if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) beginMatureVoiceRecording(context, onStarted = { r, f -> recorder = r; recordingFile = f; recordingStarted = System.currentTimeMillis(); elapsed = 0L; recording = true }, onError = { message -> error = message }) else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                        if (type == FynxStatusType.VOICE && mediaUri != null) IconButton(onClick = { mediaUri = null; elapsed = 0L; error = null }, enabled = !publishing) { Icon(Icons.Default.Close, "Clear voice", tint = Color.White) }
                        if (type == FynxStatusType.VOICE && recording) IconButton(onClick = { stopMatureVoiceRecording(recorder, recordingFile) { uri, message -> if (uri != null) { mediaUri = uri; error = null } else error = message ?: "Voice recording could not be saved."; recorder = null; recordingFile = null; recording = false } }) { Icon(Icons.Default.Stop, "Stop", tint = Color.White) }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Audience", color = Color.White, style = MaterialTheme.typography.labelLarge); Spacer(Modifier.width(10.dp)); AssistChip(onClick = { audience = if (audience == FynxStatusAudience.EVERYONE) FynxStatusAudience.FRIENDS else FynxStatusAudience.EVERYONE }, enabled = !recording && !publishing, label = { Text(if (audience == FynxStatusAudience.EVERYONE) "Everyone" else "Friends") }); Spacer(Modifier.weight(1f)); if (publishing) Text("Sharing…", color = Color.White, style = MaterialTheme.typography.labelLarge) }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun MatureStatusModeButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) { Column(horizontalAlignment = Alignment.CenterHorizontally) { IconButton(onClick = onClick, enabled = enabled) { Icon(icon, label, tint = if (selected) Color.White else Color.White.copy(alpha = .65f), modifier = Modifier.size(28.dp)) }; Text(label, color = Color.White.copy(alpha = if (enabled && selected) 1f else .45f), style = MaterialTheme.typography.labelSmall) } }

@Composable
private fun MatureStatusMedia(uri: Uri, video: Boolean, modifier: Modifier) { AndroidView(modifier = modifier, factory = { context -> if (video) VideoView(context).apply { layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT); setVideoURI(uri); setOnPreparedListener { it.isLooping = true; it.start() } } else ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setImageURI(uri) } }, update = { view -> if (view is VideoView) view.setVideoURI(uri) else (view as ImageView).setImageURI(uri) }) }
private fun matureStatusFont(font: FynxStatusTextFont): FontFamily = when (font) { FynxStatusTextFont.SERIF -> FontFamily.Serif; FynxStatusTextFont.TYPEWRITER -> FontFamily.Monospace; else -> FontFamily.SansSerif }
private fun beginMatureVoiceRecording(context: Context, onStarted: (MediaRecorder, File) -> Unit, onError: (String) -> Unit) { val file = File(context.cacheDir, "fynx-status-${System.currentTimeMillis()}.m4a"); var recorder: MediaRecorder? = null; try { recorder = MediaRecorder().apply { setAudioSource(MediaRecorder.AudioSource.MIC); setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); setAudioEncoder(MediaRecorder.AudioEncoder.AAC); setMaxDuration(FYNX_STATUS_MAX_VOICE_DURATION_MS.toInt()); setOutputFile(file.absolutePath); prepare(); start() }; onStarted(recorder, file) } catch (e: Exception) { runCatching { recorder?.reset() }; runCatching { recorder?.release() }; runCatching { file.delete() }; onError(e.message ?: "Unable to start voice recording.") } }
private fun stopMatureVoiceRecording(recorder: MediaRecorder?, file: File?, onFinished: (Uri?, String?) -> Unit) { if (recorder == null) { onFinished(null, "Voice recorder is not active."); return }; var stopped = false; try { recorder.stop(); stopped = true } catch (e: Exception) { runCatching { recorder.reset() }; onFinished(null, e.message ?: "Unable to stop voice recording.") } finally { runCatching { recorder.release() } }; if (stopped) { val output = file?.takeIf { it.exists() && it.length() > 0L }; if (output != null) onFinished(Uri.fromFile(output), null) else { runCatching { file?.delete() }; onFinished(null, "Voice recording was empty.") } } }
private fun formatMatureTime(ms: Long): String { val total = (ms / 1000L).coerceAtLeast(0L); return "%02d:%02d".format(total / 60L, total % 60L) }