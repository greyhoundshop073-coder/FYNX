package com.fynx.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream

@Composable
fun FynxCameraCapturePanel(
    onCaptured: (Uri, String) -> Unit,
    onDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    var hasCamera by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var hasAudio by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        hasCamera = result[Manifest.permission.CAMERA] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        hasAudio = result[Manifest.permission.RECORD_AUDIO] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    LaunchedEffect(Unit) {
        val missing = buildList {
            if (!hasCamera) add(Manifest.permission.CAMERA)
            if (!hasAudio) add(Manifest.permission.RECORD_AUDIO)
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }

    var lens by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var mode by remember { mutableStateOf(CameraMode.PHOTO) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }
    var cameraInfo by remember { mutableStateOf<androidx.camera.core.CameraInfo?>(null) }
    var torchEnabled by remember { mutableStateOf(false) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var exposure by remember { mutableIntStateOf(0) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var recordingStartedAt by remember { mutableLongStateOf(0L) }
    var recordingElapsed by remember { mutableLongStateOf(0L) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var pendingType by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf(CameraFilter.NATURAL) }

    LaunchedEffect(recording != null, recordingStartedAt) {
        while (recording != null) {
            recordingElapsed = (System.currentTimeMillis() - recordingStartedAt).coerceAtLeast(0L)
            delay(200L)
        }
    }

    fun formatCameraRecordingTime(milliseconds: Long): String {
        val totalSeconds = milliseconds / 1000L
        return "%02d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
    }

    fun bindCamera(previewView: PreviewView) {
        val owner = activity ?: run { error = "Camera requires an Android activity"; return }
        if (!hasCamera || pendingUri != null) return
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val selector = CameraSelector.Builder().requireLensFacing(lens).build()
                val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
                    .build()
                val video = VideoCapture.withOutput(recorder)
                provider.unbindAll()
                val camera = if (mode == CameraMode.PHOTO) provider.bindToLifecycle(owner, selector, preview, capture)
                else provider.bindToLifecycle(owner, selector, preview, video)
                imageCapture = capture
                videoCapture = video
                cameraControl = camera.cameraControl
                cameraInfo = camera.cameraInfo
                cameraControl?.setZoomRatio(zoomRatio)
                cameraControl?.enableTorch(torchEnabled)
                cameraControl?.setExposureCompensationIndex(exposure)
            }.onFailure { error = it.message ?: "Camera could not start" }
        }, ContextCompat.getMainExecutor(context))
    }

    fun retake() {
        pendingUri?.let { uri -> if (uri.scheme == "file") File(uri.path ?: "").delete() }
        pendingUri = null
        pendingType = null
        error = null
    }

    fun rotatePhoto() {
        val uri = pendingUri ?: return
        if (pendingType != "image") return
        val source = uri.path?.let { File(it) } ?: return
        runCatching {
            val bitmap = BitmapFactory.decodeFile(source.absolutePath) ?: error("Unable to decode photo")
            val matrix = Matrix().apply { postRotate(90f) }
            val rotated = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            FileOutputStream(source).use { output -> rotated.compress(android.graphics.Bitmap.CompressFormat.JPEG, 94, output) }
            bitmap.recycle()
            rotated.recycle()
        }.onFailure { error = it.message ?: "Photo rotation failed" }
    }

    fun applyFilterToPhoto(selected: CameraFilter) {
        val uri = pendingUri ?: return
        if (pendingType != "image") return
        val source = uri.path?.let { File(it) } ?: return
        runCatching {
            val bitmap = BitmapFactory.decodeFile(source.absolutePath) ?: error("Unable to decode photo")
            val matrix = android.graphics.ColorMatrix().apply { setFynxFilter(selected.saturation, selected.brightness, selected.contrast, 1f) }
            val outputBitmap = android.graphics.Bitmap.createBitmap(bitmap.width, bitmap.height, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(outputBitmap)
            val paint = android.graphics.Paint().apply { colorFilter = android.graphics.ColorMatrixColorFilter(matrix) }
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            FileOutputStream(source).use { output -> outputBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 94, output) }
            bitmap.recycle()
            outputBitmap.recycle()
            filter = selected
        }.onFailure { error = it.message ?: "Filter could not be applied" }
    }

    if (!hasCamera) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("FYNX needs camera access to capture photos and videos.")
                Spacer(Modifier.height(12.dp))
                Button(onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) }) { Text("Allow camera") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
        return
    }

    val previewUri = pendingUri
    val previewType = pendingType
    if (previewUri != null && previewType != null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (previewType == "image") {
                AndroidView(
                    factory = { android.widget.ImageView(it).apply { scaleType = android.widget.ImageView.ScaleType.FIT_CENTER } },
                    update = { view -> view.setImageURI(previewUri) },
                    modifier = Modifier.fillMaxSize().padding(18.dp)
                )
            } else {
                AndroidView(
                    factory = { android.widget.VideoView(it).apply { setVideoURI(previewUri); setOnPreparedListener { player -> player.isLooping = true; start() } } },
                    update = { view -> if (view.tag != previewUri.toString()) { view.tag = previewUri.toString(); view.setVideoURI(previewUri); view.start() } },
                    modifier = Modifier.fillMaxSize().padding(18.dp)
                )
            }
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { retake() }) { Icon(Icons.Default.Close, "Discard media") }
                    Spacer(Modifier.weight(1f))
                    Text(if (previewType == "video") "Video preview" else "Photo edit", style = MaterialTheme.typography.titleMedium)
                }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(18.dp)) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp)) }
                if (previewType == "image") {
                    Text("Filters", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CameraFilter.values().forEach { option ->
                            FilterChip(selected = filter == option, onClick = { applyFilterToPhoto(option) }, label = { Text(option.label) })
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { retake() }, modifier = Modifier.weight(1f)) { Text("Retake") }
                    if (previewType == "image") {
                        OutlinedButton(onClick = { rotatePhoto() }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.RotateRight, null); Spacer(Modifier.width(4.dp)); Text("Rotate") }
                    }
                    Button(onClick = { onCaptured(previewUri, previewType) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Send, null); Spacer(Modifier.width(4.dp)); Text("Use") }
                }
            }
        }
        return
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AndroidView(
            factory = { PreviewView(it).also { view -> bindCamera(view) } },
            update = { bindCamera(it) },
            modifier = Modifier.fillMaxSize()
        )
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (recording == null) onDismiss() }) { Icon(Icons.Default.Close, "Close camera") }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = {
                    if (recording == null) lens = if (lens == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                }) { Icon(Icons.Default.Cameraswitch, "Switch front/back camera") }
                IconButton(onClick = {
                    if (recording == null) { torchEnabled = !torchEnabled; cameraControl?.enableTorch(torchEnabled) }
                }) { Icon(Icons.Default.FlashOn, if (torchEnabled) "Turn flash off" else "Turn flash on") }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp)) }
            if (recording != null) Text("Recording ${formatCameraRecordingTime(recordingElapsed)}", style = MaterialTheme.typography.titleMedium)
            if (recording == null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Zoom", style = MaterialTheme.typography.labelSmall)
                    Slider(value = zoomRatio.coerceIn(1f, 4f), onValueChange = { zoomRatio = it; cameraControl?.setZoomRatio(it) }, valueRange = 1f..4f, modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Exposure", style = MaterialTheme.typography.labelSmall)
                    Slider(value = exposure.toFloat(), onValueChange = { exposure = it.toInt(); cameraControl?.setExposureCompensationIndex(exposure) }, valueRange = -2f..2f, steps = 4, modifier = Modifier.weight(1f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = mode == CameraMode.PHOTO, onClick = { if (recording == null) mode = CameraMode.PHOTO }, label = { Text("Photo") }, leadingIcon = { Icon(Icons.Default.PhotoCamera, null) })
                FilledIconButton(onClick = {
                    if (mode == CameraMode.PHOTO) {
                        val file = File(context.cacheDir, "fynx_photo_${System.currentTimeMillis()}.jpg")
                        val output = ImageCapture.OutputFileOptions.Builder(file).build()
                        imageCapture?.takePicture(output, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(result: ImageCapture.OutputFileResults) { pendingUri = Uri.fromFile(file); pendingType = "image" }
                            override fun onError(exception: ImageCaptureException) { error = exception.message ?: "Photo capture failed" }
                        }) ?: run { error = "Camera is still starting" }
                    } else {
                        val active = recording
                        if (active != null) active.stop()
                        else {
                            val capture = videoCapture ?: run { error = "Video camera is still starting"; return@FilledIconButton }
                            val file = File(context.cacheDir, "fynx_video_${System.currentTimeMillis()}.mp4")
                            val output = FileOutputOptions.Builder(file).build()
                            val pending = capture.output.prepareRecording(context, output)
                            val withAudio = if (hasAudio) pending.withAudioEnabled() else pending
                            recordingStartedAt = System.currentTimeMillis()
                            recording = withAudio.start(ContextCompat.getMainExecutor(context)) { event ->
                                if (event is VideoRecordEvent.Finalize) {
                                    if (!event.hasError() && file.exists() && file.length() > 0L) { pendingUri = Uri.fromFile(file); pendingType = "video" }
                                    else error = "Video capture failed (${event.error})"
                                    recording = null
                                }
                            }
                        }
                    }
                }, modifier = Modifier.size(72.dp)) {
                    Icon(if (mode == CameraMode.PHOTO) Icons.Default.PhotoCamera else if (recording != null) Icons.Default.Stop else Icons.Default.Videocam, if (recording != null) "Stop video" else if (mode == CameraMode.PHOTO) "Take photo" else "Record video")
                }
                FilterChip(selected = mode == CameraMode.VIDEO, onClick = { if (recording == null) mode = CameraMode.VIDEO }, label = { Text("Video") }, leadingIcon = { Icon(Icons.Default.Videocam, null) })
            }
        }
    }
}

enum class CameraMode { PHOTO, VIDEO }

enum class CameraFilter(val label: String, val saturation: Float, val brightness: Float, val contrast: Float) {
    NATURAL("Natural", 1f, 0f, 1f),
    VIVID("Vivid", 1.35f, 0f, 1.08f),
    WARM("Warm", 1.1f, 0.04f, 1.02f),
    COOL("Cool", 0.9f, 0.02f, 1.02f),
    BW("B&W", 0f, 0f, 1.08f)
}
