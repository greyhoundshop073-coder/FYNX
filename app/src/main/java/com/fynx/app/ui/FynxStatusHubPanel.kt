package com.fynx.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import java.io.File
import java.io.FileOutputStream
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import java.util.UUID

private data class FynxRecentMedia(
    val uri: Uri,
    val isVideo: Boolean,
    val dateAddedSeconds: Long
)

/** Single Status/Stories surface. */
@Composable
fun FynxStatusHubPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var addStatusOpen by remember { mutableStateOf(false) }
    var composing by remember { mutableStateOf(false) }
    var cameraOpen by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var timelineRefreshKey by remember { mutableIntStateOf(0) }
    var selectedInitialMedia by remember { mutableStateOf<FynxRecentMedia?>(null) }
    var selectedStatusMusic by remember { mutableStateOf<FynxMusicCatalogueTrack?>(null) }
    var showStatusMusicPicker by remember { mutableStateOf(false) }
    var statusMusicSearch by remember { mutableStateOf("") }
    var statusMusicCatalogue by remember { mutableStateOf<List<FynxMusicCatalogueTrack>>(emptyList()) }
    var statusMusicLoading by remember { mutableStateOf(false) }

    fun openCapturedStatus(uri: Uri, type: String) {
        selectedInitialMedia = FynxRecentMedia(uri = uri, isVideo = type == "video", dateAddedSeconds = System.currentTimeMillis() / 1000L)
        cameraError = null
        cameraOpen = false
        addStatusOpen = false
        composing = true
    }

    LaunchedEffect(showStatusMusicPicker, statusMusicSearch) {
        if (showStatusMusicPicker) {
            statusMusicLoading = true
            FynxMusicCatalogueClient.listPublished(context, statusMusicSearch)
                .onSuccess { statusMusicCatalogue = it }
                .onFailure { cameraError = it.message ?: "Music catalogue could not be loaded." }
            statusMusicLoading = false
        }
    }

    LaunchedEffect(composing, cameraOpen, addStatusOpen) {
        if (!composing && !cameraOpen && !addStatusOpen) timelineRefreshKey++
    }

    BackHandler(enabled = addStatusOpen) { addStatusOpen = false }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            when {
                composing -> FynxMatureStatusComposerPanel(
                    initialMediaUri = selectedInitialMedia?.uri,
                    initialType = selectedInitialMedia?.let { if (it.isVideo) FynxStatusType.VIDEO else FynxStatusType.PHOTO },
                    initialMusic = selectedStatusMusic,
                    onClose = { selectedInitialMedia = null; selectedStatusMusic = null; composing = false }
                )
                addStatusOpen -> FynxAddStatusPanel(
                    onClose = { addStatusOpen = false },
                    onCamera = {
                        if (!cameraOpen) {
                            cameraError = null
                            cameraOpen = true
                        }
                    },
                    onText = { selectedInitialMedia = null; composing = true; addStatusOpen = false },
                    onVoice = { selectedInitialMedia = null; composing = true; addStatusOpen = false },
                    onMusic = { showStatusMusicPicker = true },
                    onMediaSelected = { media ->
                        selectedInitialMedia = media
                        composing = true
                        addStatusOpen = false
                    }
                )
                else -> key(timelineRefreshKey) {
                    // FynxStatusTimelinePanel() remains the single backend Status hub surface.
                    FynxStatusTimelinePanel(
                        onCameraClick = {
                            if (!cameraOpen) {
                                cameraError = null
                                cameraOpen = true
                            }
                        },
                        onCreateClick = { addStatusOpen = true }
                    )
                }
            }
            if (showStatusMusicPicker) {
                AlertDialog(
                    onDismissRequest = { showStatusMusicPicker = false; statusMusicSearch = "" },
                    title = { Text("FYNX Music") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = statusMusicSearch,
                                onValueChange = { statusMusicSearch = it.take(80) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Search music") },
                                placeholder = { Text("Song or artist") }
                            )
                            Column(
                                Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (statusMusicLoading) {
                                    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                                } else if (statusMusicCatalogue.isEmpty()) {
                                    Text("No FYNX music is published yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    statusMusicCatalogue.forEach { track ->
                                        TextButton(
                                            onClick = {
                                                selectedStatusMusic = track
                                                showStatusMusicPicker = false
                                                statusMusicSearch = ""
                                                addStatusOpen = false
                                                composing = true
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Icon(Icons.Default.MusicNote, "Music", tint = MaterialTheme.colorScheme.primary)
                                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                                                    Text(track.title.ifBlank { "Untitled" }, maxLines = 1)
                                                    Text(track.artist.ifBlank { "FYNX" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                                }
                                                Text(track.durationMs.div(60000).toString() + ":" + ((track.durationMs.div(1000) % 60).toString().padStart(2, '0')), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showStatusMusicPicker = false; statusMusicSearch = "" }) { Text("Close") }
                    }
                )
            }

            cameraError?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 102.dp, start = 18.dp, end = 18.dp)
                )
            }
        }
    }

    if (cameraOpen) {
        Dialog(
            onDismissRequest = { cameraOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize()) {
                    FynxCameraCapturePanel(
                        onCaptured = { uri, type -> openCapturedStatus(uri, type) },
                        onDismiss = { cameraOpen = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun FynxAddStatusPanel(
    onClose: () -> Unit,
    onCamera: () -> Unit,
    onText: () -> Unit,
    onVoice: () -> Unit,
    onMusic: () -> Unit,
    onMediaSelected: (FynxRecentMedia) -> Unit
) {
    val context = LocalContext.current
    var mediaPermissionGranted by remember {
        mutableStateOf(fynxHasMediaPermission(context))
    }
    var recentMedia by remember { mutableStateOf<List<FynxRecentMedia>>(emptyList()) }
    var loadingMedia by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedMediaUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    var layoutMode by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        mediaPermissionGranted = fynxHasMediaPermission(context)
    }

    fun refreshMedia() {
        if (!mediaPermissionGranted) return
        loadingMedia = true
        recentMedia = loadFynxRecentMedia(context)
        loadingMedia = false
    }

    fun toggleSelected(media: FynxRecentMedia) { val key = media.uri.toString(); selectedMediaUris = if (key in selectedMediaUris) selectedMediaUris - key else selectedMediaUris + key }
    fun finishSelection() {
        val selected = recentMedia.filter { it.uri.toString() in selectedMediaUris }.take(4)
        if (selected.isEmpty()) return
        if (layoutMode && selected.size > 1) {
            createFynxLayoutCollage(context, selected)?.let { onMediaSelected(FynxRecentMedia(it, false, System.currentTimeMillis() / 1000L)) }
        } else onMediaSelected(selected.first())
        selectedMediaUris = emptySet()
        selectionMode = false
        layoutMode = false
    }

    LaunchedEffect(mediaPermissionGranted) {
        if (mediaPermissionGranted) refreshMedia()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close Add status")
            }
            Text(
                "Add status",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.width(48.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FynxStatusToolPill(
                    icon = Icons.Default.Notes,
                    label = "Text",
                    onClick = onText,
                    modifier = Modifier.weight(1f)
                )
                FynxStatusToolPill(
                    icon = Icons.Default.MusicNote,
                    label = "Music",
                    onClick = onMusic,
                    modifier = Modifier.weight(1f)
                )
                FynxStatusToolPill(
                    icon = Icons.Default.GridView,
                    label = "Layout",
                    onClick = { layoutMode = true; selectionMode = true; selectedMediaUris = emptySet() },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                FynxStatusToolPill(
                    icon = Icons.Default.Mic,
                    label = "Voice",
                    onClick = onVoice,
                    modifier = Modifier.widthIn(min = 120.dp, max = 180.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Recents",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
            Spacer(Modifier.width(4.dp))
            Text("▼", style = MaterialTheme.typography.labelMedium)
        }

        if (!mediaPermissionGranted) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "Allow FYNX to access your photos and videos",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        permissionLauncher.launch(fynxMediaPermissions())
                    }
                ) {
                    Text("Allow media access")
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = rememberLazyGridState(),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 86.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                item(span = { GridItemSpan(1) }) {
                    FynxCameraGridCell(onClick = { if (!selectionMode) onCamera() })
                }
                items(recentMedia, key = { it.uri.toString() }) { media ->
                    val selected = media.uri.toString() in selectedMediaUris
                    FynxRecentMediaCell(media, selected = selected, selectionMode = selectionMode, onClick = { if (selectionMode) toggleSelected(media) else onMediaSelected(media) })
                }
            }
        }
    }

    FloatingActionButton(
        onClick = { selectionMode = !selectionMode; layoutMode = false; if (!selectionMode) selectedMediaUris = emptySet() },
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .navigationBarsPadding()
            .padding(end = 18.dp, bottom = 72.dp)
            .size(48.dp),
        containerColor = MaterialTheme.colorScheme.primary
    ) {
        Icon(Icons.Default.SelectAll, contentDescription = if (selectionMode) "Cancel media selection" else "Select multiple media")
    }
    if (selectionMode) { Surface(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding(), tonalElevation = 6.dp, color = MaterialTheme.colorScheme.surface) { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { Text("${selectedMediaUris.size} selected", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge); TextButton(onClick = { selectedMediaUris = emptySet(); selectionMode = false }) { Text("Cancel") }; Button(onClick = ::finishSelection, enabled = selectedMediaUris.isNotEmpty()) { Text(if (layoutMode) "Create layout" else "Next") } } } }
    }
}

private fun createFynxLayoutCollage(context: android.content.Context, media: List<FynxRecentMedia>): Uri? {
    if (media.isEmpty()) return null
    val thumbs = media.take(4).mapNotNull { item ->
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 29) context.contentResolver.loadThumbnail(item.uri, Size(600, 600), null)
            else context.contentResolver.openInputStream(item.uri)?.use { android.graphics.BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }
    if (thumbs.isEmpty()) return null
    val out = Bitmap.createBitmap(1200, 1200, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    canvas.drawColor(android.graphics.Color.BLACK)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val columns = if (thumbs.size == 1) 1 else 2
    val rows = when (thumbs.size) { 1 -> 1; 2 -> 1; else -> 2 }
    val cellW = 1200f / columns
    val cellH = 1200f / rows
    thumbs.forEachIndexed { index, bitmap ->
        val left = (index % columns) * cellW
        val top = (index / columns) * cellH
        val scale = maxOf(cellW / bitmap.width, cellH / bitmap.height)
        val srcW = (cellW / scale).toInt().coerceAtMost(bitmap.width)
        val srcH = (cellH / scale).toInt().coerceAtMost(bitmap.height)
        val srcLeft = ((bitmap.width - srcW) / 2).coerceAtLeast(0)
        val srcTop = ((bitmap.height - srcH) / 2).coerceAtLeast(0)
        canvas.drawBitmap(bitmap, android.graphics.Rect(srcLeft, srcTop, srcLeft + srcW, srcTop + srcH), android.graphics.RectF(left, top, left + cellW, top + cellH), paint)
        bitmap.recycle()
    }
    return runCatching {
        val file = File(context.cacheDir, "fynx_status_layout_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { check(out.compress(Bitmap.CompressFormat.JPEG, 92, it)) }
        out.recycle()
        Uri.fromFile(file)
    }.getOrElse { out.recycle(); null }
}

@Composable
private fun FynxStatusToolPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        modifier = modifier.height(42.dp)
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}

@Composable
private fun FynxCameraGridCell(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.CameraAlt, contentDescription = "Camera", modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(4.dp))
            Text("Camera", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun FynxRecentMediaCell(media: FynxRecentMedia, selected: Boolean, selectionMode: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember(media.uri) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(media.uri, context) {
        bitmap = runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                contextLoadThumbnail(context, media.uri)
            } else {
                null
            }
        }.getOrNull()
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = if (media.isVideo) "Video" else "Photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        if (selectionMode && selected) { Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) { Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) { Text("✓", color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)) } } }
        if (media.isVideo) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp),
                shape = CircleShape,
                color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.65f)
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Video",
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.padding(5.dp).size(14.dp)
                )
            }
        }
    }
}

