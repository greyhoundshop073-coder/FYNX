package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Single Status/Stories surface. The old local-only Stories panel is no longer rendered beside the backend feed. */
@Composable
fun FynxStatusHubPanel() {
    var composing by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            if (composing) FynxStatusComposerPanel(onClose = { composing = false }) else FynxStatusTimelinePanel()
            if (!composing) FloatingActionButton(onClick = { composing = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp)) { Icon(Icons.Default.Add, contentDescription = "Create Status") }
        }
    }
}
