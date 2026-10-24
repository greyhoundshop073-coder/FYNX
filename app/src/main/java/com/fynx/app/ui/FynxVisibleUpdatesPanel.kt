package com.fynx.app.ui

import android.media.MediaMetadataRetriever
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
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
    val homeRefreshKey = FynxHomeLifecycleRefreshBus.currentVersion()
    var statuses by remember { mutableStateOf<List<FynxStatus>>(emptyList()) }
    var followingUsernames by remember { mutableStateOf<Set<String>>(emptySet()) }
    var ownerPhotoIds by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
    var ownerUserIds by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var ownerActivityVisible by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }

    fun resolveOwnerPhotos(items: List<FynxStatus>) {
        val names = items.map { it.ownerUsername.removePrefix("@").trim() }.filter { it.isNotBlank() }.distinct()
        val missing = names.filterNot { ownerPhotoIds.containsKey(it.lowercase()) && ownerUserIds.containsKey(it.lowercase()) && ownerActivityVisible.containsKey(it.lowercase()) }
        if (missing.isEmpty()) return
        scope.launch {
            val resolved = missing.map { username ->
                async(Dispatchers.IO) {
                    username.lowercase() to FynxProfileRemoteClient.get(context, username).getOrNull()
                }
            }.awaitAll().toMap()
            ownerPhotoIds = ownerPhotoIds + resolved.mapValues { it.value?.profilePhotoMediaId }
            ownerUserIds = ownerUserIds + resolved.mapNotNull { (username, profile) -> profile?.id?.takeIf { it.isNotBlank() }?.let { username to it } }.toMap()
            ownerActivityVisible = ownerActivityVisible + resolved.mapNotNull { (username, profile) -> profile?.let { username to it.activityVisible } }.toMap()
        }
    }

    fun refreshStatuses() {
        scope.launch(Dispatchers.IO) {
            val latest = FynxStatusClient.list(context).getOrNull()
            val following = FynxProfileRemoteClient.following(context)
                .getOrDefault(emptyList())
                .map { it.username.removePrefix("@").trim().lowercase() }
                .toSet()
            withContext(Dispatchers.Main) {
                followingUsernames = following
                // A timeout or temporary offline response must not erase a previously
                // loaded Status row; the next lifecycle refresh can retry normally.
                if (latest != null) {
                    statuses = latest
                    resolveOwnerPhotos(latest)
                }
            }
        }
    }

    LaunchedEffect(currentUsername, homeRefreshKey) { refreshStatuses() }

    val current = currentUsername.removePrefix("@").trim().lowercase()
    val activeStatuses = statuses.filterNot(FynxStatus::isExpired)
    val viewedStatusIds = FynxStatusClient.viewedStatusIds(context)
    val unseenOwners = activeStatuses
        .groupBy { it.ownerUsername.removePrefix("@").trim().lowercase() }
        .filterValues { ownerStatuses -> ownerStatuses.any { it.id !in viewedStatusIds } }
        .keys
    // Normalize owner identity before grouping so "@name", "name", and case-only
    // variants cannot produce duplicate bubbles or hide the signed-in user's own status.
    val grouped = activeStatuses
        .groupBy { it.ownerUsername.removePrefix("@").trim().lowercase() }
        .mapNotNull { (_, list) -> list.maxByOrNull { it.createdAtMillis }?.let { latest -> latest to list.size } }
    val own = grouped.firstOrNull {
        it.first.ownerUsername.removePrefix("@").trim().equals(current, ignoreCase = true)
    }
    val ownHasUnseen = activeStatuses.any {
        it.ownerUsername.removePrefix("@").trim().equals(current, ignoreCase = true) &&
            it.id !in viewedStatusIds
    }

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = FynxDesign.LargeCardShape,
            colors = CardDefaults.cardColors(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface),
            border = null
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Status", Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onOpenStories, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp), modifier = Modifier.semantics { contentDescription = "Open Stories" }) { Text("See all") }
                }
                LazyRow(
                    contentPadding = PaddingValues(start = 12.dp, end = 16.dp, top = 2.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        FynxStatusPreviewCircle(
                            own?.first,
                            currentUsername.ifBlank { "You" },
                            "Your status",
                            ownHasUnseen,
                            if (own != null) { { onOpenStatusOwner(current) } } else { onCreateStatus },
                            own?.second ?: 0,
                            ownerPhotoIds[current],
                            isOwn = true,
                            onCreateStatus = onCreateStatus
                        )
                    }
                    items(grouped.filterNot { it.first.ownerUsername.removePrefix("@").trim().equals(current, ignoreCase = true) }, key = { it.first.ownerUsername.removePrefix("@").trim().lowercase() }) { (status, count) ->
                        val ownerKey = status.ownerUsername.removePrefix("@").trim().lowercase()
                        FynxStatusPreviewCircle(
                            status, status.ownerUsername, status.ownerDisplayName.ifBlank { status.ownerUsername },
                            status.ownerUsername.removePrefix("@").trim().lowercase() in unseenOwners,
                            { onOpenStatusOwner(status.ownerUsername.removePrefix("@").trim()) }, count,
                            ownerPhotoIds[ownerKey], ownerUserId = ownerUserIds[ownerKey],
                            showOnline = ownerActivityVisible[ownerKey] == true && FynxOnlinePresenceStore.isOnline(ownerUserIds[ownerKey])
                        )
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            shape = FynxDesign.LargeCardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("FYNX Assistance", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Ask, create, translate and get help", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    IconButton(onClick = onOpenFastCamera, modifier = Modifier.size(42.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape)) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Open FYNX camera", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
                    }
                }
                Row(
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f), FynxDesign.CardShape)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Ask FYNX anything…", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    TextButton(onClick = onOpenAi, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Open FYNX AI")
                    }
                }
            }
        }
    }
}

