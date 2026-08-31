package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomePanel() {
    var prompt by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }

    val suggestions = listOf(
        "Plan my day",
        "Write a message",
        "Help me understand something",
        "Create an idea"
    )

    Column(Modifier.fillMaxSize()) {
        Text("What can I help you with?", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            "Ask FYNX about everyday tasks, ideas, writing, planning and more.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(16.dp))

        if (submitted && prompt.isNotBlank()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("You", style = MaterialTheme.typography.labelLarge)
                    Text(prompt)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "FYNX is ready to help. AI connection will be added next.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { }) { Text("Copy") }
                        TextButton(onClick = { }) { Text("Share") }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Text("Try asking", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(suggestions) { suggestion ->
                OutlinedButton(
                    onClick = {
                        prompt = suggestion
                        submitted = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(suggestion) }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it; submitted = false },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Message FYNX…") },
                shape = RoundedCornerShape(24.dp)
            )
            Button(
                onClick = { if (prompt.isNotBlank()) submitted = true },
                modifier = Modifier.height(56.dp)
            ) { Text("Send") }
        }
    }
}
