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
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

private val MATURE_STATUS_BACKGROUNDS = listOf(
    0xFF111111, 0xFF4527A0, 0xFF1565C0, 0xFF00695C, 0xFF2E7D32,
    0xFFEF6C00, 0xFFC62828, 0xFFAD1457, 0xFF37474F, 0xFF455A64,
    0xFF0D47A1, 0xFF1B5E20, 0xFF4A148C, 0xFF880E4F, 0xFF263238
)
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
    var displayName by remember(username) { mutableStateOf(username.ifBlank { "You" }) }
    LaunchedEffect(username) {
        if (username.isNotBlank() && username != "preview") {
            FynxProfileRemoteClient.get(context, username).onSuccess { profile ->
                displayName = profile.displayName.ifBlank { username }
            }
        }
    }

    var type by remember(initialType) { mutableStateOf(initialType ?: if (initialMediaUri != null) FynxStatusType.PHOTO else FynxStatusType.TEXT) }
    var text by remember { mutableStateOf("") }
    var mediaUri by remember(initialMediaUri) { mutableStateOf(initialMediaUri) }
    var selectedMusic by remember(initialMusic) { mutableStateOf(initialMusic) }
    var background by remember { mutableLongStateOf(MATURE_STATUS_BACKGROUNDS.first()) }
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
    var showPreview by remember { mutableStateOf(false) }
    var cropMedia by remember { mutableStateOf(false) }
    var showMusicPicker by remember { mutableStateOf(false) }
    var musicSearch by remember { mutableStateOf("") }
    var musicCatalogue by remember { mutableStateOf<List<FynxMusicCatalogueTrack>>(emptyList()) }
    var musicLoading by remember { mutableStateOf(false) }
    var musicPreviewId by remember { mutableStateOf<Long?>(null) }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            mediaUri = it
            val mime = context.contentResolver.getType(it).orEmpty().lowercase()
            type = if (mime.startsWith("video/")) FynxStatusType.VIDEO else FynxStatusType.PHOTO
            showColors = false
            showTools = false
            showPreview = false
            error = null
        }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { mediaUri = it; type = FynxStatusType.PHOTO; showPreview = false; error = null }
    }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { mediaUri = it; type = FynxStatusType.VIDEO; showPreview = false; error = null }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            beginMatureVoiceRecording(
                context,
                onStarted = { r, f -> recorder = r; recordingFile = f; recordingStarted = System.currentTimeMillis(); elapsed = 0L; recording = true; error = null },
                onError = { message -> error = message }
            )
        } else error = "Microphone permission is required for a voice Status."
    }

    LaunchedEffect(showMusicPicker, musicSearch) {
        if (!showMusicPicker) return@LaunchedEffect
        musicLoading = true
        FynxMusicCatalogueClient.listPublished(context, musicSearch)
            .onSuccess { musicCatalogue = it }
            .onFailure { error = it.message ?: "Music catalogue could not be loaded." }
        musicLoading = false
    }

    LaunchedEffect(recording, recordingStarted) {
        while (recording) {
            elapsed = System.currentTimeMillis() - recordingStarted
            if (elapsed >= FYNX_STATUS_MAX_VOICE_DURATION_MS) {
                stopMatureVoiceRecording(recorder, recordingFile) { uri, message ->
                    if (uri != null) { mediaUri = uri; error = null } else error = message ?: "Voice recording could not be saved."
                    recorder = null; recordingFile = null; recording = false; type = FynxStatusType.VOICE
                }
            }
            delay(200)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recorder?.let { runCatching { it.stop() }; runCatching { it.release() } }
            recordingFile?.let { if (it.exists()) runCatching { it.delete() } }
        }
    }

    fun clearDraft() {
        if (publishing || recording) return
        mediaUri = null; selectedMusic = null; text = ""; type = FynxStatusType.TEXT
        background = MATURE_STATUS_BACKGROUNDS.first(); foreground = 0xFFFFFFFF
        font = FynxStatusTextFont.CLASSIC; alignment = 1; showColors = false
        showTools = false; showMediaTools = false; showPreview = false; cropMedia = false; error = null
    }

    fun publish() {
        if (publishing || recording) return
        if (type == FynxStatusType.TEXT && text.isBlank()) { error = "Write something first."; return }
        if (type != FynxStatusType.TEXT && mediaUri == null) { error = "Add your media first."; return }
        publishing = true; error = null; showTools = false; showMediaTools = false; showPreview = false
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
                    FynxStatusClient.uploadMedia(context, source, mime).getOrElse {
                        error = it.message ?: "Media upload failed."; return@launch
                    }
                }
                val now = System.currentTimeMillis()
                val status = FynxStatus(
                    id = UUID.randomUUID().toString(), ownerUsername = username, ownerDisplayName = displayName,
                    type = type, contentUri = mediaId?.let { "/api/media/$it" }, text = text.trim().ifBlank { null },
                    createdAtMillis = now, expiresAtMillis = now + FYNX_STATUS_EXPIRY_MS,
                    textStyle = FynxStatusTextStyle(background, foreground, font, alignment),
                    privateStatus = audience != FynxStatusAudience.EVERYONE,
                    voiceDurationMs = if (type == FynxStatusType.VOICE) elapsed else 0L,
                    audience = audience, musicCatalogueId = selectedMusic?.id,
                    musicTitle = selectedMusic?.title, musicArtist = selectedMusic?.artist,
                    musicDurationMs = selectedMusic?.durationMs ?: 0L
                )
                FynxStatusClient.create(context, status, mediaId).getOrElse {
                    error = it.message ?: "Status publishing failed."; return@launch
                }
                FynxStatusStore.save(context, status)
                onClose()
            } finally { publishing = false }
        }
    }

    fun openVoice() {
        if (publishing || recording) return
        type = FynxStatusType.VOICE; showColors = false; showPreview = false; error = null
        if (mediaUri == null) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                beginMatureVoiceRecording(context, onStarted = { r, f -> recorder = r; recordingFile = f; recordingStarted = System.currentTimeMillis(); elapsed = 0L; recording = true }, onError = { error = it })
            } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    BackHandler(enabled = !publishing && !recording) {
        when {
            cameraOpen -> cameraOpen = false
            showMusicPicker -> showMusicPicker = false
            showPreview -> showPreview = false
            showColors -> showColors = false
            showMediaTools -> showMediaTools = false
            else -> onClose()
        }
    }

    if (showPreview) {
        FynxStatusCreationPreview(
            type = type, uri = mediaUri, text = text, background = background, foreground = foreground,
            font = font, alignment = alignment, elapsed = elapsed, audience = audience,
            selectedMusic = selectedMusic, cropMedia = cropMedia,
            onBack = { showPreview = false }, onShare = ::publish,
            onAudienceChange = { audience = it }
        )
    } else if (initialType == null && initialMediaUri == null && mediaUri == null && text.isBlank() && type == FynxStatusType.TEXT && !showMediaTools) {
        FynxStatusTypeSelection(
            displayName = displayName,
            onClose = onClose,
            onText = { type = FynxStatusType.TEXT; showMediaTools = true; showColors = true },
            onPhoto = { pickPhoto.launch(arrayOf("image/*")) },
            onVideo = { pickVideo.launch(arrayOf("video/*")) },
            onVoice = ::openVoice
        )
    } else {
        FynxStatusEditorSurface(
            context = context, displayName = displayName, username = username, type = type, text = text,
            background = background, foreground = foreground, font = font, alignment = alignment,
            mediaUri = mediaUri, recording = recording, elapsed = elapsed, publishing = publishing,
            showColors = showColors, showMediaTools = showMediaTools, selectedMusic = selectedMusic,
            audience = audience, cropMedia = cropMedia, error = error,
            onClose = onClose, onTextChange = { text = it.take(FYNX_STATUS_MAX_TEXT_LENGTH) },
            onBackgroundChange = { background = it }, onForegroundChange = { foreground = it },
            onFontChange = { font = it }, onAlignmentChange = { alignment = it },
            onAudienceChange = { audience = it }, onPickMedia = { pickMedia.launch(arrayOf("image/*", "video/*")) },
            onPickPhoto = { pickPhoto.launch(arrayOf("image/*")) }, onPickVideo = { pickVideo.launch(arrayOf("video/*")) },
            onCamera = { cameraOpen = true; showColors = false; showTools = false; error = null },
            onVoice = ::openVoice, onStopVoice = {
                stopMatureVoiceRecording(recorder, recordingFile) { uri, message ->
                    if (uri != null) { mediaUri = uri; error = null } else error = message ?: "Voice recording could not be saved."
                    recorder = null; recordingFile = null; recording = false
                }
            },
            onShowColors = { showColors = it }, onShowMediaTools = { showMediaTools = it },
            onOpenMusic = { showMusicPicker = true }, onRemoveMusic = { selectedMusic = null },
            onCropToggle = { cropMedia = !cropMedia }, onPreview = { showPreview = true }, onClear = ::clearDraft,
            onEmoji = { emoji -> text = (text + emoji).take(FYNX_STATUS_MAX_TEXT_LENGTH) }
        )
    }

    if (showMusicPicker) {
        AlertDialog(
            onDismissRequest = { showMusicPicker = false; musicSearch = "" },
            title = { Text("FYNX Music") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = musicSearch, onValueChange = { musicSearch = it.take(80) }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search music") }, placeholder = { Text("Song or artist") })
                    Column(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        if (musicLoading) Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        else if (musicCatalogue.isEmpty()) Text("No FYNX music is published yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else musicCatalogue.forEach { track ->
                            Card(Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Icon(Icons.Default.MusicNote, "Music", tint = MaterialTheme.colorScheme.primary)
                                    Column(Modifier.weight(1f)) { Text(track.title.ifBlank { "Untitled" }, maxLines = 1); Text(track.artist.ifBlank { "FYNX" }, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
                                    if (musicPreviewId == track.id) {
                                        FynxRemoteAudio("/api/social/music/catalogue/${track.id}/media", Modifier.width(92.dp), track.durationMs.coerceAtLeast(1_000L))
                                        TextButton(onClick = { musicPreviewId = null }) { Text("Stop") }
                                    } else TextButton(onClick = { musicPreviewId = track.id }) { Text("Preview") }
                                    IconButton(onClick = { selectedMusic = track; showMusicPicker = false; musicSearch = ""; musicPreviewId = null }) { Icon(Icons.Default.Check, "Add music") }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMusicPicker = false; musicSearch = "" }) { Text("Close") } }
        )
    }

    if (cameraOpen) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { if (!publishing && !recording) cameraOpen = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = !publishing && !recording, dismissOnClickOutside = false)
        ) {
            Surface(Modifier.fillMaxSize(), color = Color.Black) {
                Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    FynxCameraCapturePanel(
                        onCaptured = { uri, capturedType -> mediaUri = uri; type = if (capturedType == "video") FynxStatusType.VIDEO else FynxStatusType.PHOTO; cameraOpen = false; error = null; showPreview = false },
                        onDismiss = { if (!publishing && !recording) cameraOpen = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun FynxStatusTypeSelection(displayName: String, onClose: () -> Unit, onText: () -> Unit, onPhoto: () -> Unit, onVideo: () -> Unit, onVoice: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xFF061522)).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Create Status", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text("Share a moment with your friends.", color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.bodySmall)
                    Text("It expires after 24 hours.", color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.size(48.dp))
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                StatusTypeTile(Icons.Default.TextFields, "Text", Color(0xFF4721E8), Modifier.weight(1f), onText)
                StatusTypeTile(Icons.Default.Photo, "Photo", Color(0xFF087AF5), Modifier.weight(1f), onPhoto)
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                StatusTypeTile(Icons.Default.Videocam, "Video", Color(0xFFE52C55), Modifier.weight(1f), onVideo)
                StatusTypeTile(Icons.Default.Mic, "Voice", Color(0xFF12B99A), Modifier.weight(1f), onVoice)
            }
            Spacer(Modifier.weight(1.2f))
            Text("Posting as $displayName", color = Color.White.copy(alpha = .5f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun StatusTypeTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = RoundedCornerShape(24.dp), color = color, modifier = Modifier.fillMaxWidth().aspectRatio(1f).clickable(onClick = onClick)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(46.dp)) }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FynxStatusEditorSurface(
    context: Context, displayName: String, username: String, type: FynxStatusType, text: String,
    background: Long, foreground: Long, font: FynxStatusTextFont, alignment: Int, mediaUri: Uri?,
    recording: Boolean, elapsed: Long, publishing: Boolean, showColors: Boolean, showMediaTools: Boolean,
    selectedMusic: FynxMusicCatalogueTrack?, audience: FynxStatusAudience, cropMedia: Boolean,
    error: String?, onClose: () -> Unit, onTextChange: (String) -> Unit, onBackgroundChange: (Long) -> Unit,
    onForegroundChange: (Long) -> Unit, onFontChange: (FynxStatusTextFont) -> Unit, onAlignmentChange: (Int) -> Unit,
    onAudienceChange: (FynxStatusAudience) -> Unit, onPickMedia: () -> Unit, onPickPhoto: () -> Unit,
    onPickVideo: () -> Unit, onCamera: () -> Unit, onVoice: () -> Unit, onStopVoice: () -> Unit,
    onShowColors: (Boolean) -> Unit, onShowMediaTools: (Boolean) -> Unit, onOpenMusic: () -> Unit,
    onRemoveMusic: () -> Unit, onCropToggle: () -> Unit, onPreview: () -> Unit, onClear: () -> Unit,
    onEmoji: (String) -> Unit
) {
    val textAlign = when (alignment) { 0 -> TextAlign.Start; 2 -> TextAlign.End; else -> TextAlign.Center }
    val weight = if (font == FynxStatusTextFont.BOLD) FontWeight.Bold else FontWeight.Normal
    val fontFamily = matureStatusFont(font)
    Box(Modifier.fillMaxSize().background(Color(background)).windowInsetsPadding(WindowInsets.safeDrawing)) {
        when (type) {
            FynxStatusType.TEXT -> BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                OutlinedTextField(
                    value = text, onValueChange = onTextChange,
                    placeholder = { Text("Type a Status", color = Color(foreground).copy(alpha = .55f), textAlign = textAlign, modifier = Modifier.fillMaxWidth()) },
                    textStyle = LocalTextStyle.current.copy(color = Color(foreground), textAlign = textAlign, fontFamily = fontFamily, fontWeight = weight, fontSize = ((maxWidth.value * .085f).coerceIn(24f, 42f)).sp, lineHeight = ((maxWidth.value * .105f).coerceIn(30f, 52f)).sp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent, cursorColor = Color(foreground), focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 74.dp, bottom = if (showColors || showMediaTools) 220.dp else 132.dp).imePadding(),
                    minLines = 2, maxLines = 10
                )
            }
            FynxStatusType.PHOTO -> mediaUri?.let { MatureStatusMedia(it, false, cropMedia, Modifier.fillMaxSize().padding(top = 64.dp, bottom = if (showMediaTools) 176.dp else 122.dp)) }
            FynxStatusType.VIDEO -> mediaUri?.let { MatureStatusMedia(it, true, cropMedia, Modifier.fillMaxSize().padding(top = 64.dp, bottom = if (showMediaTools) 176.dp else 122.dp)) }
            FynxStatusType.VOICE -> Box(Modifier.fillMaxSize().padding(top = 64.dp, bottom = if (showMediaTools) 176.dp else 122.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Surface(shape = CircleShape, color = Color(0xFF00D5A8).copy(alpha = .16f), modifier = Modifier.size(124.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Mic, null, tint = Color(0xFF28E6C0), modifier = Modifier.size(54.dp)) } }
                    Text(if (recording) formatMatureTime(elapsed) else if (mediaUri != null) "Voice Status ready" else "Press the microphone to record", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    VoiceWaveform(progress = if (recording) (elapsed.toFloat() / FYNX_STATUS_MAX_VOICE_DURATION_MS).coerceIn(0f, 1f) else 1f)
                    if (recording) FilledTonalButton(onClick = onStopVoice) { Icon(Icons.Default.Stop, "Stop"); Spacer(Modifier.width(6.dp)); Text("Stop • ${formatMatureTime(elapsed)}") }
                    else if (mediaUri != null) FynxRemoteAudio(mediaUri.toString(), Modifier.width(240.dp), elapsed.coerceAtLeast(1_000L))
                    else TextButton(onClick = onVoice) { Icon(Icons.Default.Mic, null); Spacer(Modifier.width(6.dp)); Text("Record voice") }
                }
            }
        }

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose, enabled = !publishing && !recording) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    FynxProfileImage(username, FynxPreferencesStore.loadProfilePhoto(context), Modifier.size(36.dp).clip(CircleShape))
                    Spacer(Modifier.width(10.dp)); Text(displayName, color = Color.White, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                }
                IconButton(onClick = { onShowColors(type == FynxStatusType.TEXT) }, enabled = !recording && !publishing) { Icon(if (type == FynxStatusType.TEXT) Icons.Default.TextFields else Icons.Default.MusicNote, "Status tools", tint = Color.White) }
            }

            Spacer(Modifier.weight(1f))
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                selectedMusic?.let { music ->
                    Surface(color = Color.Black.copy(alpha = .34f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MusicNote, "Music", tint = Color.White)
                            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) { Text(music.title.ifBlank { "FYNX Music" }, color = Color.White, maxLines = 1); Text(music.artist.ifBlank { "FYNX" }, color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                            IconButton(onClick = onRemoveMusic, enabled = !recording && !publishing) { Icon(Icons.Default.Close, "Remove music", tint = Color.White) }
                        }
                    }
                }

                if (showColors && type == FynxStatusType.TEXT) {
                    Surface(color = Color.Black.copy(alpha = .58f), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("Background", color = Color.White, style = MaterialTheme.typography.labelLarge)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) { items(MATURE_STATUS_BACKGROUNDS) { value -> Surface(shape = CircleShape, color = Color(value), modifier = Modifier.size(42.dp).clickable(enabled = !recording && !publishing) { onBackgroundChange(value) }) {} } }
                            Text("Text color", color = Color.White, style = MaterialTheme.typography.labelLarge)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) { items(MATURE_STATUS_TEXT_COLORS) { value -> Surface(shape = CircleShape, color = Color(value), modifier = Modifier.size(42.dp).clickable(enabled = !recording && !publishing) { onForegroundChange(value) }) {} } }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) { items(FynxStatusTextFont.values().toList()) { option -> FilterChip(selected = font == option, onClick = { onFontChange(option) }, enabled = !recording && !publishing, label = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) }) } }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                IconButton(onClick = { onAlignmentChange(0) }) { Icon(Icons.Default.FormatAlignLeft, "Align left", tint = if (alignment == 0) Color.White else Color.White.copy(alpha = .5f)) }
                                IconButton(onClick = { onAlignmentChange(1) }) { Icon(Icons.Default.FormatAlignCenter, "Align center", tint = if (alignment == 1) Color.White else Color.White.copy(alpha = .5f)) }
                                IconButton(onClick = { onAlignmentChange(2) }) { Icon(Icons.Default.FormatAlignRight, "Align right", tint = if (alignment == 2) Color.White else Color.White.copy(alpha = .5f)) }
                            }
                            TextButton(onClick = { onShowColors(false) }) { Text("Done", color = Color.White) }
                        }
                    }
                }

                if (type != FynxStatusType.TEXT && mediaUri != null) {
                    OutlinedTextField(value = text, onValueChange = onTextChange, modifier = Modifier.fillMaxWidth().imePadding(), singleLine = true, placeholder = { Text("Add a caption…", color = Color.White.copy(alpha = .72f)) }, textStyle = LocalTextStyle.current.copy(color = Color.White, fontWeight = FontWeight.Bold), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color.White.copy(alpha = .7f), unfocusedBorderColor = Color.White.copy(alpha = .42f), cursorColor = Color.White, focusedContainerColor = Color.Black.copy(alpha = .18f), unfocusedContainerColor = Color.Black.copy(alpha = .18f)))
                }

                if (showMediaTools) {
                    LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(17.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                        item { MatureStatusModeButton(Icons.Default.TextFields, "Text", type == FynxStatusType.TEXT, !recording && !publishing) { onShowMediaTools(false); onShowColors(false) } }
                        item { MatureStatusModeButton(Icons.Default.Photo, "Photo", type == FynxStatusType.PHOTO, !recording && !publishing, onPickPhoto) }
                        item { MatureStatusModeButton(Icons.Default.Videocam, "Video", type == FynxStatusType.VIDEO, !recording && !publishing, onPickVideo) }
                        item { MatureStatusModeButton(Icons.Default.CameraAlt, "Camera", false, !recording && !publishing, onCamera) }
                        item { MatureStatusModeButton(Icons.Default.Mic, "Voice", type == FynxStatusType.VOICE, !publishing && !recording, onVoice) }
                        item { MatureStatusModeButton(Icons.Default.MusicNote, "Music", selectedMusic != null, !recording && !publishing, onOpenMusic) }
                        if (type == FynxStatusType.PHOTO || type == FynxStatusType.VIDEO) item { MatureStatusModeButton(Icons.Default.Photo, if (cropMedia) "Fit" else "Crop", cropMedia, !recording && !publishing, onCropToggle) }
                    }
                }

                if (type == FynxStatusType.TEXT) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TextFields, null, tint = Color.White)
                        if (text.length >= FYNX_STATUS_MAX_TEXT_LENGTH) Text("$FYNX_STATUS_MAX_TEXT_LENGTH/$FYNX_STATUS_MAX_TEXT_LENGTH", color = Color.White, modifier = Modifier.padding(start = 8.dp))
                        Spacer(Modifier.weight(1f))
                        var showStatusEmoji by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showStatusEmoji = true }, enabled = !recording && !publishing) { Icon(Icons.Default.EmojiEmotions, "Add emoji", tint = Color.White) }
                            DropdownMenu(expanded = showStatusEmoji, onDismissRequest = { showStatusEmoji = false }) { listOf("😀","😂","😍","🔥","❤️","👍","🎉","😮").forEach { emoji -> DropdownMenuItem(text = { Text(emoji, fontSize = 22.sp) }, onClick = { onEmoji(emoji); showStatusEmoji = false }) } }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Audience", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(10.dp))
                    var audienceMenu by remember { mutableStateOf(false) }
                    Box {
                        AssistChip(onClick = { audienceMenu = true }, enabled = !recording && !publishing, leadingIcon = { Icon(if (audience == FynxStatusAudience.EVERYONE) Icons.Default.Public else if (audience == FynxStatusAudience.FRIENDS) Icons.Default.People else Icons.Default.Lock, null) }, label = { Text(if (audience == FynxStatusAudience.EVERYONE) "Everyone" else if (audience == FynxStatusAudience.FRIENDS) "Friends" else "Only me") })
                        DropdownMenu(expanded = audienceMenu, onDismissRequest = { audienceMenu = false }) {
                            DropdownMenuItem(text = { Text("Everyone") }, onClick = { onAudienceChange(FynxStatusAudience.EVERYONE); FynxPreferencesStore.saveVisibility(context, "status_audience", FynxStatusAudience.EVERYONE.name); audienceMenu = false })
                            DropdownMenuItem(text = { Text("Friends") }, onClick = { onAudienceChange(FynxStatusAudience.FRIENDS); FynxPreferencesStore.saveVisibility(context, "status_audience", FynxStatusAudience.FRIENDS.name); audienceMenu = false })
                            DropdownMenuItem(text = { Text("Only me") }, onClick = { onAudienceChange(FynxStatusAudience.ONLY_ME); FynxPreferencesStore.saveVisibility(context, "status_audience", FynxStatusAudience.ONLY_ME.name); audienceMenu = false })
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = onPreview, enabled = !publishing && !recording && ((type == FynxStatusType.TEXT && text.isNotBlank()) || (type != FynxStatusType.TEXT && mediaUri != null)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .45f))) { Text("Preview") }
                }

                Button(onClick = { onShowMediaTools(!showMediaTools) }, enabled = !recording && !publishing, modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = .12f), contentColor = Color.White), shape = RoundedCornerShape(15.dp)) {
                    Icon(if (showMediaTools) Icons.Default.Close else Icons.Default.AddAPhoto, if (showMediaTools) "Hide tools" else "Add media tools")
                    Spacer(Modifier.width(7.dp)); Text(if (showMediaTools) "Hide editing tools" else "Editing tools")
                }
                if (publishing) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = Color.White, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun FynxStatusCreationPreview(
    type: FynxStatusType, uri: Uri?, text: String, background: Long, foreground: Long, font: FynxStatusTextFont,
    alignment: Int, elapsed: Long, audience: FynxStatusAudience, selectedMusic: FynxMusicCatalogueTrack?, cropMedia: Boolean,
    onBack: () -> Unit, onShare: () -> Unit, onAudienceChange: (FynxStatusAudience) -> Unit
) {
    val textAlign = when (alignment) { 0 -> TextAlign.Start; 2 -> TextAlign.End; else -> TextAlign.Center }
    Box(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing)) {
        when (type) {
            FynxStatusType.TEXT -> Box(Modifier.fillMaxSize().background(Color(background)).padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
                Text(text, color = Color(foreground), textAlign = textAlign, fontFamily = matureStatusFont(font), fontWeight = if (font == FynxStatusTextFont.BOLD) FontWeight.Bold else FontWeight.Normal, fontSize = 30.sp, modifier = Modifier.fillMaxWidth())
            }
            FynxStatusType.PHOTO -> uri?.let { MatureStatusMedia(it, false, cropMedia, Modifier.fillMaxSize()) }
            FynxStatusType.VIDEO -> uri?.let { MatureStatusMedia(it, true, cropMedia, Modifier.fillMaxSize()) }
            FynxStatusType.VOICE -> Box(Modifier.fillMaxSize().background(Color(background)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Surface(shape = CircleShape, color = Color.White.copy(alpha = .12f), modifier = Modifier.size(112.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Mic, null, tint = Color.White, modifier = Modifier.size(48.dp)) } }
                    Text(formatMatureTime(elapsed), color = Color.White, style = MaterialTheme.typography.titleLarge)
                    VoiceWaveform(1f)
                    if (uri != null) FynxRemoteAudio(uri.toString(), Modifier.width(250.dp), elapsed.coerceAtLeast(1_000L))
                }
            }
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                Text("Preview", color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.weight(1f))
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                selectedMusic?.let { Surface(color = Color.Black.copy(alpha = .42f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.MusicNote, null, tint = Color.White); Spacer(Modifier.width(8.dp)); Column { Text(it.title.ifBlank { "FYNX Music" }, color = Color.White); Text(it.artist.ifBlank { "FYNX" }, color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.labelSmall) } } } }
                Surface(color = Color.Black.copy(alpha = .48f), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Who can see this Status?", color = Color.White, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.weight(1f))
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            AssistChip(onClick = { menu = true }, leadingIcon = { Icon(if (audience == FynxStatusAudience.EVERYONE) Icons.Default.Public else if (audience == FynxStatusAudience.FRIENDS) Icons.Default.People else Icons.Default.Lock, null) }, label = { Text(if (audience == FynxStatusAudience.EVERYONE) "Everyone" else if (audience == FynxStatusAudience.FRIENDS) "Friends" else "Only me") })
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Everyone") }, onClick = { onAudienceChange(FynxStatusAudience.EVERYONE); menu = false })
                                DropdownMenuItem(text = { Text("Friends") }, onClick = { onAudienceChange(FynxStatusAudience.FRIENDS); menu = false })
                                DropdownMenuItem(text = { Text("Only me") }, onClick = { onAudienceChange(FynxStatusAudience.ONLY_ME); menu = false })
                            }
                        }
                    }
                }
                Button(onClick = onShare, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D5A8))) { Text("Share Status", color = Color(0xFF00251D), fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun VoiceWaveform(progress: Float) {
    val bars = listOf(18, 30, 24, 42, 28, 52, 35, 64, 42, 56, 26, 46, 32, 58, 38, 28, 50, 34, 44, 22)
    Row(Modifier.fillMaxWidth().height(54.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        bars.forEachIndexed { index, height ->
            Box(Modifier.padding(horizontal = 2.dp).width(3.dp).height((height * (.55f + .45f * progress)).dp).clip(RoundedCornerShape(3.dp)).background(if (index.toFloat() / bars.size <= progress) Color(0xFF12D8B0) else Color.White.copy(alpha = .28f)))
        }
    }
}

@Composable
private fun MatureStatusModeButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 48.dp)) {
        IconButton(onClick = onClick, enabled = enabled) { Icon(icon, label, tint = if (selected) Color.White else Color.White.copy(alpha = .68f), modifier = Modifier.size(27.dp)) }
        Text(label, color = Color.White.copy(alpha = if (enabled && selected) 1f else .72f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun MatureStatusMedia(uri: Uri, video: Boolean, crop: Boolean, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            if (video) VideoView(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setVideoURI(uri)
                setOnPreparedListener { it.isLooping = true; it.start() }
            } else ImageView(context).apply {
                scaleType = if (crop) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
                setImageURI(uri)
            }
        },
        update = { view ->
            if (view is VideoView) { view.setVideoURI(uri); view.scaleX = 1f; view.scaleY = 1f }
            else { view as ImageView; view.scaleType = if (crop) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER; view.setImageURI(uri) }
        }
    )
}

private fun matureStatusFont(font: FynxStatusTextFont): FontFamily = when (font) {
    FynxStatusTextFont.SERIF -> FontFamily.Serif
    FynxStatusTextFont.TYPEWRITER -> FontFamily.Monospace
    else -> FontFamily.SansSerif
}

private fun beginMatureVoiceRecording(context: Context, onStarted: (MediaRecorder, File) -> Unit, onError: (String) -> Unit) {
    val file = File(context.cacheDir, "fynx-status-${System.currentTimeMillis()}.m4a")
    var recorder: MediaRecorder? = null
    try {
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setMaxDuration(FYNX_STATUS_MAX_VOICE_DURATION_MS.toInt())
            setOutputFile(file.absolutePath)
            prepare(); start()
        }
        onStarted(recorder, file)
    } catch (e: Exception) {
        runCatching { recorder?.reset() }; runCatching { recorder?.release() }; runCatching { file.delete() }
        onError(e.message ?: "Unable to start voice recording.")
    }
}

private fun stopMatureVoiceRecording(recorder: MediaRecorder?, file: File?, onFinished: (Uri?, String?) -> Unit) {
    if (recorder == null) { onFinished(null, "Voice recorder is not active."); return }
    var stopped = false
    try { recorder.stop(); stopped = true } catch (e: Exception) { runCatching { recorder.reset() }; onFinished(null, e.message ?: "Unable to stop voice recording.") }
    finally { runCatching { recorder.release() } }
    if (stopped) {
        val output = file?.takeIf { it.exists() && it.length() > 0L }
        if (output != null) onFinished(Uri.fromFile(output), null)
        else { runCatching { file?.delete() }; onFinished(null, "Voice recording was empty.") }
    }
}

private fun formatMatureTime(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    return "%02d:%02d".format(total / 60L, total % 60L)
}
