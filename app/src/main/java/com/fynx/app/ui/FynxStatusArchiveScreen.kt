package com.fynx.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun FynxStatusArchiveScreen(
    statuses: List<FynxStatus>,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onOpen: (FynxStatus) -> Unit,
    onDelete: (FynxStatus) -> Unit
) {
    var pendingDelete by remember { mutableStateOf<FynxStatus?>(null) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text("Status archive", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("Your expired updates", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.Archive, null, tint = MaterialTheme.colorScheme.primary)
        }
        HorizontalDivider()
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("Loading your archive…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            statuses.isEmpty() -> Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Archive, null, modifier = Modifier.size(46.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Your archive is empty", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Expired Status updates will appear here so you can revisit them later.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(statuses, key = { it.id }) { status ->
                    StatusArchiveCard(status, onOpen = { onOpen(status) }, onDelete = { pendingDelete = status })
                }
            }
        }
    }
    pendingDelete?.let { status ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete archived Status?") },
            text = { Text("This permanently removes this Status from your archive. This action can't be undone.") },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
            confirmButton = { TextButton(onClick = { pendingDelete = null; onDelete(status) }) { Text("Delete") } }
        )
    }
}

@Composable
private fun StatusArchiveCard(status: FynxStatus, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f))) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(68.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF171A22)), contentAlignment = Alignment.Center) {
                when (status.type) {
                    FynxStatusType.TEXT -> Icon(Icons.Default.TextFields, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    FynxStatusType.PHOTO -> Icon(Icons.Default.Image, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    FynxStatusType.VIDEO -> Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(30.dp))
                    FynxStatusType.VOICE -> Icon(Icons.Default.Mic, null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(status.text?.ifBlank { null } ?: archiveTypeLabel(status.type), maxLines = 2, fontWeight = FontWeight.Medium)
                Text(formatStatusTimestamp(status.createdAtMillis) + " • Expired", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete archived Status") }
        }
    }
}

private fun archiveTypeLabel(type: FynxStatusType) = when (type) {
    FynxStatusType.TEXT -> "Text Status"
    FynxStatusType.PHOTO -> "Photo Status"
    FynxStatusType.VIDEO -> "Video Status"
    FynxStatusType.VOICE -> "Voice Status"
}
