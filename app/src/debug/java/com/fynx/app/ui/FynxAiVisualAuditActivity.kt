package com.fynx.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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
