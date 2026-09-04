package com.fynx.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Keeps the existing Home experience intact while routing captured media into the real FYNX social backend. */
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
    var showComposer by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }
    var capturedUri by remember { mutableStateOf<Uri?>(null) }
    var capturedType by remember { mutableStateOf("image") }
    var text by remember { mutableStateOf("") }
    var visibility by remember { mutableStateOf(FynxPostVisibility.PUBLIC) }
    var notice by remember { mutableStateOf<String?>(null) }
    var posting by remember { mutableStateOf(false) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            capturedUri = uri
            capturedType = if (context.contentResolver.getType(uri)?.startsWith("video/") == true) "video" else "image"
            showComposer = true
        }
    }

    LaunchedEffect(initialCaption) {
        if (!initialCaption.isNullOrBlank()) {
            text = initialCaption.trim().take(4000)
            capturedUri = null
            notice = null
            showComposer = true
            onCaptionConsumed()
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            HomePanel(
                currentUsername = currentUsername,
                onOpenChats = onOpenChats,
                onOpenStories = onOpenStories,
                onOpenProfile = onOpenProfile,
                onOpenMarketplace = onOpenMarketplace,
                onOpenNotifications = onOpenNotifications,
                onOpenFindPeople = onOpenFindPeople,
                onOpenAi = onOpenAi
            )
            FloatingActionButton(
                onClick = { showComposer = true; capturedUri = null; text = ""; notice = null },
                modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp)
            ) { Icon(Icons.Default.AddAPhoto, "Create post") }
        }
    }

    if (showComposer) {
        FynxPlainDialog(
            onDismissRequest = { if (!posting) { showComposer = false; capturedUri = null } },
            title = { Text("Create a FYNX post") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = text, onValueChange = { text = it.take(4000) }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 7, placeholder = { Text("Share something with your FYNX circle…") }, enabled = !posting)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showComposer = false; showCamera = true }, modifier = Modifier.weight(1f), enabled = !posting) { Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(4.dp)); Text("Camera") }
                        OutlinedButton(onClick = { gallery.launch(arrayOf("image/*", "video/*")) }, modifier = Modifier.weight(1f), enabled = !posting) { Icon(Icons.Default.VideoLibrary, null); Spacer(Modifier.width(4.dp)); Text("Gallery") }
                    }
                    capturedUri?.let {
                        Text(if (capturedType == "video") "Video captured and ready" else "Photo captured and ready", color = MaterialTheme.colorScheme.primary)
                    }
                    Text("Who can see this?", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(visibility == FynxPostVisibility.PUBLIC, { visibility = FynxPostVisibility.PUBLIC }, label = { Text("Public") }, enabled = !posting)
                        FilterChip(visibility == FynxPostVisibility.FRIENDS_ONLY, { visibility = FynxPostVisibility.FRIENDS_ONLY }, label = { Text("Friends") }, enabled = !posting)
                    }
                    notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(enabled = !posting && (text.isNotBlank() || capturedUri != null), onClick = {
                    posting = true
                    notice = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { FynxRemoteSocialClient.createPost(context, text, visibility, capturedUri) }
                        result.onSuccess {
                            showComposer = false
                            capturedUri = null
                            text = ""
                        }.onFailure { notice = it.message ?: "Post could not be published." }
                        posting = false
                    }
                }) { Text(if (posting) "Publishing…" else "Post") }
            },
            dismissButton = { TextButton(onClick = { if (!posting) { showComposer = false; capturedUri = null } }, enabled = !posting) { Text("Cancel") } }
        )
    }

    if (showCamera) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            FynxCameraCapturePanel(
                onCaptured = { uri, type -> capturedUri = uri; capturedType = type; showCamera = false; showComposer = true },
                onDismiss = { showCamera = false; showComposer = true }
            )
        }
    }
}
