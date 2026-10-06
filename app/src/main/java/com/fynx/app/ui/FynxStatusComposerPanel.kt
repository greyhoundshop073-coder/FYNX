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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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

private val STATUS_BACKGROUNDS = listOf(0xFF111111, 0xFF4527A0, 0xFF6A1B9A, 0xFF1565C0, 0xFF00695C, 0xFF2E7D32, 0xFFEF6C00, 0xFFC62828, 0xFFAD1457, 0xFF37474F)
private val STATUS_FOREGROUNDS = listOf(0xFFFFFFFF, 0xFF000000, 0xFFFFF3E0, 0xFFE3F2FD, 0xFFE8F5E9)

@Composable
fun FynxStatusComposerPanel(onClose: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = remember(context) { FynxAuthStore.load(context) }
    val username = auth.username?.removePrefix("@").orEmpty().ifBlank { "preview" }
    var displayName by remember(username) { mutableStateOf(username.ifBlank { "You" }) }
    LaunchedEffect(username) {
        if (username.isNotBlank() && username != "preview") {
            FynxProfileRemoteClient.get(context, username).onSuccess { profile ->
                displayName = profile.displayName.ifBlank { username }
            }
        }
    }
    var type by remember { mutableStateOf(FynxStatusType.TEXT) }
    var text by remember { mutableStateOf("") }
    var mediaUri by remember { mutableStateOf<Uri?>(null) }
    var background by remember { mutableLongStateOf(0xFF111111) }
    var foreground by remember { mutableLongStateOf(0xFFFFFFFF) }
    var font by remember { mutableStateOf(FynxStatusTextFont.CLASSIC) }
    var alignment by remember { mutableIntStateOf(1) }
    var audience by remember {
        mutableStateOf(
            runCatching {
                FynxStatusAudience.valueOf(
                    FynxPreferencesStore.loadVisibility(context, "status_audience", FynxStatusAudience.EVERYONE.name)
                )
            }.getOrDefault(FynxStatusAudience.EVERYONE)
        )
    }
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
        if (granted) startStatusRecording(context) { r, f -> recorder = r; recordingFile = f; recordingStarted = System.currentTimeMillis(); elapsed = 0L; recording = true }
        else error = "Microphone permission is required for a voice Status."
    }

    LaunchedEffect(recording, recordingStarted) {
        while (recording) {
            elapsed = System.currentTimeMillis() - recordingStarted
            if (elapsed >= FYNX_STATUS_MAX_VOICE_DURATION_MS) {
                stopStatusRecording(recorder, recordingFile) { uri -> mediaUri = uri; recorder = null; recordingFile = null; recording = false; type = FynxStatusType.VOICE }
            }
            delay(200)
        }
    }
    DisposableEffect(Unit) { onDispose { runCatching { recorder?.stop() }; recorder?.release() } }

    fun publish() {
        if (publishing) return
        if (type == FynxStatusType.TEXT && text.isBlank()) { error = "Write something first."; return }
        if (type != FynxStatusType.TEXT && mediaUri == null) { error = "Choose media first."; return }
        publishing = true
        error = null
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
                    textStyle = FynxStatusTextStyle(background, foreground, font, alignment),
                    privateStatus = audience == FynxStatusAudience.FRIENDS,
                    voiceDurationMs = if (type == FynxStatusType.VOICE) elapsed else 0L,
                    audience = audience
                )
                FynxStatusClient.create(context, status, mediaId).getOrElse { error = it.message ?: "Status publishing failed."; return@launch }
                FynxStatusStore.save(context, status.copy(contentUri = mediaId?.let { "/api/media/$it" }))
                mediaUri = null
                text = ""
                preview = false
            } finally {
                publishing = false
            }
        }
    }

    if (preview) {
        FynxStatusPreview(type, mediaUri, text, background, foreground, font, alignment, elapsed, { preview = false }, ::publish)
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeContent)
            .imePadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Create Status", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text("Share a moment that disappears after 24 hours", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onClose) { Text("Close") }
            }
        }
        item {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Create with", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    val options = FynxStatusType.values().toList()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.take(2).forEach { option ->
                            StatusTypeChoice(option, type == option, !recording) { type = option }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.drop(2).forEach { option ->
                            StatusTypeChoice(option, type == option, !recording) { type = option }
                        }
                    }
                }
            }
        }
        item {
            when (type) {
                FynxStatusType.TEXT -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatusTextCanvas(text, background, foreground, font, alignment, Modifier.fillMaxWidth().height(250.dp))
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it.take(FYNX_STATUS_MAX_TEXT_LENGTH) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 5,
                            placeholder = { Text("Write your Status…") },
                            supportingText = { Text("${text.length}/$FYNX_STATUS_MAX_TEXT_LENGTH") },
                            shape = RoundedCornerShape(18.dp)
                        )
                        StatusEditorSection("Background") { ColorChoices(STATUS_BACKGROUNDS, background) { background = it } }
                        StatusEditorSection("Text color") { ColorChoices(STATUS_FOREGROUNDS, foreground) { foreground = it } }
                        StatusEditorSection("Font") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(FynxStatusTextFont.values().toList()) { option ->
                                    FilterChip(font == option, { font = option }, label = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) })
                                }
                            }
                        }
                        StatusEditorSection("Alignment") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(0 to "Left", 1 to "Center", 2 to "Right").forEach { (value, label) ->
                                    item { FilterChip(alignment == value, { alignment = value }, label = { Text(label) }) }
                                }
                            }
                        }
                    }
                }
                FynxStatusType.PHOTO -> MediaPickerCard("Photo", mediaUri) { pickImage.launch(arrayOf("image/*")) }
                FynxStatusType.VIDEO -> MediaPickerCard("Video", mediaUri) { pickVideo.launch(arrayOf("video/*")) }
                FynxStatusType.VOICE -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        VoiceRecorderCard(recording, elapsed, mediaUri != null, {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                startStatusRecording(context) { r, f -> recorder = r; recordingFile = f; recordingStarted = System.currentTimeMillis(); elapsed = 0L; recording = true }
                            } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }, {
                            stopStatusRecording(recorder, recordingFile) { uri -> mediaUri = uri; recorder = null; recordingFile = null; recording = false; type = FynxStatusType.VOICE }
                        })
                        StatusEditorSection("Voice background") { ColorChoices(STATUS_BACKGROUNDS, background) { background = it } }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Who can see this Status?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    AudienceOption("Everyone", FynxStatusAudience.EVERYONE, audience) { audience = it }
                    AudienceOption("Friends only", FynxStatusAudience.FRIENDS, audience) { audience = it }
                    Text("Your selected audience is enforced by FYNX on the server.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth()) }
        }
        item {
            Button(
                onClick = { preview = true },
                enabled = !recording && !publishing && ((type == FynxStatusType.TEXT && text.isNotBlank()) || (type != FynxStatusType.TEXT && mediaUri != null)),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp)
            ) { Text(if (publishing) "Publishing…" else "Preview Status") }
        }
    }
}

