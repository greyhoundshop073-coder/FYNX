package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Backend-first Status timeline. Stories remain available as the creation/viewer surface. */
@Composable
fun FynxStatusTimelinePanel() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var statuses by remember { mutableStateOf<List<FynxStatus>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshKey) {
        loading = true
        error = null
        FynxStatusClient.list(context)
            .onSuccess { statuses = it.filterNot { status -> status.isExpired() } }
            .onFailure { error = it.message ?: "Unable to load Status." }
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Status timeline", style = MaterialTheme.typography.headlineSmall)
                Text("Live Status from authenticated FYNX accounts", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { refreshKey++ }) { Text("Refresh") }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!loading && statuses.isEmpty() && error == null) {
            Card(Modifier.fillMaxWidth()) { Text("No active Status yet. Create one with the + button.", Modifier.padding(16.dp)) }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(statuses, key = { it.id }) { status ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(status.ownerDisplayName.ifBlank { status.ownerUsername }, style = MaterialTheme.typography.titleMedium)
                                Text("@${status.ownerUsername.removePrefix("@")} • ${status.type.name.lowercase()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(formatRemaining(status.createdAtMillis, System.currentTimeMillis()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        when (status.type) {
                            FynxStatusType.TEXT -> Text(status.text.orEmpty(), Modifier.fillMaxWidth().padding(vertical = 8.dp))
                            FynxStatusType.PHOTO -> status.contentUri?.let { FynxRemoteMedia(it, "image", Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 420.dp)) }
                            FynxStatusType.VIDEO -> status.contentUri?.let { FynxRemoteMedia(it, "video", Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 420.dp)) }
                            FynxStatusType.VOICE -> status.contentUri?.let { FynxRemoteAudio(it) }
                        }
                    }
                }
            }
        }
    }
}
