package com.fynx.app.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Poll
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
    var openCamera by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    IconButton(enabled = !uploading, onClick = { openCamera = true }) { Icon(Icons.Default.PhotoCamera, contentDescription = "Open FYNX group camera") }
    if (uploading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
    error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    if (openCamera) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { if (!uploading) openCamera = false }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                FynxCameraCapturePanel(
                    onCaptured = { uri, type -> onMediaSelected(uri, type); openCamera = false },
                    onDismiss = { if (!uploading) openCamera = false }
                )
            }
        }
    }
}

@Composable
fun FynxGroupSocialDialog(group: FynxGroup, onDismiss: () -> Unit, onInvite: (String) -> Unit, onMedia: (Uri) -> Unit, onStoryShare: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var showFriends by remember { mutableStateOf(false) }
    var showContent by remember { mutableStateOf(false) }
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
    } else if (showContent) {
        FynxGroupContentPanel(groupId = group.id, canManage = group.members.any { it.role != FynxGroupRole.MEMBER && it.username.equals(group.ownerUsername, true) || it.role == FynxGroupRole.ADMIN }) {
            showContent = false
        }
    } else {
        AlertDialog(onDismissRequest = onDismiss, title = { Text("Group tools") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Share and organize things your group can come back to.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { showContent = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Poll, null); Spacer(Modifier.width(8.dp)); Text("Posts, polls & events") }
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

@Composable
private fun FynxGroupContentPanel(groupId: String, canManage: Boolean, onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var content by remember { mutableStateOf<FynxGroupRemoteClient.RemoteContent?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showPoll by remember { mutableStateOf(false) }
    var showEvent by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }

    fun reload() {
        scope.launch {
            loading = true
            FynxGroupRemoteClient.loadContent(context, groupId).onSuccess { content = it; error = null }.onFailure { error = it.message ?: "Group content could not be loaded." }
            loading = false
        }
    }
    LaunchedEffect(groupId) { reload() }

    AlertDialog(onDismissRequest = onClose, title = { Text("Group content") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Posts, polls and events stay with the group and sync across devices.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (canManage) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showPoll = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Poll, null); Spacer(Modifier.width(5.dp)); Text("Poll") }
                    OutlinedButton(onClick = { showEvent = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Event, null); Spacer(Modifier.width(5.dp)); Text("Event") }
                }
            }
            if (loading) CircularProgressIndicator(Modifier.size(24.dp))
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            val current = content
            if (!loading && current != null && current.posts.isEmpty() && current.events.isEmpty()) {
                Text("Nothing shared here yet. Start with a poll or an event.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            current?.posts?.take(8)?.forEach { post ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("@${post.authorUsername.removePrefix("@")}", style = MaterialTheme.typography.labelLarge)
                            if (post.pinned) Icon(Icons.Default.PushPin, "Pinned", tint = MaterialTheme.colorScheme.primary)
                        }
                        post.poll?.let { poll ->
                            Text(poll.question, style = MaterialTheme.typography.titleMedium)
                            poll.options.forEach { option ->
                                OutlinedButton(onClick = { scope.launch { FynxGroupRemoteClient.votePoll(context, groupId, poll.id, option.id).onSuccess { reload() } } }, modifier = Modifier.fillMaxWidth()) {
                                    Text("${option.text}  •  ${option.voteCount}")
                                }
                            }
                        }
                        if (post.text.isNotBlank()) Text(post.text)
                        if (post.marketplaceProductId != null) Text("Marketplace item shared in this group", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        if (canManage) TextButton(onClick = { scope.launch { FynxGroupRemoteClient.setGroupPostPinned(context, groupId, post.id, !post.pinned).onSuccess { reload() } } }) { Text(if (post.pinned) "Unpin" else "Pin") }
                    }
                }
            }
            current?.events?.take(6)?.forEach { event ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(event.title, style = MaterialTheme.typography.titleMedium)
                        if (event.description.isNotBlank()) Text(event.description)
                        Text("${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(event.startsAt))}${if (event.location.isBlank()) "" else " • ${event.location}"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = { scope.launch { FynxGroupRemoteClient.rsvpEvent(context, groupId, event.id, "GOING").onSuccess { reload() } } }) { Text("Going ${event.goingCount}") }
                            TextButton(onClick = { scope.launch { FynxGroupRemoteClient.rsvpEvent(context, groupId, event.id, "MAYBE").onSuccess { reload() } } }) { Text("Maybe ${event.maybeCount}") }
                        }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text("Done") } })

    if (showPoll) FynxCreateGroupPollDialog(onDismiss = { showPoll = false }) { question, options, multiple ->
        scope.launch { FynxGroupRemoteClient.createPoll(context, groupId, question, options, multiple).onSuccess { showPoll = false; reload() }.onFailure { error = it.message } }
    }
    if (showEvent) FynxCreateGroupEventDialog(onDismiss = { showEvent = false }) { title, description, startsAt, location ->
        scope.launch { FynxGroupRemoteClient.createEvent(context, groupId, title, description, startsAt, location).onSuccess { showEvent = false; reload() }.onFailure { error = it.message } }
    }
}

@Composable
private fun FynxCreateGroupPollDialog(onDismiss: () -> Unit, onCreate: (String, List<String>, Boolean) -> Unit) {
    var question by remember { mutableStateOf("") }
    var optionA by remember { mutableStateOf("") }
    var optionB by remember { mutableStateOf("") }
    var optionC by remember { mutableStateOf("") }
    var multiple by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Create a poll") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ask the group a simple question and let everyone choose.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(question, { question = it }, label = { Text("Question") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(optionA, { optionA = it }, label = { Text("Option 1") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(optionB, { optionB = it }, label = { Text("Option 2") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(optionC, { optionC = it }, label = { Text("Option 3 (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(multiple, { multiple = it }); Text("Allow multiple choices") }
        }
    }, confirmButton = { TextButton(enabled = question.trim().length >= 2 && optionA.isNotBlank() && optionB.isNotBlank(), onClick = { onCreate(question.trim(), listOf(optionA, optionB, optionC).filter { it.isNotBlank() }, multiple) }) { Text("Create poll") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun FynxCreateGroupEventDialog(onDismiss: () -> Unit, onCreate: (String, String, String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var startsAt by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Create an event") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Give everyone one clear place to see when and where the group is meeting.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(title, { title = it }, label = { Text("Event name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { description = it }, label = { Text("Details (optional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(startsAt, { startsAt = it }, label = { Text("Start date/time") }, placeholder = { Text("2026-09-20T18:00:00Z") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(location, { location = it }, label = { Text("Location or online") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }, confirmButton = { TextButton(enabled = title.trim().length >= 2 && startsAt.isNotBlank(), onClick = { onCreate(title.trim(), description.trim(), startsAt.trim(), location.trim()) }) { Text("Create event") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

fun createGroupMediaMessage(uri: Uri, type: String = "image"): ChatMessage = ChatMessage(text = if (type == "video") "Video" else "Photo", fromMe = true, id = java.util.UUID.randomUUID().toString(), delivered = true, read = true, attachmentUri = uri.toString(), attachmentType = type)
