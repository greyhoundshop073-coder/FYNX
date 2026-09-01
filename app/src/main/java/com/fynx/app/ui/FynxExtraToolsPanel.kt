package com.fynx.app.ui

import androidx.compose.foundation.BorderStroke
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

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Extra Tools", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Useful everyday tools that complement FYNX without duplicating its main modules.",
            color = FynxDesign.TextSecondary
        )

        Card(
            onClick = { showNoteEditor = true },
            modifier = Modifier.fillMaxWidth(),
            shape = FynxDesign.CardShape,
            colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface),
            border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = 0.5f))
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = FynxDesign.ControlShape, color = FynxDesign.SelectedContainer) {
                    Icon(Icons.Default.EditNote, "Quick Notes", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(9.dp).size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Quick Notes", style = MaterialTheme.typography.titleMedium)
                    Text("Capture a short note without leaving FYNX.", color = FynxDesign.TextSecondary)
                }
            }
        }

        Card(
            onClick = onOpenCalendar,
            modifier = Modifier.fillMaxWidth(),
            shape = FynxDesign.CardShape,
            colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface),
            border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = 0.5f))
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = FynxDesign.ControlShape, color = FynxDesign.SelectedContainer) {
                    Icon(Icons.Default.CalendarMonth, "Events", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(9.dp).size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Events", style = MaterialTheme.typography.titleMedium)
                    Text("Jump to event planning through Calendar.", color = FynxDesign.TextSecondary)
                }
            }
        }

        if (notes.isNotEmpty()) {
            Text("Recent notes", style = MaterialTheme.typography.titleMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(notes, key = { it.id }) { note ->
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = FynxDesign.CardShape,
                        colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface),
                        border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = 0.4f))
                    ) {
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
                    shape = FynxDesign.ControlShape,
                    placeholder = { Text("Write a note…") }
                )
            },
            dismissButton = { TextButton(onClick = { showNoteEditor = false }) { Text("Cancel") } },
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
