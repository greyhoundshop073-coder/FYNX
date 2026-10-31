package com.fynx.app.ui

import com.fynx.app.R

import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.ColorMatrix as ComposeColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import android.graphics.RectF
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.UseCase
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
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

private const val MAX_CAMERA_GALLERY_IMAGE_BYTES = 96L * 1024 * 1024
private const val MAX_CAMERA_GALLERY_VIDEO_BYTES = 512L * 1024 * 1024

/**
 * Copies selected media into app-owned cache before preview/editing. The picker URI is never
 * uploaded automatically; the existing composer callback runs only after the user taps Send.
 * Returns (working file, untouched original, semantic media type).
 */
private fun copyCameraGalleryMediaToCache(context: Context, uri: Uri): Triple<File, File, String> {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri)?.substringBefore(';')?.trim()?.lowercase()
        ?: error("The selected media type could not be determined.")
    val type = when {
        mime.startsWith("image/") -> "image"
        mime.startsWith("video/") -> "video"
        else -> error("Choose an image or video from Gallery.")
    }
    val extension = if (type == "image") {
        // Normalization writes JPEG pixels, so keep the cache extension and actual MIME aligned.
        "jpg"
    } else {
        MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            ?.filter { it.isLetterOrDigit() }
            ?.take(10)
            ?.takeIf { it.isNotBlank() }
            ?: "mp4"
    }
    val original = File.createTempFile("fynx_gallery_source_", ".$extension", context.cacheDir)
    var workingCopy: File? = null
    try {
        val input = resolver.openInputStream(uri)
            ?: error("The selected Gallery item could not be opened.")
        val maxBytes = if (type == "image") MAX_CAMERA_GALLERY_IMAGE_BYTES else MAX_CAMERA_GALLERY_VIDEO_BYTES
        input.use { source ->
            FileOutputStream(original).use { output ->
                val buffer = ByteArray(64 * 1024)
                var totalBytes = 0L
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    totalBytes += count.toLong()
                    require(totalBytes <= maxBytes) {
                        if (type == "image") "The selected photo is too large (maximum 96 MB)."
                        else "The selected video is too large (maximum 512 MB)."
                    }
                    output.write(buffer, 0, count)
                }
            }
        }
        require(original.length() > 0L) { "The selected Gallery item is empty." }
        val working = if (type == "image") {
            val copy = File.createTempFile("fynx_gallery_work_", ".$extension", context.cacheDir)
            try {
                original.copyTo(copy, overwrite = true)
            } catch (failure: Exception) {
                copy.delete()
                throw failure
            }
            copy
        } else {
            original
        }
        workingCopy = working
        if (type == "image") {
            // Normalize EXIF orientation and cache both versions as correctly labelled JPEGs.
            normalizeCapturedPhoto(working, mirrorFront = false, forceJpeg = true)
            if (working != original) working.copyTo(original, overwrite = true)
        }
        return Triple(working, original, type)
    } catch (failure: Exception) {
        workingCopy?.takeIf { it != original }?.delete()
        original.delete()
        throw failure
    }
}

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
    statusCapture: Boolean = false
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
    var bindGeneration by remember { mutableIntStateOf(0) }
    var torchEnabled by remember { mutableStateOf(false) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var exposure by remember { mutableIntStateOf(0) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var activeRecordingFile by remember { mutableStateOf<File?>(null) }
    var recordingNotice by remember { mutableStateOf<String?>(null) }
    var recordingStartedAt by remember { mutableLongStateOf(0L) }
    var recordingElapsed by remember { mutableLongStateOf(0L) }
    BackHandler { if (recording == null) onDismiss() }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var pendingOriginalUri by remember { mutableStateOf<Uri?>(null) }
    var pendingType by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf(CameraFilter.NATURAL) }
    var faceEffect by remember { mutableStateOf(CameraFaceEffect.OFF) }
    var backgroundEffect by remember { mutableStateOf(CameraBackgroundEffect.OFF) }
    var studioEffect by remember { mutableStateOf(CameraStudioEffect.OFF) }
    var videoStyle by remember { mutableStateOf(CameraVideoStyle.NATURAL) }
    var videoCaptionText by remember { mutableStateOf("") }
    var applyingVideoEffects by remember { mutableStateOf(false) }
    var generatingCaptions by remember { mutableStateOf(false) }
    var trackedFaceBounds by remember { mutableStateOf<List<CameraFaceBounds>>(emptyList()) }
    var filterIntensity by remember { mutableFloatStateOf(1f) }
    var enhancing by remember { mutableStateOf(false) }
    var applyingFilter by remember { mutableStateOf(false) }
    var showCaptureControls by remember { mutableStateOf(true) }
    var showEffectTray by remember { mutableStateOf(false) }
    var captureTimerSeconds by remember { mutableIntStateOf(0) }
    var timerCountingDown by remember { mutableIntStateOf(0) }
    var showGrid by remember { mutableStateOf(false) }
    var selectedCameraTool by remember { mutableStateOf("Looks") }
    var showAdjustments by remember { mutableStateOf(false) }
    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { selectedUri ->
        if (selectedUri != null) {
            error = null
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { copyCameraGalleryMediaToCache(context, selectedUri) }
                }
                result.onSuccess { (working, original, selectedType) ->
                    pendingUri = Uri.fromFile(working)
                    pendingOriginalUri = Uri.fromFile(original)
                    pendingType = if (selectedType == "video" && videoNoteMode) "video_note" else selectedType
                    selectedCameraTool = "Looks"
                    filter = CameraFilter.NATURAL
                    faceEffect = CameraFaceEffect.OFF
                    backgroundEffect = CameraBackgroundEffect.OFF
                    studioEffect = CameraStudioEffect.OFF
                    videoStyle = CameraVideoStyle.NATURAL
                    videoCaptionText = ""
                    filterIntensity = 1f
                    trackedFaceBounds = emptyList()
                    showCaptureControls = true
                }.onFailure {
                    error = it.message ?: "The selected media could not be opened."
                }
            }
        }
    }

    val faceAnalysisEnabled = faceEffect != CameraFaceEffect.OFF && mode == CameraMode.PHOTO && pendingUri == null
    val faceAnalyzer = remember(context, previewView) {
        FynxCameraFaceAnalyzer(
            context = context,
            previewView = previewView,
            mainExecutor = ContextCompat.getMainExecutor(context),
            isActive = { faceEffect != CameraFaceEffect.OFF && mode == CameraMode.PHOTO && pendingUri == null },
            isFrontCamera = { lens == CameraSelector.LENS_FACING_FRONT },
            onFaces = { trackedFaceBounds = it }
        )
    }
    val faceImageAnalysis = remember(faceAnalyzer) {
        ImageAnalysis.Builder()
            .setTargetResolution(android.util.Size(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(faceAnalyzer.executor, faceAnalyzer) }
    }
    LaunchedEffect(faceEffect) { if (faceEffect == CameraFaceEffect.OFF) trackedFaceBounds = emptyList() }
    // Do not apply Android RenderEffect to PreviewView's internal TextureView. On Android 15
    // emulator/device camera rebinding can leave the TextureView above Compose, corrupt the
    // preview and swallow Gallery/flash/capture controls. Looks are applied to the captured
    // image in the editor, so keep the live preview clean and the controls reliably interactive.

    LaunchedEffect(recording, recordingStartedAt, videoNoteMode, statusCapture, activeRecordingFile) {
        val active = recording ?: return@LaunchedEffect
        val maximumDurationMs = when {
            videoNoteMode -> 60_000L
            statusCapture -> FYNX_STATUS_MAX_VIDEO_DURATION_MS
            else -> null
        }
        while (recording == active) {
            recordingElapsed = (System.currentTimeMillis() - recordingStartedAt).coerceAtLeast(0L)
            val sizeLimitReached = statusCapture && (activeRecordingFile?.length() ?: 0L) >= FYNX_STATUS_MAX_VIDEO_BYTES
            if (sizeLimitReached) {
                recordingNotice = "Status video reached the upload-size limit. Your clip is ready to preview."
                active.stop()
                break
            }
            if (maximumDurationMs != null && recordingElapsed >= maximumDurationMs) {
                if (statusCapture) recordingNotice = "Status video reached the time limit. Your clip is ready to preview."
                active.stop()
                break
            }
            delay(200L)
        }
    }
    // Keep the shutter and capture tools visible; only the self-timer deliberately hides them during countdown.
    LaunchedEffect(timerCountingDown) { if (timerCountingDown > 0) { delay(1000L); timerCountingDown -= 1 } }
    LaunchedEffect(hasCamera, lens, mode, pendingUri, lifecycleOwner, faceAnalysisEnabled) {
        val generation = bindGeneration + 1; bindGeneration = generation
        if (!hasCamera || pendingUri != null) { cameraProvider?.unbindAll(); imageCapture = null; videoCapture = null; cameraControl = null; cameraInfo = null; return@LaunchedEffect }
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
                    QualitySelector.fromOrderedList(listOf(Quality.SD, Quality.HD), FallbackStrategy.lowerQualityOrHigherThan(Quality.HD))
                } else {
                    QualitySelector.fromOrderedList(listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD), FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD))
                }
                val recorderBuilder = Recorder.Builder().setQualitySelector(qualitySelector)
                if (statusCapture) recorderBuilder.setTargetVideoEncodingBitRate(700_000)
                val recorder = recorderBuilder.build()
                val video = VideoCapture.Builder(recorder)
                    .setTargetRotation(targetRotation)
                    .setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY)
                    .build()
                provider.unbindAll(); if (generation != bindGeneration || pendingUri != null) return@runCatching
                val useCases = mutableListOf<UseCase>(preview)
                if (mode == CameraMode.PHOTO) useCases += capture else useCases += video
                if (faceAnalysisEnabled && mode == CameraMode.PHOTO) useCases += faceImageAnalysis
                val camera = provider.bindToLifecycle(lifecycleOwner, selector, *useCases.toTypedArray())
                cameraProvider = provider; imageCapture = capture; videoCapture = video; cameraControl = camera.cameraControl; cameraInfo = camera.cameraInfo
                cameraControl?.setZoomRatio(zoomRatio.coerceIn(1f, 4f)); val range = cameraInfo?.exposureState?.exposureCompensationRange
                exposure = exposure.coerceIn(range?.lower ?: -2, range?.upper ?: 2); cameraControl?.setExposureCompensationIndex(exposure)
                if (torchEnabled && cameraInfo?.hasFlashUnit() == true) cameraControl?.enableTorch(true) else if (torchEnabled) torchEnabled = false
                error = null
            }.onFailure { if (generation == bindGeneration) error = it.message ?: "Camera could not start" }
        }, ContextCompat.getMainExecutor(context))
    }
    DisposableEffect(Unit) { onDispose { recording?.stop(); cameraProvider?.unbindAll(); faceAnalyzer.close() } }
    fun deleteUri(uri: Uri?) { if (uri?.scheme == "file") File(uri.path ?: "").delete() }
    fun retake() { deleteUri(pendingUri); deleteUri(pendingOriginalUri); pendingUri = null; pendingOriginalUri = null; pendingType = null; filter = CameraFilter.NATURAL; faceEffect = CameraFaceEffect.OFF; backgroundEffect = CameraBackgroundEffect.OFF; studioEffect = CameraStudioEffect.OFF; videoStyle = CameraVideoStyle.NATURAL; videoCaptionText = ""; recordingNotice = null; activeRecordingFile = null; selectedCameraTool = "Looks"; applyingVideoEffects = false; generatingCaptions = false; filterIntensity = 1f; enhancing = false; applyingFilter = false; error = null }
    fun rotatePhoto() {
        val currentUri = pendingUri ?: return
        if (pendingType != "image") return
        val currentFile = currentUri.path?.let(::File) ?: return
        val originalFile = pendingOriginalUri?.path?.let(::File)
        runCatching {
            // Rotate the editing baseline too, so changing an effect after Rotate cannot restore
            // the old orientation. Write via a same-directory temporary file before replacement.
            listOfNotNull(originalFile, currentFile)
                .distinctBy { it.absolutePath }
                .forEach(::rotateCameraPhotoFile)
        }.onFailure { error = it.message ?: "Photo rotation failed" }
    }
    fun applyFilterToPhoto(
        selected: CameraFilter,
        intensity: Float = filterIntensity,
        faceEffectOverride: CameraFaceEffect? = null,
        backgroundEffectOverride: CameraBackgroundEffect? = null,
        studioEffectOverride: CameraStudioEffect? = null
    ) {
        val current = pendingUri ?: return
        val original = pendingOriginalUri ?: current
        if (pendingType != "image" || applyingFilter) return
        val source = original.path?.let { File(it) } ?: return
        val target = current.path?.let { File(it) } ?: return
        val strength = intensity.coerceIn(0f, 1f)
        // Explicit overrides avoid reading the previous Compose state after a chip tap.
        val selectedFaceEffect = faceEffectOverride ?: faceEffect
        val selectedBackgroundEffect = backgroundEffectOverride ?: backgroundEffect
        val selectedStudioEffect = studioEffectOverride ?: studioEffect
        filter = selected
        filterIntensity = strength
        applyingFilter = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    applyCameraColorFilterToPhotoFile(source, target, selected, strength)
                    if (selectedBackgroundEffect != CameraBackgroundEffect.OFF) {
                        applyCameraBackgroundEffectToPhotoFile(target, selectedBackgroundEffect)
                    }
                    if (selectedStudioEffect != CameraStudioEffect.OFF) {
                        applyCameraStudioEffectToPhotoFile(target, selectedStudioEffect)
                    }
                    if (selectedFaceEffect != CameraFaceEffect.OFF) {
                        applyCameraFaceEffectToPhotoFile(target, selectedFaceEffect)
                    }
                }
            }
            result.onFailure { error = it.message ?: "Filter could not be applied" }
            applyingFilter = false
        }
    }
    fun enhancePhoto() {
        val source = pendingUri ?: return
        if (pendingType != "image" || enhancing) return
        enhancing = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                FynxAiPhotoEnhancer.enhance(context, source).mapCatching { enhancedUri ->
                    val enhancedFile = File(enhancedUri.path ?: error("Enhanced photo path is missing"))
                    val baselineFile = File.createTempFile("fynx_smart_enhanced_base_", ".jpg", context.cacheDir)
                    try {
                        enhancedFile.copyTo(baselineFile, overwrite = true)
                        // Separate output and baseline files so later effect changes always start
                        // from a clean enhanced image instead of mutating their own source.
                        enhancedUri to Uri.fromFile(baselineFile)
                    } catch (failure: Exception) {
                        baselineFile.delete()
                        enhancedFile.delete()
                        throw failure
                    }
                }
            }
            result.onSuccess { (enhancedUri, baselineUri) ->
                val previousOriginal = pendingOriginalUri
                if (enhancedUri != source) deleteUri(source)
                if (previousOriginal != null && previousOriginal != source && previousOriginal != enhancedUri) {
                    deleteUri(previousOriginal)
                }
                // The enhancement already includes the active image edits. Make its separate
                // baseline the reset point and clear baked selections so edits are not duplicated.
                pendingUri = enhancedUri
                pendingOriginalUri = baselineUri
                filter = CameraFilter.NATURAL
                faceEffect = CameraFaceEffect.OFF
                backgroundEffect = CameraBackgroundEffect.OFF
                studioEffect = CameraStudioEffect.OFF
                filterIntensity = 1f
                trackedFaceBounds = emptyList()
                error = null
            }.onFailure { error = it.message ?: "AI photo enhancement failed" }
            enhancing = false
        }
    }
    fun generateCaptionsForVideo() {
        val current = pendingUri ?: return
        if (pendingType != "video" && pendingType != "video_note") return
        val source = pendingOriginalUri ?: current
        generatingCaptions = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { generateFynxVideoTranscript(context, source) } }
            result.onSuccess {
                videoCaptionText = it
                error = "Transcript ready. Caption timing is approximate; review it before sending."
            }.onFailure { error = it.message ?: "Automatic captions could not be generated." }
            generatingCaptions = false
        }
    }

    fun applyVideoEdits() {
        val current = pendingUri ?: return
        if (pendingType != "video" && pendingType != "video_note") return
        if (videoStyle == CameraVideoStyle.NATURAL && videoCaptionText.isBlank()) {
            error = "Choose a video style or generate captions first."
            return
        }
        val source = pendingOriginalUri ?: current
        applyingVideoEffects = true
        error = null
        scope.launch {
            // Media3 Transformer is main-thread confined for construction/start/callback wiring.
            // It performs export work asynchronously itself; do not invoke it from Dispatchers.IO.
            val result = runCatching { applyFynxVideoEdits(context, source, videoStyle, videoCaptionText) }
            result.onSuccess { output ->
                if (current != source) deleteUri(current)
                pendingUri = output
                error = null
            }.onFailure { error = it.message ?: "Video effects could not be applied." }
            applyingVideoEffects = false
        }
    }

    fun capturePhotoNow() {
        val capture = imageCapture ?: run { error = "Camera is still starting"; return }
        val stamp = System.currentTimeMillis()
        val file = File(context.cacheDir, "fynx_photo_"+stamp+".jpg")
        val original = File(context.cacheDir, "fynx_photo_original_"+stamp+".jpg")
        capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                runCatching { normalizeCapturedPhoto(file, lens == CameraSelector.LENS_FACING_FRONT); file.copyTo(original, overwrite = true) }
                    .onSuccess {
                    pendingUri = Uri.fromFile(file)
                    pendingOriginalUri = Uri.fromFile(original)
                    pendingType = "image"
                    selectedCameraTool = "Looks"
                    error = null
                    showCaptureControls = true
                    if (filter != CameraFilter.NATURAL || faceEffect != CameraFaceEffect.OFF || backgroundEffect != CameraBackgroundEffect.OFF || studioEffect != CameraStudioEffect.OFF) applyFilterToPhoto(filter, filterIntensity)
                }
                    .onFailure { file.delete(); original.delete(); error = it.message ?: "Photo could not be prepared"; showCaptureControls = true }
            }
            override fun onError(exception: ImageCaptureException) { error = exception.message ?: "Photo capture failed"; showCaptureControls = true }
        })
    }

    if (!hasCamera) { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Text("FYNX needs camera access to capture photos and videos.");Spacer(Modifier.height(12.dp));Button(onClick={permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO))}){Text("Allow camera")};TextButton(onClick=onDismiss){Text("Close")}}};return }
    val previewUri=pendingUri; val previewType=pendingType
    if(previewUri!=null && previewType!=null){
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)){
            if(previewType=="image") AndroidView(factory={android.widget.ImageView(it).apply{scaleType=android.widget.ImageView.ScaleType.FIT_CENTER}},update={it.setImageURI(previewUri)},modifier=Modifier.fillMaxSize()) else AndroidView(factory={android.widget.VideoView(it).apply{setVideoURI(previewUri);setOnPreparedListener{p->p.isLooping=true;start()}}},update={view->if(view.tag!=previewUri.toString()){view.tag=previewUri.toString();view.setVideoURI(previewUri);view.start()}},modifier=Modifier.fillMaxSize())
            Column(
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .background(Color(0xF20A0C12), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 30.dp)
            ) {
                error?.let{Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(bottom=8.dp))};recordingNotice?.let{Text(it,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(bottom=8.dp))}
                Column(Modifier.heightIn(max = LocalConfiguration.current.screenHeightDp.dp * if (WindowInsets.ime.getBottom(LocalDensity.current) > 0) 0.18f else 0.30f).verticalScroll(rememberScrollState())) {
                if (previewType == "image") {
                    Text("Edit photo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("Looks", "Face FX", "Background", "AI Studio").forEach { category ->
                            FilterChip(
                                selected = selectedCameraTool == category,
                                onClick = { selectedCameraTool = category },
                                label = { Text(category) }
                            )
                        }
                    }
                    when (selectedCameraTool) {
                        "Face FX" -> {
                            Text("Face FX", style = MaterialTheme.typography.labelLarge)
                            Text("Face-tracked photo effects", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                CameraFaceEffect.values().forEach { option ->
                                    CameraEffectTile(
                                        category = "Face FX",
                                        label = option.label,
                                        previewStyle = cameraEffectPreviewStyle("Face FX", option.label),
                                        selected = faceEffect == option,
                                        enabled = !enhancing && !applyingFilter,
                                        onClick = {
                                            faceEffect = option
                                            applyFilterToPhoto(filter, filterIntensity, faceEffectOverride = option)
                                        },
                                        modifier = Modifier.width(78.dp)
                                    )
                                }
                            }
                        }
                        "Background" -> {
                            Text("Background", style = MaterialTheme.typography.labelLarge)
                            Text("Portrait blur and scene replacements · applied after capture", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                CameraBackgroundEffect.values().forEach { option ->
                                    CameraEffectTile(
                                        category = "Background",
                                        label = option.label,
                                        previewStyle = cameraEffectPreviewStyle("Background", option.label),
                                        selected = backgroundEffect == option,
                                        enabled = !enhancing && !applyingFilter,
                                        onClick = {
                                            backgroundEffect = option
                                            applyFilterToPhoto(filter, filterIntensity, backgroundEffectOverride = option)
                                        },
                                        modifier = Modifier.width(78.dp)
                                    )
                                }
                            }
                            if (backgroundEffect != CameraBackgroundEffect.OFF) {
                                Text("Subject separation runs on-device; no camera image is uploaded for this effect.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        "AI Studio" -> {
                            Text("AI Studio", style = MaterialTheme.typography.labelLarge)
                            Text("Local photo styling · applied after capture", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                CameraStudioEffect.values().forEach { option ->
                                    CameraEffectTile(
                                        category = "AI Studio",
                                        label = option.label,
                                        previewStyle = cameraEffectPreviewStyle("AI Studio", option.label),
                                        selected = studioEffect == option,
                                        enabled = !enhancing && !applyingFilter,
                                        onClick = {
                                            studioEffect = option
                                            applyFilterToPhoto(filter, filterIntensity, studioEffectOverride = option)
                                        },
                                        modifier = Modifier.width(78.dp)
                                    )
                                }
                            }
                            Text("Mature Look is a stylized edit, not a genuine age-prediction model.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        else -> {
                            Text("Looks", style = MaterialTheme.typography.labelLarge)
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                CameraFilter.values().forEach { option ->
                                    CameraEffectTile(
                                        category = "Looks",
                                        label = option.label,
                                        previewStyle = cameraEffectPreviewStyle("Looks", option.label),
                                        selected = filter == option,
                                        enabled = !enhancing && !applyingFilter,
                                        onClick = { applyFilterToPhoto(option, filterIntensity) },
                                        modifier = Modifier.width(78.dp)
                                    )
                                }
                            }
                            if (filter != CameraFilter.NATURAL) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Intensity", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(64.dp))
                                    Slider(
                                        value = filterIntensity,
                                        onValueChange = { filterIntensity = it },
                                        onValueChangeFinished = { applyFilterToPhoto(filter, filterIntensity) },
                                        valueRange = 0f..1f,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                    if (applyingFilter) {
                        Text("Applying selected effect on this device…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    OutlinedButton(
                        onClick = ::enhancePhoto,
                        enabled = !enhancing && !applyingFilter,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Text(if (enhancing) "Enhancing…" else "✨ Auto Enhance")
                    }
                }
                else {
                    Text("Video styles", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CameraVideoStyle.values().forEach { option ->
                            CameraEffectTile(
                                category = "Video FX",
                                label = option.label,
                                previewStyle = cameraEffectPreviewStyle("Video FX", option.label),
                                selected = videoStyle == option,
                                enabled = !applyingVideoEffects && !generatingCaptions,
                                onClick = { videoStyle = option },
                                modifier = Modifier.width(78.dp)
                            )
                        }
                    }
                    OutlinedButton(onClick = ::generateCaptionsForVideo, enabled = !generatingCaptions && !applyingVideoEffects, modifier = Modifier.fillMaxWidth()) {
                        Text(if (generatingCaptions) "Generating captions…" else "✦ Generate on-device captions")
                    }
                    if (videoCaptionText.isNotBlank()) {
                        OutlinedTextField(
                            value = videoCaptionText, onValueChange = { videoCaptionText = it },
                            label = { Text("Review/edit transcript") },
                            supportingText = { Text("Local transcription. Caption timing is estimated; review before sending.") },
                            minLines = 2, maxLines = 4, enabled = !applyingVideoEffects, modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text("Captions require Android 12+ and a compatible on-device speech model.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = ::applyVideoEdits, enabled = !applyingVideoEffects && !generatingCaptions && (videoStyle != CameraVideoStyle.NATURAL || videoCaptionText.isNotBlank()), modifier = Modifier.fillMaxWidth()) {
                        Text(if (applyingVideoEffects) "Processing video…" else "Apply style / captions")
                    }
                }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){OutlinedButton(onClick={if(!enhancing && !applyingFilter && !applyingVideoEffects && !generatingCaptions)retake()},enabled=!enhancing && !applyingFilter && !applyingVideoEffects && !generatingCaptions,modifier=Modifier.weight(1f).heightIn(min=52.dp)){Text("Retake")};if(previewType=="image")OutlinedButton(onClick={if(!enhancing && !applyingFilter)rotatePhoto()},enabled=!enhancing && !applyingFilter,modifier=Modifier.weight(1f).heightIn(min=52.dp)){Icon(Icons.Default.RotateRight,null);Spacer(Modifier.width(4.dp));Text("Rotate")};Button(onClick={if(!enhancing && !applyingFilter && !applyingVideoEffects && !generatingCaptions)onCaptured(previewUri,previewType)},enabled=!enhancing && !applyingFilter && !applyingVideoEffects && !generatingCaptions,modifier=Modifier.weight(1f).heightIn(min=52.dp)){Icon(Icons.Default.Send,null);Spacer(Modifier.width(4.dp));Text("Send")}}
            }
        };return
    }
    // Camera UI is deliberately dark so preview controls remain legible on bright or dark
    // camera feeds, and so Material chips use high-contrast labels over the preview.
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFB8A7FF),
            secondary = Color(0xFF72D8C9),
            background = Color(0xFF080A10),
            surface = Color(0xFF171A22),
            surfaceVariant = Color(0xFF292D38)
        )
    ) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).clickable { if (recording == null) showCaptureControls = true }){
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )
        if (faceAnalysisEnabled && faceEffect != CameraFaceEffect.OFF && trackedFaceBounds.isNotEmpty()) {
            Canvas(Modifier.fillMaxSize()) {
                val nativeCanvas = drawContext.canvas.nativeCanvas
                trackedFaceBounds.forEach { face ->
                    drawFynxCameraFaceEffect(
                        nativeCanvas,
                        RectF(face.left, face.top, face.right, face.bottom),
                        faceEffect
                    )
                }
            }
        }
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
        if (pendingUri == null && liveProofMode) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 62.dp, bottom = 10.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color.Black.copy(alpha = 0.62f)
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("LIVE PROOF", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text(liveProofListingTitle.ifBlank { "Marketplace item" }, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    if (liveProofSellerUsername.isNotBlank()) Text("Seller @${liveProofSellerUsername.removePrefix("@").trim()}", color = Color.White.copy(alpha = .82f), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (pendingUri == null) {
            Row(
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { if (recording == null) onDismiss() }, enabled = recording == null, modifier = Modifier.background(Color.Black.copy(alpha = 0.58f), androidx.compose.foundation.shape.CircleShape)) {
                    Icon(Icons.Default.Close, "Close camera")
                }
                if (!liveProofMode) {
                    profilePhotoId?.takeIf { it.isNotBlank() }?.let { photoId ->
                        FynxRemoteMedia("/api/media/$photoId", "image", Modifier.padding(start = 7.dp).size(28.dp), contentScale = androidx.compose.ui.layout.ContentScale.Crop, rounded = true)
                    } ?: Surface(
                        modifier = Modifier.padding(start = 7.dp).size(28.dp),
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = Color(0xFF343847)
                    ) {}
                    Text(
                        "FYNX",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 7.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                
            }
        }

        if (pendingUri == null && !fastCapture && recording == null) {
            Column(
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding()
                    .padding(end = 8.dp, top = 64.dp)
                    .width(58.dp)
                    .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(24.dp))
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = {
                            error = null
                            lens = if (lens == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                            showCaptureControls = true
                        },
                        modifier = Modifier.size(42.dp)
                    ) { Icon(Icons.Default.Cameraswitch, "Switch front/back camera", tint = Color.White) }
                    Text("Flip", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = {
                            if (cameraInfo?.hasFlashUnit() == true) {
                                torchEnabled = !torchEnabled
                                cameraControl?.enableTorch(torchEnabled)
                            } else error = "Flash is not available on this camera."
                            showCaptureControls = true
                        },
                        modifier = Modifier.size(42.dp)
                    ) { Icon(Icons.Default.FlashOn, if (torchEnabled) "Turn flash off" else "Turn flash on", tint = if (torchEnabled) Color(0xFFFFD76A) else Color.White) }
                    Text("Flash", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { showGrid = !showGrid; showCaptureControls = true },
                        modifier = Modifier.size(42.dp)
                    ) { Icon(if (showGrid) Icons.Default.GridOff else Icons.Default.GridOn, if (showGrid) "Hide grid" else "Show grid", tint = Color.White) }
                    Text("Grid", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { showAdjustments = !showAdjustments; showCaptureControls = true },
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(Icons.Default.Timer, contentDescription = "Set capture timer", tint = Color.White)
                    }
                    Text("Timer", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        if (showCaptureControls || recording != null) Column(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .background(Color(0xF20A0C12), RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            error?.let{Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(horizontal=18.dp, vertical=4.dp))};if(recording!=null)Text(if (videoNoteMode) "Video note ${formatCameraRecordingTime(recordingElapsed)}" else "Recording ${formatCameraRecordingTime(recordingElapsed)}",style=MaterialTheme.typography.titleMedium, modifier=Modifier.padding(horizontal=18.dp))
            Column(Modifier.heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.30f).verticalScroll(rememberScrollState())) {
            if (recording == null && !fastCapture) {
                
                if (showAdjustments) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = Color.Black.copy(alpha = 0.70f)
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Zoom", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(58.dp))
                                Slider(value = zoomRatio.coerceIn(1f, 4f), onValueChange = { zoomRatio = it; cameraControl?.setZoomRatio(it) }, valueRange = 1f..4f, modifier = Modifier.weight(1f))
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Exposure", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(58.dp))
                                val range = cameraInfo?.exposureState?.exposureCompensationRange
                                val lower = (range?.lower ?: -2).toFloat()
                                val upper = (range?.upper ?: 2).toFloat()
                                Slider(value = exposure.toFloat().coerceIn(lower, upper), onValueChange = { exposure = it.toInt(); cameraControl?.setExposureCompensationIndex(exposure) }, valueRange = lower..upper, steps = ((upper - lower).toInt() - 1).coerceAtLeast(0), modifier = Modifier.weight(1f))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Text("Timer", style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.width(8.dp))
                                FilterChip(selected = captureTimerSeconds == 0, onClick = { captureTimerSeconds = 0 }, label = { Text("Off") })
                                Spacer(Modifier.width(6.dp))
                                FilterChip(selected = captureTimerSeconds == 3, onClick = { captureTimerSeconds = 3 }, label = { Text("3 sec") })
                                Spacer(Modifier.width(6.dp))
                                FilterChip(selected = captureTimerSeconds == 10, onClick = { captureTimerSeconds = 10 }, label = { Text("10 sec") })
                            }
                        }
                    }
                }
            }
            if (showEffectTray && recording == null && !fastCapture) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp).padding(bottom = 6.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xD9232732),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                    shadowElevation = 3.dp
                ) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                        val effectOptions = if (mode == CameraMode.VIDEO) {
                            CameraVideoStyle.values().map { option ->
                                CameraEffectOption(
                                    label = option.label,
                                    category = "Video FX",
                                    previewStyle = cameraEffectPreviewStyle("Video FX", option.label),
                                    selected = videoStyle == option,
                                    enabled = !applyingVideoEffects && !applyingFilter && !enhancing,
                                    onClick = { videoStyle = option; showEffectTray = false; showCaptureControls = true }
                                )
                            }
                        } else when (selectedCameraTool) {
                            "Face FX" -> CameraFaceEffect.values().map { option ->
                                CameraEffectOption(
                                    label = option.label,
                                    category = "Face FX",
                                    previewStyle = cameraEffectPreviewStyle("Face FX", option.label),
                                    selected = faceEffect == option,
                                    enabled = !applyingFilter && !enhancing,
                                    onClick = { faceEffect = option; showEffectTray = false; showCaptureControls = true }
                                )
                            }
                            "Background" -> CameraBackgroundEffect.values().map { option ->
                                CameraEffectOption(
                                    label = option.label,
                                    category = "Background",
                                    previewStyle = cameraEffectPreviewStyle("Background", option.label),
                                    selected = backgroundEffect == option,
                                    enabled = !applyingFilter && !enhancing,
                                    onClick = { backgroundEffect = option; showEffectTray = false; showCaptureControls = true }
                                )
                            }
                            "AI Studio" -> CameraStudioEffect.values().map { option ->
                                CameraEffectOption(
                                    label = option.label,
                                    category = "AI Studio",
                                    previewStyle = cameraEffectPreviewStyle("AI Studio", option.label),
                                    selected = studioEffect == option,
                                    enabled = !applyingFilter && !enhancing,
                                    onClick = { studioEffect = option; showEffectTray = false; showCaptureControls = true }
                                )
                            }
                            else -> CameraFilter.values().map { option ->
                                CameraEffectOption(
                                    label = option.label,
                                    category = "Looks",
                                    previewStyle = cameraEffectPreviewStyle("Looks", option.label),
                                    selected = filter == option,
                                    enabled = recording == null && !applyingFilter && !enhancing,
                                    onClick = { filter = option; filterIntensity = 1f; showEffectTray = false; showCaptureControls = true }
                                )
                            }
                        }
                        Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp).size(width = 38.dp, height = 4.dp).background(Color.White.copy(alpha = 0.42f), androidx.compose.foundation.shape.CircleShape))
                        // Scrollable category pills keep the tray tidy on narrow phones.
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            (if (mode == CameraMode.VIDEO) listOf("Video FX") else listOf("Looks", "Face FX", "Background", "AI Studio")).forEach { category ->
                                val selected = mode == CameraMode.VIDEO || selectedCameraTool == category
                                Surface(
                                    onClick = {
                                        if (mode == CameraMode.PHOTO) selectedCameraTool = category
                                        showCaptureControls = true
                                    },
                                    shape = RoundedCornerShape(18.dp),
                                    color = if (selected) Color(0xFF3A315A) else Color(0xFF22252E),
                                    border = androidx.compose.foundation.BorderStroke(
                                        if (selected) 1.5.dp else 1.dp,
                                        if (selected) Color(0xFFB8A7FF) else Color.White.copy(alpha = 0.10f)
                                    )
                                ) {
                                    Text(
                                        category,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                        color = if (selected) Color.White else Color.White.copy(alpha = 0.74f),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        // One horizontal effects carousel keeps the live preview and capture controls clear.
                        Row(
                            Modifier.fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            effectOptions.forEach { option ->
                                CameraEffectTile(
                                    category = option.category,
                                    label = option.label,
                                    previewStyle = option.previewStyle,
                                    selected = option.selected,
                                    enabled = option.enabled,
                                    onClick = option.onClick,
                                    modifier = Modifier.width(76.dp)
                                )
                            }
                        }
                    }
                }
            }

            }
            // Capture mode sits above the shutter, keeping the main action row uncluttered.
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = mode == CameraMode.PHOTO,
                    onClick = { if (recording == null) { mode = CameraMode.PHOTO; showEffectTray = false; showCaptureControls = true } },
                    label = { Text("Photo") },
                    leadingIcon = { Icon(Icons.Default.PhotoCamera, null) }
                )
                Spacer(Modifier.width(10.dp))
                FilterChip(
                    selected = mode == CameraMode.VIDEO,
                    onClick = {
                        if (recording == null) {
                            mode = CameraMode.VIDEO
                            showEffectTray = false
                            filter = CameraFilter.NATURAL
                            filterIntensity = 1f
                            showCaptureControls = true
                        }
                    },
                    label = { Text("Video") },
                    leadingIcon = { Icon(Icons.Default.Videocam, null) }
                )
            }
            // Main capture row: Effects on the left, shutter centered, Gallery on the right.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!fastCapture) Surface(
                    onClick = { showEffectTray = !showEffectTray; showCaptureControls = true },
                    modifier = Modifier.size(width = 64.dp, height = 64.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = if (showEffectTray) Color(0xFF3A315A) else Color(0xFF22252E),
                    border = androidx.compose.foundation.BorderStroke(
                        if (showEffectTray) 1.5.dp else 1.dp,
                        if (showEffectTray) Color(0xFFB8A7FF) else Color.White.copy(alpha = 0.12f)
                    )
                ) {
                    Column(Modifier.fillMaxSize().padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        Text(if (mode == CameraMode.VIDEO) "Video FX" else "Effects", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                } else Spacer(Modifier.size(width = 64.dp, height = 64.dp))
                FilledIconButton(onClick = {
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
                        if (active != null) active.stop() else {
                            if (!hasAudio) {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                return@FilledIconButton
                            }
                            val capture = videoCapture ?: run {
                                error = "Video camera is still starting"
                                return@FilledIconButton
                            }
                            val file = File(context.cacheDir, "fynx_video_${System.currentTimeMillis()}.mp4")
                            val pending = capture.output.prepareRecording(context, FileOutputOptions.Builder(file).build())
                            val withAudio = if (hasAudio) pending.withAudioEnabled() else pending
                            recordingStartedAt = System.currentTimeMillis()
                            activeRecordingFile = file
                            recordingNotice = null
                            recording = withAudio.start(ContextCompat.getMainExecutor(context)) { event ->
                                if (event is VideoRecordEvent.Finalize) {
                                    if (!event.hasError() && file.exists() && file.length() > 0L) {
                                        pendingUri = Uri.fromFile(file)
                                        pendingOriginalUri = Uri.fromFile(file)
                                        pendingType = if (videoNoteMode) "video_note" else "video"
                                        error = null
                                    } else {
                                        file.delete()
                                        error = "Video capture failed (${event.error})"
                                    }
                                    recording = null
                                    activeRecordingFile = null
                                }
                            }
                        }
                    }
                }, modifier = Modifier.size(80.dp).semantics(mergeDescendants = true) {
                    contentDescription = if (recording != null) "Stop video note"
                    else if (mode == CameraMode.PHOTO) "Take photo" else "Record video note"
                }, shape = androidx.compose.foundation.shape.CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (videoNoteMode && recording != null) {
                            val progress = (recordingElapsed.toFloat() / 60_000f).coerceIn(0f, 1f)
                            Canvas(Modifier.fillMaxSize().padding(2.dp)) {
                                drawArc(Color(0xFF8B7BE8), -90f, 360f * progress, false,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(5.dp.toPx()))
                            }
                        }
                        Icon(
                            if (mode == CameraMode.PHOTO) Icons.Default.PhotoCamera
                            else if (recording != null) Icons.Default.Stop else Icons.Default.Videocam,
                            contentDescription = null
                        )
                    }
                }
                if (!fastCapture && !liveProofMode) Surface(
                    onClick = {
                        galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        showCaptureControls = true
                    },
                    modifier = Modifier.size(width = 64.dp, height = 64.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF22252E),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                ) {
                    Column(Modifier.fillMaxSize().padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        Text("Gallery", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                } else Spacer(Modifier.size(width = 64.dp, height = 64.dp))
            }

        }
    }
    }
}

