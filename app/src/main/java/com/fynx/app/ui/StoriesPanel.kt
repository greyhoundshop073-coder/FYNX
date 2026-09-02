package com.fynx.app.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private const val STORY_PREFS = "fynx_stories"
private const val STORY_LIST_KEY = "story_items"
private const val STORY_PRIVATE_KEY = "private_story"
private const val STORY_EXPIRY_MS = 24L * 60L * 60L * 1000L

@Composable
fun StoriesPanel() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(STORY_PREFS, Context.MODE_PRIVATE) }
    val auth = remember(context) { if (FynxAuthStore.load(context).state == AuthState.SIGNED_IN) FynxAuthStore.load(context) else null }
    val ownerUsername = auth?.username?.ifBlank { "preview" } ?: "preview"
    val ownerName = ownerUsername.removePrefix("@").ifBlank { "You" }

    var privacy by remember { mutableStateOf(prefs.getBoolean(STORY_PRIVATE_KEY, false)) }
    var stories by remember { mutableStateOf(loadStories(prefs).filterNot { it.isExpired() }) }
    var storyType by remember { mutableStateOf<FynxStoryType?>(null) }
    var storyUri by remember { mutableStateOf<Uri?>(null) }
    var textStory by remember { mutableStateOf("") }
    var showTextComposer by remember { mutableStateOf(false) }
    var previewStory by remember { mutableStateOf<FynxStory?>(null) }
    var replyText by remember { mutableStateOf("") }

    fun refreshStories() {
        stories = loadStories(prefs).filterNot { it.isExpired() }
    }

    fun resetComposer() {
        storyType = null
        storyUri = null
        textStory = ""
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            storyUri = uri
            storyType = FynxStoryType.PHOTO
        }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            storyUri = uri
            storyType = FynxStoryType.VIDEO
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Your Story", style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FynxAvatar(ownerName, Modifier.size(62.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Add to your story", style = MaterialTheme.typography.titleMedium)
                        Text(
                            when {
                                storyType == null -> "Share a photo, video or text"
                                storyType == FynxStoryType.PHOTO -> "Photo ready to preview"
                                storyType == FynxStoryType.VIDEO -> "Video ready to preview"
                                else -> "Text ready to preview"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { photoPicker.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f)) { Text("Photo") }
                    OutlinedButton(onClick = { videoPicker.launch(arrayOf("video/*")) }, modifier = Modifier.weight(1f)) { Text("Video") }
                    OutlinedButton(onClick = { showTextComposer = true }, modifier = Modifier.weight(1f)) { Text("Text") }
                }
                if (storyType != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { previewStory = buildDraftStory(ownerName, ownerUsername, storyType!!, storyUri, textStory, privacy) }, modifier = Modifier.weight(1f)) { Text("Preview") }
                        Button(
                            onClick = {
                                val draft = buildDraftStory(ownerName, ownerUsername, storyType!!, storyUri, textStory, privacy)
                                saveStory(prefs, draft)
                                refreshStories()
                                previewStory = null
                                resetComposer()
                            },
                            enabled = storyType != FynxStoryType.TEXT || textStory.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) { Text("Share story") }
                    }
                }
            }
        }

        val ownStories = stories.filter { it.ownerUsername == ownerUsername }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Stories", style = MaterialTheme.typography.titleMedium)
            if (ownStories.isNotEmpty()) Text("${ownStories.size} active", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (ownStories.isEmpty()) {
            Text("Your active Stories will appear here for the next 24 hours.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                items(ownStories, key = { it.id }) { story ->
                    Column(Modifier.width(74.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        FynxAvatar(ownerName, Modifier.size(64.dp).clickable { previewStory = story })
                        Spacer(Modifier.height(6.dp))
                        Text(storyTypeLabel(story.type), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }

        HorizontalDivider()
        Text("Friends' Stories", style = MaterialTheme.typography.titleMedium)
        Text("Stories from your friends will appear here when FYNX connects their shared accounts. We don't show fake people or activity.", color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Private story", style = MaterialTheme.typography.titleSmall)
                Text("Only selected friends can view it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = privacy, onCheckedChange = { privacy = it; prefs.edit().putBoolean(STORY_PRIVATE_KEY, it).apply() })
        }
    }

    if (showTextComposer) {
        AlertDialog(
            onDismissRequest = { showTextComposer = false },
            title = { Text("Text story") },
            text = {
                OutlinedTextField(
                    value = textStory,
                    onValueChange = { textStory = it.take(500) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    placeholder = { Text("Write something…") },
                    supportingText = { Text("${textStory.length}/500") }
                )
            },
            confirmButton = { TextButton(enabled = textStory.isNotBlank(), onClick = { storyType = FynxStoryType.TEXT; storyUri = null; showTextComposer = false }) { Text("Done") } },
            dismissButton = { TextButton(onClick = { showTextComposer = false }) { Text("Cancel") } }
        )
    }

    previewStory?.let { story ->
        StoryViewer(
            story = story,
            onDismiss = { previewStory = null; replyText = "" },
            replyText = replyText,
            onReplyTextChange = { replyText = it.take(300) },
            onReact = { emoji ->
                val updated = story.copy(reaction = emoji)
                updateStory(prefs, updated)
                previewStory = updated
                refreshStories()
            },
            onReply = {
                if (replyText.isNotBlank()) {
                    val updated = story.copy(reply = replyText.trim())
                    updateStory(prefs, updated)
                    previewStory = updated
                    replyText = ""
                    refreshStories()
                }
            }
        )
    }
}

private fun buildDraftStory(name: String, username: String, type: FynxStoryType, uri: Uri?, text: String, privateStory: Boolean): FynxStory =
    FynxStory(
        id = UUID.randomUUID().toString(),
        ownerName = name,
        ownerUsername = username,
        type = type,
        contentUri = uri?.toString(),
        text = text.trim().ifBlank { null },
        createdAtMillis = System.currentTimeMillis(),
        privateStory = privateStory
    )

private fun storyTypeLabel(type: FynxStoryType): String = when (type) {
    FynxStoryType.PHOTO -> "Photo story"
    FynxStoryType.VIDEO -> "Video story"
    FynxStoryType.TEXT -> "Text story"
}

private fun loadStories(prefs: android.content.SharedPreferences): List<FynxStory> {
    val raw = prefs.getString(STORY_LIST_KEY, null) ?: return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        List(array.length()) { index ->
            val o = array.getJSONObject(index)
            FynxStory(
                id = o.optString("id"),
                ownerName = o.optString("ownerName"),
                ownerUsername = o.optString("ownerUsername"),
                type = FynxStoryType.valueOf(o.optString("type", FynxStoryType.TEXT.name)),
                contentUri = o.optString("contentUri").ifBlank { null },
                text = o.optString("text").ifBlank { null },
                createdAtMillis = o.optLong("createdAtMillis"),
                privateStory = o.optBoolean("privateStory"),
                reaction = o.optString("reaction").ifBlank { null },
                reply = o.optString("reply").ifBlank { null }
            )
        }
    }.getOrElse { emptyList() }
}

private fun saveStory(prefs: android.content.SharedPreferences, story: FynxStory) {
    val stories = loadStories(prefs).filterNot { it.isExpired() } + story
    val array = JSONArray()
    stories.forEach { o ->
        array.put(JSONObject().apply {
            put("id", o.id)
            put("ownerName", o.ownerName)
            put("ownerUsername", o.ownerUsername)
            put("type", o.type.name)
            put("contentUri", o.contentUri ?: "")
            put("text", o.text ?: "")
            put("createdAtMillis", o.createdAtMillis)
            put("privateStory", o.privateStory)
            put("reaction", o.reaction ?: "")
            put("reply", o.reply ?: "")
        })
    }
    prefs.edit().putString(STORY_LIST_KEY, array.toString()).putBoolean("story_shared", true).apply()
}

private fun updateStory(prefs: android.content.SharedPreferences, story: FynxStory) {
    val updated = loadStories(prefs).map { if (it.id == story.id) story else it }.filterNot { it.isExpired() }
    val array = JSONArray()
    updated.forEach { o ->
        array.put(JSONObject().apply {
            put("id", o.id); put("ownerName", o.ownerName); put("ownerUsername", o.ownerUsername); put("type", o.type.name)
            put("contentUri", o.contentUri ?: ""); put("text", o.text ?: ""); put("createdAtMillis", o.createdAtMillis)
            put("privateStory", o.privateStory); put("reaction", o.reaction ?: ""); put("reply", o.reply ?: "")
        })
    }
    prefs.edit().putString(STORY_LIST_KEY, array.toString()).apply()
}

@Composable
private fun StoryViewer(
    story: FynxStory,
    onDismiss: () -> Unit,
    replyText: String,
    onReplyTextChange: (String) -> Unit,
    onReact: (String) -> Unit,
    onReply: () -> Unit
) {
    val context = LocalContext.current
    var showReply by remember(story.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(story.ownerName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(storyTypeLabel(story.type), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                when (story.type) {
                    FynxStoryType.TEXT -> Box(Modifier.fillMaxWidth().heightIn(min = 160.dp).background(FynxDesign.Surface, RoundedCornerShape(18.dp)).padding(20.dp), contentAlignment = Alignment.Center) {
                        Text(story.text.orEmpty(), style = MaterialTheme.typography.headlineSmall)
                    }
                    FynxStoryType.PHOTO -> Text(if (story.contentUri != null) "Photo story selected from your device." else "Photo content unavailable.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FynxStoryType.VIDEO -> Text(if (story.contentUri != null) "Video story selected from your device." else "Video content unavailable.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Expires ${formatRemaining(story.createdAtMillis)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("❤️", "😂", "😮", "😢").forEach { emoji ->
                        FilterChip(selected = story.reaction == emoji, onClick = { onReact(emoji) }, label = { Text(emoji) })
                    }
                }
                if (story.reply != null) Text("Your reply: ${story.reply}", style = MaterialTheme.typography.bodySmall)
                if (showReply) {
                    OutlinedTextField(value = replyText, onValueChange = onReplyTextChange, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Reply to this story…") })
                    Button(onClick = onReply, enabled = replyText.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Send reply") }
                } else {
                    TextButton(onClick = { showReply = true }) { Text("Reply") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

private fun formatRemaining(createdAt: Long): String {
    val remaining = (createdAt + STORY_EXPIRY_MS - System.currentTimeMillis()).coerceAtLeast(0L)
    val hours = remaining / 3_600_000L
    val minutes = (remaining / 60_000L) % 60L
    return if (hours > 0) "in ${hours}h ${minutes}m" else "in ${minutes}m"
}
