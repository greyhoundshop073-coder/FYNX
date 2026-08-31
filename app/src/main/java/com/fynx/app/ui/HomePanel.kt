package com.fynx.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun HomePanel() {
    val chats = listOf(
        Triple("Maria S.", "Hey! How are you?", "10:30 AM"),
        Triple("Daniel K.", "Let's catch up later!", "9:45 AM"),
        Triple("Nassi K.", "Voice message", "8:20 AM"),
        Triple("Flora", "Photo", "Yesterday"),
        Triple("Prince Hamdan", "Insha'Allah", "Yesterday")
    )

    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                "Stories",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "See All",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
        ) {
            item {
                StoryCircle("＋", "Add Story", true)
            }
            item {
                StoryCircle("You", "You", false)
            }
            items(sampleStories.take(4)) { story ->
                StoryCircle(story.displayName, story.displayName, false)
            }
        }

        Text(
            "Chats",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            LazyColumn(
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(chats) { chat ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FynxAvatar(
                            chat.first,
                            Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                        )
                        Column(Modifier.weight(1f)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(chat.first, fontWeight = FontWeight.SemiBold)
                                Text(
                                    chat.third,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                chat.second,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 74.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun StoryCircle(name: String, label: String, addStory: Boolean) {
    Column(
        Modifier.width(68.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        FynxAvatar(
            name,
            Modifier
                .size(62.dp)
                .border(
                    BorderStroke(
                        if (addStory) 2.dp else 2.dp,
                        MaterialTheme.colorScheme.primary
                    ),
                    CircleShape
                )
                .clip(CircleShape)
        )
        Spacer(Modifier.height(5.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}
