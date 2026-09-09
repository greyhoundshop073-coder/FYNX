package com.fynx.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class FynxEditPreset(val label: String, val saturation: Float, val contrast: Float, val brightness: Float) {
    ORIGINAL("Original", 1f, 1f, 0f), CLEAN("Clean", 1.05f, 1.05f, 0.02f), BRIGHT("Bright", 1.04f, 1.02f, 0.08f), VIVID("Vivid", 1.22f, 1.06f, 0.01f), SOFT("Soft", 0.96f, 0.96f, 0.04f), B_W("B&W", 0f, 1.04f, 0.02f)
}

/** AI-assisted photo editing that can hand the edited image directly back into the post composer. */
@Composable
fun FynxAiPhotoEditorPanel(
    initialUri: Uri? = null,
    onDone: (Uri?) -> Unit = {}
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var sourceUri by remember { mutableStateOf<Uri?>(initialUri) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var preset by remember { mutableStateOf(FynxEditPreset.ORIGINAL) }
    var rotation by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var aiLoading by remember { mutableStateOf(false) }
    var aiAdvice by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun loadPhoto(uri: Uri) {
        sourceUri = uri
        preset = FynxEditPreset.ORIGINAL
        rotation = 0
        aiAdvice = ""
        error = null
        loading = true
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { decodeBitmap(context, uri) }
            preview = bitmap
            loading = false
            if (bitmap == null) error = "The selected photo could not be opened."
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) loadPhoto(uri)
    }

    LaunchedEffect(initialUri) {
        if (initialUri != null && initialUri != sourceUri) loadPhoto(initialUri)
    }

    fun render() {
        val uri = sourceUri ?: return
        loading = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { renderPhoto(context, uri, preset, rotation) }
            result.onSuccess { preview = it }.onFailure { error = it.message ?: "Photo edit failed" }
            loading = false
        }
    }

    fun askAi() {
        if (aiLoading) return
        val instruction = "Give concise, safe photo-editing suggestions for a FYNX social post. Recommend lighting, color, crop and background-edit ideas, but do not claim to have analyzed the actual image. Return 4 practical suggestions."
        val capability = FynxAiCapability.MEDIA_ASSIST
        val decision = FynxFutureIntelligencePolicy.authorize(
            permissions = listOf(FynxAiPermission(capability, setOf(FynxAiDataScope.NONE), true)),
            request = FynxAiRequest(capability, instruction, setOf(FynxAiDataScope.NONE))
        )
        if (!decision.allowed) return
        aiLoading = true
        aiAdvice = ""
        scope.launch {
            val response = withContext(Dispatchers.IO) { AiAssistantClient.sendMessage(context, instruction) }
            response.onSuccess { aiAdvice = it.trim() }.onFailure { error = "FYNX AI editing guidance is temporarily unavailable." }
            aiLoading = false
        }
    }

    fun finishWithEditedPhoto() {
        val bitmap = preview ?: run { onDone(sourceUri); return }
        loading = true
        scope.launch {
            val editedUri = withContext(Dispatchers.IO) { saveEditedPhoto(context, bitmap) }
            loading = false
            editedUri.onSuccess { onDone(it) }.onFailure { error = it.message ?: "Edited photo could not be prepared." }
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Column {
                Text("AI Photo Editor", style = MaterialTheme.typography.headlineSmall)
                Text("Edit the photo you are preparing to post, then return it to the post composer.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.PhotoLibrary, null)
            Spacer(Modifier.width(6.dp))
            Text(if (sourceUri == null) "Choose photo" else "Choose another photo")
        }
        preview?.let { bitmap ->
            Card(modifier = Modifier.fillMaxWidth(), shape = FynxDesign.CardShape) {
                androidx.compose.foundation.Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Edited photo preview", modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp))
            }
            Text("Edit style", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(FynxEditPreset.values().size) { index ->
                    val option = FynxEditPreset.values()[index]
                    FilterChip(selected = preset == option, enabled = !loading, onClick = { preset = option; render() }, label = { Text(option.label) })
                }
            }
            OutlinedButton(onClick = { rotation = (rotation + 90) % 360; render() }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.RotateRight, null)
                Spacer(Modifier.width(6.dp))
                Text("Rotate 90°")
            }
            Button(onClick = ::askAi, enabled = !aiLoading && !loading, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.AutoAwesome, null)
                Spacer(Modifier.width(6.dp))
                Text(if (aiLoading) "Getting AI guidance…" else "✨ AI Edit Suggestions")
            }
        }
        aiAdvice.takeIf { it.isNotBlank() }?.let { advice ->
            Card(Modifier.fillMaxWidth(), shape = FynxDesign.CardShape) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(advice)
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(advice)) }) {
                        Icon(Icons.Default.ContentCopy, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Copy")
                    }
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onDone(null) }, enabled = !loading, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(onClick = ::finishWithEditedPhoto, enabled = preview != null && !loading, modifier = Modifier.weight(1f)) { Text(if (loading) "Preparing…" else "Use in post") }
        }
    }
}

private fun decodeBitmap(context: Context, uri: Uri): Bitmap? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
}.getOrNull()?.let { decoded -> decoded.copy(Bitmap.Config.ARGB_8888, false).also { decoded.recycle() } }

private fun renderPhoto(context: Context, uri: Uri, preset: FynxEditPreset, rotation: Int): Result<Bitmap> = runCatching {
    val source = decodeBitmap(context, uri) ?: error("Photo could not be opened")
    val matrix = ColorMatrix().apply {
        setSaturation(preset.saturation)
        postConcat(ColorMatrix(floatArrayOf(
            preset.contrast, 0f, 0f, 0f, preset.brightness * 255f,
            0f, preset.contrast, 0f, 0f, preset.brightness * 255f,
            0f, 0f, preset.contrast, 0f, preset.brightness * 255f,
            0f, 0f, 0f, 1f, 0f
        )))
    }
    val transformed = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    Canvas(transformed).drawBitmap(source, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) })
    source.recycle()
    if (rotation == 0) return@runCatching transformed
    val rotated = Bitmap.createBitmap(
        transformed,
        0,
        0,
        transformed.width,
        transformed.height,
        android.graphics.Matrix().apply { postRotate(rotation.toFloat()) },
        true
    )
    transformed.recycle()
    rotated
}

private fun saveEditedPhoto(context: Context, bitmap: Bitmap): Result<Uri> = runCatching {
    val directory = File(context.cacheDir, "fynx-edits").apply { mkdirs() }
    directory.listFiles()?.forEach { file -> file.delete() }
    val output = File(directory, "post-edit-${System.currentTimeMillis()}.jpg")
    output.outputStream().use { stream ->
        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)) { "Photo compression failed" }
    }
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", output)
}
