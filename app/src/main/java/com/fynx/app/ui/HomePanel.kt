package com.fynx.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Welcome to FYNX", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Connect, share and get things done.", style = MaterialTheme.typography.bodyMedium)
            }
            FynxAvatar("FYNX", Modifier.size(48.dp).clip(CircleShape))
        }

        Text("Stories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
        ) {
            item {
                Column(Modifier.width(68.dp)) {
                    FynxAvatar("Your Story", Modifier.size(62.dp).clip(CircleShape))
                    Spacer(Modifier.height(5.dp))
                    Text("Your Story", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
            items(sampleStories.take(5)) { story ->
                Column(Modifier.width(68.dp)) {
                    FynxAvatar(story.displayName, Modifier.size(62.dp).clip(CircleShape))
                    Spacer(Modifier.height(5.dp))
                    Text(story.displayName, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }

        Text("Your Feed", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            items(feedPosts) { post ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FynxAvatar(post.first, Modifier.size(44.dp).clip(CircleShape))
                            Column(Modifier.weight(1f)) {
                                Text(post.first, fontWeight = FontWeight.Bold)
                                Text(post.third, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Text(post.second, style = MaterialTheme.typography.bodyLarge)
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            TextButton(onClick = {}) { Text("Like") }
                            TextButton(onClick = {}) { Text("Comment") }
                            TextButton(onClick = {}) { Text("Share") }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("FYNX Assistant", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Ask FYNX about everyday tasks, ideas, writing, planning and more.", style = MaterialTheme.typography.bodyMedium)
                }
            }

            items(messages) { message ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(if (message.fromUser) "You" else "FYNX", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
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
                item { Text("Try asking", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                items(suggestions) { suggestion ->
                    OutlinedButton(onClick = { prompt = suggestion }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text(suggestion) }
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
            Button(onClick = { sendMessage() }, Modifier.height(56.dp), enabled = prompt.isNotBlank() && !isLoading) { Text("Send") }
        }
    }
}
