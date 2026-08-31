package com.fynx.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomePanel() {
    var prompt by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf<AiMessage>()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val suggestions = listOf("Plan my day", "Write a message", "Help me understand something", "Create an idea")
    val feedPosts = listOf(
        Triple("FYNX Community", "Discover what is happening around you and stay connected.", "12 min ago"),
        Triple("Daily Inspiration", "Small steps, good ideas and meaningful connections can make a big difference.", "35 min ago")
    )

    fun sendMessage(textToSend: String = prompt) {
        val text = textToSend.trim()
        if (text.isEmpty() || isLoading) return
        messages = messages + AiMessage(text, true)
        prompt = ""
        error = null
        isLoading = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { AiAssistantClient.sendMessage(text) }
            result.onSuccess { reply -> messages = messages + AiMessage(reply, false) }
                .onFailure { error = it.message ?: "Unable to reach FYNX right now." }
            isLoading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("Welcome to FYNX", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text("Connect, share and get things done.", style = MaterialTheme.typography.bodyMedium)
            }
            FynxAvatar("FYNX", Modifier.size(48.dp))
        }

        Spacer(Modifier.height(16.dp))
        Text("Stories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = 8.dp)
        ) {
            item { FynxAvatar("Your Story", Modifier.size(60.dp)) }
            items(sampleStories.take(5)) { story ->
                FynxAvatar(story.displayName, Modifier.size(60.dp))
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("Your Feed", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            items(feedPosts) { post ->
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FynxAvatar(post.first, Modifier.size(42.dp))
                            Column(Modifier.weight(1f)) {
                                Text(post.first, fontWeight = FontWeight.SemiBold)
                                Text(post.third, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(post.second, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {}) { Text("Like") }
                            TextButton(onClick = {}) { Text("Comment") }
                            TextButton(onClick = {}) { Text("Share") }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                Text("FYNX Assistant", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Ask FYNX about everyday tasks, ideas, writing, planning and more.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
            }

            items(messages) { message ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(if (message.fromUser) "You" else "FYNX", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(message.text)
                        if (!message.fromUser) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("FYNX response", message.text))
                            }) { Text("Copy") }
                            TextButton(onClick = {
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, message.text)
                                }, "Share FYNX response"))
                            }) { Text("Share") }
                        }
                    }
                }
            }

            if (messages.isEmpty()) {
                item { Text("Try asking", style = MaterialTheme.typography.titleMedium) }
                items(suggestions) { suggestion ->
                    OutlinedButton(onClick = { prompt = suggestion }, Modifier.fillMaxWidth()) { Text(suggestion) }
                }
            }
            if (isLoading) item { Text("FYNX is thinking…") }
            error?.let { message ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(message, color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = { sendMessage(messages.lastOrNull { it.fromUser }?.text.orEmpty()) }) { Text("Retry") }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                prompt,
                { prompt = it },
                Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Message FYNX…") },
                shape = RoundedCornerShape(24.dp),
                enabled = !isLoading
            )
            Button(
                onClick = { sendMessage() },
                Modifier.height(56.dp),
                enabled = prompt.isNotBlank() && !isLoading
            ) { Text("Send") }
        }
    }
}
