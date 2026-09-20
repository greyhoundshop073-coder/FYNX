package com.fynx.app.ui

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FynxMusicAdminPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tracks by remember { mutableStateOf<List<FynxAdminClient.MusicTrack>>(emptyList()) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("FYNX") }
    var durationMs by remember { mutableStateOf(0L) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val mime = context.contentResolver.getType(uri)?.lowercase().orEmpty()
        if (!mime.startsWith("audio/")) {
            status = "Choose an audio file."
            return@rememberLauncherForActivityResult
        }
        selectedUri = uri
        scope.launch {
            val metadata = withContext(Dispatchers.IO) {
                runCatching {
                    MediaMetadataRetriever().run {
                        setDataSource(context, uri)
                        val readTitle = extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty()
                        val readArtist = extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty()
                        val readDuration = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                        release()
                        Triple(readTitle, readArtist, readDuration)
                    }
                }.getOrNull()
            }
            if (metadata != null) {
                if (title.isBlank()) title = metadata.first.trim().take(120)
                if (artist.isBlank()) artist = metadata.second.trim().take(120)
                durationMs = metadata.third.coerceAtLeast(0L)
            }
            status = "Audio selected. Complete the details, then publish it to FYNX."
        }
    }

    LaunchedEffect(refresh) {
        loading = true
        FynxAdminClient.musicCatalogue(context)
            .onSuccess { tracks = it; status = null }
            .onFailure { status = it.message ?: "Music catalogue could not be loaded." }
        loading = false
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("FYNX Music Library", style = MaterialTheme.typography.titleMedium)
        Text(
            "Only authorized FYNX admins can add or remove catalogue tracks. Users can only select published tracks.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))

        OutlinedButton(enabled = !loading, onClick = { picker.launch(arrayOf("audio/*")) }) {
            Icon(Icons.Default.AudioFile, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(if (selectedUri == null) "Select audio" else "Replace audio")
        }

        OutlinedTextField(title, { title = it.take(120) }, Modifier.fillMaxWidth(), label = { Text("Song title") }, singleLine = true)
        OutlinedTextField(artist, { artist = it.take(120) }, Modifier.fillMaxWidth(), label = { Text("Artist") }, singleLine = true)
        OutlinedTextField(category, { category = it.take(60) }, Modifier.fillMaxWidth(), label = { Text("Category") }, singleLine = true)

        if (selectedUri != null) {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MusicNote, "Selected audio", tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleSmall)
                        Text(artist.ifBlank { "Unknown artist" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { selectedUri = null }) {
                        Icon(Icons.Default.Close, "Clear")
                    }
                }
            }
        }

        Button(
            enabled = selectedUri != null && title.isNotBlank() && !loading,
            onClick = {
                val uri = selectedUri ?: return@Button
                loading = true
                status = "Uploading and publishing music…"
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        FynxProductionMessaging.uploadMedia(context, uri, context.contentResolver.getType(uri) ?: "audio/mpeg")
                    }
                    result.onSuccess { uploaded ->
                        scope.launch {
                            FynxAdminClient.addMusicTrack(context, uploaded.id.toString(), title, artist, durationMs, category)
                                .onSuccess {
                                    status = "Music published to the FYNX catalogue."
                                    selectedUri = null
                                    title = ""
                                    artist = ""
                                    durationMs = 0L
                                    refresh++
                                }
                                .onFailure { status = it.message ?: "Music catalogue publish failed." }
                            loading = false
                        }
                    }.onFailure {
                        status = it.message ?: "Audio upload failed."
                        loading = false
                    }
                }
            }
        ) {
            Text(if (loading) "Working…" else "Publish to FYNX")
        }

        status?.let { Text(it, color = if (it.contains("failed", true) || it.contains("error", true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }

        Text("Published tracks", style = MaterialTheme.typography.titleSmall)
        if (loading && tracks.isEmpty()) {
            CircularProgressIndicator()
        } else if (tracks.isEmpty()) {
            Text("No music has been published yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(tracks, key = { it.id }) { track ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MusicNote, "Music", tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                Text(track.title, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    track.artist + " • " + if (track.active) "Published" else "Hidden",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (track.active) {
                                TextButton(onClick = {
                                    loading = true
                                    scope.launch {
                                        FynxAdminClient.removeMusicTrack(context, track.id)
                                            .onSuccess { status = "Track removed from the user catalogue."; refresh++ }
                                            .onFailure { status = it.message ?: "Track removal failed." }
                                        loading = false
                                    }
                                }) { Text("Remove") }
                            }
                        }
                    }
                }
            }
        }
    }
}