/** Portrait effect tiles styled for quick browsing in the camera creator tray. */
private data class CameraEffectOption(
    val label: String,
    val category: String,
    val previewStyle: Int,
    val selected: Boolean,
    val enabled: Boolean,
    val onClick: () -> Unit
)

/**
 * Each effect tile uses a bundled female portrait, graded locally to illustrate the selected look.
 * Three portraits rotate through the carousel so adjacent effects do not repeat one face.
 */
private fun cameraEffectPreviewPortraitRes(previewStyle: Int): Int = when (previewStyle % 3) {
    0 -> R.drawable.camera_effect_portrait_1
    1 -> R.drawable.camera_effect_portrait_2
    else -> R.drawable.camera_effect_portrait_3
}

@Composable
private fun CameraEffectTile(
    category: String,
    label: String,
    previewStyle: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(13.dp)
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 112.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 70.dp)
                    .clip(shape)
                    .border(
                        if (selected) 2.dp else 1.dp,
                        if (selected) Color(0xFFB8A7FF) else Color.White.copy(alpha = 0.18f),
                        shape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = cameraEffectPreviewPortraitRes(previewStyle)),
                    contentDescription = "$category $label photo preview",
                    contentScale = ContentScale.Crop,
                    colorFilter = ColorFilter.colorMatrix(cameraEffectPreviewMatrix(previewStyle)),
                    modifier = Modifier.fillMaxSize()
                )
                // Reuse the actual sticker renderer so Face FX thumbnails preview the same artwork
                // as the live camera and saved-photo processor instead of displaying a generic icon.
                val facePreviewEffect = if (category == "Face FX") {
                    CameraFaceEffect.values().firstOrNull { it.label == label }
                } else null
                if (facePreviewEffect != null && facePreviewEffect != CameraFaceEffect.OFF) {
                    Canvas(Modifier.fillMaxSize()) {
                        val previewFace = RectF(
                            size.width * 0.12f, size.height * 0.18f,
                            size.width * 0.88f, size.height * 0.94f
                        )
                        drawFynxCameraFaceEffect(drawContext.canvas.nativeCanvas, previewFace, facePreviewEffect)
                    }
                }
                Box(
                    Modifier.align(Alignment.TopEnd).padding(4.dp).size(5.dp)
                        .background(Color.White.copy(alpha = 0.78f), androidx.compose.foundation.shape.CircleShape)
                )
                if (selected) {
                    Box(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp)
                            .size(17.dp)
                            .background(Color(0xFFB8A7FF), androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✓", color = Color(0xFF17131F), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(
                label,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.84f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2,
                minLines = 2,
                modifier = Modifier.fillMaxWidth().heightIn(min = 28.dp)
            )
        }
    }
}

