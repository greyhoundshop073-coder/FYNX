package com.fynx.app.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun FynxGroupMediaPicker(context: Context, onMediaSelected: (Uri, String) -> Unit) {
    val scope = rememberCoroutineScope()
    var uploading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            uploading = true
            error = null
            val mime = context.contentResolver.getType(uri)?.lowercase() ?: ""
            val type = when {
                mime.startsWith("video/") -> "video"
                mime.startsWith("image/") -> "image"
                else -> ""
            }
            if (type.isBlank()) {
                error = "Choose an image or video."
                uploading = false
                return@launch
            }
            // Return the original content URI. The authenticated group message client
            // owns the upload so media is uploaded exactly once and then persisted remotely.
            onMediaSelected(uri, type)
            uploading = false
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        IconButton(enabled = !uploading, onClick = { launcher.launch("*/*") }) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = "Add group photo or video")
        }
        if (uploading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
fun FynxGroupCameraPicker(context: Context, onMediaSelected: (Uri, String) -> Unit) {
    val scope = rememberCoroutineScope()
    var openCamera by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    IconButton(enabled = !uploading, onClick = { openCamera = true }) { Icon(Icons.Default.PhotoCamera, contentDescription = "Open FYNX group camera") }
    if (uploading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
    error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    if (openCamera) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { if (!uploading) openCamera = false }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                FynxCameraCapturePanel(
                    onCaptured = { uri, type ->
                        // Return the local captured URI to the same authenticated upload path used by gallery media.
                        onMediaSelected(uri, type)
                        openCamera = false
                    },
                    onDismiss = { if (!uploading) openCamera = false }
                )
            }
        }
    }
}

@Composable
fun FynxGroupSocialDialog(group: FynxGroup, onDismiss: () -> Unit, onInvite: (String) -> Unit, onMedia: (Uri) -> Unit, onStoryShare: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showFriends by remember { mutableStateOf(false) }
    var inviteMessage by remember { mutableStateOf("") }
    var selectedMedia by remember { mutableStateOf<Uri?>(null) }
    var mediaType by remember { mutableStateOf("image") }
    val friends = remember { FynxFriendsStore(context).load().filter { it.isFriend } }
    fun handleCaptured(uri: Uri, type: String) { selectedMedia = uri; mediaType = type; onMedia(uri) }
    if (showFriends) {
        AlertDialog(onDismissRequest = { showFriends = false }, title = { Text("Invite friends") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (friends.isEmpty()) Text("No confirmed friends are available to invite yet.")
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(friends, key = { it.username }) { friend ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) { Text(friend.displayName, style = MaterialTheme.typography.titleSmall); Text(friend.username, style = MaterialTheme.typography.bodySmall) }
                            TextButton(onClick = { onInvite(friend.username); inviteMessage = "Invitation prepared for ${friend.username}" }) { Text("Invite") }
                        }
                    }
                }
                if (inviteMessage.isNotBlank()) Text(inviteMessage, style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { TextButton(onClick = { showFriends = false }) { Text("Done") } })
    } else {
        AlertDialog(onDismissRequest = onDismiss, title = { Text("Group tools") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { showFriends = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(8.dp)); Text("Invite friends") }
                OutlinedButton(onClick = onStoryShare, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text("Share a story to group") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Group camera", modifier = Modifier.weight(1f)); FynxGroupCameraPicker(context) { uri, type -> handleCaptured(uri, type) } }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Group media", modifier = Modifier.weight(1f)); FynxGroupMediaPicker(context) { uri, type -> handleCaptured(uri, type) } }
                if (selectedMedia != null) Text(if (mediaType == "video") "Video selected for secure group upload." else "Photo selected for secure group upload.", style = MaterialTheme.typography.bodySmall)
                Text("Group media uses the authenticated FYNX media pipeline and is persisted with the group message.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
    }
}

fun createGroupMediaMessage(uri: Uri, type: String = "image"): ChatMessage = ChatMessage(text = if (type == "video") "Video" else "Photo", fromMe = true, id = java.util.UUID.randomUUID().toString(), delivered = true, read = true, attachmentUri = uri.toString(), attachmentType = type)
