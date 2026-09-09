package com.fynx.app.ui

import android.media.MediaMetadataRetriever
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FynxVisibleUpdatesPanel(currentUsername: String, onOpenStories: () -> Unit, onOpenAi: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var statuses by remember { mutableStateOf<List<FynxStatus>>(emptyList()) }
    LaunchedEffect(currentUsername) { statuses = FynxStatusClient.list(context).getOrDefault(emptyList()) }

    val activeStatuses = statuses.filter { it.expiresAtMillis <= 0L || it.expiresAtMillis > System.currentTimeMillis() }
    val grouped = activeStatuses.groupBy { it.ownerUsername }
        .mapNotNull { (_, list) -> list.maxByOrNull { it.createdAtMillis }?.let { it to list.size } }

    Card(
        onClick = onOpenStories,
        modifier = Modifier.fillMaxWidth(),
        shape = FynxDesign.LargeCardShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface),
        border = null
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(14.dp))
                Text("Status", Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onOpenStories) { Text("See all") }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    val own = grouped.firstOrNull { it.first.ownerUsername.equals(currentUsername, true) }
                    FynxStatusPreviewCircle(own?.first, currentUsername.ifBlank { "You" }, "Your status", true, onOpenStories, own?.second ?: 0)
                }
                item {
                    Column(Modifier.width(82.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = onOpenStories, modifier = Modifier.size(70.dp)) {
                            androidx.compose.foundation.layout.Box(
                                Modifier.size(64.dp).background(FynxDesign.SurfaceRaised, CircleShape).border(3.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Default.Add, "Create status", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) }
                        }
                        Text("Create status", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
                items(grouped.filterNot { it.first.ownerUsername.equals(currentUsername, true) }) { (status, count) ->
                    FynxStatusPreviewCircle(status, status.ownerUsername, status.ownerDisplayName.ifBlank { status.ownerUsername }, true, onOpenStories, count)
                }
            }
        }
    }
}

@Composable
private fun FynxStatusPreviewCircle(status: FynxStatus?, name: String, label: String, active: Boolean, onClick: () -> Unit, statusCount: Int = 0) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var bitmap by remember(status?.id) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(status?.id, status?.contentUri, status?.type) {
        bitmap = null
        if (status != null && !status.contentUri.isNullOrBlank()) {
            bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    val cached = FynxMediaCache.getOrDownload(context, status.contentUri!!, if (status.type == FynxStatusType.VIDEO) "video" else "image")
                    when {
                        cached == null -> null
                        status.type == FynxStatusType.VIDEO -> MediaMetadataRetriever().run {
                            setDataSource(cached.absolutePath)
                            val frame = getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            release(); frame
                        }
                        status.type == FynxStatusType.PHOTO -> android.graphics.BitmapFactory.decodeFile(cached.absolutePath)
                        else -> null
                    }
                }.getOrNull()
            }
        }
    }

    Column(Modifier.width(82.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, modifier = Modifier.size(72.dp)) {
            androidx.compose.foundation.layout.Box(
                Modifier.size(66.dp)
                    .background(if (status?.type == FynxStatusType.TEXT) Color(status.textStyle.backgroundColor) else FynxDesign.SurfaceRaised, CircleShape)
                    .border(3.dp, if (active) MaterialTheme.colorScheme.primary else FynxDesign.Outline, CircleShape)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                when {
                    bitmap != null -> androidx.compose.foundation.Image(bitmap!!.asImageBitmap(), "Status media preview", Modifier.fillMaxWidth(), contentScale = ContentScale.Crop)
                    status?.type == FynxStatusType.TEXT -> Text(status.text.orEmpty().take(20), color = Color(status.textStyle.foregroundColor), style = MaterialTheme.typography.labelSmall, maxLines = 3)
                    status?.type == FynxStatusType.VIDEO -> Icon(Icons.Default.PlayArrow, "Video status", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                    status?.type == FynxStatusType.VOICE -> Icon(Icons.Default.Mic, "Voice status", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    else -> FynxAvatar(name, Modifier.size(60.dp).clip(CircleShape))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            if (statusCount > 1) Text(" • $statusCount", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}
