package com.fynx.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun FynxStatusTimelinePanel() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val auth = remember(context) { FynxAuthStore.load(context) }
    val username = auth.username?.removePrefix("@").orEmpty()
    var statuses by remember { mutableStateOf<List<FynxStatus>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<FynxStatus?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    fun refresh() { scope.launch { loading = true; error = null; FynxStatusClient.list(context).onSuccess { statuses = it.filterNot(FynxStatus::isExpired) }.onFailure { error = it.message ?: "Unable to load Status." }; loading = false } }
    LaunchedEffect(refreshKey) { refresh() }
    val latestByOwner = statuses.groupBy { it.ownerUsername }.mapNotNull { (_, values) -> values.maxByOrNull { it.createdAtMillis } }.sortedByDescending { it.createdAtMillis }
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Status", style = MaterialTheme.typography.headlineSmall); Text("Photos, videos, text and voice • 24 hours", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; TextButton(onClick = { refreshKey++ }) { Text("Refresh") } }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!loading && latestByOwner.isEmpty() && error == null) Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(10.dp)); Text("No active Status yet. Tap + to share your first one.") } }
        else LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 4.dp)) { items(latestByOwner, key = { it.ownerUsername }) { status -> StatusBubble(status, status.ownerUsername.equals(username, true)) { selected = status } } }
        HorizontalDivider()
        Text("Recent Status", style = MaterialTheme.typography.titleMedium)
        Text("Tap a circle to open the clean full-screen viewer.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (statuses.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(statuses.take(12), key = { it.id }) { status -> AssistChip(onClick = { selected = status }, label = { Text("${status.ownerDisplayName.ifBlank { status.ownerUsername }} • ${statusTypeLabel(status.type)}") }) } }
    }
    selected?.let { initial ->
        val ownerStatuses = statuses.filter { it.ownerUsername == initial.ownerUsername }.sortedBy { it.createdAtMillis }
        StatusViewerDialog(ownerStatuses, ownerStatuses.indexOfFirst { it.id == initial.id }.coerceAtLeast(0), username, { selected = null }, { selected = null; refreshKey++ })
    }
}

@Composable private fun StatusBubble(status: FynxStatus, isMe: Boolean, onClick: () -> Unit) { Column(Modifier.width(74.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) { Box(Modifier.size(66.dp).border(3.dp, MaterialTheme.colorScheme.primary, CircleShape).padding(4.dp)) { Box(Modifier.fillMaxSize().clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Text(status.ownerDisplayName.ifBlank { status.ownerUsername }.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) } }; Spacer(Modifier.height(5.dp)); Text(if (isMe) "My status" else status.ownerDisplayName.ifBlank { status.ownerUsername }, style = MaterialTheme.typography.labelSmall, maxLines = 1); Text(statusTypeLabel(status.type), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }

@Composable private fun StatusViewerDialog(statuses: List<FynxStatus>, startIndex: Int, viewerUsername: String, onDismiss: () -> Unit, onDeleted: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var index by remember(statuses, startIndex) { mutableIntStateOf(startIndex) }
    var deleting by remember { mutableStateOf(false) }
    val status = statuses.getOrNull(index) ?: return
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { if (status.ownerUsername.equals(viewerUsername, true)) IconButton(enabled = !deleting, onClick = { deleting = true; scope.launch { FynxStatusClient.delete(context, status.id).onSuccess { onDeleted() }.onFailure { deleting = false } } }) { Icon(Icons.Default.Delete, "Delete Status") }; TextButton(onClick = onDismiss) { Text("Close") } } }, title = { Column { Text(status.ownerDisplayName.ifBlank { status.ownerUsername }); Text("${statusTypeLabel(status.type)} • ${statusTimeLeft(status.createdAtMillis)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }, text = { Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) { Box(Modifier.fillMaxWidth().heightIn(min = 340.dp, max = 560.dp), contentAlignment = Alignment.Center) { when (status.type) { FynxStatusType.TEXT -> StatusViewerText(status); FynxStatusType.PHOTO -> status.contentUri?.let { FynxRemoteMedia(it, "image", Modifier.fillMaxWidth().heightIn(min=300.dp, max=560.dp)) }; FynxStatusType.VIDEO -> status.contentUri?.let { FynxRemoteMedia(it, "video", Modifier.fillMaxWidth().heightIn(min=300.dp, max=560.dp)) }; FynxStatusType.VOICE -> status.contentUri?.let { FynxRemoteAudio(it) } } }; if (statuses.size > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { IconButton(enabled = index > 0, onClick = { index-- }) { Icon(Icons.Default.ArrowBack, "Previous Status") }; Text("${index + 1} / ${statuses.size}"); IconButton(enabled = index < statuses.lastIndex, onClick = { index++ }) { Icon(Icons.Default.ArrowForward, "Next Status") } } } })
}

@Composable private fun StatusViewerText(status: FynxStatus) { Box(Modifier.fillMaxWidth().heightIn(min=300.dp, max=560.dp).background(Color(status.textStyle.backgroundColor), RoundedCornerShape(18.dp)).padding(26.dp), contentAlignment = Alignment.Center) { Text(status.text.orEmpty(), color = Color(status.textStyle.foregroundColor), style = MaterialTheme.typography.headlineSmall) } }
private fun statusTypeLabel(type: FynxStatusType) = when (type) { FynxStatusType.TEXT -> "Text"; FynxStatusType.PHOTO -> "Photo"; FynxStatusType.VIDEO -> "Video"; FynxStatusType.VOICE -> "Voice" }
private fun statusTimeLeft(createdAt: Long, now: Long = System.currentTimeMillis()): String { val remaining=(createdAt+FYNX_STATUS_EXPIRY_MS-now).coerceAtLeast(0L); val h=remaining/3_600_000L; val m=(remaining/60_000L)%60L; return if(h>0) "${h}h ${m}m left" else "${m}m left" }
