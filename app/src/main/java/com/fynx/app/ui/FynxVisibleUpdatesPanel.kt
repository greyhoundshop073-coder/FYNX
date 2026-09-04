package com.fynx.app.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun FynxVisibleUpdatesPanel(
    currentUsername: String,
    onOpenStories: () -> Unit,
    onOpenAi: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var statuses by remember { mutableStateOf<List<FynxStatus>>(emptyList()) }

    LaunchedEffect(Unit) {
        statuses = FynxStatusClient.list(context).getOrDefault(emptyList())
    }

    val grouped = statuses
        .groupBy { it.ownerUsername }
        .mapNotNull { (_, list) ->
            list.maxByOrNull { it.createdAtMillis }?.let { it to list.size }
        }

    Card(
        onClick = onOpenStories,
        modifier = Modifier.fillMaxWidth(),
        shape = FynxDesign.LargeCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = .55f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.width(14.dp))
                Text(
                    "Status",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(onClick = onOpenStories) { Text("See all") }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    FynxStatusPreviewCircle(
                        currentUsername,
                        "Your status",
                        true,
                        onOpenStories,
                        grouped.firstOrNull {
                            it.first.ownerUsername.equals(currentUsername, true)
                        }?.second ?: 0
                    )
                }
                item {
                    FynxStatusPreviewCircle(
                        "FYNX",
                        "Create status",
                        false,
                        onOpenStories
                    )
                }
                items(grouped.filterNot {
                    it.first.ownerUsername.equals(currentUsername, true)
                }) { (status, count) ->
                    FynxStatusPreviewCircle(
                        status.ownerUsername,
                        status.ownerDisplayName.ifBlank { status.ownerUsername },
                        true,
                        onOpenStories,
                        count
                    )
                }
            }
            Spacer(Modifier.size(4.dp))
        }
    }
}

@Composable
private fun FynxStatusPreviewCircle(
    name: String,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    statusCount: Int = 0
) {
    Column(
        modifier = Modifier.width(82.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        androidx.compose.material3.IconButton(
            onClick = onClick,
            modifier = Modifier.size(66.dp)
        ) {
            FynxAvatar(
                name,
                Modifier
                    .size(60.dp)
                    .border(
                        BorderStroke(
                            3.dp,
                            if (active) MaterialTheme.colorScheme.primary else FynxDesign.Outline
                        ),
                        CircleShape
                    )
                    .clip(CircleShape)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
            if (statusCount > 1) {
                Text(
                    " • $statusCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
