package com.fynx.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.io.File

private const val MAX_VOICE_POST_DURATION_MS = 120_000L

@Composable
fun FynxVoicePostRecorder(onRecorded: (Uri) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var outputFile by remember { mutableStateOf<File?>(null) }
    var recording by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var hasRecording by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var previewPositionMs by remember { mutableLongStateOf(0L) }
    var previewDurationMs by remember { mutableLongStateOf(0L) }

    val previewPlayer = remember(outputFile, hasRecording) {
        outputFile?.takeIf { hasRecording && it.exists() && it.length() > 0L }?.let { file ->
            runCatching { MediaPlayer().apply { setDataSource(file.absolutePath); prepare() } }.getOrNull()
        }
    }

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
        elapsedMs = 0L
        previewPositionMs = 0L
        previewDurationMs = 0L
        error = null
    }

    fun stopRecording() {
        val active = recorder ?: return
        runCatching { active.stop() }.onFailure {
            outputFile?.delete()
            hasRecording = false
            error = "The recording could not be saved. Please try again."
        }
        active.runCatching { release() }
        recorder = null
        recording = false
        paused = false
        hasRecording = outputFile?.let { it.exists() && it.length() > 0L } == true
        elapsedMs = elapsedMs.coerceAtMost(MAX_VOICE_POST_DURATION_MS)
        previewPlayer?.let {
            previewDurationMs = it.duration.coerceAtLeast(0)
            previewPositionMs = 0L
        }
    }

    fun discard() {
        releaseRecorder()
        previewPlayer?.runCatching { stop() }
        outputFile?.delete()
        outputFile = null
        hasRecording = false
        playing = false
        elapsedMs = 0L
        previewPositionMs = 0L
        previewDurationMs = 0L
        error = null
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) runCatching { startRecording() }.onFailure { error = it.message ?: "Microphone could not start." }
        else error = "Microphone permission is required to record a voice post."
    }

    LaunchedEffect(recording, paused) {
        if (recording && !paused) {
            while (recording && !paused && elapsedMs < MAX_VOICE_POST_DURATION_MS) {
                delay(250L)
                elapsedMs = (elapsedMs + 250L).coerceAtMost(MAX_VOICE_POST_DURATION_MS)
                if (elapsedMs >= MAX_VOICE_POST_DURATION_MS) stopRecording()
            }
        }
    }

    LaunchedEffect(playing, previewPlayer) {
        while (playing && previewPlayer != null) {
            delay(200L)
            previewPositionMs = previewPlayer.currentPosition.toLong().coerceAtLeast(0L)
        }
    }

    DisposableEffect(previewPlayer) {
        previewPlayer?.setOnCompletionListener {
            playing = false
            previewPositionMs = 0L
        }
        previewPlayer?.let { previewDurationMs = it.duration.coerceAtLeast(0).toLong() }
        onDispose { previewPlayer?.runCatching { release() } }
    }

    DisposableEffect(Unit) {
        onDispose { releaseRecorder(); outputFile?.delete() }
    }

    AlertDialog(
        onDismissRequest = { discard(); onDismiss() },
        title = { Text(if (hasRecording) "Voice post preview" else "Record a voice post") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    when {
                        recording && paused -> "Recording paused"
                        recording -> "Recording your voice…"
                        hasRecording -> "Preview your voice before publishing. Your recording is not uploaded until you press Post."
                        else -> "Record up to 2 minutes, preview it, then choose Use recording."
                    }
                )
                if (recording) {
                    Text("${formatVoiceTime(elapsedMs)} / ${formatVoiceTime(MAX_VOICE_POST_DURATION_MS)}")
                    LinearProgressIndicator(
                        progress = { elapsedMs.toFloat() / MAX_VOICE_POST_DURATION_MS.toFloat() },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    if (paused) recorder?.resume() else recorder?.pause()
                                    paused = !paused
                                }.onFailure { error = "Pause/resume is not available for this recording." }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text(if (paused) "Resume" else "Pause") }
                        Button(onClick = { stopRecording() }, modifier = Modifier.weight(1f)) { Text("Stop") }
                    }
                } else if (hasRecording) {
                    Text("Voice recording • ${formatVoiceTime(previewDurationMs.coerceAtLeast(elapsedMs))}")
                    if (previewDurationMs > 0L) {
                        Slider(
                            value = previewPositionMs.toFloat().coerceIn(0f, previewDurationMs.toFloat()),
                            onValueChange = { value ->
                                previewPositionMs = value.toLong()
                                previewPlayer?.seekTo(previewPositionMs.toInt())
                            },
                            valueRange = 0f..previewDurationMs.toFloat(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("${formatVoiceTime(previewPositionMs)} / ${formatVoiceTime(previewDurationMs)}")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                previewPlayer?.let { player ->
                                    runCatching {
                                        if (player.isPlaying) {
                                            player.pause()
                                            playing = false
                                        } else {
                                            player.start()
                                            playing = true
                                        }
                                    }.onFailure { error = "The recording could not be played." }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text(if (playing) "Pause preview" else "▶ Preview") }
                        OutlinedButton(
                            onClick = {
                                discard()
                                runCatching { startRecording() }.onFailure { error = it.message ?: "Microphone could not start." }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Record again") }
                    }
                } else {
                    Button(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                runCatching { startRecording() }.onFailure { error = it.message ?: "Microphone could not start." }
                            } else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        Modifier.fillMaxWidth()
                    ) { Text("Start recording") }
                }
                error?.let { Text(it) }
            }
        },
        confirmButton = {
            if (hasRecording && !recording) {
                Button(onClick = {
                    outputFile?.let {
                        onRecorded(Uri.fromFile(it))
                        outputFile = null
                        hasRecording = false
                        playing = false
                    }
                }) { Text("Use recording") }
            }
        },
        dismissButton = { TextButton(onClick = { discard(); onDismiss() }) { Text("Cancel") } }
    )
}

private fun formatVoiceTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1000L).toInt()
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
