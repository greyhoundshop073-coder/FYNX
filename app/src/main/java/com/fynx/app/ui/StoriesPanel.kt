package com.fynx.app.ui

import android.content.Context
import android.net.Uri
import android.widget.ImageView
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private const val STORY_PREFS = "fynx_stories"
private const val STORY_LIST_KEY = "story_items"
private const val STORY_PRIVATE_KEY = "private_story"
private const val STORY_VISIBILITY_KEY = "status_visibility"
private const val STORY_REPLIES_KEY = "status_replies"
private const val STORY_EXPIRY_MS = 24L * 60L * 60L * 1000L

@Composable
fun StoriesPanel() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(STORY_PREFS, Context.MODE_PRIVATE) }
    val auth = remember(context) { FynxAuthStore.load(context).takeIf { it.state == AuthState.SIGNED_IN } }
    val ownerUsername = auth?.username?.ifBlank { "preview" } ?: "preview"
    val ownerName = ownerUsername.removePrefix("@").ifBlank { "You" }
    var visibility by remember { mutableStateOf(prefs.getString(STORY_VISIBILITY_KEY, "My friends") ?: "My friends") }
    var allowReplies by remember { mutableStateOf(prefs.getBoolean(STORY_REPLIES_KEY, true)) }
    var stories by remember { mutableStateOf(loadStories(prefs).filterNot { it.isExpired() }) }
    var storyType by remember { mutableStateOf<FynxStoryType?>(null) }
    var storyUri by remember { mutableStateOf<Uri?>(null) }
    var textStory by remember { mutableStateOf("") }
    var showTextComposer by remember { mutableStateOf(false) }
    var showStatusSettings by remember { mutableStateOf(false) }
    var previewStoryId by remember { mutableStateOf<String?>(null) }
    var replyText by remember { mutableStateOf("") }

    fun refreshStories() {
        stories = loadStories(prefs).filterNot { it.isExpired() }.sortedByDescending { it.createdAtMillis }
    }
    fun resetComposer() {
        storyType = null
        storyUri = null
        textStory = ""
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            refreshStories()
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            storyUri = uri
            storyType = FynxStoryType.PHOTO
        }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            storyUri = uri
            storyType = FynxStoryType.VIDEO
        }
    }

    val ownStories = stories.filter { it.ownerUsername == ownerUsername }.sortedByDescending { it.createdAtMillis }
    val selectedStory = previewStoryId?.let { id -> stories.firstOrNull { it.id == id } }

    Column(
        Modifier.fillMaxSize().navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Status", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Share photos, videos or text that disappear after 24 hours.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { showStatusSettings = true }) { Text("Settings") }
        }

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FynxAvatar(ownerName, Modifier.size(58.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("My status", style = MaterialTheme.typography.titleMedium)
                        Text(
                            when {
                                storyType == null -> "Tap Photo, Video or Text to post"
                                storyType == FynxStoryType.PHOTO -> "Photo ready"
                                storyType == FynxStoryType.VIDEO -> "Video ready"
                                else -> "Text ready"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (ownStories.isNotEmpty()) {
                        TextButton(onClick = { previewStoryId = ownStories.first().id }) { Text("View") }
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { photoPicker.launch(arrayOf("image/*")) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Photo") }
                    OutlinedButton(
                        onClick = { videoPicker.launch(arrayOf("video/*")) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Video") }
                    OutlinedButton(
                        onClick = { showTextComposer = true },
                        modifier = Modifier.weight(1f)
                    ) { Text("Text") }
                }

                if (storyType != null) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val draft = buildDraftStory(
                                    ownerName,
                                    ownerUsername,
                                    storyType!!,
                                    storyUri,
                                    textStory,
                                    visibility == "Only me"
                                )
                                saveStory(prefs, draft)
                                refreshStories()
                                previewStoryId = draft.id
                                resetComposer()
                            },
                            enabled = storyType != FynxStoryType.TEXT || textStory.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) { Text("Post status") }
                        TextButton(
                            onClick = { resetComposer() },
                            modifier = Modifier.weight(1f)
                        ) { Text("Cancel") }
                    }
                }

                Text(
                    "Visible to: $visibility",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("My status", style = MaterialTheme.typography.titleMedium)
            if (ownStories.isNotEmpty()) {
                Text(
                    "${ownStories.size} active",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (ownStories.isEmpty()) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Text(
                    "Your status updates will appear here for 24 hours.",
                    Modifier.fillMaxWidth().padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(ownStories, key = { it.id }) { story ->
                    Column(
                        Modifier.width(76.dp).clickable { previewStoryId = story.id },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        FynxAvatar(ownerName, Modifier.size(66.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            storyTypeLabel(story.type),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        HorizontalDivider()
        Text("Friends' status", style = MaterialTheme.typography.titleMedium)
        Text(
            "Status updates from your friends will appear here when FYNX connects their shared accounts.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }

    if (showTextComposer) {
        AlertDialog(
            onDismissRequest = { showTextComposer = false },
            title = { Text("Text status") },
            text = {
                OutlinedTextField(
                    value = textStory,
                    onValueChange = { textStory = it.take(500) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    placeholder = { Text("Type a status…") },
                    supportingText = { Text("${textStory.length}/500") }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = textStory.isNotBlank(),
                    onClick = {
                        storyType = FynxStoryType.TEXT
                        storyUri = null
                        showTextComposer = false
                    }
                ) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { showTextComposer = false }) { Text("Cancel") }
            }
        )
    }

    if (showStatusSettings) {
        AlertDialog(
            onDismissRequest = { showStatusSettings = false },
            title = { Text("Status settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Who can see my status?", style = MaterialTheme.typography.titleSmall)
                    listOf("My friends", "Only me").forEach { option ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                visibility = option
                                prefs.edit()
                                    .putString(STORY_VISIBILITY_KEY, option)
                                    .putBoolean(STORY_PRIVATE_KEY, option == "Only me")
                                    .apply()
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = visibility == option,
                                onClick = {
                                    visibility = option
                                    prefs.edit()
                                        .putString(STORY_VISIBILITY_KEY, option)
                                        .putBoolean(STORY_PRIVATE_KEY, option == "Only me")
                                        .apply()
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(option)
                        }
                    }
                    HorizontalDivider()
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Allow replies", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Let friends reply to your status.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = allowReplies,
                            onCheckedChange = {
                                allowReplies = it
                                prefs.edit().putBoolean(STORY_REPLIES_KEY, it).apply()
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStatusSettings = false }) { Text("Done") }
            }
        )
    }

    selectedStory?.let { story ->
        val visibleStories = stories
            .filter { !it.isExpired() && (!it.privateStory || it.ownerUsername == ownerUsername) }
            .sortedByDescending { it.createdAtMillis }
        val startIndex = visibleStories.indexOfFirst { it.id == story.id }.coerceAtLeast(0)
        StoryViewer(
            stories = visibleStories,
            startIndex = startIndex,
            viewerUsername = ownerUsername,
            allowReplies = allowReplies,
            onDismiss = { previewStoryId = null; replyText = "" },
            replyText = replyText,
            onReplyTextChange = { replyText = it.take(300) },
            onReact = { current, emoji ->
                if (current.ownerUsername == ownerUsername) return@StoryViewer
                val updated = current.copy(reaction = emoji)
                updateStory(prefs, updated)
                refreshStories()
            },
            onReply = { current ->
                if (replyText.isNotBlank() && current.ownerUsername != ownerUsername) {
                    updateStory(prefs, current.copy(reply = replyText.trim()))
                    replyText = ""
                    refreshStories()
                }
            },
            onStoryChanged = { previewStoryId = it.id }
        )
    }
}

private fun buildDraftStory(
    name: String,
    username: String,
    type: FynxStoryType,
    uri: Uri?,
    text: String,
    privateStory: Boolean
) = FynxStory(
    UUID.randomUUID().toString(),
    name,
    username,
    type,
    uri?.toString(),
    text.trim().ifBlank { null },
    System.currentTimeMillis(),
    privateStory
)

private fun storyTypeLabel(type: FynxStoryType) = when (type) {
    FynxStoryType.PHOTO -> "Photo"
    FynxStoryType.VIDEO -> "Video"
    FynxStoryType.TEXT -> "Text"
}

private fun loadStories(prefs: android.content.SharedPreferences): List<FynxStory> {
    val raw = prefs.getString(STORY_LIST_KEY, null) ?: return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        List(array.length()) { index ->
            val o = array.getJSONObject(index)
            FynxStory(
                o.optString("id"),
                o.optString("ownerName"),
                o.optString("ownerUsername"),
                FynxStoryType.valueOf(o.optString("type", FynxStoryType.TEXT.name)),
                o.optString("contentUri").ifBlank { null },
                o.optString("text").ifBlank { null },
                o.optLong("createdAtMillis"),
                o.optBoolean("privateStory"),
                o.optString("reaction").ifBlank { null },
                o.optString("reply").ifBlank { null }
            )
        }
    }.getOrElse { emptyList() }
}

private fun saveStory(prefs: android.content.SharedPreferences, story: FynxStory) {
    val stories = loadStories(prefs).filterNot { it.isExpired() } + story
    val array = JSONArray()
    stories.forEach { o -> array.put(storyJson(o)) }
    prefs.edit().putString(STORY_LIST_KEY, array.toString()).putBoolean("story_shared", true).apply()
}

private fun updateStory(prefs: android.content.SharedPreferences, story: FynxStory) {
    val updated = loadStories(prefs)
        .map { if (it.id == story.id) story else it }
        .filterNot { it.isExpired() }
    val array = JSONArray()
    updated.forEach { o -> array.put(storyJson(o)) }
    prefs.edit().putString(STORY_LIST_KEY, array.toString()).apply()
}

private fun storyJson(story: FynxStory) = JSONObject().apply {
    put("id", story.id)
    put("ownerName", story.ownerName)
    put("ownerUsername", story.ownerUsername)
    put("type", story.type.name)
    put("contentUri", story.contentUri ?: "")
    put("text", story.text ?: "")
    put("createdAtMillis", story.createdAtMillis)
    put("privateStory", story.privateStory)
    put("reaction", story.reaction ?: "")
    put("reply", story.reply ?: "")
}

@Composable
private fun StoryViewer(
    stories: List<FynxStory>,
    startIndex: Int,
    viewerUsername: String,
    allowReplies: Boolean,
    onDismiss: () -> Unit,
    replyText: String,
    onReplyTextChange: (String) -> Unit,
    onReact: (FynxStory, String) -> Unit,
    onReply: (FynxStory) -> Unit,
    onStoryChanged: (FynxStory) -> Unit
) {
    if (stories.isEmpty()) {
        onDismiss()
        return
    }
    var index by remember(stories.map { it.id }) {
        mutableIntStateOf(startIndex.coerceIn(0, stories.lastIndex))
    }
    var showReply by remember { mutableStateOf(false) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val story = stories.getOrNull(index) ?: run { onDismiss(); return }
    val isOwner = story.ownerUsername == viewerUsername

    LaunchedEffect(story.id) {
        showReply = false
        while (true) {
            nowMillis = System.currentTimeMillis()
            if (story.isExpired(nowMillis)) break
            delay(1_000L)
        }
    }

    fun moveNext() {
        if (index < stories.lastIndex) {
            index++
            onStoryChanged(stories[index])
        } else {
            onDismiss()
        }
    }
    fun movePrevious() {
        if (index > 0) {
            index--
            onStoryChanged(stories[index])
        }
    }

    BackHandler(onBack = onDismiss)
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FynxAvatar(if (isOwner) "You" else story.ownerName, Modifier.size(40.dp))
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(if (isOwner) "Your status" else story.ownerName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${story.ownerUsername} • ${storyTypeLabel(story.type)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        formatRemaining(story.createdAtMillis, nowMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onDismiss) { Text("Close") }
                }

                Box(
                    Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surface).pointerInput(story.id) {
                        detectTapGestures { offset ->
                            if (offset.x < size.width / 2f) movePrevious() else moveNext()
                        }
                    }
                ) {
                    when (story.type) {
                        FynxStoryType.TEXT -> Box(
                            Modifier.fillMaxSize().background(FynxDesign.Surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                story.text.orEmpty(),
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.padding(28.dp)
                            )
                        }
                        FynxStoryType.PHOTO -> StoryPhotoContent(story.contentUri)
                        FynxStoryType.VIDEO -> StoryVideoContent(story.contentUri)
                    }
                    Row(
                        Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        stories.forEachIndexed { storyIndex, _ ->
                            LinearProgressIndicator(
                                progress = { if (storyIndex < index) 1f else if (storyIndex == index) 0.55f else 0f },
                                modifier = Modifier.weight(1f).height(3.dp)
                            )
                        }
                    }
                }

                if (!isOwner && allowReplies) {
                    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                        Column(
                            Modifier.fillMaxWidth().padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("❤️", "😂", "😮", "😢").forEach { emoji ->
                                    FilterChip(
                                        selected = story.reaction == emoji,
                                        onClick = { onReact(story, emoji) },
                                        label = { Text(emoji) }
                                    )
                                }
                            }
                            if (story.reply != null) {
                                Text("Your reply: ${story.reply}", style = MaterialTheme.typography.bodySmall)
                            }
                            if (showReply) {
                                OutlinedTextField(
                                    value = replyText,
                                    onValueChange = onReplyTextChange,
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("Reply to this status…") },
                                    singleLine = true
                                )
                                Button(
                                    onClick = { onReply(story); showReply = false },
                                    enabled = replyText.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Send") }
                            } else {
                                OutlinedButton(
                                    onClick = { showReply = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Reply to this status…") }
                            }
                        }
                    }
                } else {
                    Text(
                        if (isOwner) "Your status • expires after 24 hours" else "Replies are turned off for this status.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StoryPhotoContent(contentUri: String?) {
    val context = LocalContext.current
    val uri = contentUri?.let(Uri::parse)
    if (uri == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Photo content unavailable.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    AndroidView(
        factory = {
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                adjustViewBounds = true
            }
        },
        update = { it.setImageURI(uri) },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun StoryVideoContent(contentUri: String?) {
    val context = LocalContext.current
    val uri = contentUri?.let(Uri::parse)
    if (uri == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Video content unavailable.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    AndroidView(
        factory = { VideoView(context).apply { setVideoURI(uri) } },
        update = { view ->
            if (view.tag != contentUri) {
                view.tag = contentUri
                view.setVideoURI(uri)
                view.setOnPreparedListener { player ->
                    player.isLooping = true
                    view.start()
                }
            } else if (!view.isPlaying) {
                view.start()
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

private fun formatRemaining(createdAt: Long, now: Long = System.currentTimeMillis()): String {
    val remaining = (createdAt + STORY_EXPIRY_MS - now).coerceAtLeast(0L)
    val hours = remaining / 3_600_000L
    val minutes = (remaining / 60_000L) % 60L
    val seconds = (remaining / 1_000L) % 60L
    return if (hours > 0) "${hours}h ${minutes}m left" else if (minutes > 0) "${minutes}m left" else "${seconds}s left"
}