private fun cameraEffectPreviewStyle(category: String, label: String): Int = when (category) {
    "Looks" -> when (label) {
        "Natural" -> 0
        "Portrait" -> 1
        "Beauty Glow" -> 2
        "Bright" -> 3
        "Warm Glow" -> 4
        "Cinematic" -> 5
        "Low-Light Rescue" -> 6
        "2000s Flash" -> 7
        "Cool" -> 8
        "Vintage" -> 9
        "B&W" -> 10
        else -> 0
    }
    "Face FX" -> when (label) {
        "Off" -> 0
        "Cute Ears" -> 11
        "Smart Glasses" -> 12
        "Royal Crown" -> 13
        "Puppy / Animal" -> 14
        "Virtual Makeup" -> 15
        "Hair Colour" -> 16
        "Funny Face" -> 17
        else -> 0
    }
    "Background" -> when (label) {
        "Off" -> 0
        "Portrait Blur" -> 18
        "Blue" -> 19
        "Sunset" -> 20
        "Mint" -> 21
        "Studio Scene" -> 22
        "Beach Scene" -> 23
        else -> 0
    }
    "AI Studio" -> when (label) {
        "Off" -> 0
        "Mature Look" -> 24
        "Face Symmetry" -> 25
        "Anime / Cartoon" -> 26
        else -> 0
    }
    "Video FX" -> when (label) {
        "Natural" -> 0
        "Cinematic" -> 5
        "Warm Film" -> 4
        "Vintage" -> 9
        "B&W" -> 10
        "Vivid" -> 3
        else -> 0
    }
    else -> 0
}

