package com.fynx.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Real conversational voice entry point for Home. This replaces the old Android speech recognizer widget. */
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
    Row(modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("✨", style = MaterialTheme.typography.titleMedium)
        Column(Modifier.weight(1f)) {
            Text("FYNX AI", style = MaterialTheme.typography.titleSmall)
            Text(if (connecting) "Connecting…" else if (connected) "Listening • voice replies enabled" else status, style = MaterialTheme.typography.bodySmall, color = FynxDesign.TextSecondary, maxLines = 1)
            transcript?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1, color = FynxDesign.TextSecondary) }
        }
        if (connected) {
            IconButton(onClick = { muted = !muted; engine.setMicrophoneEnabled(!muted) }) { Icon(if (muted) Icons.Default.MicOff else Icons.Default.Mic, if (muted) "Unmute FYNX AI" else "Mute FYNX AI") }
            IconButton(onClick = { engine.close(); connected = false; muted = false; status = "Voice session ended" }) { Icon(Icons.Default.PhoneDisabled, "End FYNX AI voice") }
        } else {
            IconButton(enabled = !connecting, onClick = {
                if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) startHomeVoice(engine, scope, { connecting = it }, { connected = it }, { status = it }, { transcript = it }) else permission.launch(Manifest.permission.RECORD_AUDIO)
            }) { Icon(Icons.Default.Mic, if (connecting) "Connecting" else "Start FYNX AI voice") }
        }
    }
}

@Composable
fun HomeAiVoiceCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember { FynxAiWebRtcEngine(context) }
    var connected by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Tap the microphone and talk naturally") }
    var transcript by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startHomeVoice(engine, scope, { connecting = it }, { connected = it }, { status = it }, { transcript = it })
        else status = "Microphone permission is required for FYNX AI voice."
    }

    DisposableEffect(Unit) { onDispose { engine.close() } }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = FynxDesign.SurfaceRaised),
        shape = FynxDesign.ControlShape
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("FYNX AI Voice", style = MaterialTheme.typography.titleMedium)
                    Text(if (connecting) "Connecting…" else if (connected) "Listening and ready to respond" else status, style = MaterialTheme.typography.bodySmall, color = FynxDesign.TextSecondary)
                }
                IconButton(enabled = !connecting, onClick = {
                    if (connected) {
                        engine.close(); connected = false; muted = false; status = "Voice session ended"; return@IconButton
                    }
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        startHomeVoice(engine, scope, { connecting = it }, { connected = it }, { status = it }, { transcript = it })
                    } else permission.launch(Manifest.permission.RECORD_AUDIO)
                }) {
                    Icon(if (connected) Icons.Default.PhoneDisabled else Icons.Default.Mic, if (connected) "End FYNX AI voice" else "Start FYNX AI voice")
                }
            }
            if (connected) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { muted = !muted; engine.setMicrophoneEnabled(!muted) }, label = { Text(if (muted) "Unmute" else "Mute") }, leadingIcon = { Icon(if (muted) Icons.Default.MicOff else Icons.Default.Mic, null) })
                    AssistChip(onClick = { engine.sendEvent(JSONObject().put("type", "response.create").toString()) }, label = { Text("Ask FYNX to respond") })
                }
            }
            transcript?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
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
