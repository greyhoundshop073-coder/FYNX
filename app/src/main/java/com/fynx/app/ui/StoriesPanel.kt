package com.fynx.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun StoriesPanel() {
    var privacy by remember { mutableStateOf(false) }
    var storyType by remember { mutableStateOf<String?>(null) }
    var storyUri by remember { mutableStateOf<Uri?>(null) }
    var textStory by remember { mutableStateOf("") }
    var showTextComposer by remember { mutableStateOf(false) }

    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            storyUri = uri
            storyType = if (uri.toString().contains("video", true)) "Video" else "Photo"
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Your Story", style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FynxAvatar("You", Modifier.size(62.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Add to your story", style = MaterialTheme.typography.titleMedium)
                        Text(if (storyType == null) "Share a photo, video or text" else storyType + " ready to share", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { mediaPicker.launch("image/*") }, modifier = Modifier.weight(1f)) { Text("Photo") }
                    OutlinedButton(onClick = { mediaPicker.launch("video/*") }, modifier = Modifier.weight(1f)) { Text("Video") }
                    OutlinedButton(onClick = { showTextComposer = true }, modifier = Modifier.weight(1f)) { Text("Text") }
                }
                if (storyType != null) {
                    Button(onClick = { }, modifier = Modifier.fillMaxWidth()) { Text("Share story") }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Friends' Stories", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { }) { Text("See all") }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            items(sampleStories.filterNot { it.isMine }, key = { it.username }) { story ->
                Column(Modifier.width(70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    FynxAvatar(story.displayName, Modifier.size(64.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(story.displayName, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Private story", style = MaterialTheme.typography.titleSmall)
                Text("Only selected friends can view it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = privacy, onCheckedChange = { privacy = it })
        }
    }

    if (showTextComposer) {
        AlertDialog(
            onDismissRequest = { showTextComposer = false },
            title = { Text("Text story") },
            text = { OutlinedTextField(value = textStory, onValueChange = { textStory = it }, modifier = Modifier.fillMaxWidth(), minLines = 4, placeholder = { Text("Write something…") }) },
            confirmButton = { TextButton(enabled = textStory.isNotBlank(), onClick = { storyType = "Text"; storyUri = null; showTextComposer = false }) { Text("Done") } },
            dismissButton = { TextButton(onClick = { showTextComposer = false }) { Text("Cancel") } }
        )
    }
}