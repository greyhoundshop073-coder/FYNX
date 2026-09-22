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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
    onCreateStatus: () -> Unit = onOpenStories
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
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

    LaunchedEffect(currentUsername) { refreshStatuses() }
    DisposableEffect(lifecycleOwner, currentUsername) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshStatuses()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val current = currentUsername.removePrefix("@").trim().lowercase()
    val activeStatuses = statuses
        .filter { it.expiresAtMillis <= 0L || it.expiresAtMillis > System.currentTimeMillis() }
        .filter {
            val owner = it.ownerUsername.removePrefix("@").trim().lowercase()
            owner == current || owner in followingUsernames
        }
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
                    TextButton(onClick = onOpenStories, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) { Text("See all") }
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Box(Modifier.width(82.dp)) {
                            FynxStatusPreviewCircle(
                                own?.first,
                                currentUsername.ifBlank { "You" },
                                "Your status",
                                true,
                                if (own != null) onOpenStories else onCreateStatus,
                                own?.second ?: 0,
                                ownerPhotoIds[current]
                            )
                            Surface(
                                modifier = Modifier.align(Alignment.TopEnd).size(26.dp).clickable(onClick = onCreateStatus),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Add, "Create your status", modifier = Modifier.size(17.dp))
                                }
                            }
                        }
                    }
                    items(
                        grouped.filterNot { it.first.ownerUsername.equals(currentUsername, true) },
                        key = { it.first.ownerUsername }
                    ) { (status, count) ->
                        FynxStatusPreviewCircle(
                            status,
                            status.ownerUsername,
                            status.ownerDisplayName.ifBlank { status.ownerUsername },
                            true,
                            onOpenStories,
                            count,
                            ownerPhotoIds[status.ownerUsername.removePrefix("@").trim().lowercase()]
                        )
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = FynxDesign.LargeCardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("FYNX Assistance", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("Ask, create, translate and get help", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                IconButton(
                    onClick = onOpenCamera,
                    modifier = Modifier.size(42.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Open FYNX camera", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Ask FYNX anything…", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onOpenAi, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Open AI")
                }
            }
        }
    }
}

@Composable
private fun FynxStatusPreviewCircle(status: FynxStatus?, name: String, label: String, active: Boolean, onClick: () -> Unit, statusCount: Int = 0, profilePhotoMediaId: String? = null) {
    val context = LocalContext.current
    var bitmap by remember(status?.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(status?.id, status?.contentUri, status?.type) {
        bitmap = null
        if (status != null && !status.contentUri.isNullOrBlank()) bitmap = withContext(Dispatchers.IO) { runCatching { val cached = FynxMediaCache.getOrDownload(context, status.contentUri!!, if (status.type == FynxStatusType.VIDEO) "video" else "image"); when { cached == null -> null; status.type == FynxStatusType.VIDEO -> MediaMetadataRetriever().run { setDataSource(cached.absolutePath); val frame = getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC); release(); frame }; status.type == FynxStatusType.PHOTO -> android.graphics.BitmapFactory.decodeFile(cached.absolutePath); else -> null } }.getOrNull() }
    }
    Column(Modifier.width(82.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, modifier = Modifier.size(72.dp)) {
            androidx.compose.foundation.layout.Box(Modifier.size(66.dp).background(if (status?.type == FynxStatusType.TEXT) Color(status.textStyle.backgroundColor) else FynxDesign.SurfaceRaised, CircleShape).border(3.dp, if (active) MaterialTheme.colorScheme.primary else FynxDesign.Outline, CircleShape).clip(CircleShape), contentAlignment = Alignment.Center) {
                when { bitmap != null -> androidx.compose.foundation.Image(bitmap!!.asImageBitmap(), "Status media preview", Modifier.fillMaxWidth(), contentScale = ContentScale.Crop); status?.type == FynxStatusType.TEXT -> Text(status.text.orEmpty().take(20), color = Color(status.textStyle.foregroundColor), style = MaterialTheme.typography.labelSmall, maxLines = 3); status?.type == FynxStatusType.VIDEO -> Icon(Icons.Default.PlayArrow, "Video status", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)); status?.type == FynxStatusType.VOICE -> Icon(Icons.Default.Mic, "Voice status", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)); profilePhotoMediaId != null -> FynxRemoteProfileAvatar(profilePhotoMediaId, name, Modifier.size(60.dp).clip(CircleShape)); else -> FynxAvatar(name, Modifier.size(60.dp).clip(CircleShape)) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1); if (statusCount > 1) Text(" • $statusCount", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
    }
}