@Composable
private fun RowScope.StatusTypeChoice(type: FynxStatusType, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        label = {
            Text(
                type.name.lowercase().replaceFirstChar { it.uppercase() },
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    )
}

@Composable
private fun StatusEditorSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun AudienceOption(label: String, value: FynxStatusAudience, selected: FynxStatusAudience, onSelect: (FynxStatusAudience) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected == value, { onSelect(value) })
        Text(label)
    }
}

@Composable
private fun MediaPickerCard(label: String, uri: Uri?, onPick: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("$label Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (uri != null) LocalStatusMediaPreview(uri, label.lowercase(), Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 340.dp))
            else Box(Modifier.fillMaxWidth().height(220.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                Text("Choose a $label", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(uri?.lastPathSegment ?: "No $label selected", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            Button(onClick = onPick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text(if (uri == null) "Choose $label" else "Choose another") }
        }
    }
}

@Composable
private fun VoiceRecorderCard(recording: Boolean, elapsed: Long, hasVoice: Boolean, onRecord: () -> Unit, onStop: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(92.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(46.dp)), contentAlignment = Alignment.Center) {
                Text(if (recording) "●" else "MIC", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
            }
            Text(if (recording) "Recording voice Status" else "Voice Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(if (recording) formatStatusTime(elapsed) else if (hasVoice) "Voice ready" else "Up to 30 seconds", style = MaterialTheme.typography.bodyMedium)
            if (recording) LinearProgressIndicator(progress = { (elapsed.toFloat() / FYNX_STATUS_MAX_VOICE_DURATION_MS).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Button(onClick = if (recording) onStop else onRecord, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text(if (recording) "Stop recording" else if (hasVoice) "Record again" else "Record voice")
            }
        }
    }
}

@Composable
private fun LocalStatusMediaPreview(uri: Uri, kind: String, modifier: Modifier) {
    if (kind == "video") AndroidView(
        modifier = modifier,
        factory = { context ->
            VideoView(context).apply {
                setVideoURI(uri)
                setMediaController(android.widget.MediaController(context))
                setOnPreparedListener { it.isLooping = true; start() }
            }
        },
        update = { it.setVideoURI(uri) }
    ) else AndroidView(
        modifier = modifier,
        factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setImageURI(uri) } },
        update = { it.setImageURI(uri) }
    )
}

@Composable
private fun FynxStatusPreview(type: FynxStatusType, uri: Uri?, text: String, background: Long, foreground: Long, font: FynxStatusTextFont, alignment: Int, durationMs: Long, onBack: () -> Unit, onPublish: () -> Unit) {
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeContent).imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Status preview", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onBack) { Text("Edit") }
        }
        Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            when (type) {
                FynxStatusType.TEXT -> StatusTextCanvas(text, background, foreground, font, alignment, Modifier.fillMaxSize())
                FynxStatusType.PHOTO -> uri?.let { LocalStatusMediaPreview(it, "image", Modifier.fillMaxWidth().heightIn(min = 320.dp, max = 620.dp)) }
                FynxStatusType.VIDEO -> uri?.let { LocalStatusMediaPreview(it, "video", Modifier.fillMaxWidth().heightIn(min = 320.dp, max = 620.dp)) }
                FynxStatusType.VOICE -> Box(Modifier.fillMaxWidth().height(220.dp).background(Color(background), RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Voice Status", color = Color(foreground), fontWeight = FontWeight.Bold)
                        Text(formatStatusTime(durationMs), color = Color(foreground).copy(alpha = 0.82f))
                    }
                }
            }
        }
        Button(onClick = onPublish, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) { Text("Share Status") }
    }
}

