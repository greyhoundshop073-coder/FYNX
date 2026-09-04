package com.fynx.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun FynxVisibleUpdatesPanel(currentUsername: String, onOpenStories: () -> Unit, onOpenAi: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var statuses by remember { mutableStateOf<List<FynxStatus>>(emptyList()) }
    LaunchedEffect(Unit) { statuses = FynxStatusClient.list(context).getOrDefault(emptyList()) }
    val grouped = statuses.groupBy { it.ownerUsername }.values.map { it.maxByOrNull { s -> s.createdAtMillis }!! to it.size }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(onClick = onOpenStories, modifier = Modifier.fillMaxWidth(), shape = FynxDesign.LargeCardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface), border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = .55f))) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Spacer(Modifier.size(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(14.dp))
                    Text("Status", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onOpenStories) { Text("See all") }
                    Spacer(Modifier.width(4.dp))
                }
                LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    item { FynxStatusPreviewCircle(currentUsername, "Your status", true, onOpenStories, grouped.firstOrNull { it.first.ownerUsername.equals(currentUsername, true) }?.second ?: 0) }
                    item { FynxStatusPreviewCircle("FYNX", "Create status", false, onOpenStories, 0) }
                    items(grouped.filterNot { it.first.ownerUsername.equals(currentUsername, true) }) { (status, count) ->
                        FynxStatusPreviewCircle(status.ownerUsername, status.ownerDisplayName.ifBlank { status.ownerUsername }, true, onOpenStories, count)
                    }
                }
                Spacer(Modifier.size(4.dp))
            }
        }