@Composable
private fun FynxStatusPreviewCircle(status: FynxStatus?, name: String, label: String, active: Boolean, onClick: () -> Unit, statusCount: Int = 0, profilePhotoMediaId: String? = null, isOwn: Boolean = false, onCreateStatus: () -> Unit = onClick, ownerUserId: String? = null, showOnline: Boolean = false) {
    val context = LocalContext.current
    var bitmap by remember(status?.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(status?.id, status?.contentUri, status?.type) {
        bitmap = null
        if (status != null && !status.contentUri.isNullOrBlank()) bitmap = withContext(Dispatchers.IO) { runCatching { val cached = FynxMediaCache.getOrDownload(context, status.contentUri!!, if (status.type == FynxStatusType.VIDEO) "video" else "image"); when { cached == null -> null; status.type == FynxStatusType.VIDEO -> MediaMetadataRetriever().run { setDataSource(cached.absolutePath); val frame = getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC); release(); frame }; status.type == FynxStatusType.PHOTO -> android.graphics.BitmapFactory.decodeFile(cached.absolutePath); else -> null } }.getOrNull() }
    }
    Column(Modifier.width(82.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.requiredSize(78.dp), contentAlignment = Alignment.Center) {
            IconButton(onClick = onClick, modifier = Modifier.requiredSize(76.dp).semantics { contentDescription = label }) {
                Box(
                    Modifier.size(70.dp)
                        .background(if (status?.type == FynxStatusType.TEXT) Color(status.textStyle.backgroundColor) else FynxDesign.SurfaceRaised, CircleShape)
                        .border(3.dp, if (active) fynxStatusRingBrush() else Brush.sweepGradient(listOf(FynxDesign.Outline, FynxDesign.Outline)), CircleShape)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    when { bitmap != null -> androidx.compose.foundation.Image(bitmap!!.asImageBitmap(), "Status media preview", Modifier.fillMaxWidth(), contentScale = ContentScale.Crop); status?.type == FynxStatusType.TEXT -> Text(status.text.orEmpty().take(20), color = Color(status.textStyle.foregroundColor), style = MaterialTheme.typography.labelSmall, maxLines = 3); status?.type == FynxStatusType.VIDEO -> Icon(Icons.Default.PlayArrow, "Video status", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)); status?.type == FynxStatusType.VOICE -> Icon(Icons.Default.Mic, "Voice status", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)); profilePhotoMediaId != null -> FynxRemoteProfileAvatar(profilePhotoMediaId, name, Modifier.size(64.dp).clip(CircleShape), ownerUsername = status?.ownerUsername); else -> FynxAvatar(name, Modifier.size(64.dp).clip(CircleShape), ownerUsername = status?.ownerUsername) }
                }
            }
            if (showOnline && !ownerUserId.isNullOrBlank()) {
                Box(
                    Modifier.align(Alignment.BottomEnd)
                        .padding(end = 2.dp, bottom = if (isOwn) 27.dp else 2.dp)
                        .size(13.dp)
                        .background(Color(0xFF22C55E), CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        .semantics { contentDescription = "$label is online" }
                )
            }
            if (isOwn) {
                IconButton(onClick = onCreateStatus, modifier = Modifier.align(Alignment.BottomEnd).requiredSize(25.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        .clip(CircleShape)
                        .semantics { contentDescription = "Create your status" }
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(17.dp))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1); if (statusCount > 1) Text(" • $statusCount", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
    }
}
