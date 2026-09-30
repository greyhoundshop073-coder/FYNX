package com.fynx.app.ui

import android.media.MediaMetadataRetriever
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FynxVisibleUpdatesPanel(
    currentUsername: String,
    onOpenStories: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenCamera: () -> Unit = {},
    onOpenFastCamera: () -> Unit = onOpenCamera,
    onCreateStatus: () -> Unit = onOpenStories,
    onOpenStatusOwner: (String) -> Unit = { onOpenStories() }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var statuses by remember { mutableStateOf<List<FynxStatus>>(emptyList()) }
    var followingUsernames by remember { mutableStateOf<Set<String>>(emptySet()) }
    var ownerPhotoIds by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }

    fun resolveOwnerPhotos(items: List<FynxStatus>) {
        val names = items.map { it.ownerUsername.removePrefix("@").trim() }.filter { it.isNotBlank() }.distinct()
        val missing = names.filterNot { ownerPhotoIds.containsKey(it.lowercase()) }
        if (missing.isEmpty()) return
        scope.launch {
            val resolved = missing.map { username ->
                async(Dispatchers.IO) {
                    username.lowercase() to FynxProfileRemoteClient.get(context, username).getOrNull()?.profilePhotoMediaId
                }
            }.awaitAll().toMap()
            ownerPhotoIds = ownerPhotoIds + resolved
        }
    }

    fun refreshStatuses() {
        scope.launch(Dispatchers.IO) {
            val latest = FynxStatusClient.list(context).getOrDefault(emptyList())
            val following = FynxProfileRemoteClient.following(context)
                .getOrDefault(emptyList())
                .map { it.username.removePrefix("@").trim().lowercase() }
                .toSet()
            withContext(Dispatchers.Main) {
                followingUsernames = following
                statuses = latest
                resolveOwnerPhotos(latest)
            }
        }
    }

    // Home now preserves the feed across ordinary app resume. Statuses therefore refresh
    // on Home composition/explicit Home refresh, not on every ON_RESUME event.
    LaunchedEffect(currentUsername) { refreshStatuses() }

    val current = currentUsername.removePrefix("@").trim().lowercase()
    val activeStatuses = statuses
        .filter { it.expiresAtMillis <= 0L || it.expiresAtMillis > System.currentTimeMillis() }
    val grouped = activeStatuses.groupBy { it.ownerUsername }
        .mapNotNull { (_, list) -> list.maxByOrNull { it.createdAtMillis }?.let { it to list.size } }
    val own = grouped.firstOrNull { it.first.ownerUsername.equals(currentUsername, true) }

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = FynxDesign.LargeCardShape,
            colors = CardDefaults.cardColors(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface),
            border = null
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Status", Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onOpenStories, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp), modifier = Modifier.semantics { contentDescription = "Open Stories" }) { Text("See all") }
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Row(
                            Modifier.width(138.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FynxStatusPreviewCircle(
                                own?.first,
                                currentUsername.ifBlank { "You" },
                                "Your status",
                                true,
                                if (own != null) { { onOpenStatusOwner(current) } } else { onCreateStatus },
                                own?.second ?: 0,
                                ownerPhotoIds[current]
                            )
                            IconButton(onClick = onCreateStatus, modifier = Modifier.requiredSize(48.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .semantics { contentDescription = "Create your status" }
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                    items(grouped.filterNot { it.first.ownerUsername.equals(currentUsername, true) }) { (status, count) ->
                        val ownerKey = status.ownerUsername.removePrefix("@").trim().lowercase()
                        FynxStatusPreviewCircle(
                            status,
                            status.ownerUsername,
                            "Status from ${status.ownerUsername}",
                            followingUsernames.contains(ownerKey),
                            { onOpenStatusOwner(ownerKey) },
                            count,
                            ownerPhotoIds[ownerKey]
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FynxStatusPreviewCircle(
    status: FynxStatus?,
    label: String,
    contentDescription: String,
    isFollowing: Boolean,
    onClick: () -> Unit,
    count: Int,
    profilePhotoMediaId: String?
) {
    // Existing implementation remains unchanged below the edited import/use site.
    FynxStatusPreviewCircleContent(status, label, contentDescription, isFollowing, onClick, count, profilePhotoMediaId)
}