private fun cameraEffectPreviewMatrix(style: Int): ComposeColorMatrix {
    val (red, green, blue, saturation, brightness) = when (style) {
        1 -> listOf(1.05f, 1.02f, 0.98f, 1.05f, 0.025f)
        2, 15 -> listOf(1.10f, 0.98f, 1.02f, 1.08f, 0.045f)
        3 -> listOf(1.06f, 1.05f, 1.04f, 1.06f, 0.09f)
        4, 20 -> listOf(1.12f, 1.01f, 0.87f, 1.08f, 0.035f)
        5 -> listOf(1.08f, 0.97f, 0.90f, 0.92f, 0.015f)
        6 -> listOf(1.08f, 1.07f, 1.04f, 1.03f, 0.12f)
        7 -> listOf(1.14f, 1.10f, 1.05f, 1.18f, 0.13f)
        8, 19 -> listOf(0.88f, 0.98f, 1.14f, 0.96f, 0.015f)
        9, 24 -> listOf(1.10f, 0.98f, 0.82f, 0.74f, 0.025f)
        10 -> listOf(1f, 1f, 1f, 0f, 0.015f)
        11 -> listOf(1.12f, 0.94f, 1.01f, 1.04f, 0.02f)
        12 -> listOf(0.91f, 1.00f, 1.12f, 0.94f, 0.02f)
        13, 25 -> listOf(1.13f, 1.03f, 0.92f, 1.06f, 0.035f)
        14 -> listOf(1.09f, 0.99f, 0.87f, 1.02f, 0.02f)
        16 -> listOf(0.92f, 1.07f, 1.14f, 1.12f, 0.025f)
        17, 26 -> listOf(1.12f, 0.94f, 1.10f, 1.28f, 0.035f)
        18 -> listOf(0.92f, 0.96f, 1.07f, 0.86f, 0.025f)
        21 -> listOf(0.89f, 1.12f, 1.02f, 0.98f, 0.025f)
        22 -> listOf(0.98f, 1.00f, 1.04f, 0.88f, 0.035f)
        23 -> listOf(1.08f, 1.09f, 0.91f, 1.08f, 0.04f)
        else -> listOf(1f, 1f, 1f, 1f, 0f)
    }
    val desaturation = 1f - saturation
    val redLuma = 0.213f * desaturation
    val greenLuma = 0.715f * desaturation
    val blueLuma = 0.072f * desaturation
    val lift = brightness * 255f
    return ComposeColorMatrix(
        floatArrayOf(
            (redLuma + saturation) * red, greenLuma * red, blueLuma * red, 0f, lift,
            redLuma * green, (greenLuma + saturation) * green, blueLuma * green, 0f, lift,
            redLuma * blue, greenLuma * blue, (blueLuma + saturation) * blue, 0f, lift,
            0f, 0f, 0f, 1f, 0f
        )
    )
}

