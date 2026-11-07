package com.fynx.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Debug-build-only screenshot host for the production AI composables.
 * It uses fixed local text and never starts a backend session or writes chat data.
 */
class FynxAiVisualAuditActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        val screen = intent.getStringExtra("screen") ?: "voice"
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF007FFF),
                    onPrimary = Color.White,
                    background = Color(0xFF0E1118),
                    onBackground = Color(0xFFF5F7FA),
                    surface = Color(0xFF171D27),
                    onSurface = Color(0xFFF5F7FA),
                    surfaceVariant = Color(0xFF202938),
                    onSurfaceVariant = Color(0xFFB6C1D1)
                )
            ) {
                when (screen) {
                    "chat" -> FynxAiChatScreenAudit()
                    "actions" -> FynxAiResponseActionAudit(active = false)
                    "reading" -> FynxAiResponseActionAudit(active = true)
                    else -> {
                        var muted by remember { mutableStateOf(false) }
                        FynxAiFullScreenVoice(
                            connecting = false,
                            connected = true,
                            muted = muted,
                            phase = if (muted) "MUTED" else "LISTENING",
                            error = null,
                            input = "",
                            hasPendingAttachment = false,
                            attachmentUploading = false,
                            onInputChange = {},
                            onAttachImage = {},
                            onSend = {},
                            onToggleMute = { muted = !muted },
                            onClose = { finish() },
                            onMenu = { finish() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FynxAiResponseActionAudit(active: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E1118))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(
                if (active) "FYNX AI · READING A RESPONSE" else "FYNX AI · RESPONSE ACTIONS",
                color = Color(0xFF8CB9FF),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            AiChatBubble(
                message = FynxAiUiMessage(
                    id = "debug-visual-only",
                    text = "Here is the key point: FYNX AI can read the full answer aloud in a clear, natural conversational voice. You can pause, resume, copy, rate or share this response.",
                    fromUser = false
                ),
                feedback = 0,
                speaking = active,
                readAloudProgress = if (active) "FYNX AI can read the full answer aloud in a clear, natural conversational voice." else "",
                waveformLevels = if (active) listOf(0.18f, 0.42f, 0.76f, 0.34f, 0.58f, 0.88f, 0.31f, 0.67f, 0.45f, 0.94f, 0.27f, 0.55f, 0.72f, 0.38f, 0.81f, 0.22f, 0.63f, 0.49f, 0.91f, 0.36f, 0.68f, 0.29f, 0.84f, 0.43f, 0.74f, 0.33f, 0.59f, 0.96f, 0.25f, 0.52f) else emptyList(),
                elapsedMs = if (active) 18_000L else 0L,
                durationMs = if (active) 63_000L else 0L,
                paused = false,
                connecting = false,
                onCopy = {},
                onSpeak = {},
                onStopSpeaking = {},
                onFeedback = {},
                onShare = {}
            )
        }
    }
}

@Composable
private fun FynxAiChatScreenAudit() {
    var draft by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = Color(0xFF007FFF),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .22f))
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.padding(9.dp).size(22.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("FYNX Assistant", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("Ask · Create · Discover", style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFB8C5D6))
            }
            IconButton(onClick = {}, modifier = Modifier.size(40.dp).background(Color(0xFF1C2434), androidx.compose.foundation.shape.CircleShape)) {
                Icon(Icons.Default.Add, contentDescription = "Start new conversation", tint = Color.White)
            }
            IconButton(onClick = {}, modifier = Modifier.size(40.dp).background(Color(0xFF1C2434), androidx.compose.foundation.shape.CircleShape)) {
                Icon(Icons.Default.MoreVert, contentDescription = "Conversation history", tint = Color.White)
            }
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AiChatBubble(
                message = FynxAiUiMessage(
                    id = "audit-user-message",
                    text = "Can you explain how FYNX AI helps me save time?",
                    fromUser = true
                ),
                feedback = 0,
                speaking = false,
                readAloudProgress = "",
                waveformLevels = emptyList(),
                elapsedMs = 0L,
                durationMs = 0L,
                paused = false,
                connecting = false,
                onCopy = {},
                onSpeak = {},
                onStopSpeaking = {},
                onFeedback = {},
                onShare = {}
            )
            AiChatBubble(
                message = FynxAiUiMessage(
                    id = "audit-assistant-message",
                    text = "FYNX AI can help you write, understand information, plan your day and work through questions. You can read the answer, copy or share it, or tap the speaker to hear the complete response in FYNX's natural voice.",
                    fromUser = false
                ),
                feedback = 0,
                speaking = false,
                readAloudProgress = "",
                waveformLevels = emptyList(),
                elapsedMs = 0L,
                durationMs = 0L,
                paused = false,
                connecting = false,
                onCopy = {},
                onSpeak = {},
                onStopSpeaking = {},
                onFeedback = {},
                onShare = {}
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .65f))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {}) {
                    Icon(Icons.Default.Add, contentDescription = "Attach to FYNX Assistant")
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message FYNX Assistant…") },
                    maxLines = 4
                )
                IconButton(onClick = {}) {
                    Icon(Icons.Default.Mic, contentDescription = "Start voice conversation with FYNX Assistant")
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Default.Send, contentDescription = "Send message")
                }
            }
        }
        Text(
            "FYNX Assistant can make mistakes. Check important information.",
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
