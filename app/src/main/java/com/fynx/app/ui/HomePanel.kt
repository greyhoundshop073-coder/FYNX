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
    var messages by remember { mutableStateOf(listOf<AiMessage>()) }

    val suggestions = listOf(
        "Plan my day",
        "Write a message",
        "Help me understand something",
        "Create an idea"
    )

    fun sendMessage() {
        val text = prompt.trim()
        if (text.isNotEmpty()) {
            messages = messages + AiMessage(text = text, fromUser = true)
            prompt = ""
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text("What can I help you with?", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            "Ask FYNX about everyday tasks, ideas, writing, planning and more.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(16.dp))

        if (messages.isEmpty()) {
            Text("Try asking", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { message ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            if (message.fromUser) "You" else "FYNX",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(message.text)
                    }
                }
            }

            if (messages.isEmpty()) {
                items(suggestions) { suggestion ->
                    OutlinedButton(
                        onClick = { prompt = suggestion },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(suggestion) }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Message FYNX…") },
                shape = RoundedCornerShape(24.dp)
            )
            Button(
                onClick = { sendMessage() },
                modifier = Modifier.height(56.dp),
                enabled = prompt.isNotBlank()
            ) { Text("Send") }
        }
    }
}