private fun rotateCameraPhotoFile(file: File) {
    val bitmap = decodeCameraEditingBitmap(file, maxEdge = 2400)
    var rotated: android.graphics.Bitmap? = null
    val temp = File(file.parentFile ?: error("Photo directory is unavailable"), file.name + ".rotating-" + System.currentTimeMillis())
    try {
        val matrix = Matrix().apply { postRotate(90f) }
        val rotatedBitmap = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        rotated = rotatedBitmap
        FileOutputStream(temp).use {
            check(rotatedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 94, it)) {
                "Unable to save rotated photo"
            }
        }
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            check(file.length() > 0L) { "Unable to replace rotated photo" }
        }
    } finally {
        temp.delete()
        rotated?.takeIf { it !== bitmap }?.recycle()
        bitmap.recycle()
    }
}

private fun formatCameraRecordingTime(milliseconds:Long):String{val totalSeconds=milliseconds/1000L;return "%02d:%02d".format(totalSeconds/60L,totalSeconds%60L)}
enum class CameraMode{PHOTO,VIDEO}
enum class CameraFilter(val label:String,val saturation:Float,val brightness:Float,val contrast:Float,val warmth:Float = 0f){
    NATURAL("Natural",1f,0f,1f),
    PORTRAIT("Portrait",0.96f,0.025f,1.01f),
    BEAUTY_GLOW("Beauty Glow",1.04f,0.075f,1.02f,0.025f),
    BRIGHT("Bright",1.03f,0.08f,1.02f),
    WARM("Warm Glow",1.10f,0.04f,1.02f,0.10f),
    CINEMATIC("Cinematic",0.86f,-0.01f,1.14f,-0.035f),
    LOW_LIGHT("Low-Light Rescue",0.96f,0.16f,1.06f),
    FLASH_2000S("2000s Flash",1.20f,0.075f,1.12f,0.065f),
    COOL("Cool",0.92f,0.02f,1.02f,-0.12f),
    VINTAGE("Vintage",0.82f,0.015f,0.94f,0.035f),
    BW("B&W",0f,0f,1.08f)
}
/**
 * Apply the exact colour-look pipeline used by the camera editor.
 * The temporary output protects the source image if compression or replacement fails.
 */
