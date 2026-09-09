package com.fynx.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * FYNX Home voice entry point.
 *
 * The compact inline control is the single source of truth for Home so the
 * microphone, connection state and voice interaction do not appear twice or
 * drift into two different implementations.
 */
@Composable
fun HomeAiVoiceInlineControl(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember { FynxAiWebRtcEngine(context) }
    var connected by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Talk to FYNX AI") }
    var transcript by remember { mutableStateOf<String?>(null) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startHomeVoice(engine, scope, { connecting = it }, { connected = it }, { status = it }, { transcript = it })
        else status = "Microphone permission required"
    }

    DisposableEffect(Unit) { onDispose { engine.close() } }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .background(
                    if (connected) MaterialTheme.colorScheme.primary else FynxDesign.SurfaceRaised,
                    CircleShape
                )
                .border(
                    width = if (connecting || connected) 3.dp else 1.dp,
                    color = if (connecting || connected) MaterialTheme.colorScheme.primary else FynxDesign.Outline.copy(alpha = .65f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                enabled = !connecting,
                onClick = {
                    if (connected) {
                        muted = !muted
                        engine.setMicrophoneEnabled(!muted)
                    } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        startHomeVoice(engine, scope, { connecting = it }, { connected = it }, { status = it }, { transcript = it })
                    } else permission.launch(Manifest.permission.RECORD_AUDIO)
                }
            ) {
                Icon(
                    imageVector = if (connected && muted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = if (connected && muted) "Unmute FYNX AI" else "Start FYNX AI voice",
                    tint = if (connected) Color.White else FynxDesign.TextPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Column(Modifier.weight(1f)) {
            Text("FYNX AI", style = MaterialTheme.typography.titleSmall)
            Text(
                if (connecting) "Connecting…" else if (connected) "Listening • voice replies enabled" else status,
                style = MaterialTheme.typography.bodySmall,
                color = FynxDesign.TextSecondary,
                maxLines = 1
            )
            transcript?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1, color = FynxDesign.TextSecondary)
            }
        }

        if (connected) {
            IconButton(onClick = { engine.close(); connected = false; muted = false; status = "Voice session ended" }) {
                Icon(Icons.Default.PhoneDisabled, "End FYNX AI voice")
            }
        }
    }
}

/** Legacy/card entry point now delegates to the same Home control. */
@Composable
fun HomeAiVoiceCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = FynxDesign.SurfaceRaised),
        shape = FynxDesign.ControlShape
    ) {
        HomeAiVoiceInlineControl(Modifier.padding(12.dp))
    }
}

private fun startHomeVoice(
    engine: FynxAiWebRtcEngine,
    scope: kotlinx.coroutines.CoroutineScope,
    setConnecting: (Boolean) -> Unit,
    setConnected: (Boolean) -> Unit,
    setStatus: (String) -> Unit,
    setTranscript: (String) -> Unit
) {
    scope.launch {
        setConnecting(true)
        setStatus("Connecting to FYNX AI…")
        engine.connect(
            onStateChanged = { state, error ->
                when (state) {
                    FynxAiWebRtcEngine.State.CONNECTING -> { setConnecting(true); setConnected(false) }
                    FynxAiWebRtcEngine.State.CONNECTED -> { setConnecting(false); setConnected(true); setStatus("Speak naturally — FYNX AI will answer with voice") }
                    FynxAiWebRtcEngine.State.FAILED -> { setConnecting(false); setConnected(false); setStatus(error ?: "FYNX AI voice connection failed") }
                    FynxAiWebRtcEngine.State.CLOSED -> { setConnecting(false); setConnected(false) }
                    FynxAiWebRtcEngine.State.IDLE -> Unit
                }
            },
            onEvent = { raw -> parseHomeVoiceEvent(raw, setTranscript) }
        )
        setConnecting(false)
    }
}

private fun parseHomeVoiceEvent(raw: String, setTranscript: (String) -> Unit) {
    runCatching {
        val event = JSONObject(raw)
        when (event.optString("type")) {
            "conversation.item.input_audio_transcription.completed" -> event.optString("transcript").takeIf { it.isNotBlank() }?.let { setTranscript("You: $it") }
            "response.audio_transcript.done", "response.output_text.done" -> {
                val text = event.optString("transcript").ifBlank { event.optString("text") }
                if (text.isNotBlank()) setTranscript("FYNX AI: $text")
            }
        }
    }
}

// CI verification marker: Push 6 correction revalidated from current main.
