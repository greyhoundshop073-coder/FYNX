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
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.UUID

@Composable
fun FynxGroupMediaPicker(
    context: Context,
    onMediaSelected: (Uri) -> Unit
) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onMediaSelected(uri)
    }
    IconButton(onClick = { launcher.launch("image/*") }) {
        Icon(Icons.Default.PhotoLibrary, contentDescription = "Add group photo")
    }
}

@Composable
fun FynxGroupSocialDialog(
    group: FynxGroup,
    onDismiss: () -> Unit,
    onInvite: (String) -> Unit,
    onMedia: (Uri) -> Unit,
    onStoryShare: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showFriends by remember { mutableStateOf(false) }
    var inviteMessage by remember { mutableStateOf("") }
    var selectedMedia by remember { mutableStateOf<Uri?>(null) }
    val friends = remember { FynxFriendsStore(context).load().filter { it.isFriend } }

    if (showFriends) {
        AlertDialog(
            onDismissRequest = { showFriends = false },
            title = { Text("Invite friends") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (friends.isEmpty()) Text("No confirmed friends are available to invite yet.")
                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(friends, key = { it.username }) { friend ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text(friend.displayName, style = MaterialTheme.typography.titleSmall)
                                    Text(friend.username, style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = { onInvite(friend.username); inviteMessage = "Invitation prepared for ${friend.username}" }) { Text("Invite") }
                            }
                        }
                    }
                    if (inviteMessage.isNotBlank()) Text(inviteMessage, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { showFriends = false }) { Text("Done") } }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Group tools") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { showFriends = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(8.dp)); Text("Invite friends")
                    }
                    OutlinedButton(onClick = { onStoryShare() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text("Share a story to group")
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Group media", modifier = Modifier.weight(1f))
                        FynxGroupMediaPicker(context) { uri -> selectedMedia = uri; onMedia(uri) }
                    }
                    if (selectedMedia != null) Text("Photo attached to this group chat.", style = MaterialTheme.typography.bodySmall)
                    Text("Sharing stays local until a backend is connected.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
        )
    }
}

fun createGroupMediaMessage(uri: Uri): ChatMessage = ChatMessage(
    text = "Photo",
    fromMe = true,
    id = UUID.randomUUID().toString(),
    delivered = true,
    read = true,
    attachmentUri = uri.toString(),
    attachmentType = "image"
)