internal fun applyCameraColorFilterToPhotoFile(
    source: File,
    target: File,
    filter: CameraFilter,
    intensity: Float
) {
    if (filter == CameraFilter.NATURAL) {
        if (source.absolutePath != target.absolutePath) source.copyTo(target, overwrite = true)
        return
    }

    val bitmap = decodeCameraEditingBitmap(source, maxEdge = 2400)
    val temporary = File(
        target.parentFile ?: error("Photo output directory is unavailable"),
        target.name + ".color-filter-" + System.currentTimeMillis()
    )
    try {
        val matrix = cameraFilterMatrix(filter, intensity)
        val output = android.graphics.Bitmap.createBitmap(
            bitmap.width, bitmap.height, android.graphics.Bitmap.Config.ARGB_8888
        )
        try {
            android.graphics.Canvas(output).drawBitmap(
                bitmap, 0f, 0f,
                android.graphics.Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) }
            )
            FileOutputStream(temporary).use {
                check(output.compress(android.graphics.Bitmap.CompressFormat.JPEG, 94, it)) {
                    "Unable to save filtered photo"
                }
            }
        } finally {
            output.recycle()
        }
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            check(target.length() > 1024L) { "Unable to replace filtered photo" }
        }
    } finally {
        temporary.delete()
        bitmap.recycle()
    }
}

