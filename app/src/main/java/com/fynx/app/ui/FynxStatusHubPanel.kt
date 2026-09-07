package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Backend-first Status hub while preserving the existing Stories creation/viewer surface. */
@Composable
fun FynxStatusHubPanel() {
    var composing by remember { mutableStateOf(false) }
    if (composing) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            FynxStatusComposerPanel(onClose = { composing = false })
        }
    } else {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) { FynxStatusTimelinePanel() }
                HorizontalDivider()
                Box(Modifier.weight(1f).fillMaxWidth()) { StoriesPanel() }
            }
            FloatingActionButton(
                onClick = { composing = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Status")
            }
        }
    }
}