@Composable
private fun StatusTextCanvas(text: String, background: Long, foreground: Long, font: FynxStatusTextFont, alignment: Int, modifier: Modifier) {
    val family = when (font) { FynxStatusTextFont.SERIF -> FontFamily.Serif; FynxStatusTextFont.TYPEWRITER -> FontFamily.Monospace; else -> FontFamily.SansSerif }
    val weight = if (font == FynxStatusTextFont.BOLD) FontWeight.Bold else FontWeight.Normal
    val textAlign = when (alignment) { 0 -> TextAlign.Start; 2 -> TextAlign.End; else -> TextAlign.Center }
    Box(modifier.background(Color(background), RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
        Text(text.ifBlank { "Your Status" }, color = Color(foreground), fontFamily = family, fontWeight = weight, textAlign = textAlign, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth().padding(28.dp))
    }
}

@Composable
private fun ColorChoices(colors: List<Long>, selected: Long, onSelected: (Long) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(colors) { color -> FilterChip(selected == color, { onSelected(color) }, label = { Text("●", color = Color(color)) }) }
    }
}

private fun startStatusRecording(context: Context, onStarted: (MediaRecorder, File) -> Unit) {
    val file = File(context.cacheDir, "fynx_status_voice_${System.currentTimeMillis()}.m4a")
    runCatching {
        val r = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        r.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        onStarted(r, file)
    }
}

private fun stopStatusRecording(recorder: MediaRecorder?, file: File?, onStopped: (Uri?) -> Unit) {
    runCatching { recorder?.stop() }
    recorder?.release()
    onStopped(file?.takeIf { it.exists() && it.length() > 0L }?.let(Uri::fromFile))
}

private fun formatStatusTime(milliseconds: Long): String = "%02d:%02d".format(milliseconds / 60_000L, (milliseconds / 1000L) % 60L)