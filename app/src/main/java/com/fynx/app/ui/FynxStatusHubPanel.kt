package com.fynx.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Keeps the existing Stories viewer while making the real Status composer reachable. */
@Composable
fun FynxStatusHubPanel() {
    var composing by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        StoriesPanel()
        FloatingActionButton(
            onClick = { composing = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Create Status")
        }
        if (composing) {
            androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxSize(),
                color = androidx.compose.material3.MaterialTheme.colorScheme.background
            ) {
                FynxStatusComposerPanel(onClose = { composing = false })
            }
        }
    }
}
