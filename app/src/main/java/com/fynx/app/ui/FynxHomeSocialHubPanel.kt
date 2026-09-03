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

/** Keeps the existing Home feed intact and makes the completed AI/Status capabilities visible from Home. */
@Composable
fun FynxHomeSocialHubPanel(
    currentUsername: String,
    onOpenChats: () -> Unit = {},
    onOpenStories: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenMarketplace: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenFindPeople: () -> Unit = {}
) {
    val context = LocalContext.current
    var showComposer by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }
    var capturedUri by remember { mutableStateOf<Uri?>(null) }
    var capturedType by remember { mutableStateOf("image") }
    var text by remember { mutableStateOf("") }
    var visibility by remember { mutableStateOf(FynxPostVisibility.PUBLIC) }
    var notice by remember { mutableStateOf<String?>(null) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            capturedUri = uri
            capturedType = if (context.contentResolver.getType(uri)?.startsWith("video/") == true) "video" else "image"
            showComposer = true
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FynxVisibleUpdatesPanel(
            currentUsername = currentUsername,
            onOpenStories = onOpenStories,
            onOpenAi = {}
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            HomePanel(
                currentUsername = currentUsername,
                onOpenChats = onOpenChats,
                onOpenStories = onOpenStories,
                onOpenProfile = onOpenProfile,
                onOpenMarketplace = onOpenMarketplace,
                onOpenNotifications = onOpenNotifications,
                onOpenFindPeople = onOpenFindPeople
            )
            FloatingActionButton(
                onClick = { showComposer = true; capturedUri = null; text = ""; notice = null },
                modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp)
            ) { Icon(Icons.Default.AddAPhoto, "Create post") }
        }
    }

    if (showComposer) {
        AlertDialog(
            onDismissRequest = { showComposer = false; capturedUri = null },
            title = { Text("Create a FYNX post") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = text, onValueChange = { text = it.take(4000) }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 7, placeholder = { Text("Share something with your FYNX circle…") })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showCamera = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(4.dp)); Text("Camera") }
                        OutlinedButton(onClick = { gallery.launch(arrayOf("image/*", "video/*")) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.VideoLibrary, null); Spacer(Modifier.width(4.dp)); Text("Gallery") }
                    }
                    capturedUri?.let { uri ->
                        Text(if (capturedType == "video") "Video captured and ready" else "Photo captured and ready", color = MaterialTheme.colorScheme.primary)
                        Text(uri.toString(), style = MaterialTheme.typography.labelSmall, maxLines = 2)
                    }
                    Text("Who can see this?", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(visibility == FynxPostVisibility.PUBLIC, { visibility = FynxPostVisibility.PUBLIC }, label = { Text("Public") })
                        FilterChip(visibility == FynxPostVisibility.FRIENDS_ONLY, { visibility = FynxPostVisibility.FRIENDS_ONLY }, label = { Text("Friends") })
                    }
                    notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val result = FynxHomePostStore.create(context, text, visibility, capturedUri?.toString())
                    if (result != null) {
                        notice = null; showComposer = false; capturedUri = null; text = ""
                    } else notice = "Add text or capture/select a photo or video before posting."
                }) { Text("Post") }
            },
            dismissButton = { TextButton(onClick = { showComposer = false; capturedUri = null }) { Text("Cancel") } }
        )
    }

    if (showCamera) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            FynxCameraCapturePanel(
                onCaptured = { uri, type -> capturedUri = uri; capturedType = type; showCamera = false; showComposer = true },
                onDismiss = { showCamera = false }
            )
        }
    }
}
