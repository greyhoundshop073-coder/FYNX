package com.fynx.app.ui

import android.content.Intent
import android.net.Uri
import android.widget.ImageView
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Keeps the existing Home experience intact while providing a real, large social post composer. */
@Composable
fun FynxHomeSocialHubPanel(
    currentUsername: String,
    initialCaption: String? = null,
    onCaptionConsumed: () -> Unit = {},
    onOpenChats: () -> Unit = {},
    onOpenStories: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenMarketplace: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenFindPeople: () -> Unit = {},
    onOpenAi: () -> Unit = {},
    onOpenAuthorProfile: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuredPostVisibility = remember { FynxPreferencesStore.loadVisibility(context, "privacy_posts_visibility") }
    val postingAllowed = configuredPostVisibility != "Nobody"
    val defaultPostVisibility = if (configuredPostVisibility == "Everyone") FynxPostVisibility.PUBLIC else FynxPostVisibility.FRIENDS_ONLY
    var showComposer by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }
    var showVoiceRecorder by remember { mutableStateOf(false) }
    var showPhotoEditor by remember { mutableStateOf(false) }
    var capturedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var capturedTypes by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedVisualIndex by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf("") }
    var visibility by remember { mutableStateOf(defaultPostVisibility) }
    var notice by remember { mutableStateOf<String?>(null) }
    var posting by remember { mutableStateOf(false) }
    var aiCaptionLoading by remember { mutableStateOf(false) }
    var networkLevel by remember { mutableStateOf(FynxNetworkQuality.current(context)) }

    LaunchedEffect(Unit) {
        while (true) { networkLevel = FynxNetworkQuality.current(context); delay(5_000L) }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            uris.take(4).forEach { uri -> runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
            val selected = uris.distinct().take(4)
            capturedUris = (capturedUris.filterNot { it in selected } + selected).take(4)
            capturedTypes = capturedUris.map { uri -> if (context.contentResolver.getType(uri)?.startsWith("video/") == true) "video" else "image" }
            selectedVisualIndex = 0
            showComposer = true
        }
    }

    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            capturedUris = (capturedUris.filterNot { it == uri } + uri).take(4)
            capturedTypes = capturedUris.map { item -> when { context.contentResolver.getType(item)?.startsWith("video/") == true -> "video"; context.contentResolver.getType(item)?.startsWith("audio/") == true -> "audio"; else -> "image" } }
            showComposer = true
        }
    }

    LaunchedEffect(initialCaption) {
        if (!initialCaption.isNullOrBlank()) { text = initialCaption.trim().take(4000); capturedUris = emptyList(); capturedTypes = emptyList(); selectedVisualIndex = 0; notice = null; showComposer = true; onCaptionConsumed() }
    }

    fun requestInlineCaptionHelp() {
        if (aiCaptionLoading || text.trim().isBlank()) return
        val capability = FynxAiCapability.MEDIA_ASSIST
        val instruction = "Improve this social-media post caption. Keep the user's original meaning and facts, make it natural, clear and engaging, and do not add invented personal details. Return only the finished caption.\n\nCaption:\n${text.trim().take(4000)}"
        val decision = FynxFutureIntelligencePolicy.authorize(permissions = listOf(FynxAiPermission(capability, setOf(FynxAiDataScope.NONE), true)), request = FynxAiRequest(capability, instruction, setOf(FynxAiDataScope.NONE)))
        if (!decision.allowed) { notice = "FYNX AI could not assist with this caption right now."; return }
        aiCaptionLoading = true; notice = null
        scope.launch { val response = withContext(Dispatchers.IO) { AiAssistantClient.improvePostCaption(context, text) }; response.onSuccess { improved -> text = improved.trim().take(4000) }.onFailure { notice = "FYNX AI caption assistance is temporarily unavailable." }; aiCaptionLoading = false }
    }

    fun recomputeTypes() {
        capturedTypes = capturedUris.map { item -> when { context.contentResolver.getType(item)?.startsWith("video/") == true -> "video"; context.contentResolver.getType(item)?.startsWith("audio/") == true -> "audio"; else -> "image" } }
        val visualCount = capturedTypes.count { it == "image" || it == "video" }
        selectedVisualIndex = selectedVisualIndex.coerceIn(0, (visualCount - 1).coerceAtLeast(0))
    }

    fun removeCapturedUri(uri: Uri) {
        if (posting || aiCaptionLoading) return
        capturedUris = capturedUris.filterNot { it == uri }
        recomputeTypes()
    }

    fun clearComposer() {
        if (!posting && !aiCaptionLoading) {
            showComposer = false
            capturedUris = emptyList()
            capturedTypes = emptyList()
            selectedVisualIndex = 0
            text = ""
            notice = null
        }
    }

    fun finishComposerAfterSuccess() {
        showComposer = false
        capturedUris = emptyList()
        capturedTypes = emptyList()
        selectedVisualIndex = 0
        text = ""
        notice = null
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (networkLevel != FynxNetworkQuality.Level.GOOD) Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) { Text(if (networkLevel == FynxNetworkQuality.Level.OFFLINE) "You are offline. FYNX will keep the app usable while you reconnect." else "Weak connection detected. Media uploads may take longer.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            HomePanel(currentUsername = currentUsername, onOpenChats = onOpenChats, onOpenStories = onOpenStories, onOpenProfile = onOpenProfile, onOpenMarketplace = onOpenMarketplace, onOpenNotifications = onOpenNotifications, onOpenFindPeople = onOpenFindPeople, onOpenAi = onOpenAi, onCreatePost = { showComposer = true; notice = null }, onOpenAuthorProfile = onOpenAuthorProfile)
        }
    }

    if (showComposer) {
        Dialog(
            onDismissRequest = { clearComposer() },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !posting && !aiCaptionLoading, dismissOnClickOutside = !posting && !aiCaptionLoading)
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().imePadding()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { clearComposer() }, enabled = !posting && !aiCaptionLoading) { Icon(Icons.Default.Close, "Close") }
                        Text("Create post", style = MaterialTheme.typography.titleLarge)
                        Button(enabled = !posting && !aiCaptionLoading && postingAllowed && networkLevel != FynxNetworkQuality.Level.OFFLINE && (text.isNotBlank() || capturedUris.isNotEmpty()), onClick = {
                            if (FynxNetworkQuality.current(context) == FynxNetworkQuality.Level.OFFLINE) { notice = "You are offline. Reconnect before publishing this post."; return@Button }
                            posting = true; notice = null
                            scope.launch { val result = withContext(Dispatchers.IO) { FynxMultiMediaPostClient.createPost(context, text, visibility, capturedUris) }; result.onSuccess { finishComposerAfterSuccess() }.onFailure { notice = it.message ?: "Post could not be published." }; posting = false }
                        }) { Text(if (posting) "Publishing…" else "Post") }
                    }

                    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (!postingAllowed) Text("Posting is disabled by your Posts privacy setting.", color = MaterialTheme.colorScheme.error)

                        Text("Share something with your FYNX circle", style = MaterialTheme.typography.headlineSmall)
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it.take(4000) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 190.dp),
                            minLines = 7,
                            maxLines = 14,
                            placeholder = { Text("What's on your mind? Write your post here…", style = MaterialTheme.typography.titleMedium) },
                            textStyle = MaterialTheme.typography.bodyLarge,
                            enabled = !posting && !aiCaptionLoading && postingAllowed,
                            trailingIcon = { IconButton(onClick = ::requestInlineCaptionHelp, enabled = !posting && !aiCaptionLoading && postingAllowed && text.trim().isNotBlank()) { Icon(Icons.Default.AutoAwesome, "Improve caption with FYNX AI") } }
                        )
                        if (aiCaptionLoading) Text("FYNX AI is improving your caption…", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)

                        Text("Add to your post • up to 4 photos/videos", style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ComposerAction("Photo", Icons.Default.Image, { gallery.launch(arrayOf("image/*")) }, !posting && !aiCaptionLoading && postingAllowed, Modifier.weight(1f))
                            ComposerAction("Video", Icons.Default.VideoLibrary, { gallery.launch(arrayOf("video/*")) }, !posting && !aiCaptionLoading && postingAllowed, Modifier.weight(1f))
                            ComposerAction("Camera", Icons.Default.CameraAlt, { showComposer = false; showCamera = true }, !posting && !aiCaptionLoading && postingAllowed, Modifier.weight(1f))
                            ComposerAction("Voice", Icons.Default.Mic, { showComposer = false; showVoiceRecorder = true }, !posting && !aiCaptionLoading && postingAllowed, Modifier.weight(1f))
                        }

                        if (capturedUris.isNotEmpty()) {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    val audioCount = capturedTypes.count { it == "audio" }
                                    val visualItems = capturedUris.mapIndexedNotNull { index, uri ->
                                        capturedTypes.getOrNull(index)?.takeIf { it == "image" || it == "video" }?.let { type -> Triple(index, uri, type) }
                                    }
                                    val selectedVisual = visualItems.getOrNull(selectedVisualIndex.coerceIn(0, (visualItems.size - 1).coerceAtLeast(0)))
                                    Text("Attached media", style = MaterialTheme.typography.titleMedium)
                                    if (selectedVisual != null) {
                                        Box(Modifier.fillMaxWidth().heightIn(min = 150.dp, max = 280.dp)) {
                                            if (selectedVisual.third == "video") {
                                                AndroidView(factory = { VideoView(it).apply { setVideoURI(selectedVisual.second); setOnPreparedListener { player -> player.isLooping = true; start() } } }, update = { view -> if (view.tag != selectedVisual.second.toString()) { view.tag = selectedVisual.second.toString(); view.setVideoURI(selectedVisual.second); view.start() } }, modifier = Modifier.fillMaxSize())
                                            } else {
                                                AndroidView(factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER } }, update = { view -> view.setImageURI(selectedVisual.second) }, modifier = Modifier.fillMaxSize())
                                            }
                                        }
                                    }
                                    if (visualItems.size > 1) {
                                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            visualItems.forEachIndexed { visualIndex, (_, uri, type) ->
                                                Box(Modifier.size(76.dp).clickable(enabled = !posting && !aiCaptionLoading) { selectedVisualIndex = visualIndex }) {
                                                    Card(Modifier.fillMaxSize()) {
                                                        if (type == "video") {
                                                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Default.VideoLibrary, "Video ${visualIndex + 1}", modifier = Modifier.size(28.dp)) }
                                                        } else {
                                                            AndroidView(factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.CENTER_CROP } }, update = { view -> view.setImageURI(uri) }, modifier = Modifier.fillMaxSize())
                                                        }
                                                    }
                                                    IconButton(onClick = { removeCapturedUri(uri) }, enabled = !posting && !aiCaptionLoading, modifier = Modifier.align(Alignment.TopEnd).size(30.dp)) { Icon(Icons.Default.Close, "Remove media") }
                                                }
                                            }
                                        }
                                    } else if (visualItems.size == 1) {
                                        TextButton(onClick = { removeCapturedUri(visualItems.first().second) }, enabled = !posting && !aiCaptionLoading) { Icon(Icons.Default.Close, null); Spacer(Modifier.width(4.dp)); Text("Remove photo/video") }
                                    }
                                    Text("${capturedUris.size} item${if (capturedUris.size == 1) "" else "s"} ready${if (audioCount > 0) " • $audioCount audio" else ""}", color = MaterialTheme.colorScheme.primary)
                                    if (visualItems.size == 1 && visualItems.first().third == "image" && audioCount == 0) TextButton(onClick = { showComposer = false; showPhotoEditor = true }, enabled = !posting && !aiCaptionLoading) { Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(4.dp)); Text("Edit this photo with FYNX AI") }
                                    Text("Preview before publishing. Select any thumbnail to inspect it, or remove media you don't want to post.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        Text("Who can see this?", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = visibility == FynxPostVisibility.PUBLIC, onClick = { visibility = FynxPostVisibility.PUBLIC }, label = { Text("Public") }, enabled = !posting && !aiCaptionLoading && postingAllowed && configuredPostVisibility == "Everyone")
                            FilterChip(selected = visibility == FynxPostVisibility.FRIENDS_ONLY, onClick = { visibility = FynxPostVisibility.FRIENDS_ONLY }, label = { Text("Friends") }, enabled = !posting && !aiCaptionLoading && postingAllowed)
                        }
                        if (networkLevel == FynxNetworkQuality.Level.OFFLINE) Text("You are offline. Reconnect before publishing this post.", color = MaterialTheme.colorScheme.error)
                        notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }

    if (showVoiceRecorder) {
        FynxVoicePostRecorder(
            onRecorded = { uri ->
                capturedUris = (capturedUris.filterNot { it == uri } + uri).take(4)
                recomputeTypes()
                showVoiceRecorder = false
                showComposer = true
            },
            onDismiss = { showVoiceRecorder = false; showComposer = true }
        )
    }

    if (showPhotoEditor) Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { FynxAiPhotoEditorPanel(initialUri = capturedUris.firstOrNull(), onDone = { editedUri -> if (editedUri != null) { capturedUris = listOf(editedUri); capturedTypes = listOf("image") }; selectedVisualIndex = 0; showPhotoEditor = false; showComposer = true }) }
    if (showCamera) Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { FynxCameraCapturePanel(onCaptured = { uri, type -> capturedUris = (capturedUris + uri).take(4); recomputeTypes(); selectedVisualIndex = 0; showCamera = false; showComposer = true }, onDismiss = { showCamera = false; showComposer = true }) }
}

@Composable
private fun ComposerAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.height(76.dp), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(5.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}