package com.fynx.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.io.File

@Composable
fun FynxVoicePostRecorder(
    onRecorded: (Uri) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var outputFile by remember { mutableStateOf<File?>(null) }
    var recording by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var hasRecording by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun releaseRecorder() {
        recorder?.runCatching { reset() }
        recorder?.runCatching { release() }
        recorder = null
        recording = false
        paused = false
    }

    fun startRecording() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        val file = File(context.cacheDir, "fynx_voice_${System.currentTimeMillis()}.m4a")
        val next = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44_100)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        outputFile?.takeIf { it != file }?.delete()
        outputFile = file
        recorder = next
        recording = true
        paused = false
        hasRecording = false
        error = null
    }

    fun stopRecording() {
        val active = recorder ?: return
        runCatching { active.stop() }.onFailure { outputFile?.delete(); error = "The recording could not be saved." }
        active.runCatching { release() }
        recorder = null
        recording = false
        paused = false
        hasRecording = outputFile?.let { it.exists() && it.length() > 0L } == true
    }

    fun discard() {
        releaseRecorder()
        outputFile?.delete()
        outputFile = null
        hasRecording = false
        error = null
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) runCatching { startRecording() }.onFailure { error = it.message ?: "Microphone could not start." }
        else error = "Microphone permission is required to record a voice post."
    }

    DisposableEffect(Unit) {
        onDispose {
            releaseRecorder()
            outputFile?.delete()
        }
    }

    AlertDialog(
        onDismissRequest = { discard(); onDismiss() },
        title = { Text(if (hasRecording) "Voice post preview" else "Record a voice post") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(when {
                    recording && paused -> "Recording paused"
                    recording -> "Recording…"
                    hasRecording -> "Recording ready to preview and post"
                    else -> "Record your voice, then preview it before posting."
                })
                error?.let { Text(it) }
                if (recording) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            runCatching {
                                if (paused) recorder?.resume() else recorder?.pause()
                                paused = !paused
                            }.onFailure { error = "Pause/resume is not available for this recording." }
                        }) { Text(if (paused) "Resume" else "Pause") }
                        Button(onClick = { stopRecording() }) { Text("Stop") }
                    }
                } else if (hasRecording) {
                    Text("Use the preview controls in the post composer after selecting this recording.")
                    OutlinedButton(onClick = { discard(); startRecording() }, Modifier.fillMaxWidth()) { Text("Record again") }
                } else {
                    Button(onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            runCatching { startRecording() }.onFailure { error = it.message ?: "Microphone could not start." }
                        } else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }, Modifier.fillMaxWidth()) { Text("Start recording") }
                }
            }
        },
        confirmButton = {
            if (hasRecording && !recording) {
                Button(onClick = { outputFile?.let { onRecorded(Uri.fromFile(it)); outputFile = null; hasRecording = false } }) { Text("Use recording") }
            }
        },
        dismissButton = {
            TextButton(onClick = { discard(); onDismiss() }) { Text("Cancel") }
        }
    )
}
