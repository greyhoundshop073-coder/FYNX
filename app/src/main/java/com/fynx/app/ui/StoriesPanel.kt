package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun StoriesPanel() {
    var privacy by remember { mutableStateOf(false) }
    var storyAdded by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Text("Stories", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        Button(onClick = { storyAdded = true }) {
            Text(if (storyAdded) "Story added" else "＋ Add story")
        }

        Spacer(Modifier.height(16.dp))
        Text("Friends' stories", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sampleStories) { story ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FynxAvatar(story.displayName)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(story.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(story.username)
                            Text(if (story.isMine && storyAdded) "Your story · just now" else story.timeLabel)
                        }
                        if (!story.isMine) {
                            OutlinedButton(onClick = {}) { Text("View") }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Private story")
            Switch(checked = privacy, onCheckedChange = { privacy = it })
        }
    }
}
