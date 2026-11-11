package com.fynx.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Small hand-off bridge from the feed's song destination to the Home composer.
 * The current feed contract contains a media ID, not a catalogue ID. The existing
 * create-post API accepts that media ID; the placeholder catalogue ID is never
 * persisted or used as a catalogue identity.
 */
internal object FynxMusicSongNavigation {
    var onUseTrack: ((Long, String, String, Long) -> Unit)? = null

    fun useTrack(mediaId: Long, title: String, artist: String, durationMs: Long): Boolean {
        if (mediaId < 1L) return false
        val handler = onUseTrack ?: return false
        handler(mediaId, title, artist, durationMs)
        return true
    }
}

/** Song destination for a soundtrack already attached to a Home post. */
@Composable
internal fun FynxMusicSongDetailSurface(
    mediaId: String,
    title: String,
    artist: String,
    durationMs: Long,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val previewOwnerKey = remember(mediaId) { "song-detail:$mediaId" }
    val numericMediaId = remember(mediaId) { mediaId.toLongOrNull()?.takeIf { it > 0L } }
    var audioFile by remember(mediaId) { mutableStateOf<java.io.File?>(null) }
    var audioLoading by remember(mediaId) { mutableStateOf(false) }
    var audioLoadFailed by remember(mediaId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Do not download audio merely because a user opened the song destination.
    // Load it only after an explicit Preview tap to protect mobile data and feed cache.
    fun loadAndPreview() {
        if (audioLoading || numericMediaId == null) return
        val cached = audioFile
        if (cached != null) {
            FynxCatalogueMusicPlayback.toggle(context, mediaId, cached, previewOwnerKey)
            return
        }
        scope.launch {
            audioLoading = true
            audioLoadFailed = false
            val downloaded = withContext(Dispatchers.IO) {
                runCatching {
                    FynxMediaCache.getOrDownload(context, "/api/social/music/media/$mediaId", "audio")
                }.getOrNull()
            }
            audioFile = downloaded
            audioLoadFailed = downloaded == null
            audioLoading = false
            downloaded?.let { FynxCatalogueMusicPlayback.toggle(context, mediaId, it, previewOwnerKey) }
        }
    }

    DisposableEffect(previewOwnerKey) {
        onDispose { FynxCatalogueMusicPlayback.pause(previewOwnerKey) }
    }

    val playback = FynxCatalogueMusicPlayback.snapshot
    val isCurrentTrack = playback.trackKey == mediaId && playback.ownerKey == previewOwnerKey
    val isPlaying = isCurrentTrack && playback.isPlaying
    val isPreparing = isCurrentTrack && playback.isPreparing
    val canUseTrack = numericMediaId != null && FynxMusicSongNavigation.onUseTrack != null

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            shape = RoundedCornerShape(30.dp),
            color = Color(0xFFF7FBFF),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "FYNX SOUND",
                        modifier = Modifier.weight(1f),
                        color = Color(0xFF52708D),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close song details")
                    }
                }

                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF007FFF), Color(0xFF76BEFF), Color(0xFFDCEEFF))
                            )
                        )
                        .padding(vertical = 34.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier.size(104.dp).clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.88f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = "Official FYNX music",
                            tint = Color(0xFF007FFF),
                            modifier = Modifier.size(54.dp)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        title.ifBlank { "FYNX music track" },
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color(0xFF172B45),
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        artist.ifBlank { "FYNX" },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF61748B)
                    )
                    Text(
                        formatMusicDuration(durationMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF8293A6)
                    )
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("More from this sound", fontWeight = FontWeight.SemiBold, color = Color(0xFF172B45))
                        Text(
                            "Public creations will appear here when FYNX's catalogue-linked discovery service is available.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF687D93)
                        )
                    }
                }

                if (audioLoadFailed) {
                    Text(
                        "Preview could not be loaded. Check your connection and tap Preview to retry.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { loadAndPreview() },
                        enabled = numericMediaId != null && !audioLoading && !isPreparing,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(15.dp)
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            when {
                                audioLoading -> "Loading preview…"
                                isPreparing -> "Preparing…"
                                isPlaying -> "Pause preview"
                                else -> "Preview"
                            }
                        )
                    }
                    Button(
                        onClick = {
                            if (numericMediaId != null &&
                                FynxMusicSongNavigation.useTrack(
                                    numericMediaId,
                                    title,
                                    artist,
                                    durationMs
                                )
                            ) onDismiss()
                        },
                        enabled = canUseTrack,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF007FFF),
                            contentColor = Color.White
                        )
                    ) {
                        Text("Use this song")
                    }
                }
            }
        }
    }
}

private fun formatMusicDuration(durationMs: Long): String {
    val seconds = durationMs.coerceAtLeast(0L) / 1000L
    return (seconds / 60L).toString() + ":" + (seconds % 60L).toString().padStart(2, '0')
}
