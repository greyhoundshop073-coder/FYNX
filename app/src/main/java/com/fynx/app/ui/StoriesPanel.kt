package com.fynx.app.ui

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
    var storyAdded by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Your Story", style = MaterialTheme.typography.titleMedium)

        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FynxAvatar(
                    "＋",
                    Modifier.size(62.dp)
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Add Story", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (storyAdded) "Your story is live" else "Share a moment",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(onClick = { storyAdded = true }) {
                    Text(if (storyAdded) "Added" else "Add")
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Friends' Stories", style = MaterialTheme.typography.titleMedium)
            Text("See All", color = MaterialTheme.colorScheme.primary)
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(sampleStories, key = { it.username }) { story ->
                Column(
                    Modifier.width(70.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    FynxAvatar(story.displayName, Modifier.size(64.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        story.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
        }

        HorizontalDivider()

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Private story", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Only selected friends can view it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = privacy, onCheckedChange = { privacy = it })
        }
    }
}
