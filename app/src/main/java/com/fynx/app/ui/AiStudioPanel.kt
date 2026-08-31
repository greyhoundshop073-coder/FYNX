package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AiStudioPanel() {
    var prompt by remember { mutableStateOf("") }
    var selectedTool by remember { mutableStateOf("Video") }
    var submitted by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Text("AI Studio", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text("Create images, videos and other AI content from one simple place.")
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Image", "Video").forEach { tool ->
                FilterChip(
                    selected = selectedTool == tool,
                    onClick = { selectedTool = tool; submitted = false },
                    label = { Text(tool) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it; submitted = false },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            label = { Text("Describe what you want to create") }
        )
        Spacer(Modifier.height(10.dp))

        Button(
            onClick = { if (prompt.isNotBlank()) submitted = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (selectedTool == "Video") "Generate video" else "Generate image")
        }

        Spacer(Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            if (submitted) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Generation requested", style = MaterialTheme.typography.titleMedium)
                            Text("Your ${selectedTool.lowercase()} will appear here when the AI service is connected.")
                        }
                    }
                }
            }
        }
    }
}
