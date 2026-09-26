package com.fynx.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FynxConversationMomentsSheet(
    messages: List<ChatMessage>,
    title: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val selectable = messages.takeLast(80).asReversed()
    var selectedIds by remember(messages) { mutableStateOf(emptySet<String>()) }
    val selected = selectable.filter { it.id in selectedIds }
    val momentText = remember(selected) {
        buildString {
            append("FYNX Moment • ")
            append(title)
            append("\n\n")
            selected.asReversed().forEach { message ->
                val sender = if (message.fromMe) "You" else message.senderName ?: message.senderUsername ?: "Participant"
                append(sender)
                append(" • ")
                append(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.timestamp)))
                append("\n")
                append(message.text.takeIf { it.isNotBlank() } ?: fynxMomentMediaLabel(message))
                append("\n\n")
            }
        }.trim()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Conversation Moments", style = MaterialTheme.typography.titleLarge)
                    Text("Select real messages to make a shareable memory.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
            }

            Text("${selected.size}/12 selected", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

            if (selectable.isEmpty()) {
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text("There are no conversation messages to turn into a moment yet.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 390.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(selectable, key = { it.id }) { message ->
                        val checked = message.id in selectedIds
                        Surface(
                            onClick = {
                                selectedIds = if (checked) selectedIds - message.id
                                else if (selectedIds.size < 12) selectedIds + message.id else selectedIds
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (checked) Icons.Default.CheckCircle else Icons.Default.AutoAwesome, null, tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(if (message.fromMe) "You" else message.senderName ?: message.senderUsername ?: "Participant", style = MaterialTheme.typography.labelMedium)
                                    Text(message.text.takeIf { it.isNotBlank() } ?: fynxMomentMediaLabel(message), maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { if (selected.isNotEmpty()) clipboard.setText(AnnotatedString(momentText)) },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(6.dp)); Text("Copy")
                }
                Button(
                    onClick = {
                        if (selected.isNotEmpty()) {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, momentText)
                            }, "Share FYNX Moment"))
                        }
                    },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text("Share")
                }
            }

            Text("Moments use only messages already present in this conversation. No sample or fabricated content is created.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun fynxMomentMediaLabel(message: ChatMessage): String = when (message.attachmentType) {
    "video_note" -> "Video note"
    "video" -> "Video"
    "image" -> "Photo"
    "audio" -> "Voice message"
    "document" -> "Document"
    else -> if (message.voiceUri != null) "Voice message" else "Message"
}
