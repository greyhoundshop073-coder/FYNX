package com.fynx.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

private data class FynxCallItem(val name: String, val type: String, val time: String, val missed: Boolean)

@Composable
fun FynxCallsPanel() {
    var activeCall by remember { mutableStateOf<String?>(null) }
    var video by remember { mutableStateOf(false) }
    val calls = remember {
        listOf(
            FynxCallItem("Maria", "Voice call", "Today, 10:32", false),
            FynxCallItem("Alex", "Video call", "Yesterday, 18:41", true),
            FynxCallItem("David", "Voice call", "Monday, 09:18", false)
        )
    }
    if (activeCall != null) {
        FynxActiveCallPanel(name = activeCall!!, video = video, onEnd = { activeCall = null })
        return
    }
    Column(Modifier.fillMaxSize().background(FynxDesign.Background).padding(16.dp)) {
        Text("Calls", style = MaterialTheme.typography.headlineSmall)
        Text("Voice and video calls", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(calls) { call ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                            Icon(if (call.type == "Video call") Icons.Default.Videocam else Icons.Default.Call, "Call type", tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(call.name, style = MaterialTheme.typography.titleMedium)
                            Text("${call.type} • ${call.time}", style = MaterialTheme.typography.bodySmall, color = if (call.missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { video = call.type == "Video call"; activeCall = call.name }) { Icon(if (call.type == "Video call") Icons.Default.Videocam else Icons.Default.Call, "Call ${call.name}") }
                    }
                }
            }
        }
    }
}

@Composable
fun FynxActiveCallPanel(name: String, video: Boolean, onEnd: () -> Unit) {
    var muted by remember { mutableStateOf(false) }
    var cameraOn by remember { mutableStateOf(video) }
    Column(Modifier.fillMaxSize().background(FynxDesign.Background), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(44.dp))
        Text(name, style = MaterialTheme.typography.headlineSmall)
        Text(if (video) "Video call" else "Voice call", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(30.dp))
        Box(Modifier.size(190.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Text(name.take(1).uppercase(), style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { muted = !muted }) { Icon(if (muted) Icons.Default.MicOff else Icons.Default.Mic, "Mute") }
            if (video) FilledTonalIconButton(onClick = { cameraOn = !cameraOn }) { Icon(if (cameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff, "Camera") }
            FloatingActionButton(onClick = onEnd, containerColor = MaterialTheme.colorScheme.error) { Icon(Icons.Default.CallEnd, "End call") }
        }
        Spacer(Modifier.height(32.dp))
    }
}
