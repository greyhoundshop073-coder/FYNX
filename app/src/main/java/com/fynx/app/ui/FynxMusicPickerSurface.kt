package com.fynx.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Shared glass-style official music browser used by Home and Status. */
@Composable
internal fun FynxMusicPickerSurface(
    tracks: List<FynxMusicCatalogueTrack>,
    search: String,
    loading: Boolean,
    error: String?,
    previewingTrackId: Long?,
    onSearchChange: (String) -> Unit,
    onPreview: (FynxMusicCatalogueTrack) -> Unit,
    onStopPreview: () -> Unit,
    onSelect: (FynxMusicCatalogueTrack) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val azure = Color(0xFF007FFF)
    val ink = Color(0xFF172B45)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color(0x66071324)).padding(horizontal = 12.dp, vertical = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().fillMaxSize(0.96f).shadow(24.dp, RoundedCornerShape(30.dp)),
                shape = RoundedCornerShape(30.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.9f))
            ) {
                Column(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(Color(0xF7FFFFFF), Color(0xF0F1F8FF), Color(0xF5FFFFFF)))
                    ).padding(horizontal = 18.dp, vertical = 16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(48.dp).clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Color(0xFF007FFF), Color(0xFF65B5FF))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = Color.White, modifier = Modifier.size(25.dp))
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text("Choose your music", color = ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Official FYNX sounds • Preview before adding", color = Color(0xFF61748B), style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close music picker", tint = ink) }
                    }
                    Spacer(Modifier.height(18.dp))
                    OutlinedTextField(
                        value = search,
                        onValueChange = { onSearchChange(it.take(80)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = azure) },
                        placeholder = { Text("Search song or artist") },
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = azure,
                            unfocusedBorderColor = Color(0xFFD5E4F2),
                            focusedContainerColor = Color.White.copy(alpha = 0.86f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.72f)
                        )
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("FYNX MUSIC LIBRARY", color = Color(0xFF58708B), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        if (!loading && error == null) Text(tracks.size.toString() + " tracks", color = Color(0xFF71849A), style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    when {
                        loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                CircularProgressIndicator(color = azure)
                                Text("Loading the music library…", color = Color(0xFF61748B))
                            }
                        }
                        error != null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(error, color = Color(0xFF52677E), style = MaterialTheme.typography.bodyMedium)
                                if (!error.contains("Sign in", ignoreCase = true)) {
                                    TextButton(onClick = onRetry) {
                                        Icon(Icons.Default.Refresh, contentDescription = null)
                                        Text(" Try again")
                                    }
                                }
                            }
                        }
                        tracks.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFF8BA7C2), modifier = Modifier.size(38.dp))
                                Text(if (search.isBlank()) "No official tracks have been published yet." else "No songs match that search.", color = Color(0xFF52677E))
                                Text("Try another title or artist.", color = Color(0xFF8293A6), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        else -> LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            items(tracks, key = { it.id }) { track ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(20.dp),
                                    border = BorderStroke(1.dp, Color(0xFFDCE9F5)),
                                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.82f))
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 11.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            Modifier.size(46.dp).clip(RoundedCornerShape(15.dp))
                                                .background(Brush.linearGradient(listOf(Color(0xFFDCEEFF), Color(0xFFB9DCFF))),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = azure, modifier = Modifier.size(25.dp))
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Text(track.title.ifBlank { "Untitled track" }, color = ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(track.artist.ifBlank { "FYNX" }, color = Color(0xFF687D93), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(formatPickerDuration(track.durationMs), color = Color(0xFF8A9AAD), style = MaterialTheme.typography.labelSmall)
                                        }
                                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                            if (previewingTrackId == track.id) {
                                                IconButton(onClick = onStopPreview, modifier = Modifier.size(34.dp)) {
                                                    Icon(Icons.Default.Stop, contentDescription = "Stop preview", tint = Color(0xFF536D87))
                                                }
                                            } else {
                                                IconButton(onClick = { onPreview(track) }, modifier = Modifier.size(34.dp)) {
                                                    Icon(Icons.Default.PlayArrow, contentDescription = "Preview " + track.title, tint = azure)
                                                }
                                            }
                                            Button(
                                                onClick = { onSelect(track) },
                                                shape = RoundedCornerShape(13.dp),
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 13.dp, vertical = 6.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = azure, contentColor = Color.White)
                                            ) { Text("Use", style = MaterialTheme.typography.labelLarge) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (tracks.isNotEmpty() && !loading && error == null) {
                        Spacer(Modifier.height(10.dp))
                        Text("Select a track to attach it to your post or Status.", color = Color(0xFF71849A), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private fun formatPickerDuration(durationMs: Long): String {
    val seconds = durationMs.coerceAtLeast(0L) / 1000L
    return (seconds / 60L).toString() + ":" + (seconds % 60L).toString().padStart(2, '0')
}
