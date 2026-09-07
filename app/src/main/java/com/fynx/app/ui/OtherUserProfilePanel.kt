package com.fynx.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Message
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun OtherUserProfilePanel(
    username: String,
    onBack: () -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var profile by remember(username) { mutableStateOf<FynxProfileRemoteClient.Profile?>(null) }
    var posts by remember(username) { mutableStateOf<List<FynxProfileRemoteClient.ProfilePost>>(emptyList()) }
    var loading by remember(username) { mutableStateOf(true) }
    var error by remember(username) { mutableStateOf<String?>(null) }
    var following by remember(username) { mutableStateOf(false) }
    var busy by remember(username) { mutableStateOf(false) }
    var reportOpen by remember(username) { mutableStateOf(false) }
    var reportReason by remember(username) { mutableStateOf("Safety or spam") }
    var reportDetails by remember(username) { mutableStateOf("") }
    var reportMessage by remember(username) { mutableStateOf<String?>(null) }

    fun loadProfile() {
        scope.launch {
            loading = true
            error = null
            FynxProfileRemoteClient.get(context, username)
                .onSuccess { loaded ->
                    profile = loaded
                    following = loaded.followedByCurrentUser
                    FynxProfileRemoteClient.posts(context, loaded.username)
                        .onSuccess { loadedPosts -> posts = loadedPosts }
                        .onFailure { posts = emptyList() }
                }
                .onFailure { error = it.message ?: "Unable to load this profile." }
            loading = false
        }
    }

    LaunchedEffect(username) {
        loading = true
        error = null
        FynxProfileRemoteClient.get(context, username)
            .onSuccess { loaded ->
                profile = loaded
                following = loaded.followedByCurrentUser
                FynxProfileRemoteClient.posts(context, loaded.username)
                    .onSuccess { loadedPosts -> posts = loadedPosts }
                    .onFailure { posts = emptyList() }
            }
            .onFailure { error = it.message ?: "Unable to load this profile." }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text("Profile", style = MaterialTheme.typography.titleLarge)
        }

        when {
            loading && profile == null -> {
                Box(
                    Modifier.fillMaxWidth().padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            profile == null -> {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(error ?: "User not found", color = FynxDesign.TextSecondary)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { loadProfile() }) {
                        Text("Retry")
                    }
                }
            }

            else -> {
                val person = profile!!
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item {
                        RemoteProfilePhoto(
                            person.profilePhotoMediaId,
                            person.displayName,
                            Modifier.size(104.dp)
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                person.displayName.ifBlank { person.username },
                                style = MaterialTheme.typography.headlineSmall
                            )
                            if (person.verified) {
                                Spacer(Modifier.width(6.dp))
                                FynxVerifiedBadge()
                            }
                        }
                        Text(
                            "@${person.username.removePrefix("@").trim()}",
                            color = FynxDesign.TextSecondary
                        )
                        if (person.bio.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(person.bio, color = FynxDesign.TextSecondary)
                        }
                        if (person.country.isNotBlank()) {
                            Text(person.country, color = FynxDesign.TextSecondary)
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = !busy,
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        FynxRemoteSocialClient.follow(
                                            context,
                                            person.username,
                                            following
                                        ).onSuccess { following = it }
                                            .onFailure { error = it.message }
                                        busy = false
                                    }
                                }
                            ) {
                                Text(if (following) "Following" else "Follow")
                            }
                            OutlinedButton(
                                enabled = !busy,
                                onClick = { onMessage(person.username) }
                            ) {
                                Icon(Icons.Default.Message, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Message")
                            }
                        }
                        TextButton(
                            enabled = !busy,
                            onClick = {
                                reportMessage = null
                                reportOpen = true
                            }
                        ) {
                            Text("Report")
                        }
                        Text(
                            "${person.postCount} posts • ${person.mutualFriends} mutual friends",
                            color = FynxDesign.TextSecondary
                        )
                        if (posts.isEmpty()) {
                            Text(
                                "No posts to show",
                                Modifier.padding(top = 20.dp),
                                color = FynxDesign.TextSecondary
                            )
                        }
                        posts.forEach { post -> ProfilePostCard(post) }
                    }
                }
            }
        }
    }

    if (reportOpen && profile != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) reportOpen = false },
            title = { Text("Report profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = reportReason,
                        onValueChange = { reportReason = it },
                        label = { Text("Reason") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = reportDetails,
                        onValueChange = { reportDetails = it },
                        label = { Text("Details (optional)") },
                        minLines = 3
                    )
                    reportMessage?.let { message ->
                        Text(message, color = FynxDesign.TextSecondary)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            FynxProfileRemoteClient.report(
                                context,
                                profile!!.username,
                                reportReason,
                                reportDetails
                            ).onSuccess { report ->
                                reportMessage = "Report submitted (${report.status.lowercase()})."
                            }.onFailure { failure ->
                                reportMessage = failure.message ?: "Report failed. Try again."
                            }
                            busy = false
                        }
                    }
                ) {
                    Text("Submit")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { reportOpen = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun RemoteProfilePhoto(
    mediaId: String?,
    name: String,
    modifier: Modifier
) {
    val context = LocalContext.current
    var bitmap by remember(mediaId) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(mediaId) {
        if (mediaId != null) {
            val uri = FynxProductionMessaging
                .cacheRemoteMedia(context, mediaId, "/api/social/media/$mediaId")
                .getOrNull()
            if (uri != null) {
                bitmap = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri).use {
                            BitmapFactory.decodeStream(it)
                        }
                    }.getOrNull()
                }
            }
        }
    }

    val image = bitmap
    if (image != null) {
        Image(
            image.asImageBitmap(),
            contentDescription = "Profile photo",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        FynxAvatar(name, modifier)
    }
}

@Composable
private fun ProfilePostCard(post: FynxProfileRemoteClient.ProfilePost) {
    Card(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Column(Modifier.padding(14.dp)) {
            if (post.text.isNotBlank()) Text(post.text)
            val mediaUrl = post.mediaUrl ?: post.mediaId?.let { "/api/social/media/$it" }
            if (!mediaUrl.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                FynxRemoteMedia(
                    mediaUrl = mediaUrl,
                    type = post.mediaType ?: "auto",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 420.dp)
                )
            }
            Text(
                "${post.likeCount} likes • ${post.commentCount} comments",
                color = FynxDesign.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