@androidx.annotation.RequiresApi(29)
private fun contextLoadThumbnail(context: android.content.Context, uri: Uri): android.graphics.Bitmap? {
    return if (android.os.Build.VERSION.SDK_INT >= 29) {
        context.contentResolver.loadThumbnail(uri, Size(360, 360), null)
    } else {
        null
    }
}

private fun fynxHasMediaPermission(context: android.content.Context): Boolean {
    return if (android.os.Build.VERSION.SDK_INT >= 33) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
}

private fun fynxMediaPermissions(): Array<String> {
    return if (android.os.Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

private fun loadFynxRecentMedia(context: android.content.Context): List<FynxRecentMedia> {
    val result = mutableListOf<FynxRecentMedia>()
    val imageProjection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED)
    context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        imageProjection,
        null,
        null,
        MediaStore.Images.Media.DATE_ADDED + " DESC"
    )?.use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        while (cursor.moveToNext() && result.size < 30) {
            val id = cursor.getLong(idIndex)
            result += FynxRecentMedia(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI.buildUpon().appendPath(id.toString()).build(),
                false,
                cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED))
            )
        }
    }

    val videos = mutableListOf<FynxRecentMedia>()
    val videoProjection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DATE_ADDED)
    context.contentResolver.query(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        videoProjection,
        null,
        null,
        MediaStore.Video.Media.DATE_ADDED + " DESC"
    )?.use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        while (cursor.moveToNext() && videos.size < 30) {
            val id = cursor.getLong(idIndex)
            videos += FynxRecentMedia(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI.buildUpon().appendPath(id.toString()).build(),
                true,
                cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED))
            )
        }
    }

    return (result + videos)
        .distinctBy { it.uri.toString() }
        .sortedByDescending { it.dateAddedSeconds }
        .take(60)
}
