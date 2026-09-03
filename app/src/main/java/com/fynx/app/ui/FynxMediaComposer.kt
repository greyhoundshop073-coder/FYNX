package com.fynx.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Capture result gate: keeps media in a preview state before the caller sends it. */
@Composable
fun FynxMediaComposer(
    uri: Uri,
    mediaType: String,
    onSend: (Uri, String) -> Unit,
    onDelete: () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (mediaType == "video") "Video ready" else "Photo ready", style = MaterialTheme.typography.titleLarge)
            Text("Preview before sending")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDelete) { Text("Delete") }
                Button(onClick = { onSend(uri, mediaType) }) { Text("Send") }
            }
        }
    }
}
