package com.fynx.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraEffect
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.core.MirrorMode
import androidx.camera.view.PreviewView
import androidx.camera.media3.effect.Media3Effect
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(UnstableApi::class)
@Composable
fun FynxCameraCapturePanel(
    onCaptured: (Uri, String) -> Unit,
    fastCapture: Boolean = false,
    initialMode: CameraMode = CameraMode.PHOTO,
    videoNoteMode: Boolean = false,
    liveProofMode: Boolean = false,
    liveProofListingTitle: String = "",
    liveProofSellerUsername: String = "",
    onLiveProofStart: () -> Unit = {},
    onDismiss: () -> Unit = {},
    statusCapture: Boolean = false,
    initialMusic: FynxMusicCatalogueTrack? = null,
    onMusicSelected: (FynxMusicCatalogueTrack) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE; scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val scope = rememberCoroutineScope()
    var profilePhotoId by remember { mutableStateOf<String?>(null) }
    var profileDisplayName by remember { mutableStateOf("") }
    val session = FynxAuthStore.load(context)
    LaunchedEffect(session.username) {
        val username = session.username?.removePrefix("@").orEmpty()
        if (username.isNotBlank()) {
            FynxProfileRemoteClient.get(context, username).onSuccess { profile ->
                profilePhotoId = profile.profilePhotoMediaId
                profileDisplayName = profile.displayName.ifBlank { username }
            }
        }
    }
    var hasCamera by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var hasAudio by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        hasCamera = result[Manifest.permission.CAMERA] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        hasAudio = result[Manifest.permission.RECORD_AUDIO] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasAudio = granted || ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    LaunchedEffect(Unit) { if (!hasCamera) permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA)) }

    var lens by remember { mutableStateOf(CameraSelector.LENS_FACING_FRONT) }
    var mode by remember(initialMode, videoNoteMode) { mutableStateOf(if (videoNoteMode) CameraMode.VIDEO else initialMode) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }
    var cameraInfo by remember { mutableStateOf<androidx.camera.core.CameraInfo?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var liveCameraEffect by remember { mutableStateOf<Media3Effect?>(null) }
    var bindGeneration by remember { mutableIntStateOf(0) }
    var torchEnabled by remember { mutableStateOf(false) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var exposure by remember { mutableIntStateOf(0) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var recordingStartedAt by remember { mutableLongStateOf(0L) }
    var recordingElapsed by remember { mutableLongStateOf(0L) }
    var activeRecordingFile by remember { mutableStateOf<File?>(null) }
    var recordingNotice by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var pendingOriginalUri by remember { mutableStateOf<Uri?>(null) }
    var pendingType by remember { mutableStateOf<String?>(null) }
    var preparingPhoto by remember { mutableStateOf(false) }
    var drawingMode by remember { mutableStateOf(false) }
    BackHandler { if (drawingMode) drawingMode = false else if (recording == null && !preparingPhoto) onDismiss() }
    var filter by remember { mutableStateOf(CameraFilter.NATURAL) }
    var filterIntensity by remember { mutableFloatStateOf(1f) }
    var photoTextOverlay by remember { mutableStateOf("") }
    var selectedPhotoSticker by remember { mutableStateOf<String?>(null) }
    var showPhotoTextDialog by remember { mutableStateOf(false) }
    var photoEditorTab by remember { mutableStateOf("Filters") }
    var selectedDrawingColorArgb by remember { mutableIntStateOf(0xFFFFD54F.toInt()) }
    var drawingStrokes by remember { mutableStateOf<List<FynxCameraDrawStroke>>(emptyList()) }
    var activeDrawingPoints by remember { mutableStateOf<List<FynxCameraDrawPoint>>(emptyList()) }
    var photoPixelWidth by remember { mutableIntStateOf(0) }
    var photoPixelHeight by remember { mutableIntStateOf(0) }
    var photoGeometryRevision by remember { mutableIntStateOf(0) }
    var videoDurationMs by remember { mutableLongStateOf(0L) }
    var videoTrimStartMs by remember { mutableLongStateOf(0L) }
    var videoTrimEndMs by remember { mutableLongStateOf(0L) }
    var muteVideoAudio by remember { mutableStateOf(false) }
    var enhancing by remember { mutableStateOf(false) }
    var showCaptureControls by remember { mutableStateOf(true) }
    var showProControls by remember { mutableStateOf(false) }
    var showTimerControls by remember { mutableStateOf(false) }
    var captureTimerSeconds by remember { mutableIntStateOf(0) }
    var timerCountingDown by remember { mutableIntStateOf(0) }
    var showGrid by remember { mutableStateOf(false) }
    var selectedVideoDurationMs by remember(fastCapture) {
        mutableLongStateOf(if (fastCapture) FynxCameraRecordingDuration.FIFTEEN_SECONDS_MS else FynxCameraRecordingDuration.SIXTY_SECONDS_MS)
    }
    var showEffectCarousel by remember { mutableStateOf(true) }
    var captureStyleFilter by remember { mutableStateOf(CameraFilter.NATURAL) }
    var galleryImporting by remember { mutableStateOf(false) }
    var showCameraMusicPicker by remember { mutableStateOf(false) }
    var cameraMusicSearch by remember { mutableStateOf("") }
    var cameraMusicTracks by remember { mutableStateOf<List<FynxMusicCatalogueTrack>>(emptyList()) }
    var cameraMusicLoading by remember { mutableStateOf(false) }
    var cameraMusicError by remember { mutableStateOf<String?>(null) }
    var cameraMusicRetryKey by remember { mutableIntStateOf(0) }
    var cameraMusicPreviewId by remember { mutableStateOf<Long?>(null) }
    var selectedCameraMusic by remember(initialMusic) { mutableStateOf(initialMusic) }

    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { selectedUri ->
        if (selectedUri != null) {
            galleryImporting = true
            scope.launch {
                val imported = withContext(Dispatchers.IO) {
                    runCatching {
                        val mime = context.contentResolver.getType(selectedUri).orEmpty().lowercase()
                        val mediaType = when {
                            mime.startsWith("image/") -> "image"
                            mime.startsWith("video/") -> "video"
                            else -> error("Choose a photo or video supported by FYNX.")
                        }
                        val extension = when {
                            mediaType == "image" -> ".jpg"
                            mime == "video/quicktime" -> ".mov"
                            mime == "video/webm" -> ".webm"
                            mime == "video/3gpp" -> ".3gp"
                            else -> ".mp4"
                        }
                        val file = File(context.cacheDir, "fynx_gallery_" + System.currentTimeMillis() + extension)
                        try {
                            val input = context.contentResolver.openInputStream(selectedUri)
                                ?: error("The selected media could not be opened.")
                            input.use { stream -> FileOutputStream(file).use { output -> stream.copyTo(output) } }
                            check(file.length() > 0L) { "The selected media is empty." }
                            if (mediaType == "image") {
                                normalizeImportedPhotoToJpeg(file)
                            } else if (statusCapture) {
                                require(file.length() <= FYNX_STATUS_MAX_VIDEO_BYTES) {
                                    "This Status video is above the upload-size limit. Choose a smaller clip."
                                }
                                val metadata = android.media.MediaMetadataRetriever()
                                val duration = try {
                                    metadata.setDataSource(file.absolutePath)
                                    metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                                } finally { metadata.release() }
                                require(duration in 1L..FYNX_STATUS_MAX_VIDEO_DURATION_MS) {
                                    "Status videos must be no longer than 90 seconds."
                                }
                            }
                            val original = if (mediaType == "image") {
                                File(context.cacheDir, "fynx_gallery_original_" + System.currentTimeMillis() + ".jpg")
                                    .also { file.copyTo(it, overwrite = true) }
                            } else null
                            Triple(file, mediaType, original)
                        } catch (failure: Throwable) {
                            file.delete()
                            throw failure
                        }
                    }
                }
                imported.onSuccess { (file, mediaType, original) ->
                    pendingUri = Uri.fromFile(file)
                    pendingOriginalUri = Uri.fromFile(original ?: file)
                    pendingType = mediaType
                    filter = CameraFilter.NATURAL
                    filterIntensity = 1f
                    videoDurationMs = 0L
                    videoTrimStartMs = 0L
                    videoTrimEndMs = 0L
                    muteVideoAudio = false
                    error = null
                    recordingNotice = null
                    showCaptureControls = false
                }.onFailure {
                    error = it.message ?: "The selected media could not be opened."
                    showCaptureControls = true
                }
                galleryImporting = false
            }
        }
    }

    LaunchedEffect(showCameraMusicPicker, cameraMusicSearch, cameraMusicRetryKey) {
        if (showCameraMusicPicker) {
            cameraMusicLoading = true
            cameraMusicError = null
            delay(250L)
            if (!FynxBackendClient.hasAccessToken(context)) {
                cameraMusicTracks = emptyList()
                cameraMusicError = "Sign in to FYNX to browse published music."
            } else {
                val result = FynxMusicCatalogueClient.listPublished(context, cameraMusicSearch)
                result.onSuccess { cameraMusicTracks = it; cameraMusicError = null }
                    .onFailure { cameraMusicTracks = emptyList(); cameraMusicError = it.message ?: "The FYNX music catalogue could not be loaded." }
            }
            cameraMusicLoading = false
        }
    }

    LaunchedEffect(recording != null, recordingStartedAt) { while (recording != null) { recordingElapsed = (System.currentTimeMillis() - recordingStartedAt).coerceAtLeast(0L); delay(200L) } }
    LaunchedEffect(recording, recordingStartedAt, videoNoteMode, statusCapture, activeRecordingFile, mode, selectedVideoDurationMs) {
        val active = recording ?: return@LaunchedEffect
        val maximumDurationMs = FynxCameraRecordingDuration.maximumDurationMs(videoNoteMode, statusCapture, mode, selectedVideoDurationMs)
        while (recording == active) {
            if (statusCapture && (activeRecordingFile?.length() ?: 0L) >= FYNX_STATUS_MAX_VIDEO_BYTES) {
                recordingNotice = "Status video reached the upload-size limit. Your clip is ready to preview."
                active.stop()
                break
            }
            if (maximumDurationMs != null &&
                System.currentTimeMillis() - recordingStartedAt >= maximumDurationMs
            ) {
                recordingNotice = when {
                    statusCapture && !videoNoteMode -> "Status video reached its 90-second limit. Your clip is ready to preview."
                    videoNoteMode -> "Video note reached its 60-second limit. Your clip is ready to preview."
                    else -> "Your " + formatCameraRecordingTime(maximumDurationMs) + " clip is ready to preview."
                }
                active.stop()
                break
            }
            delay(200L)
        }
    }
    // Keep the main camera controls persistent; only a timer countdown hides the dock.
    LaunchedEffect(timerCountingDown) { if (timerCountingDown > 0) { delay(1000L); timerCountingDown -= 1 } }
    LaunchedEffect(pendingUri, pendingType, photoGeometryRevision) {
        val path = pendingUri?.path
        if (pendingType != "image" || path.isNullOrBlank()) {
            photoPixelWidth = 0
            photoPixelHeight = 0
            return@LaunchedEffect
        }
        val dimensions = withContext(Dispatchers.IO) {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)
            options.outWidth to options.outHeight
        }
        if (pendingUri?.path == path && pendingType == "image") {
            photoPixelWidth = dimensions.first.coerceAtLeast(0)
            photoPixelHeight = dimensions.second.coerceAtLeast(0)
        }
    }
    LaunchedEffect(captureStyleFilter, liveCameraEffect) {
        val effect = liveCameraEffect ?: return@LaunchedEffect
        runCatching { effect.setEffects(fynxCameraLiveEffects(captureStyleFilter)) }
            .onFailure { error = "Camera effect could not be applied: " + (it.message ?: "unsupported effect") }
    }
    LaunchedEffect(hasCamera, lens, mode, pendingUri, lifecycleOwner) {
        val generation = bindGeneration + 1; bindGeneration = generation
        if (!hasCamera || pendingUri != null) {
            cameraProvider?.unbindAll()
            liveCameraEffect?.close()
            liveCameraEffect = null
            imageCapture = null
            videoCapture = null
            cameraControl = null
            cameraInfo = null
            return@LaunchedEffect
        }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (generation != bindGeneration || !hasCamera || pendingUri != null) return@addListener
            runCatching {
                val provider = future.get(); val selector = CameraSelector.Builder().requireLensFacing(lens).build()
                if (!provider.hasCamera(selector)) { error = if (lens == CameraSelector.LENS_FACING_FRONT) "Front camera is not available on this device." else "Back camera is not available on this device."; if (lens == CameraSelector.LENS_FACING_FRONT) lens = CameraSelector.LENS_FACING_BACK; return@runCatching }
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val targetRotation = previewView.display?.rotation ?: android.view.Surface.ROTATION_0
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetRotation(targetRotation)
                    .build()
                val qualitySelector = if (statusCapture) {
                    QualitySelector.fromOrderedList(
                        listOf(Quality.SD, Quality.HD),
                        FallbackStrategy.lowerQualityOrHigherThan(Quality.HD)
                    )
                } else {
                    QualitySelector.fromOrderedList(
                        listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD),
                        FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD)
                    )
                }
                val recorderBuilder = Recorder.Builder().setQualitySelector(qualitySelector)
                if (statusCapture) {
                    // CameraX's target is approximate; the live file-size guard below
                    // is the hard stop. This target keeps typical 90-second Status clips
                    // safely below the backend's 12 MiB upload ceiling.
                    recorderBuilder.setTargetVideoEncodingBitRate(700_000)
                }
                val recorder = recorderBuilder.build()
                val video = VideoCapture.Builder(recorder)
                    .setTargetRotation(targetRotation)
                    .setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY)
                    .build()
                provider.unbindAll()
                liveCameraEffect?.close()
                liveCameraEffect = null
                if (generation != bindGeneration || pendingUri != null) return@runCatching
                val targets = if (mode == CameraMode.PHOTO) CameraEffect.PREVIEW else (CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE)
                val cameraEffect = Media3Effect(
                    context, targets, ContextCompat.getMainExecutor(context)
                ) { failure ->
                    error = "Live effect stopped: " + (failure.message ?: "camera effect unavailable")
                }
                try {
                    val groupBuilder = UseCaseGroup.Builder().addUseCase(preview)
                    if (mode == CameraMode.PHOTO) groupBuilder.addUseCase(capture) else groupBuilder.addUseCase(video)
                    groupBuilder.addEffect(cameraEffect)
                    val camera = provider.bindToLifecycle(lifecycleOwner, selector, groupBuilder.build())
                    liveCameraEffect = cameraEffect
                    cameraProvider = provider; imageCapture = capture; videoCapture = video; cameraControl = camera.cameraControl; cameraInfo = camera.cameraInfo
                    cameraControl?.setZoomRatio(zoomRatio.coerceIn(1f, cameraInfo?.zoomState?.value?.maxZoomRatio ?: 1f))
                    val range = cameraInfo?.exposureState?.exposureCompensationRange
                    exposure = exposure.coerceIn(range?.lower ?: -2, range?.upper ?: 2); cameraControl?.setExposureCompensationIndex(exposure)
                    if (torchEnabled && cameraInfo?.hasFlashUnit() == true) cameraControl?.enableTorch(true) else if (torchEnabled) torchEnabled = false
                    error = null
                } catch (failure: Throwable) {
                    cameraEffect.close()
                    throw failure
                }
            }.onFailure { if (generation == bindGeneration) error = it.message ?: "Camera could not start" }
        }, ContextCompat.getMainExecutor(context))
    }
    DisposableEffect(Unit) { onDispose { recording?.stop(); cameraProvider?.unbindAll(); liveCameraEffect?.close() } }
    fun deleteUri(uri: Uri?) { if (uri?.scheme == "file") File(uri.path ?: "").delete() }
    fun retake() {
        deleteUri(pendingUri)
        deleteUri(pendingOriginalUri)
        pendingType = null
        pendingUri = null
        pendingOriginalUri = null
        filter = CameraFilter.NATURAL
        filterIntensity = 1f
        photoTextOverlay = ""
        selectedPhotoSticker = null
        showPhotoTextDialog = false
        drawingMode = false
        drawingStrokes = emptyList()
        activeDrawingPoints = emptyList()
        photoPixelWidth = 0
        photoPixelHeight = 0
        videoDurationMs = 0L
        videoTrimStartMs = 0L
        videoTrimEndMs = 0L
        muteVideoAudio = false
        enhancing = false
        recordingNotice = null
        captureStyleFilter = CameraFilter.NATURAL
        showCaptureControls = true
        error = null
    }
    fun rotatePhoto() {
        if (pendingType != "image" || enhancing) return
        val current = pendingUri?.path?.let(::File) ?: return
        val original = pendingOriginalUri?.path?.let(::File)
        enhancing = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    fun rotatedCopy(source: File, destination: File) {
                        val bitmap = BitmapFactory.decodeFile(source.absolutePath)
                            ?: error("Unable to decode photo for rotation")
                        val rotated = android.graphics.Bitmap.createBitmap(
                            bitmap, 0, 0, bitmap.width, bitmap.height,
                            Matrix().apply { postRotate(90f) }, true
                        )
                        val temp = File(context.cacheDir, "fynx_rotate_${System.nanoTime()}.jpg")
                        try {
                            FileOutputStream(temp).use {
                                check(rotated.compress(android.graphics.Bitmap.CompressFormat.JPEG, 94, it))
                            }
                            temp.copyTo(destination, overwrite = true)
                        } finally {
                            temp.delete()
                            bitmap.recycle()
                            rotated.recycle()
                        }
                    }
                    rotatedCopy(current, current)
                    if (original != null && original.absolutePath != current.absolutePath) {
                        rotatedCopy(original, original)
                    }
                }
            }
            result.onFailure { error = it.message ?: "Photo rotation failed" }
            result.onSuccess { photoGeometryRevision++; drawingStrokes = emptyList(); activeDrawingPoints = emptyList(); drawingMode = false }
            enhancing = false
        }
    }
    fun cropPhotoAspect(aspectRatio: Float, presetName: String) {
        if (pendingType != "image" || enhancing) return
        if (!aspectRatio.isFinite() || aspectRatio <= 0f) {
            error = "Choose a supported crop aspect ratio."
            return
        }
        val current = pendingUri?.path?.let(::File) ?: return
        val original = pendingOriginalUri?.path?.let(::File)
        enhancing = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    fun cropToPreset(source: File, output: File) {
                        val bitmap = BitmapFactory.decodeFile(source.absolutePath)
                            ?: error("Unable to decode photo for cropping")
                        val rect = calculateFynxCameraCropRect(bitmap.width, bitmap.height, aspectRatio)
                            ?: run {
                                bitmap.recycle()
                                error("Photo dimensions do not support this crop.")
                            }
                        val cropped = android.graphics.Bitmap.createBitmap(
                            bitmap, rect.left, rect.top, rect.cropWidth, rect.cropHeight
                        )
                        try {
                            FileOutputStream(output).use {
                                check(cropped.compress(android.graphics.Bitmap.CompressFormat.JPEG, 94, it)) {
                                    "Cropped photo could not be saved"
                                }
                            }
                        } finally {
                            bitmap.recycle()
                            cropped.recycle()
                        }
                    }
                    val currentTemp = File(context.cacheDir, "fynx_crop_current_${System.nanoTime()}.jpg")
                    val originalTemp = File(context.cacheDir, "fynx_crop_original_${System.nanoTime()}.jpg")
                    try {
                        cropToPreset(current, currentTemp)
                        if (original != null && original.absolutePath != current.absolutePath) {
                            cropToPreset(original, originalTemp)
                        }
                        currentTemp.copyTo(current, overwrite = true)
                        if (original != null && original.absolutePath != current.absolutePath) {
                            originalTemp.copyTo(original, overwrite = true)
                        }
                    } finally {
                        currentTemp.delete()
                        originalTemp.delete()
                    }
                }
            }
            result.onFailure { error = it.message ?: "Could not apply the $presetName crop." }
            result.onSuccess {
                photoGeometryRevision++
                drawingStrokes = emptyList()
                activeDrawingPoints = emptyList()
                drawingMode = false
                error = null
            }
            enhancing = false
        }
    }
    fun cropPhotoSquare() = cropPhotoAspect(1f, "square")
    fun cropPhotoPortrait() = cropPhotoAspect(4f / 5f, "portrait 4:5")
    fun cropPhotoLandscape() = cropPhotoAspect(16f / 9f, "landscape 16:9")
    fun renderPhotoOverlays(uri: Uri, textOverlay: String, sticker: String?, drawStrokes: List<FynxCameraDrawStroke>) {
        if (textOverlay.isBlank() && sticker.isNullOrBlank() && drawStrokes.isEmpty()) return
        val destination = uri.path?.let(::File) ?: error("Captured photo is not a local file")
        val bitmap = BitmapFactory.decodeFile(destination.absolutePath)
            ?: error("Unable to decode photo for editing")
        val output = bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
            ?: run { bitmap.recycle(); error("Unable to prepare photo editor canvas") }
        val canvas = android.graphics.Canvas(output)
        try {
            if (textOverlay.isNotBlank()) {
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.WHITE
                    textSize = (bitmap.width * 0.055f).coerceIn(24f, 84f)
                    textAlign = android.graphics.Paint.Align.CENTER
                    typeface = android.graphics.Typeface.create(
                        "sans-serif-medium", android.graphics.Typeface.BOLD
                    )
                    setShadowLayer(5f, 1f, 2f, android.graphics.Color.BLACK)
                }
                val lines = wrapFynxCameraOverlayText(textOverlay, paint, bitmap.width * 0.82f)
                val lineHeight = paint.textSize * 1.18f
                val firstBaseline = bitmap.height * 0.43f - (lines.size - 1) * lineHeight / 2f
                lines.forEachIndexed { index, line ->
                    canvas.drawText(line, bitmap.width / 2f, firstBaseline + index * lineHeight, paint)
                }
            }
            if (!sticker.isNullOrBlank()) {
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.WHITE
                    textSize = (bitmap.width * 0.15f).coerceIn(56f, 180f)
                    textAlign = android.graphics.Paint.Align.CENTER
                    setShadowLayer(7f, 1f, 2f, android.graphics.Color.BLACK)
                }
                canvas.drawText(sticker, bitmap.width * 0.78f, bitmap.height * 0.82f, paint)
            }
            drawStrokes.forEach { stroke ->
                if (stroke.points.isEmpty()) return@forEach
                val path = android.graphics.Path().apply {
                    moveTo(stroke.points.first().x * bitmap.width, stroke.points.first().y * bitmap.height)
                    stroke.points.drop(1).forEach { point ->
                        lineTo(point.x * bitmap.width, point.y * bitmap.height)
                    }
                }
                val penWidth = (minOf(bitmap.width, bitmap.height) * 0.012f).coerceAtLeast(3f)
                val pen = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = stroke.colorArgb
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = penWidth
                    strokeCap = android.graphics.Paint.Cap.ROUND
                    strokeJoin = android.graphics.Paint.Join.ROUND
                    setShadowLayer(2f, 0f, 0f, android.graphics.Color.BLACK)
                }
                if (stroke.points.size == 1) {
                    canvas.drawCircle(
                        stroke.points.first().x * bitmap.width,
                        stroke.points.first().y * bitmap.height,
                        penWidth / 2f,
                        pen
                    )
                } else {
                    canvas.drawPath(path, pen)
                }
            }
            val temp = File(context.cacheDir, "fynx_photo_overlay_${System.nanoTime()}.jpg")
            try {
                FileOutputStream(temp).use {
                    check(output.compress(android.graphics.Bitmap.CompressFormat.JPEG, 94, it))
                }
                temp.copyTo(destination, overwrite = true)
            } finally {
                temp.delete()
            }
        } finally {
            bitmap.recycle()
            output.recycle()
        }
    }
    fun sendPreview(previewUri: Uri, previewType: String) {
        if (previewType != "image" ||
            (photoTextOverlay.isBlank() && selectedPhotoSticker.isNullOrBlank() && drawingStrokes.isEmpty())
        ) {
            onCaptured(previewUri, previewType)
            return
        }
        if (enhancing) return
        val textToRender = photoTextOverlay
        val stickerToRender = selectedPhotoSticker
        val drawingStrokesToRender = drawingStrokes.toList()
        enhancing = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { renderPhotoOverlays(previewUri, textToRender, stickerToRender, drawingStrokesToRender) }
            }
            result.onFailure { error = it.message ?: "Photo edits could not be applied" }
            enhancing = false
            if (result.isSuccess) onCaptured(previewUri, previewType)
        }
    }
    fun applyVideoEdits(previewUri: Uri) {
        if (enhancing || videoDurationMs < FynxCameraVideoEditRange.MIN_CLIP_DURATION_MS) return
        val source = previewUri.path?.let(::File) ?: run {
            error = "This video cannot be edited from its current location."
            return
        }
        val endMs = videoTrimEndMs.coerceIn(FynxCameraVideoEditRange.MIN_CLIP_DURATION_MS, videoDurationMs)
        val startMs = videoTrimStartMs.coerceIn(0L, (endMs - FynxCameraVideoEditRange.MIN_CLIP_DURATION_MS).coerceAtLeast(0L))
        val hasTrim = startMs > 0L || endMs < videoDurationMs
        if (!hasTrim && !muteVideoAudio) return
        enhancing = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val edited = FynxCameraVideoEditor.edit(source, startMs, endMs, muteVideoAudio)
                    if (statusCapture && edited.length() > FYNX_STATUS_MAX_VIDEO_BYTES) {
                        edited.delete()
                        error("The edited Status video is above the upload-size limit. Shorten the clip further.")
                    }
                    edited
                }
            }
            result.onSuccess { edited ->
                if (edited.absolutePath != source.absolutePath) {
                    val previous = pendingUri
                    pendingUri = Uri.fromFile(edited)
                    videoDurationMs = 0L
                    videoTrimStartMs = 0L
                    videoTrimEndMs = 0L
                    muteVideoAudio = false
                    error = null
                    scope.launch {
                        delay(700L)
                        if (previous?.path == source.absolutePath) deleteUri(previous)
                    }
                }
            }.onFailure { error = it.message ?: "Video edits could not be applied" }
            enhancing = false
        }
    }
    fun applyFilterToPhoto(selected: CameraFilter, intensity: Float = filterIntensity) {
        val current = pendingUri ?: return
        val original = pendingOriginalUri ?: current
        if (pendingType != "image" || enhancing) return
        val source = original.path?.let(::File) ?: return
        val target = current.path?.let(::File) ?: return
        val strength = intensity.coerceIn(0f, 1f)
        enhancing = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bitmap = BitmapFactory.decodeFile(source.absolutePath)
                        ?: error("Unable to decode photo for filtering")
                    var outputBitmap: android.graphics.Bitmap? = null
                    val temp = File(context.cacheDir, "fynx_photo_filter_${System.nanoTime()}.jpg")
                    try {
                        val matrix = ColorMatrix().apply {
                            setFynxFilter(
                                1f + (selected.saturation - 1f) * strength,
                                selected.brightness * strength,
                                1f + (selected.contrast - 1f) * strength,
                                1f
                            )
                        }
                        outputBitmap = android.graphics.Bitmap.createBitmap(
                            bitmap.width, bitmap.height, android.graphics.Bitmap.Config.ARGB_8888
                        )
                        android.graphics.Canvas(outputBitmap!!).drawBitmap(
                            bitmap,
                            0f,
                            0f,
                            android.graphics.Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) }
                        )
                        FileOutputStream(temp).use {
                            check(outputBitmap!!.compress(android.graphics.Bitmap.CompressFormat.JPEG, 94, it)) {
                                "Filtered photo could not be saved"
                            }
                        }
                        temp.copyTo(target, overwrite = true)
                    } finally {
                        temp.delete()
                        bitmap.recycle()
                        outputBitmap?.recycle()
                    }
                }
            }
            result.onSuccess {
                filter = selected
                filterIntensity = strength
                error = null
            }.onFailure {
                error = it.message ?: "Filter could not be applied"
            }
            enhancing = false
        }
    }
    fun enhancePhoto() {
        val source = pendingUri ?: return
        if (pendingType != "image" || enhancing) return
        enhancing = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val baselineUri = FynxAiPhotoEnhancer.enhance(context, source).getOrThrow()
                    val baselineFile = baselineUri.path?.let(::File)
                        ?: error("Enhanced photo did not return a local file")
                    val workingFile = File(
                        context.cacheDir, "fynx_smart_enhanced_working_${System.nanoTime()}.jpg"
                    )
                    try {
                        baselineFile.copyTo(workingFile, overwrite = true)
                        Uri.fromFile(workingFile) to baselineUri
                    } catch (failure: Throwable) {
                        workingFile.delete()
                        baselineFile.delete()
                        throw failure
                    }
                }
            }
            result.onSuccess { (workingUri, baselineUri) ->
                val oldUris = listOf(pendingUri, pendingOriginalUri).distinctBy { it?.path }
                pendingUri = workingUri
                pendingOriginalUri = baselineUri
                filter = CameraFilter.NATURAL
                filterIntensity = 1f
                drawingStrokes = emptyList()
                activeDrawingPoints = emptyList()
                drawingMode = false
                photoGeometryRevision++
                error = null
                oldUris.forEach { old ->
                    if (old?.path != workingUri.path && old?.path != baselineUri.path) deleteUri(old)
                }
            }.onFailure {
                error = it.message ?: "AI photo enhancement failed"
            }
            enhancing = false
        }
    }
    LaunchedEffect(pendingUri, pendingType, captureStyleFilter) {
        if (pendingUri != null && pendingType == "image" && captureStyleFilter != CameraFilter.NATURAL && !enhancing) {
            applyFilterToPhoto(captureStyleFilter, 1f)
        }
    }

    fun startVideoRecording() {
        if (recording != null || timerCountingDown > 0 || galleryImporting || preparingPhoto) return
        if (!hasAudio) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        val capture = videoCapture ?: run {
            error = "Video camera is still starting"
            return
        }
        val file = File(context.cacheDir, "fynx_video_" + System.currentTimeMillis() + ".mp4")
        val pending = capture.output.prepareRecording(context, FileOutputOptions.Builder(file).build())
        val withAudio = pending.withAudioEnabled()
        recordingStartedAt = System.currentTimeMillis()
        activeRecordingFile = file
        recordingNotice = null
        recording = withAudio.start(ContextCompat.getMainExecutor(context)) { event ->
            if (event is VideoRecordEvent.Finalize) {
                val finalizedBytes = if (file.exists()) file.length() else 0L
                val exceedsStatusUploadLimit = statusCapture && finalizedBytes > FYNX_STATUS_MAX_VIDEO_BYTES
                if (!event.hasError() && file.exists() && finalizedBytes > 0L && !exceedsStatusUploadLimit) {
                    pendingUri = Uri.fromFile(file)
                    pendingType = if (videoNoteMode) "video_note" else "video"
                    error = null
                } else {
                    file.delete()
                    recordingNotice = null
                    error = when {
                        exceedsStatusUploadLimit ->
                            "Status video exceeded the 12 MiB upload-size limit. Record a shorter clip."
                        else -> "Video capture failed (" + event.error + ")"
                    }
                }
                recording = null
                activeRecordingFile = null
            }
        }
    }

    fun capturePhotoNow() {
        val capture = imageCapture ?: run { error = "Camera is still starting"; return }
        val stamp = System.currentTimeMillis()
        val file = File(context.cacheDir, "fynx_photo_"+stamp+".jpg")
        val original = File(context.cacheDir, "fynx_photo_original_"+stamp+".jpg")
        if (preparingPhoto) return
        preparingPhoto = true
        capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                // JPEG decoding, EXIF normalization, recompression, and copying can be
                // expensive on emulator/device camera output. Keep all of that off the
                // main thread so the preview UI and its accessibility hierarchy stay live.
                scope.launch {
                    try {
                        val prepared = withContext(Dispatchers.IO) {
                            runCatching {
                                normalizeCapturedPhoto(file, lens == CameraSelector.LENS_FACING_FRONT)
                                file.copyTo(original, overwrite = true)
                            }
                        }
                        prepared
                            .onSuccess {
                                pendingUri = Uri.fromFile(file)
                                pendingOriginalUri = Uri.fromFile(original)
                                pendingType = "image"
                                filter = CameraFilter.NATURAL
                                error = null
                                showCaptureControls = true
                            }
                            .onFailure {
                                file.delete()
                                original.delete()
                                error = it.message ?: "Photo could not be prepared"
                                showCaptureControls = true
                            }
                    } finally {
                        preparingPhoto = false
                    }
                }
            }
            override fun onError(exception: ImageCaptureException) {
                preparingPhoto = false
                error = exception.message ?: "Photo capture failed"
                showCaptureControls = true
            }
        })
    }

    if (!hasCamera) { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Text("FYNX needs camera access to capture photos and videos.");Spacer(Modifier.height(12.dp));Button(onClick={permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO))}){Text("Allow camera")};TextButton(onClick=onDismiss){Text("Close")}}};return }
    val previewUri=pendingUri; val previewType=pendingType
    if(previewUri!=null && previewType!=null){
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)){
            if (previewType == "image") {
                AndroidView(
                    factory = { android.widget.ImageView(it).apply { scaleType = android.widget.ImageView.ScaleType.FIT_CENTER } },
                    update = { it.setImageURI(previewUri) },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AndroidView(
                    factory = { viewContext ->
                        android.widget.VideoView(viewContext).apply {
                            tag = previewUri.toString()
                            setOnPreparedListener { player ->
                                player.isLooping = true
                                videoDurationMs = player.duration.toLong().coerceAtLeast(0L)
                                if (videoTrimEndMs <= 0L || videoTrimEndMs > videoDurationMs) videoTrimEndMs = videoDurationMs
                                if (videoTrimStartMs > videoTrimEndMs - FynxCameraVideoEditRange.MIN_CLIP_DURATION_MS) videoTrimStartMs = 0L
                                start()
                            }
                            setVideoURI(previewUri)
                        }
                    },
                    update = { view ->
                        if (view.tag != previewUri.toString()) {
                            view.tag = previewUri.toString()
                            view.setVideoURI(previewUri)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (previewType == "image" && photoPixelWidth > 0 && photoPixelHeight > 0 &&
                (photoTextOverlay.isNotBlank() || selectedPhotoSticker != null)
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawFynxCameraPhotoPreviewOverlays(
                        textOverlay = photoTextOverlay,
                        sticker = selectedPhotoSticker,
                        imageWidth = photoPixelWidth,
                        imageHeight = photoPixelHeight
                    )
                }
            }
            if (previewType == "image" && photoPixelWidth > 0 && photoPixelHeight > 0 &&
                (drawingStrokes.isNotEmpty() || drawingMode)
            ) {
                val drawingGestureModifier = if (drawingMode) {
                    Modifier.pointerInput(previewUri, photoPixelWidth, photoPixelHeight, selectedDrawingColorArgb) {
                        detectDragGestures(
                            onDragStart = { position ->
                                val point = normalizeFynxCameraDrawPoint(
                                    position.x, position.y, size.width.toFloat(), size.height.toFloat(),
                                    photoPixelWidth.toFloat(), photoPixelHeight.toFloat()
                                )
                                activeDrawingPoints = point?.let { listOf(it) } ?: emptyList()
                            },
                            onDrag = { change, _ ->
                                val point = normalizeFynxCameraDrawPoint(
                                    change.position.x, change.position.y, size.width.toFloat(), size.height.toFloat(),
                                    photoPixelWidth.toFloat(), photoPixelHeight.toFloat()
                                )
                                if (point != null && activeDrawingPoints.isNotEmpty()) {
                                    activeDrawingPoints = activeDrawingPoints + point
                                }
                                change.consume()
                            },
                            onDragEnd = {
                                if (activeDrawingPoints.isNotEmpty()) {
                                    drawingStrokes = drawingStrokes + FynxCameraDrawStroke(activeDrawingPoints, selectedDrawingColorArgb)
                                }
                                activeDrawingPoints = emptyList()
                            },
                            onDragCancel = { activeDrawingPoints = emptyList() }
                        )
                    }
                } else Modifier
                Canvas(Modifier.fillMaxSize().then(drawingGestureModifier)) {
                    val visibleStrokes = drawingStrokes +
                        if (activeDrawingPoints.isNotEmpty()) listOf(FynxCameraDrawStroke(activeDrawingPoints, selectedDrawingColorArgb))
                        else emptyList()
                    drawFynxCameraDrawingStrokes(visibleStrokes, photoPixelWidth, photoPixelHeight)
                }
            }
            if (!drawingMode) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .background(Color(0xF0111218), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .fillMaxWidth()
                    // Editing tools stay above a dedicated bottom action dock.
                    .padding(bottom = 108.dp)
                    .fillMaxHeight(0.40f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 18.dp, end = 18.dp, top = 8.dp)
            ){
                recordingNotice?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp)) }
                error?.let{Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(bottom=8.dp))}
                if (previewType == "image") {
                    Text("EDIT PHOTO", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text("Make it yours", style = MaterialTheme.typography.bodySmall, color = Color(0xFFB9B9C8), modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Filters", "Crop", "Creative", "Enhance").forEach { tab ->
                            val selected = photoEditorTab == tab
                            Surface(
                                modifier = Modifier.weight(1f).clickable { photoEditorTab = tab },
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) Color(0xFF6B5CE7) else Color(0xFF292B35),
                                contentColor = Color.White
                            ) {
                                Text(
                                    tab,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 11.dp),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                    when (photoEditorTab) {
                        "Filters" -> {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CameraFilter.values().forEach { option ->
                                    FilterChip(
                                        selected = filter == option,
                                        enabled = !enhancing,
                                        onClick = { applyFilterToPhoto(option, if (option == CameraFilter.NATURAL) 0f else filterIntensity) },
                                        label = { Text(option.label) }
                                    )
                                }
                            }
                            if (filter != CameraFilter.NATURAL) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Strength", color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(64.dp))
                                    Slider(value = filterIntensity, onValueChange = { filterIntensity = it }, onValueChangeFinished = { applyFilterToPhoto(filter, filterIntensity) }, valueRange = 0f..1f, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                        "Crop" -> {
                            Text("Choose a crop shape", color = Color(0xFFCACAD5), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Crop square", "Portrait 4:5", "Landscape 16:9").forEach { label ->
                                    OutlinedButton(
                                        onClick = { when (label) { "Crop square" -> cropPhotoSquare(); "Portrait 4:5" -> cropPhotoPortrait(); else -> cropPhotoLandscape() } },
                                        enabled = !enhancing,
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                                    ) { Text(label, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall) }
                                }
                            }
                        }
                        "Creative" -> {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { showPhotoTextDialog = true }, enabled = !enhancing, modifier = Modifier.weight(1f)) { Text(if (photoTextOverlay.isBlank()) "Add text" else "Edit text") }
                                OutlinedButton(onClick = { drawingMode = true }, enabled = !enhancing && photoPixelWidth > 0 && photoPixelHeight > 0, modifier = Modifier.weight(1f)) { Text("Draw") }
                            }
                            Text("Stickers", color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("✨", "❤️", "🔥", "😂", "😎", "🎉", "🌈").forEach { sticker ->
                                    FilterChip(selected = selectedPhotoSticker == sticker, enabled = !enhancing, onClick = { selectedPhotoSticker = if (selectedPhotoSticker == sticker) null else sticker }, label = { Text(sticker) })
                                }
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { if (drawingStrokes.isNotEmpty()) drawingStrokes = drawingStrokes.dropLast(1) }, enabled = !enhancing && drawingStrokes.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Undo") }
                                OutlinedButton(onClick = { drawingStrokes = emptyList(); activeDrawingPoints = emptyList() }, enabled = !enhancing && drawingStrokes.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Clear") }
                            }
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(0xFFFFD54F.toInt(), 0xFFF44336.toInt(), 0xFF42A5F5.toInt(), 0xFF66BB6A.toInt(), 0xFFFFFFFF.toInt()).forEach { argb ->
                                    FilterChip(selected = selectedDrawingColorArgb == argb, enabled = !enhancing, onClick = { selectedDrawingColorArgb = argb }, label = { Box(Modifier.size(18.dp).background(Color(argb), androidx.compose.foundation.shape.CircleShape)) })
                                }
                            }
                        }
                        else -> {
                            Text("Improve lighting and contrast automatically.", color = Color(0xFFCACAD5), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
                            Button(onClick = { enhancePhoto() }, enabled = !enhancing, modifier = Modifier.fillMaxWidth()) { Text(if (enhancing) "Enhancing…" else "✨ Auto Enhance") }
                        }
                    }
                }  else {
                    Text("Edit video", style = MaterialTheme.typography.labelLarge)
                    if (videoDurationMs > FynxCameraVideoEditRange.MIN_CLIP_DURATION_MS) {
                        val minClip = FynxCameraVideoEditRange.MIN_CLIP_DURATION_MS
                        val safeEnd = videoTrimEndMs.coerceIn(minClip, videoDurationMs)
                        val safeStart = videoTrimStartMs.coerceIn(0L, (safeEnd - minClip).coerceAtLeast(0L))
                        val latestStart = (safeEnd - minClip).coerceAtLeast(0L)
                        val earliestEnd = (safeStart + minClip).coerceAtMost(videoDurationMs)
                        Text("Start · ${formatCameraRecordingTime(safeStart)}", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = safeStart.toFloat(),
                            onValueChange = { videoTrimStartMs = it.toLong().coerceIn(0L, latestStart) },
                            valueRange = 0f..maxOf(1f, latestStart.toFloat()),
                            enabled = !enhancing && latestStart > 0L
                        )
                        Text("End · ${formatCameraRecordingTime(safeEnd)}", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = safeEnd.toFloat(),
                            onValueChange = { videoTrimEndMs = it.toLong().coerceIn(earliestEnd, videoDurationMs) },
                            valueRange = if (earliestEnd < videoDurationMs) earliestEnd.toFloat()..videoDurationMs.toFloat() else 0f..videoDurationMs.toFloat(),
                            enabled = !enhancing && earliestEnd < videoDurationMs
                        )
                        Text("Selected clip · ${formatCameraRecordingTime((safeEnd - safeStart).coerceAtLeast(0L))}", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Mute original audio", style = MaterialTheme.typography.bodyMedium)
                                Text("Audio is removed when you apply edits.", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = muteVideoAudio, onCheckedChange = { muteVideoAudio = it }, enabled = !enhancing, modifier = Modifier.semantics { contentDescription = "Toggle video mute" })
                        }
                        val videoHasTrim = safeStart > 0L || safeEnd < videoDurationMs
                        OutlinedButton(
                            onClick = { applyVideoEdits(previewUri) },
                            enabled = !enhancing && (videoHasTrim || muteVideoAudio),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) { Text(if (enhancing) "Processing video…" else "Apply video edits") }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("Preparing video editor…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            } else {
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.68f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Draw on photo", color = Color.White, style = MaterialTheme.typography.labelLarge)
                        TextButton(onClick = { if (drawingStrokes.isNotEmpty()) drawingStrokes = drawingStrokes.dropLast(1) }) { Text("Undo") }
                        TextButton(onClick = { drawingStrokes = emptyList(); activeDrawingPoints = emptyList() }) { Text("Clear") }
                        Button(onClick = { drawingMode = false; activeDrawingPoints = emptyList() }) { Text("Finish drawing") }
                    }
                }
            }
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xFF171820))
                    .imePadding()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 34.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(onClick = { if (!enhancing) retake() }, enabled = !enhancing, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Retake") }
                if (previewType == "image") OutlinedButton(onClick = { if (!enhancing) rotatePhoto() }, enabled = !enhancing, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Icon(Icons.Default.RotateRight, null); Spacer(Modifier.width(4.dp)); Text("Rotate") }
                Button(onClick = { if (!enhancing && !drawingMode) sendPreview(previewUri, previewType) }, enabled = !enhancing && !drawingMode, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Icon(Icons.Default.Send, null); Spacer(Modifier.width(4.dp)); Text(if (drawingMode) "Finish drawing first" else "Send") }
            }
            if (showPhotoTextDialog) {
                AlertDialog(
                    onDismissRequest = { showPhotoTextDialog = false },
                    title = { Text("Add text to photo") },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = photoTextOverlay,
                                onValueChange = { photoTextOverlay = it.take(96) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Your text") },
                                placeholder = { Text("Write a caption for your photo") },
                                minLines = 2,
                                maxLines = 4
                            )
                            Text(
                                "Text is placed on the photo when you tap Send.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showPhotoTextDialog = false }) { Text("Apply") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPhotoTextDialog = false }) { Text("Cancel") }
                    }
                )
            }
        };return
    }
    Box(Modifier.fillMaxSize().background(Color.Black).clickable { if (recording == null) showCaptureControls = true }){
        AndroidView(factory={previewView},modifier=Modifier.fillMaxSize())
        if (showGrid && pendingUri == null && recording == null) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = androidx.compose.ui.unit.Dp.Hairline
                drawLine(Color.White.copy(alpha = 0.32f), androidx.compose.ui.geometry.Offset(size.width / 3f, 0f), androidx.compose.ui.geometry.Offset(size.width / 3f, size.height), stroke.toPx())
                drawLine(Color.White.copy(alpha = 0.32f), androidx.compose.ui.geometry.Offset(size.width * 2f / 3f, 0f), androidx.compose.ui.geometry.Offset(size.width * 2f / 3f, size.height), stroke.toPx())
                drawLine(Color.White.copy(alpha = 0.32f), androidx.compose.ui.geometry.Offset(0f, size.height / 3f), androidx.compose.ui.geometry.Offset(size.width, size.height / 3f), stroke.toPx())
                drawLine(Color.White.copy(alpha = 0.32f), androidx.compose.ui.geometry.Offset(0f, size.height * 2f / 3f), androidx.compose.ui.geometry.Offset(size.width, size.height * 2f / 3f), stroke.toPx())
            }
        }
        if (timerCountingDown > 0) {
            Surface(modifier = Modifier.align(Alignment.Center), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)) {
                Text(timerCountingDown.toString(), style = MaterialTheme.typography.displayLarge, modifier = Modifier.padding(horizontal = 28.dp, vertical = 18.dp))
            }
        }
        if (preparingPhoto) {
            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                tonalElevation = 4.dp
            ) {
                Row(
                    Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text("Preparing photo…")
                }
            }
        }
        if (pendingUri == null) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(26.dp), color = Color(0xD9151720), tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = { if (recording == null && !preparingPhoto && !galleryImporting) onDismiss() },
                        enabled = recording == null && !preparingPhoto && !galleryImporting,
                        modifier = Modifier.size(42.dp)
                    ) { Icon(Icons.Default.Close, "Close camera", tint = Color.White) }
                    profilePhotoId?.takeIf { it.isNotBlank() }?.let { photoId ->
                        FynxRemoteMedia("/api/media/$photoId", "image", Modifier.size(32.dp), contentScale = androidx.compose.ui.layout.ContentScale.Crop, rounded = true)
                    } ?: Surface(
                        Modifier.size(32.dp),
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = Color.White.copy(alpha = 0.16f)
                    ) {}
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                        Text(
                            if (liveProofMode) "LIVE PROOF" else if (statusCapture) "STATUS CAMERA" else if (videoNoteMode) "VIDEO NOTE" else "FYNX CAMERA",
                            color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold, maxLines = 1
                        )
                        Text(
                            selectedCameraMusic?.title?.take(18)?.let { "♫ " + it }
                                ?: if (liveProofMode) liveProofListingTitle.ifBlank { "Marketplace item" }
                                else profileDisplayName.ifBlank { session.username?.removePrefix("@").orEmpty().ifBlank { "Ready to capture" } },
                            color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    TextButton(
                        onClick = { if (recording == null) { showCameraMusicPicker = true; cameraMusicError = null } },
                        enabled = recording == null, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(if (selectedCameraMusic == null) "♫ Add sound" else "♫ Change", color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                    IconButton(
                        onClick = {
                            if (recording == null && !preparingPhoto && !galleryImporting) {
                                error = null
                                lens = if (lens == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                                showCaptureControls = true
                            }
                        },
                        enabled = recording == null && !preparingPhoto && !galleryImporting,
                        modifier = Modifier.size(42.dp).semantics(mergeDescendants = true) {
                            contentDescription = "Switch front/back camera"
                            role = Role.Button
                        }
                    ) {
                        Icon(Icons.Default.Cameraswitch, null, tint = Color.White)
                    }
                }
            }
        }

        if (pendingUri == null && timerCountingDown == 0) {
            Column(
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 78.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FynxCameraRailAction("Flash", "⚡", torchEnabled, recording == null) {
                    if (recording == null) {
                        if (cameraInfo?.hasFlashUnit() == true) {
                            torchEnabled = !torchEnabled
                            cameraControl?.enableTorch(torchEnabled)
                        } else error = "Flash is not available on this camera."
                        showCaptureControls = true
                    }
                }
                FynxCameraRailAction("Timer", "◷", showTimerControls, recording == null) {
                    if (recording == null) {
                        showTimerControls = !showTimerControls
                        if (showTimerControls) showProControls = false
                        showCaptureControls = true
                    }
                }
                FynxCameraRailAction("Grid", "▦", showGrid, recording == null) {
                    if (recording == null) { showGrid = !showGrid; showCaptureControls = true }
                }
                FynxCameraRailAction("Effects", "✦", showEffectCarousel, recording == null) {
                    if (recording == null) { showEffectCarousel = !showEffectCarousel; showCaptureControls = true }
                }
                FynxCameraRailAction("Adjust", "☼", showProControls, recording == null) {
                    if (recording == null) {
                        showProControls = !showProControls
                        if (showProControls) showTimerControls = false
                        showCaptureControls = true
                    }
                }
            }
        }

        if (showCaptureControls || recording != null || statusCapture) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .heightIn(max = 410.dp),
                shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
                color = Color(0xE6111218), tonalElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 410.dp)
                        .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                if (recording != null) "Recording in progress" else if (mode == CameraMode.PHOTO) "PHOTO STUDIO" else "VIDEO STUDIO",
                                color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (recording != null) {
                                    val limit = FynxCameraRecordingDuration.maximumDurationMs(videoNoteMode, statusCapture, mode, selectedVideoDurationMs)
                                    if (limit != null) formatCameraRecordingTime(recordingElapsed) + " / " + formatCameraRecordingTime(limit)
                                    else formatCameraRecordingTime(recordingElapsed)
                                } else if (mode == CameraMode.PHOTO) "Frame your shot, then capture" else "Choose a clip length, then record",
                                color = Color.White.copy(alpha = 0.68f), style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (recording == null && !fastCapture && mode == CameraMode.PHOTO) {
                            TextButton(onClick = {
                                showProControls = !showProControls
                                if (showProControls) showTimerControls = false
                            }) { Text(if (showProControls) "Less" else "Adjust") }
                        }
                    }
                    error?.let { Text(it, color = Color(0xFFFF8D8D), style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth()) }
                    recordingNotice?.let { Text(it, color = Color(0xFFFFD166), style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth()) }
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                    if (liveProofMode && recording == null) {
                        Button(onClick = onLiveProofStart, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                            Icon(Icons.Default.Videocam, null); Spacer(Modifier.width(8.dp)); Text("Start live proof")
                        }
                    }

                    if (recording == null) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(
                                selected = mode == CameraMode.PHOTO, onClick = { mode = CameraMode.PHOTO; showCaptureControls = true },
                                label = { Text("Photo") }, leadingIcon = { Icon(Icons.Default.PhotoCamera, null) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF6B5CE7), selectedLabelColor = Color.White,
                                    containerColor = Color.White.copy(alpha = 0.10f), labelColor = Color.White
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            FilterChip(
                                selected = mode == CameraMode.VIDEO, onClick = { mode = CameraMode.VIDEO; showCaptureControls = true },
                                label = { Text("Video") }, leadingIcon = { Icon(Icons.Default.Videocam, null) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF6B5CE7), selectedLabelColor = Color.White,
                                    containerColor = Color.White.copy(alpha = 0.10f), labelColor = Color.White
                                )
                            )
                        }
                    }

                    if (mode == CameraMode.VIDEO && recording == null && !statusCapture && !videoNoteMode && !fastCapture) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("VIDEO LENGTH", color = Color.White.copy(alpha = 0.68f), style = MaterialTheme.typography.labelSmall)
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically
                            ) {
                                FynxCameraRecordingDuration.videoOptions.forEach { (durationMs, label) ->
                                    FilterChip(
                                        selected = selectedVideoDurationMs == durationMs,
                                        onClick = { selectedVideoDurationMs = durationMs; showCaptureControls = true },
                                        label = { Text(label) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFFD92D79), selectedLabelColor = Color.White,
                                            containerColor = Color.White.copy(alpha = 0.10f), labelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }

                    if (showTimerControls && recording == null) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Text("TIMER", color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.labelSmall)
                            listOf(0, 3, 10).forEach { seconds ->
                                FilterChip(
                                    selected = captureTimerSeconds == seconds,
                                    onClick = { captureTimerSeconds = seconds },
                                    label = { Text(if (seconds == 0) "Off" else "${seconds}s") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFD92D79),
                                        selectedLabelColor = Color.White,
                                        containerColor = Color.White.copy(alpha = 0.10f),
                                        labelColor = Color.White
                                    )
                                )
                            }
                            Spacer(Modifier.weight(1f))
                        }
                    }

                    if (showProControls && recording == null && !fastCapture) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Exposure", color = Color.White.copy(alpha = 0.86f), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(66.dp))
                            val range = cameraInfo?.exposureState?.exposureCompensationRange
                            val lower = (range?.lower ?: -2).toFloat()
                            val upper = (range?.upper ?: 2).toFloat()
                            Slider(
                                value = exposure.toFloat().coerceIn(lower, upper),
                                onValueChange = { exposure = it.toInt(); cameraControl?.setExposureCompensationIndex(exposure) },
                                valueRange = lower..upper, steps = ((upper - lower).toInt() - 1).coerceAtLeast(0),
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color(0xFF8C80FF), inactiveTrackColor = Color.White.copy(alpha = 0.22f))
                            )
                            Text(exposure.toString(), color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                            val maxCameraZoom = (cameraInfo?.zoomState?.value?.maxZoomRatio ?: 1f).coerceAtLeast(1f)
                            val zoomStops = listOf(1f, 1.5f, 2f, 3f, 4f).filter { it <= maxCameraZoom + 0.01f }.ifEmpty { listOf(1f) }
                            zoomStops.forEach { value ->
                                FilterChip(
                                    selected = kotlin.math.abs(zoomRatio - value) < 0.05f,
                                    onClick = { zoomRatio = value.coerceIn(1f, maxCameraZoom); cameraControl?.setZoomRatio(zoomRatio) },
                                    label = { Text(value.toInt().toString() + "×") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF6B5CE7), selectedLabelColor = Color.White,
                                        containerColor = Color.White.copy(alpha = 0.10f), labelColor = Color.White
                                    )
                                )
                            }
                            Text(if (lens == CameraSelector.LENS_FACING_FRONT) "Front camera" else "Back camera", color = Color.White.copy(alpha = 0.62f), style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    if (showEffectCarousel && recording == null) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("LIVE LOOKS", color = Color.White.copy(alpha = 0.76f), style = MaterialTheme.typography.labelSmall)
                                Spacer(Modifier.weight(1f))
                                Text("Preview + video", color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall)
                            }
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                                CameraFilter.values().forEach { option ->
                                    FilterChip(
                                        selected = captureStyleFilter == option, onClick = { captureStyleFilter = option },
                                        label = { Text(option.label) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFFD92D79), selectedLabelColor = Color.White,
                                            containerColor = Color.White.copy(alpha = 0.10f), labelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        FynxCameraRailAction("Gallery", "▧", enabled = recording == null && !galleryImporting && !preparingPhoto) {
                            galleryPicker.launch(arrayOf("image/*", "video/*"))
                        }
                        val shutterActionLabel = when {
                            recording != null -> if (videoNoteMode) "Stop video note" else "Stop video recording"
                            mode == CameraMode.PHOTO && imageCapture == null -> "Camera starting"
                            mode == CameraMode.VIDEO && videoCapture == null -> "Camera starting"
                            mode == CameraMode.PHOTO -> "Take photo"
                            videoNoteMode -> "Record video note"
                            else -> "Record video"
                        }
                        val shutterReady = timerCountingDown == 0 && !preparingPhoto && !galleryImporting && (
                            if (recording != null) true else if (mode == CameraMode.PHOTO) imageCapture != null else videoCapture != null
                        )
                        FilledIconButton(
                            onClick = {
                                if (mode == CameraMode.PHOTO) {
                                    if (captureTimerSeconds > 0) {
                                        if (timerCountingDown == 0) {
                                            timerCountingDown = captureTimerSeconds
                                            showCaptureControls = false
                                            scope.launch {
                                                delay(captureTimerSeconds * 1000L)
                                                timerCountingDown = 0
                                                capturePhotoNow()
                                            }
                                        }
                                    } else capturePhotoNow()
                                } else {
                                    val active = recording
                                    if (active != null) {
                                        active.stop()
                                    } else if (captureTimerSeconds > 0) {
                                        if (timerCountingDown == 0) {
                                            timerCountingDown = captureTimerSeconds
                                            showCaptureControls = false
                                            scope.launch {
                                                delay(captureTimerSeconds * 1000L)
                                                timerCountingDown = 0
                                                startVideoRecording()
                                            }
                                        }
                                    } else {
                                        startVideoRecording()
                                    }
                                }
                            },
                            enabled = shutterReady,
                            modifier = Modifier.size(80.dp).semantics(mergeDescendants = true) {
                                contentDescription = shutterActionLabel
                                role = Role.Button
                            },
                            shape = androidx.compose.foundation.shape.CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black)
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (recording != null) {
                                    val limit = FynxCameraRecordingDuration.maximumDurationMs(videoNoteMode, statusCapture, mode, selectedVideoDurationMs)
                                    if (limit != null) {
                                        val progress = (recordingElapsed.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
                                        Canvas(Modifier.fillMaxSize().padding(2.dp)) {
                                            drawArc(Color(0xFFD92D79), -90f, 360f * progress, false, style = Stroke(5.dp.toPx()))
                                        }
                                    }
                                }
                                Icon(
                                    if (mode == CameraMode.PHOTO) Icons.Default.PhotoCamera else if (recording != null) Icons.Default.Stop else Icons.Default.Videocam,
                                    shutterActionLabel
                                )
                            }
                        }
                        Spacer(Modifier.width(52.dp))
                    }


                }
            }
        }

        if (galleryImporting) {
            Surface(modifier = Modifier.align(Alignment.Center), shape = MaterialTheme.shapes.large, color = Color(0xEE111218)) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text("Preparing selected media…", color = Color.White)
                }
            }
        }

        if (showCameraMusicPicker) {
            AlertDialog(
                onDismissRequest = { showCameraMusicPicker = false; cameraMusicPreviewId = null },
                title = { Text("Add sound") },
                text = {
                    Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = cameraMusicSearch, onValueChange = { cameraMusicSearch = it.take(80) },
                            modifier = Modifier.fillMaxWidth(), label = { Text("Search published FYNX music") }, singleLine = true
                        )
                        when {
                            cameraMusicLoading -> Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text("Loading music…")
                            }
                            cameraMusicError != null -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(cameraMusicError.orEmpty(), color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = { cameraMusicRetryKey++ }) { Text("Retry") }
                            }
                            cameraMusicTracks.isEmpty() -> Text("No published FYNX music was found.")
                            else -> cameraMusicTracks.forEach { track ->
                                Column(
                                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                    Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(onClick = { cameraMusicPreviewId = if (cameraMusicPreviewId == track.id) null else track.id }) {
                                            Text(if (cameraMusicPreviewId == track.id) "Stop" else "Preview")
                                        }
                                        if (cameraMusicPreviewId == track.id) {
                                            FynxRemoteAudio("/api/social/music/catalogue/" + track.id + "/media", Modifier.width(100.dp), track.durationMs.coerceAtLeast(1_000L))
                                        }
                                        Spacer(Modifier.weight(1f))
                                        Button(onClick = {
                                            selectedCameraMusic = track
                                            onMusicSelected(track)
                                            cameraMusicPreviewId = null
                                            showCameraMusicPicker = false
                                        }) { Text("Use sound") }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showCameraMusicPicker = false; cameraMusicPreviewId = null }) { Text("Done") } }
            )
        }
    }
}
@Composable
private fun FynxCameraRailAction(
    title: String,
    glyph: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val actionIcon = when (title) {
        "Flash" -> Icons.Default.FlashOn
        "Timer" -> Icons.Default.Timer
        "Grid" -> Icons.Default.GridOn
        "Effects" -> Icons.Default.AutoAwesome
        "Adjust" -> Icons.Default.Tune
        "Gallery" -> Icons.Default.PhotoLibrary
        else -> null
    }
    Column(
        modifier = Modifier.width(58.dp).clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = title; role = Role.Button }.padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Surface(
            modifier = Modifier.size(40.dp), shape = androidx.compose.foundation.shape.CircleShape,
            color = if (selected) Color(0xFFD92D79) else Color(0xCC151720)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (actionIcon != null) Icon(actionIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(21.dp))
                else Text(glyph, color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
        }
        Text(title, color = if (enabled) Color.White else Color.White.copy(alpha = 0.45f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

internal object FynxCameraRecordingDuration {
    const val FIFTEEN_SECONDS_MS = 15_000L
    const val SIXTY_SECONDS_MS = 60_000L
    const val THREE_MINUTES_MS = 180_000L
    const val TEN_MINUTES_MS = 600_000L
    val videoOptions: List<Pair<Long, String>> = listOf(
        FIFTEEN_SECONDS_MS to "15s",
        SIXTY_SECONDS_MS to "60s",
        THREE_MINUTES_MS to "3m",
        TEN_MINUTES_MS to "10m"
    )
    fun maximumDurationMs(videoNoteMode: Boolean, statusCapture: Boolean, mode: CameraMode, selectedVideoDurationMs: Long): Long? = when {
        videoNoteMode -> SIXTY_SECONDS_MS
        statusCapture -> FYNX_STATUS_MAX_VIDEO_DURATION_MS
        mode != CameraMode.VIDEO -> null
        videoOptions.any { it.first == selectedVideoDurationMs } -> selectedVideoDurationMs
        else -> SIXTY_SECONDS_MS
    }
}

private fun normalizeImportedPhotoToJpeg(file: File) {
    val orientation = runCatching {
        ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val rotation = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: error("FYNX could not decode the selected photo.")
    val normalized = if (rotation != 0f) {
        android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation) }, true)
    } else bitmap
    try {
        FileOutputStream(file).use { output ->
            check(normalized.compress(android.graphics.Bitmap.CompressFormat.JPEG, 94, output)) { "The selected photo could not be prepared." }
        }
    } finally {
        if (normalized !== bitmap) normalized.recycle()
        bitmap.recycle()
    }
    runCatching {
        ExifInterface(file.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            saveAttributes()
        }
    }
}


@OptIn(UnstableApi::class)
private fun fynxCameraLiveEffects(filter: CameraFilter): List<Effect> = when (filter) {
    CameraFilter.NATURAL -> emptyList()
    CameraFilter.PORTRAIT -> listOf(
        Contrast(0.04f),
        RgbAdjustment.Builder().setRedScale(1.02f).setGreenScale(1.0f).setBlueScale(0.98f).build()
    )
    CameraFilter.BRIGHT -> listOf(Brightness(0.10f), Contrast(0.04f))
    CameraFilter.WARM -> listOf(
        Brightness(0.025f),
        RgbAdjustment.Builder().setRedScale(1.12f).setGreenScale(1.02f).setBlueScale(0.90f).build()
    )
    CameraFilter.COOL -> listOf(
        RgbAdjustment.Builder().setRedScale(0.90f).setGreenScale(1.02f).setBlueScale(1.12f).build()
    )
    CameraFilter.VINTAGE -> listOf(
        Brightness(0.025f), Contrast(-0.06f),
        RgbAdjustment.Builder().setRedScale(1.08f).setGreenScale(1.0f).setBlueScale(0.88f).build()
    )
    CameraFilter.BW -> listOf(RgbFilter.createGrayscaleFilter(), Contrast(0.06f))
}

private fun formatCameraRecordingTime(milliseconds:Long):String{val totalSeconds=milliseconds/1000L;return "%02d:%02d".format(totalSeconds/60L,totalSeconds%60L)}
enum class CameraMode{PHOTO,VIDEO}
enum class CameraFilter(val label:String,val saturation:Float,val brightness:Float,val contrast:Float){
    NATURAL("Natural",1f,0f,1f),
    PORTRAIT("Portrait",0.96f,0.025f,1.01f),
    BRIGHT("Bright",1.03f,0.08f,1.02f),
    WARM("Warm",1.10f,0.04f,1.02f),
    COOL("Cool",0.92f,0.02f,1.02f),
    VINTAGE("Vintage",0.82f,0.015f,0.94f),
    BW("B&W",0f,0f,1.08f)
}
private fun ColorMatrix.setFynxFilter(saturation:Float,brightness:Float,contrast:Float,alpha:Float){setSaturation(saturation);val scale=contrast;val translate=brightness*255f;postConcat(ColorMatrix(floatArrayOf(scale,0f,0f,0f,translate,0f,scale,0f,0f,translate,0f,0f,scale,0f,translate,0f,0f,0f,alpha,0f)))}
private fun normalizeCapturedPhoto(file: File, mirrorFront: Boolean) {
    val exif = ExifInterface(file.absolutePath)
    val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    val rotation = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (!mirrorFront && rotation == 0f) return
    val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: error("Unable to decode captured photo")
    val matrix = Matrix().apply {
        if (rotation != 0f) postRotate(rotation)
        if (mirrorFront) postScale(-1f, 1f)
    }
    val normalized = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    FileOutputStream(file).use {
        check(normalized.compress(android.graphics.Bitmap.CompressFormat.JPEG, 94, it)) { "Unable to save normalized photo" }
    }
    bitmap.recycle()
    normalized.recycle()
    ExifInterface(file.absolutePath).apply {
        setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        saveAttributes()
    }
}

internal data class FynxCameraDrawPoint(val x: Float, val y: Float)
private data class FynxCameraDrawStroke(val points: List<FynxCameraDrawPoint>, val colorArgb: Int)

internal fun normalizeFynxCameraDrawPoint(
    x: Float, y: Float, canvasWidth: Float, canvasHeight: Float, imageWidth: Float, imageHeight: Float
): FynxCameraDrawPoint? {
    if (canvasWidth <= 0f || canvasHeight <= 0f || imageWidth <= 0f || imageHeight <= 0f) return null
    if (!x.isFinite() || !y.isFinite()) return null
    val scale = minOf(canvasWidth / imageWidth, canvasHeight / imageHeight)
    if (!scale.isFinite() || scale <= 0f) return null
    val renderedWidth = imageWidth * scale
    val renderedHeight = imageHeight * scale
    val left = (canvasWidth - renderedWidth) / 2f
    val top = (canvasHeight - renderedHeight) / 2f
    if (x < left || x > left + renderedWidth || y < top || y > top + renderedHeight) return null
    return FynxCameraDrawPoint(
        ((x - left) / renderedWidth).coerceIn(0f, 1f),
        ((y - top) / renderedHeight).coerceIn(0f, 1f)
    )
}

private fun DrawScope.drawFynxCameraDrawingStrokes(
    strokes: List<FynxCameraDrawStroke>, imageWidth: Int, imageHeight: Int
) {
    if (imageWidth <= 0 || imageHeight <= 0) return
    val scale = minOf(size.width / imageWidth.toFloat(), size.height / imageHeight.toFloat())
    val renderedWidth = imageWidth * scale
    val renderedHeight = imageHeight * scale
    val left = (size.width - renderedWidth) / 2f
    val top = (size.height - renderedHeight) / 2f
    val penWidth = (minOf(renderedWidth, renderedHeight) * 0.012f).coerceAtLeast(2f)
    strokes.forEach { stroke ->
        if (stroke.points.isEmpty()) return@forEach
        val path = Path()
        stroke.points.forEachIndexed { index, point ->
            val x = left + point.x * renderedWidth
            val y = top + point.y * renderedHeight
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val color = Color(stroke.colorArgb)
        if (stroke.points.size == 1) {
            drawCircle(color, penWidth / 2f, Offset(
                left + stroke.points.first().x * renderedWidth,
                top + stroke.points.first().y * renderedHeight
            ))
        } else {
            drawPath(path, color, style = Stroke(width = penWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

private fun wrapFynxCameraOverlayText(
    text: String,
    paint: android.graphics.Paint,
    maxWidth: Float
): List<String> = buildList {
    text.lines().forEach { originalLine ->
        var remaining = originalLine
        while (remaining.isNotEmpty() && size < 4) {
            val count = paint.breakText(remaining, true, maxWidth, null).coerceAtLeast(1)
            add(remaining.take(count))
            remaining = remaining.drop(count).trimStart()
        }
    }
}.take(4)

private fun DrawScope.drawFynxCameraPhotoPreviewOverlays(
    textOverlay: String,
    sticker: String?,
    imageWidth: Int,
    imageHeight: Int
) {
    if (imageWidth <= 0 || imageHeight <= 0 || (textOverlay.isBlank() && sticker.isNullOrBlank())) return
    val imageScale = minOf(size.width / imageWidth.toFloat(), size.height / imageHeight.toFloat())
    if (!imageScale.isFinite() || imageScale <= 0f) return
    val renderedWidth = imageWidth * imageScale
    val renderedHeight = imageHeight * imageScale
    val left = (size.width - renderedWidth) / 2f
    val top = (size.height - renderedHeight) / 2f
    val canvas = drawContext.canvas.nativeCanvas
    val save = canvas.save()
    try {
        canvas.translate(left, top)
        canvas.scale(imageScale, imageScale)
        if (textOverlay.isNotBlank()) {
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = (imageWidth * 0.055f).coerceIn(24f, 84f)
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.create(
                    "sans-serif-medium", android.graphics.Typeface.BOLD
                )
                setShadowLayer(5f, 1f, 2f, android.graphics.Color.BLACK)
            }
            val lines = wrapFynxCameraOverlayText(textOverlay, paint, imageWidth * 0.82f)
            val lineHeight = paint.textSize * 1.18f
            val firstBaseline = imageHeight * 0.43f - (lines.size - 1) * lineHeight / 2f
            lines.forEachIndexed { index, line ->
                canvas.drawText(line, imageWidth / 2f, firstBaseline + index * lineHeight, paint)
            }
        }
        if (!sticker.isNullOrBlank()) {
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = (imageWidth * 0.15f).coerceIn(56f, 180f)
                textAlign = android.graphics.Paint.Align.CENTER
                setShadowLayer(7f, 1f, 2f, android.graphics.Color.BLACK)
            }
            canvas.drawText(sticker, imageWidth * 0.78f, imageHeight * 0.82f, paint)
        }
    } finally {
        canvas.restoreToCount(save)
    }
}

internal data class FynxCameraCropRect(
    val left: Int,
    val top: Int,
    val cropWidth: Int,
    val cropHeight: Int
)

internal fun calculateFynxCameraCropRect(
    sourceWidth: Int,
    sourceHeight: Int,
    targetAspectRatio: Float
): FynxCameraCropRect? {
    if (sourceWidth <= 0 || sourceHeight <= 0 || !targetAspectRatio.isFinite() || targetAspectRatio <= 0f) {
        return null
    }
    val sourceAspectRatio = sourceWidth.toFloat() / sourceHeight.toFloat()
    return if (sourceAspectRatio > targetAspectRatio) {
        val cropHeight = sourceHeight
        val cropWidth = (cropHeight * targetAspectRatio).toInt().coerceIn(1, sourceWidth)
        FynxCameraCropRect(
            left = (sourceWidth - cropWidth) / 2,
            top = 0,
            cropWidth = cropWidth,
            cropHeight = cropHeight
        )
    } else {
        val cropWidth = sourceWidth
        val cropHeight = (cropWidth / targetAspectRatio).toInt().coerceIn(1, sourceHeight)
        FynxCameraCropRect(
            left = 0,
            top = (sourceHeight - cropHeight) / 2,
            cropWidth = cropWidth,
            cropHeight = cropHeight
        )
    }
}
