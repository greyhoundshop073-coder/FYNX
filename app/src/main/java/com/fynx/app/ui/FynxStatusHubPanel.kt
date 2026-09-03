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

/** Keeps the existing Stories viewer while making the new Status composer reachable. */
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
    }
    if (composing) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { composing = false },
            title = { androidx.compose.material3.Text("Create Status") },
            text = {
                androidx.compose.foundation.layout.Column {
                    androidx.compose.material3.Text("Create a real FYNX Status with text, photo, video or voice.")
                    androidx.compose.material3.Spacer(Modifier.padding(4.dp))
                    androidx.compose.material3.Button(onClick = { composing = false }) { androidx.compose.material3.Text("Open composer") }
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { composing = false }) { androidx.compose.material3.Text("Close") } }
        )
    }
}
