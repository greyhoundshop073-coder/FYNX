package com.fynx.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
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
    val isVideo: Boolean
)

/** Single Status/Stories surface. */
@Composable
fun FynxStatusHubPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var addStatusOpen by remember { mutableStateOf(false) }
    var composing by remember { mutableStateOf(false) }
    var cameraOpen by remember { mutableStateOf(false) }
    var publishingCameraStatus by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var timelineRefreshKey by remember { mutableIntStateOf(0) }

    fun publishCapturedStatus(uri: Uri, type: String) {
        if (publishingCameraStatus) return
        publishingCameraStatus = true
        cameraError = null
        scope.launch {
            try {
                val auth = FynxAuthStore.load(context)
                val username = auth.username?.removePrefix("@").orEmpty().ifBlank { "preview" }
                val mime = context.contentResolver.getType(uri) ?: if (type == "video") "video/mp4" else "image/jpeg"
                val mediaId = FynxStatusClient.uploadMedia(context, uri, mime)
                    .getOrElse {
                        cameraError = it.message ?: "Status media upload failed."
                        return@launch
                    }
                val statusType = if (type == "video") FynxStatusType.VIDEO else FynxStatusType.PHOTO
                val now = System.currentTimeMillis()
                val status = FynxStatus(
                    id = UUID.randomUUID().toString(),
                    ownerUsername = username,
                    ownerDisplayName = username,
                    type = statusType,
                    text = null,
                    createdAtMillis = now,
                    expiresAtMillis = now + FYNX_STATUS_EXPIRY_MS,
                    textStyle = FynxStatusTextStyle(0xFF111111, 0xFFFFFFFF, FynxStatusTextFont.CLASSIC, 1),
                    privateStatus = true,
                    voiceDurationMs = 0L,
                    audience = FynxStatusAudience.FRIENDS
                )
                FynxStatusClient.create(context, status, mediaId).getOrElse {
                    cameraError = it.message ?: "Status publishing failed."
                    return@launch
                }
                FynxStatusStore.save(context, status.copy(contentUri = "/api/media/$mediaId"))
                cameraOpen = false
                addStatusOpen = false
                timelineRefreshKey++
            } finally {
                publishingCameraStatus = false
            }
        }
    }

    LaunchedEffect(composing, cameraOpen, addStatusOpen) {
        if (!composing && !cameraOpen && !addStatusOpen) timelineRefreshKey++
    }

    BackHandler(enabled = addStatusOpen) { addStatusOpen = false }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            when {
                composing -> FynxMatureStatusComposerPanel(onClose = { composing = false })
                addStatusOpen -> FynxAddStatusPanel(
                    onClose = { addStatusOpen = false },
                    onCamera = {
                        if (!publishingCameraStatus) {
                            cameraError = null
                            cameraOpen = true
                        }
                    },
                    onText = { composing = true; addStatusOpen = false },
                    onVoice = { composing = true; addStatusOpen = false },
                    onMusic = { },
                    onLayout = { }
                )
                else -> key(timelineRefreshKey) {
                    // FynxStatusTimelinePanel() remains the single backend Status hub surface.
                    FynxStatusTimelinePanel(
                        onCameraClick = {
                            if (!publishingCameraStatus) {
                                cameraError = null
                                cameraOpen = true
                            }
                        },
                        onCreateClick = { addStatusOpen = true }
                    )
                }
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
            onDismissRequest = { if (!publishingCameraStatus) cameraOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    FynxCameraCapturePanel(
                        onCaptured = { uri, type -> publishCapturedStatus(uri, type) },
                        onDismiss = { if (!publishingCameraStatus) cameraOpen = false }
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
    onLayout: () -> Unit
) {
    val context = LocalContext.current
    var mediaPermissionGranted by remember {
        mutableStateOf(fynxHasMediaPermission(context))
    }
    var recentMedia by remember { mutableStateOf<List<FynxRecentMedia>>(emptyList()) }
    var loadingMedia by remember { mutableStateOf(false) }
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FynxStatusToolPill(Icons.Default.Notes, "Text", onText)
            FynxStatusToolPill(Icons.Default.MusicNote, "Music", onMusic)
            FynxStatusToolPill(Icons.Default.GridView, "Layout", onLayout)
            FynxStatusToolPill(Icons.Default.Mic, "Voice", onVoice)
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
                    FynxCameraGridCell(onClick = onCamera)
                }
                items(recentMedia, key = { it.uri.toString() }) { media ->
                    FynxRecentMediaCell(media)
                }
            }
        }
    }

    FloatingActionButton(
        onClick = { },
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .navigationBarsPadding()
            .padding(end = 18.dp, bottom = 72.dp)
            .size(48.dp),
        containerColor = MaterialTheme.colorScheme.primary
    ) {
        Icon(Icons.Default.SelectAll, contentDescription = "Select multiple media")
    }
    }
}

@Composable
private fun FynxStatusToolPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        modifier = Modifier.height(42.dp)
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
private fun FynxRecentMediaCell(media: FynxRecentMedia) {
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
            .background(MaterialTheme.colorScheme.surfaceVariant),
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
                false
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
                true
            )
        }
    }

    return (result + videos).distinctBy { it.uri.toString() }.take(60)
}
