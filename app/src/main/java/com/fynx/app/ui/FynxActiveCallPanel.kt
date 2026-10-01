package com.fynx.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.webrtc.VideoTrack

@Composable
fun FynxActiveCallPanel(
    name: String,
    session: FynxCallSession,
    realtimeState: FynxRealtimeClient.State = FynxRealtimeClient.State.CONNECTED,
    localVideoTrack: VideoTrack? = null,
    remoteVideoTrack: VideoTrack? = null,
    onAnswer: () -> Unit,
    onRetry: () -> Unit,
    onToggleMicrophone: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onEnd: () -> Unit
) {
    val incoming = session.state == FynxCallState.RINGING
    val connected = session.state == FynxCallState.CONNECTED
    val video = session.type == FynxCallType.VIDEO
    val callStatus = when (session.state) {
        FynxCallState.IDLE -> "Ready"
        FynxCallState.RINGING -> "Incoming ${if (video) "video" else "voice"} call"
        FynxCallState.CONNECTING -> if (realtimeState == FynxRealtimeClient.State.CONNECTED) "Connecting…" else "Reconnecting…"
        FynxCallState.CONNECTED -> "${if (video) "Video" else "Voice"} call"
        FynxCallState.ENDED -> "Call ended"
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(FynxDesign.Background)
            .safeDrawingPadding()
            .imePadding()
    ) {
        if (video && connected) {
            Box(Modifier.fillMaxSize()) {
                if (remoteVideoTrack != null) {
                    FynxCallVideoSurface(remoteVideoTrack, Modifier.fillMaxSize(), mirror = false)
                } else {
                    FynxCallRemotePlaceholder(name, Modifier.fillMaxSize())
                }

                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 12.dp, end = 12.dp)
                        .size(width = 112.dp, height = 158.dp)
                        .clip(RoundedCornerShape(14.dp))
                ) {
                    if (localVideoTrack != null && session.cameraEnabled) {
                        FynxCallVideoSurface(localVideoTrack, Modifier.fillMaxSize(), mirror = true)
                    } else {
                        FynxCallRemotePlaceholder("You", Modifier.fillMaxSize())
                    }
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(52.dp))
                Box(
                    Modifier
                        .size(108.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(name.take(1).uppercase(), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(18.dp))
                Text(name, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                Text(callStatus, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (video && !incoming) {
                    Spacer(Modifier.height(28.dp))
                    Text("Your camera is ready", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (video && connected) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(name, style = MaterialTheme.typography.titleMedium)
                    Text(callStatus, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (incoming) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("${if (video) "Video" else "Voice"} call from $name", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FynxCallActionButton(onClick = onEnd, icon = Icons.Default.CallEnd, label = "Decline", destructive = true)
                    FynxCallActionButton(onClick = onAnswer, icon = Icons.Default.Call, label = "Answer")
                }
            }
        } else if (!connected) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(callStatus, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(14.dp))
                if (session.state == FynxCallState.CONNECTING) {
                    FynxCallActionButton(onClick = onEnd, icon = Icons.Default.CallEnd, label = "Cancel", destructive = true)
                }
            }
        } else {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 8.dp
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FynxCallActionButton(
                            onClick = onToggleMicrophone,
                            icon = if (session.microphoneEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                            label = if (session.microphoneEnabled) "Mute" else "Unmute"
                        )
                        if (video) {
                            FynxCallActionButton(
                                onClick = onToggleCamera,
                                icon = if (session.cameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                label = if (session.cameraEnabled) "Camera" else "Camera off"
                            )
                            FynxCallActionButton(onClick = onSwitchCamera, icon = Icons.Default.Cameraswitch, label = "Flip")
                        }
                        FynxCallActionButton(
                            onClick = onToggleSpeaker,
                            icon = if (session.speakerEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            label = if (session.speakerEnabled) "Speaker" else "Earpiece"
                        )
                        FynxCallActionButton(onClick = onEnd, icon = Icons.Default.CallEnd, label = "End", destructive = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun FynxCallRemotePlaceholder(name: String, modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(116.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Text(name.take(1).uppercase(), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(10.dp))
            Text("Waiting for video…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FynxCallActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    destructive: Boolean = false
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.size(58.dp),
            colors = if (destructive) {
                IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            } else {
                IconButtonDefaults.filledTonalIconButtonColors()
            }
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(25.dp))
        }
        Spacer(Modifier.height(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