private fun cameraFilterMatrix(filter: CameraFilter, intensity: Float): ColorMatrix {
    val strength = intensity.coerceIn(0f, 1f)
    return ColorMatrix().apply {
        setFynxFilter(
            1f + (filter.saturation - 1f) * strength,
            filter.brightness * strength,
            1f + (filter.contrast - 1f) * strength,
            1f
        )
        val temperature = filter.warmth * strength
        if (temperature != 0f) {
            postConcat(ColorMatrix(floatArrayOf(
                1f + temperature, 0f, 0f, 0f, 0f,
                0f, 1f + temperature * 0.12f, 0f, 0f, 0f,
                0f, 0f, 1f - temperature, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
    }
}
private fun ColorMatrix.setFynxFilter(saturation:Float,brightness:Float,contrast:Float,alpha:Float){setSaturation(saturation);val scale=contrast;val translate=brightness*255f;postConcat(ColorMatrix(floatArrayOf(scale,0f,0f,0f,translate,0f,scale,0f,0f,translate,0f,0f,scale,0f,translate,0f,0f,0f,alpha,0f)))}
private fun normalizeCapturedPhoto(file: File, mirrorFront: Boolean, forceJpeg: Boolean = false) {
    val exif = ExifInterface(file.absolutePath)
    val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    val rotation = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (!mirrorFront && rotation == 0f && !forceJpeg) return
    val bitmap = decodeCameraEditingBitmap(file, maxEdge = 2400)
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
