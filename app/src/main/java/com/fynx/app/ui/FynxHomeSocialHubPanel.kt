package com.fynx.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Keeps the existing Home experience intact while adding real multi-media creation to the existing composer. */
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
    onOpenAi: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuredPostVisibility = remember { FynxPreferencesStore.loadVisibility(context, "privacy_posts_visibility") }
    val postingAllowed = configuredPostVisibility != "Nobody"
    val defaultPostVisibility = if (configuredPostVisibility == "Everyone") FynxPostVisibility.PUBLIC else FynxPostVisibility.FRIENDS_ONLY
    var showComposer by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }
    var showPhotoEditor by remember { mutableStateOf(false) }
    var capturedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var capturedTypes by remember { mutableStateOf<List<String>>(emptyList()) }
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
            uris.take(12).forEach { uri -> runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
            capturedUris = uris.distinct().take(12)
            capturedTypes = capturedUris.map { if (context.contentResolver.getType(it)?.startsWith("video/") == true) "video" else "image" }
            showComposer = true
        }
    }

    LaunchedEffect(initialCaption) {
        if (!initialCaption.isNullOrBlank()) {
            text = initialCaption.trim().take(4000); capturedUris = emptyList(); capturedTypes = emptyList(); notice = null; showComposer = true; onCaptionConsumed()
        }
    }

    fun requestInlineCaptionHelp() {
        if (aiCaptionLoading || text.trim().isBlank()) return
        val capability = FynxAiCapability.MEDIA_ASSIST
        val instruction = "Improve this social-media post caption. Keep the user's original meaning and facts, make it natural, clear and engaging, and do not add invented personal details. Return only the finished caption.\n\nCaption:\n${text.trim().take(4000)}"
        val decision = FynxFutureIntelligencePolicy.authorize(permissions = listOf(FynxAiPermission(capability, setOf(FynxAiDataScope.NONE), true)), request = FynxAiRequest(capability, instruction, setOf(FynxAiDataScope.NONE)))
        if (!decision.allowed) { notice = "FYNX AI could not assist with this caption right now."; return }
        aiCaptionLoading = true; notice = null
        scope.launch {
            val response = withContext(Dispatchers.IO) { AiAssistantClient.improvePostCaption(context, text) }
            response.onSuccess { improved -> text = improved.trim().take(4000) }.onFailure { notice = "FYNX AI caption assistance is temporarily unavailable." }
            aiCaptionLoading = false
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (networkLevel != FynxNetworkQuality.Level.GOOD) Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
            Text(if (networkLevel == FynxNetworkQuality.Level.OFFLINE) "You are offline. FYNX will keep the app usable while you reconnect." else "Weak connection detected. Media uploads may take longer.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            HomePanel(currentUsername = currentUsername, onOpenChats = onOpenChats, onOpenStories = onOpenStories, onOpenProfile = onOpenProfile, onOpenMarketplace = onOpenMarketplace, onOpenNotifications = onOpenNotifications, onOpenFindPeople = onOpenFindPeople, onOpenAi = onOpenAi)
        }
    }

    if (showComposer) {
        FynxPlainDialog(
            onDismissRequest = { if (!posting && !aiCaptionLoading) { showComposer = false; capturedUris = emptyList(); capturedTypes = emptyList() } },
            title = { Text("Create a FYNX post") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!postingAllowed) Text("Posting is disabled by your Posts privacy setting.", color = MaterialTheme.colorScheme.error)
                    OutlinedTextField(value = text, onValueChange = { text = it.take(4000) }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 7, placeholder = { Text("Share something with your FYNX circle…") }, enabled = !posting && !aiCaptionLoading && postingAllowed, trailingIcon = { IconButton(onClick = ::requestInlineCaptionHelp, enabled = !posting && !aiCaptionLoading && postingAllowed && text.trim().isNotBlank()) { Icon(Icons.Default.AutoAwesome, contentDescription = "Improve caption with FYNX AI") } })
                    if (aiCaptionLoading) Text("FYNX AI is improving your caption…", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showComposer = false; showCamera = true }, modifier = Modifier.weight(1f), enabled = !posting && !aiCaptionLoading && postingAllowed) { Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(4.dp)); Text("Camera") }
                        OutlinedButton(onClick = { gallery.launch(arrayOf("image/*", "video/*")) }, modifier = Modifier.weight(1f), enabled = !posting && !aiCaptionLoading && postingAllowed) { Icon(Icons.Default.VideoLibrary, null); Spacer(Modifier.width(4.dp)); Text("Gallery") }
                    }
                    if (capturedUris.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${capturedUris.size} media item${if (capturedUris.size == 1) "" else "s"} ready", color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                            if (capturedUris.size == 1 && capturedTypes.firstOrNull() == "image") TextButton(onClick = { showComposer = false; showPhotoEditor = true }, enabled = !posting && !aiCaptionLoading) { Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(4.dp)); Text("AI edit") }
                        }
                    }
                    Text("Who can see this?", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = visibility == FynxPostVisibility.PUBLIC, onClick = { visibility = FynxPostVisibility.PUBLIC }, label = { Text("Public") }, enabled = !posting && !aiCaptionLoading && postingAllowed && configuredPostVisibility == "Everyone")
                        FilterChip(selected = visibility == FynxPostVisibility.FRIENDS_ONLY, onClick = { visibility = FynxPostVisibility.FRIENDS_ONLY }, label = { Text("Friends") }, enabled = !posting && !aiCaptionLoading && postingAllowed)
                    }
                    if (networkLevel == FynxNetworkQuality.Level.OFFLINE) Text("You are offline. Reconnect before publishing this post.", color = MaterialTheme.colorScheme.error)
                    notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(enabled = !posting && !aiCaptionLoading && postingAllowed && networkLevel != FynxNetworkQuality.Level.OFFLINE && (text.isNotBlank() || capturedUris.isNotEmpty()), onClick = {
                    if (FynxNetworkQuality.current(context) == FynxNetworkQuality.Level.OFFLINE) { notice = "You are offline. Reconnect before publishing this post."; return@Button }
                    posting = true; notice = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { FynxMultiMediaPostClient.createPost(context, text, visibility, capturedUris) }
                        result.onSuccess { showComposer = false; capturedUris = emptyList(); capturedTypes = emptyList(); text = "" }.onFailure { notice = it.message ?: "Post could not be published." }
                        posting = false
                    }
                }) { Text(if (posting) "Publishing…" else "Post") }
            },
            dismissButton = { TextButton(onClick = { if (!posting && !aiCaptionLoading) { showComposer = false; capturedUris = emptyList(); capturedTypes = emptyList() } }, enabled = !posting && !aiCaptionLoading) { Text("Cancel") } }
        )
    }

    if (showPhotoEditor) Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        FynxAiPhotoEditorPanel(initialUri = capturedUris.firstOrNull(), onDone = { editedUri -> if (editedUri != null) { capturedUris = listOf(editedUri); capturedTypes = listOf("image") }; showPhotoEditor = false; showComposer = true })
    }

    if (showCamera) Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        FynxCameraCapturePanel(onCaptured = { uri, type -> capturedUris = listOf(uri); capturedTypes = listOf(type); showCamera = false; showComposer = true }, onDismiss = { showCamera = false; showComposer = true })
    }
}
