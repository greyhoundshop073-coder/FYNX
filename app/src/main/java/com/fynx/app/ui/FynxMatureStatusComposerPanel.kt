package com.fynx.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

private val MATURE_STATUS_BACKGROUNDS = listOf(0xFF111111, 0xFF4527A0, 0xFF1565C0, 0xFF00695C, 0xFF2E7D32, 0xFFEF6C00, 0xFFC62828, 0xFFAD1457, 0xFF37474F, 0xFF455A64)
private val MATURE_STATUS_TEXT_COLORS = listOf(0xFFFFFFFF, 0xFF000000, 0xFFFFEB3B, 0xFFFFCDD2, 0xFFB3E5FC)

@Composable
fun FynxMatureStatusComposerPanel(
    initialMediaUri: Uri? = null,
    initialType: FynxStatusType? = null,
    initialMusic: FynxMusicCatalogueTrack? = null,
    onClose: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = remember(context) { FynxAuthStore.load(context) }
    val username = auth.username?.removePrefix("@").orEmpty().ifBlank { "preview" }
    val displayName = username.ifBlank { "You" }
    var type by remember(initialType) { mutableStateOf(initialType ?: FynxStatusType.TEXT) }
    var text by remember { mutableStateOf("") }
    var mediaUri by remember(initialMediaUri) { mutableStateOf(initialMediaUri) }
    var selectedMusic by remember(initialMusic) { mutableStateOf(initialMusic) }
    var background by remember { mutableLongStateOf(MATURE_STATUS_BACKGROUNDS.first()) }
    var foreground by remember { mutableLongStateOf(0xFFFFFFFF) }
    var font by remember { mutableStateOf(FynxStatusTextFont.CLASSIC) }
    var alignment by remember { mutableIntStateOf(1) }
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
    var showMediaTools by remember { mutableStateOf(false) }
    var cameraOpen by remember { mutableStateOf(false) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { mediaUri = it; type = FynxStatusType.PHOTO; showColors = false; showTools = false; error = null } }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { mediaUri = it; type = FynxStatusType.VIDEO; showColors = false; showTools = false; error = null } }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) beginMatureVoiceRecording(context, onStarted = { r, f -> recorder = r; recordingFile = f; recordingStarted = System.currentTimeMillis(); elapsed = 0L; recording = true; error = null }, onError = { message -> error = message }) else error = "Microphone permission is required for a voice Status."
    }
    LaunchedEffect(recording, recordingStarted) {
        while (recording) {
            elapsed = System.currentTimeMillis() - recordingStarted
            if (elapsed >= FYNX_STATUS_MAX_VOICE_DURATION_MS) stopMatureVoiceRecording(recorder, recordingFile) { uri, message -> if (uri != null) { mediaUri = uri; error = null } else error = message ?: "Voice recording could not be saved."; recorder = null; recordingFile = null; recording = false; type = FynxStatusType.VOICE }
            delay(200)
        }
    }
    DisposableEffect(Unit) { onDispose { recorder?.let { runCatching { it.stop() }; runCatching { it.release() } }; recordingFile?.let { if (it.exists()) runCatching { it.delete() } } } }

    fun clearDraft() { if (publishing || recording) return; mediaUri = null; selectedMusic = null; text = ""; type = FynxStatusType.TEXT; background = MATURE_STATUS_BACKGROUNDS.first(); foreground = 0xFFFFFFFF; font = FynxStatusTextFont.CLASSIC; alignment = 1; showColors = false; showTools = false; showMediaTools = false; error = null }
    fun publish() {
        if (publishing || recording) return
        if (type == FynxStatusType.TEXT && text.isBlank()) { error = "Write something first."; return }
        if (type != FynxStatusType.TEXT && mediaUri == null) { error = "Add your media first."; return }
        publishing = true; error = null; showTools = false; showMediaTools = false
        scope.launch {
            try {
                val source = mediaUri
                val mediaId = if (type == FynxStatusType.TEXT) null else {
                    val mime = context.contentResolver.getType(source!!) ?: when (type) { FynxStatusType.PHOTO -> "image/jpeg"; FynxStatusType.VIDEO -> "video/mp4"; FynxStatusType.VOICE -> "audio/mp4"; FynxStatusType.TEXT -> "text/plain" }
                    FynxStatusClient.uploadMedia(context, source, mime).getOrElse { error = it.message ?: "Media upload failed."; return@launch }
                }
                val now = System.currentTimeMillis()
                val status = FynxStatus(id = UUID.randomUUID().toString(), ownerUsername = username, ownerDisplayName = displayName, type = type, contentUri = mediaId?.let { "/api/media/$it" }, text = text.trim().ifBlank { null }, createdAtMillis = now, expiresAtMillis = now + FYNX_STATUS_EXPIRY_MS, textStyle = FynxStatusTextStyle(background, foreground, font, alignment), privateStatus = audience != FynxStatusAudience.EVERYONE, voiceDurationMs = if (type == FynxStatusType.VOICE) elapsed else 0L, audience = audience, musicCatalogueId = selectedMusic?.id, musicTitle = selectedMusic?.title, musicArtist = selectedMusic?.artist, musicDurationMs = selectedMusic?.durationMs ?: 0L)
                FynxStatusClient.create(context, status, mediaId).getOrElse { error = it.message ?: "Status publishing failed."; return@launch }
                FynxStatusStore.save(context, status)
                onClose()
            } finally { publishing = false }
        }
    }

    BackHandler(enabled = !publishing && !recording) { onClose() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (type) {
            FynxStatusType.TEXT -> {
                val editorTextAlign = when (alignment) { 0 -> TextAlign.Start; 2 -> TextAlign.End; else -> TextAlign.Center }
                val editorWeight = if (font == FynxStatusTextFont.BOLD) FontWeight.Bold else FontWeight.Normal
                BoxWithConstraints(Modifier.fillMaxSize().background(Color(background)), contentAlignment = Alignment.Center) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.take(FYNX_STATUS_MAX_TEXT_LENGTH) },
                        placeholder = { Text("Type a Status", color = Color(foreground).copy(alpha = .6f), textAlign = editorTextAlign, modifier = Modifier.fillMaxWidth()) },
                        textStyle = LocalTextStyle.current.copy(
                            color = Color(foreground),
                            textAlign = editorTextAlign,
                            fontFamily = matureStatusFont(font),
                            fontWeight = editorWeight,
                            fontSize = (
                                (maxWidth.value * 0.085f)
                                    .coerceIn(24f, 42f)
                                    .let { size ->
                                        if (text.length > 420) size * 0.72f else if (text.length > 240) size * 0.84f else size
                                    }
                            ).sp,
                            lineHeight = (
                                (maxWidth.value * 0.105f)
                                    .coerceIn(30f, 52f)
                                    .let { size ->
                                        if (text.length > 420) size * 0.72f else if (text.length > 240) size * 0.84f else size
                                    }
                            ).sp
                        ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = Color(foreground),
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(top = 76.dp, bottom = 220.dp)
                        .imePadding(),
                    minLines = 2,
                        maxLines = 10
                    )
                }
            }
            FynxStatusType.PHOTO -> mediaUri?.let { MatureStatusMedia(it, false, Modifier.fillMaxSize().padding(top = 64.dp, bottom = 176.dp)) }
            FynxStatusType.VIDEO -> mediaUri?.let { MatureStatusMedia(it, true, Modifier.fillMaxSize().padding(top = 64.dp, bottom = 176.dp)) }
            FynxStatusType.VOICE -> Box(Modifier.fillMaxSize().background(Color(background)).padding(top = 64.dp, bottom = 176.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) { Surface(shape = CircleShape, color = Color(foreground).copy(alpha = .14f), modifier = Modifier.size(112.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Mic, null, tint = Color(foreground), modifier = Modifier.size(48.dp)) } }; Text(if (recording) formatMatureTime(elapsed) else if (mediaUri != null) "Voice Status ready" else "Press the microphone to record", color = Color(foreground), style = MaterialTheme.typography.titleMedium); if (recording) LinearProgressIndicator(progress = { (elapsed.toFloat() / FYNX_STATUS_MAX_VOICE_DURATION_MS).coerceIn(0f, 1f) }, modifier = Modifier.width(220.dp)) } }
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose, enabled = !publishing && !recording) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    FynxProfileImage(username, FynxPreferencesStore.loadProfilePhoto(context), Modifier.size(36.dp).clip(CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Text(displayName, color = Color.White, style = MaterialTheme.typography.titleLarge)
                }
                Box {
                    IconButton(onClick = { showTools = !showTools; showColors = false }, enabled = !recording && !publishing) { Icon(Icons.Default.MoreVert, "Status tools", tint = Color.White) }
                    DropdownMenu(expanded = showTools, onDismissRequest = { showTools = false }) {
                        if (type == FynxStatusType.TEXT) DropdownMenuItem(text = { Text("Text style") }, onClick = { showColors = true; showTools = false })
                        DropdownMenuItem(text = { Text("Clear draft") }, onClick = ::clearDraft)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (showMediaTools || showColors || selectedMusic != null || type == FynxStatusType.TEXT || type != FynxStatusType.TEXT) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (showColors && type == FynxStatusType.TEXT) {
                        Surface(color = Color.Black.copy(alpha = .55f), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Text("Text style", color = Color.White, style = MaterialTheme.typography.labelLarge)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(MATURE_STATUS_BACKGROUNDS) { value -> Surface(shape = CircleShape, color = Color(value), modifier = Modifier.size(32.dp).clickable(enabled = !recording && !publishing) { background = value }) {} } }
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(MATURE_STATUS_TEXT_COLORS) { value -> Surface(shape = CircleShape, color = Color(value), modifier = Modifier.size(28.dp).clickable(enabled = !recording && !publishing) { foreground = value }) {} } }
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(FynxStatusTextFont.values().toList()) { option -> FilterChip(selected = font == option, onClick = { font = option }, enabled = !recording && !publishing, label = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) }) } }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                    IconButton(onClick = { alignment = 0 }, enabled = !recording && !publishing) { Icon(Icons.Default.FormatAlignLeft, "Align left", tint = if (alignment == 0) Color.White else Color.White.copy(alpha = .5f)) }
                                    IconButton(onClick = { alignment = 1 }, enabled = !recording && !publishing) { Icon(Icons.Default.FormatAlignCenter, "Align center", tint = if (alignment == 1) Color.White else Color.White.copy(alpha = .5f)) }
                                    IconButton(onClick = { alignment = 2 }, enabled = !recording && !publishing) { Icon(Icons.Default.FormatAlignRight, "Align right", tint = if (alignment == 2) Color.White else Color.White.copy(alpha = .5f)) }
                                }
                                TextButton(onClick = { showColors = false }) { Text("Done", color = Color.White) }
                            }
                        }
                    }
                    selectedMusic?.let { music ->
                        Surface(color = Color.Black.copy(alpha = .32f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.MusicNote, "Music", tint = Color.White)
                                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                    Text(music.title.ifBlank { "FYNX Music" }, color = Color.White, maxLines = 1)
                                    Text(music.artist.ifBlank { "FYNX" }, color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                }
                                FynxRemoteAudio("/api/social/music/catalogue/" + music.id + "/media", Modifier.width(120.dp), music.durationMs.coerceAtLeast(1_000L))
                                IconButton(onClick = { selectedMusic = null }, enabled = !recording && !publishing) {
                                    Icon(Icons.Default.Close, "Remove music", tint = Color.White)
                                }
                            }
                        }
                    }
                    if (type == FynxStatusType.TEXT) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TextFields, null, tint = Color.White)
                            Text("${text.length}/$FYNX_STATUS_MAX_TEXT_LENGTH", color = Color.White, modifier = Modifier.padding(start = 8.dp))
                            Spacer(Modifier.weight(1f))
                            var showStatusEmoji by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { showStatusEmoji = true }, enabled = !recording && !publishing) { Icon(Icons.Default.EmojiEmotions, "Add emoji", tint = Color.White) }
                                DropdownMenu(expanded = showStatusEmoji, onDismissRequest = { showStatusEmoji = false }) {
                                    listOf("😀","😂","😍","🔥","❤️","👍","🎉","😮").forEach { emoji ->
                                        DropdownMenuItem(text = { Text(emoji, fontSize = 22.sp) }, onClick = { text = (text + emoji).take(FYNX_STATUS_MAX_TEXT_LENGTH); showStatusEmoji = false })
                                    }
                                }
                            }
                        }
                    }
                    if (type != FynxStatusType.TEXT && mediaUri != null) {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it.take(FYNX_STATUS_MAX_TEXT_LENGTH) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).imePadding(),
                            singleLine = true,
                            placeholder = { Text("Add a caption…", color = Color.White.copy(alpha = .72f)) },
                            textStyle = LocalTextStyle.current.copy(color = Color.White, fontWeight = FontWeight.Bold),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.White.copy(alpha = .75f),
                                unfocusedBorderColor = Color.White.copy(alpha = .45f),
                                cursorColor = Color.White,
                                focusedContainerColor = Color.Black.copy(alpha = .18f),
                                unfocusedContainerColor = Color.Black.copy(alpha = .18f)
                            )
                        )
                    }
                    // Media creation tools stay hidden until the user explicitly opens them.
                    if (showMediaTools) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            item { MatureStatusModeButton(Icons.Default.TextFields, "Text", type == FynxStatusType.TEXT, !recording && !publishing) { type = FynxStatusType.TEXT; mediaUri = null; showColors = false; error = null } }
                            item { MatureStatusModeButton(Icons.Default.Photo, "Photo", type == FynxStatusType.PHOTO, !recording && !publishing) { pickImage.launch(arrayOf("image/*")) } }
                            item { MatureStatusModeButton(Icons.Default.CameraAlt, "Camera", false, !recording && !publishing) { cameraOpen = true; showColors = false; showTools = false; error = null } }
                            item { MatureStatusModeButton(Icons.Default.Videocam, "Video", type == FynxStatusType.VIDEO, !recording && !publishing) { pickVideo.launch(arrayOf("video/*")) } }
                            item { MatureStatusModeButton(Icons.Default.Mic, "Voice", type == FynxStatusType.VOICE, !publishing && !recording) { type = FynxStatusType.VOICE; showColors = false; error = null; if (mediaUri == null) if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) beginMatureVoiceRecording(context, onStarted = { r, f -> recorder = r; recordingFile = f; recordingStarted = System.currentTimeMillis(); elapsed = 0L; recording = true }, onError = { message -> error = message }) else micPermission.launch(Manifest.permission.RECORD_AUDIO) } }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            onClick = { showMediaTools = !showMediaTools },
                            enabled = !recording && !publishing,
                            modifier = Modifier.size(44.dp)
                        ) {
                            if (showMediaTools) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Hide status media tools",
                                    tint = Color.White
                                )
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.AddAPhoto,
                                        contentDescription = "Show status media tools",
                                        tint = Color.White,
                                        modifier = Modifier.size(25.dp)
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.White,
                                        modifier = Modifier
                                            .size(11.dp)
                                            .align(Alignment.BottomEnd)
                                    ) {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier
                                                .padding(1.dp)
                                                .size(9.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                                        if (type == FynxStatusType.VOICE) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            if (recording) {
                                FilledTonalButton(onClick = { stopMatureVoiceRecording(recorder, recordingFile) { uri, message -> if (uri != null) { mediaUri = uri; error = null } else error = message ?: "Voice recording could not be saved."; recorder = null; recordingFile = null; recording = false } }) {
                                    Icon(Icons.Default.Stop, "Stop")
                                    Spacer(Modifier.width(6.dp))
                                    Text("Stop • ${formatMatureTime(elapsed)}")
                                }
                            } else if (mediaUri != null) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FynxRemoteAudio(mediaUri.toString(), Modifier.fillMaxWidth().padding(horizontal = 24.dp), elapsed.coerceAtLeast(1_000L))
                                    OutlinedButton(onClick = { mediaUri = null; elapsed = 0L; error = null }, enabled = !publishing) {
                                    Icon(Icons.Default.Close, "Clear voice")
                                    Spacer(Modifier.width(6.dp))
                                    Text("Clear voice")
                                    }
                                }
                            } else {
                                Text("Tap Voice to start recording", color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Audience", color = Color.White, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(10.dp))
                        var audienceMenu by remember { mutableStateOf(false) }
                        Box {
                            AssistChip(
                                onClick = { audienceMenu = true },
                                enabled = !recording && !publishing,
                                leadingIcon = { Icon(if (audience == FynxStatusAudience.EVERYONE) Icons.Default.Public else if (audience == FynxStatusAudience.FRIENDS) Icons.Default.People else Icons.Default.Lock, null) },
                                label = { Text(if (audience == FynxStatusAudience.EVERYONE) "Everyone" else if (audience == FynxStatusAudience.FRIENDS) "Friends" else "Only me") }
                            )
                            DropdownMenu(expanded = audienceMenu, onDismissRequest = { audienceMenu = false }) {
                                DropdownMenuItem(text = { Text("Everyone") }, onClick = { audience = FynxStatusAudience.EVERYONE; audienceMenu = false })
                                DropdownMenuItem(text = { Text("Friends") }, onClick = { audience = FynxStatusAudience.FRIENDS; audienceMenu = false })
                                DropdownMenuItem(text = { Text("Only me") }, onClick = { audience = FynxStatusAudience.ONLY_ME; audienceMenu = false })
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = ::publish,
                            enabled = !publishing && !recording && ((type == FynxStatusType.TEXT && text.isNotBlank()) || (type != FynxStatusType.TEXT && mediaUri != null)),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 9.dp)
                        ) {
                            Icon(Icons.Default.Send, "Send")
                            Spacer(Modifier.width(6.dp))
                            Text(if (publishing) "Sending…" else "Send")
                        }
                    }
                    if (publishing) LinearProgressIndicator(Modifier.fillMaxWidth())
                    error?.let { Text(it, color = Color.White, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }

    if (cameraOpen) {
        Dialog(
            onDismissRequest = { if (!publishing && !recording) cameraOpen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnBackPress = !publishing && !recording,
                dismissOnClickOutside = false
            )
        ) {
            Surface(Modifier.fillMaxSize(), color = Color.Black) {
                Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    FynxCameraCapturePanel(
                        onCaptured = { uri, capturedType ->
                            mediaUri = uri
                            type = if (capturedType == "video") FynxStatusType.VIDEO else FynxStatusType.PHOTO
                            cameraOpen = false
                            error = null
                        },
                        onDismiss = { if (!publishing && !recording) cameraOpen = false }
                    )
                }
            }
        }
    }
}
@Composable private fun MatureStatusModeButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) { Column(horizontalAlignment = Alignment.CenterHorizontally) { IconButton(onClick = onClick, enabled = enabled) { Icon(icon, label, tint = if (selected) Color.White else Color.White.copy(alpha = .65f), modifier = Modifier.size(28.dp)) }; Text(label, color = Color.White.copy(alpha = if (enabled && selected) 1f else .45f), style = MaterialTheme.typography.labelSmall) } }
@Composable private fun MatureStatusMedia(uri: Uri, video: Boolean, modifier: Modifier) { AndroidView(modifier = modifier, factory = { context -> if (video) VideoView(context).apply { layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT); setVideoURI(uri); setOnPreparedListener { it.isLooping = true; it.start() } } else ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER; setImageURI(uri) } }, update = { view -> if (view is VideoView) view.setVideoURI(uri) else (view as ImageView).setImageURI(uri) }) }
private fun matureStatusFont(font: FynxStatusTextFont): FontFamily = when (font) { FynxStatusTextFont.SERIF -> FontFamily.Serif; FynxStatusTextFont.TYPEWRITER -> FontFamily.Monospace; else -> FontFamily.SansSerif }
private fun beginMatureVoiceRecording(context: Context, onStarted: (MediaRecorder, File) -> Unit, onError: (String) -> Unit) { val file = File(context.cacheDir, "fynx-status-${System.currentTimeMillis()}.m4a"); var recorder: MediaRecorder? = null; try { recorder = MediaRecorder().apply { setAudioSource(MediaRecorder.AudioSource.MIC); setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); setAudioEncoder(MediaRecorder.AudioEncoder.AAC); setMaxDuration(FYNX_STATUS_MAX_VOICE_DURATION_MS.toInt()); setOutputFile(file.absolutePath); prepare(); start() }; onStarted(recorder, file) } catch (e: Exception) { runCatching { recorder?.reset() }; runCatching { recorder?.release() }; runCatching { file.delete() }; onError(e.message ?: "Unable to start voice recording.") } }
private fun stopMatureVoiceRecording(recorder: MediaRecorder?, file: File?, onFinished: (Uri?, String?) -> Unit) { if (recorder == null) { onFinished(null, "Voice recorder is not active."); return }; var stopped = false; try { recorder.stop(); stopped = true } catch (e: Exception) { runCatching { recorder.reset() }; onFinished(null, e.message ?: "Unable to stop voice recording.") } finally { runCatching { recorder.release() } }; if (stopped) { val output = file?.takeIf { it.exists() && it.length() > 0L }; if (output != null) onFinished(Uri.fromFile(output), null) else { runCatching { file?.delete() }; onFinished(null, "Voice recording was empty.") } } }
private fun formatMatureTime(ms: Long): String { val total = (ms / 1000L).coerceAtLeast(0L); return "%02d:%02d".format(total / 60L, total % 60L) }