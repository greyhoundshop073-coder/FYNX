package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class FynxQuickNote(val id: Long, val text: String)

@Composable
fun FynxExtraToolsPanel(onOpenCalendar: () -> Unit) {
    var notes by remember { mutableStateOf(listOf<FynxQuickNote>()) }
    var noteText by remember { mutableStateOf("") }
    var showNoteEditor by remember { mutableStateOf(false) }
    
    Column(Modifier.fillMaxSize()) {
        Text("Extra Tools", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Useful everyday tools that complement FYNX without duplicating its main modules.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        Card(
            onClick = { showNoteEditor = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EditNote, contentDescription = "Quick Notes", tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Quick Notes", style = MaterialTheme.typography.titleMedium)
                    Text("Capture a short note without leaving FYNX.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Card(
            onClick = onOpenCalendar,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarMonth, contentDescription = "Events", tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Events", style = MaterialTheme.typography.titleMedium)
                    Text("Jump to event planning through Calendar.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (notes.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Recent notes", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(notes, key = { it.id }) { note ->
                    Card(Modifier.fillMaxWidth()) {
                        Text(note.text, Modifier.padding(16.dp))
                    }
                }
            }
        }
    }

    if (showNoteEditor) {
        AlertDialog(
            onDismissRequest = { showNoteEditor = false },
            title = { Text("Quick Note") },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    placeholder = { Text("Write a note…") }
                )
            },
            dismissButton = {
                TextButton(onClick = { showNoteEditor = false }) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(
                    enabled = noteText.isNotBlank(),
                    onClick = {
                        notes = listOf(FynxQuickNote(System.currentTimeMillis(), noteText.trim())) + notes
                        noteText = ""
                        showNoteEditor = false
                    }
                ) { Text("Save") }
            }
        )
    }
}
