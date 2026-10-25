package com.fynx.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
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
import androidx.compose.material3.CircularProgressIndicator
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
    var previewPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var recording by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var hasRecording by remember { mutableStateOf(false) }
    var previewReady by remember { mutableStateOf(false) }
    var previewLoading by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var seeking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var previewPositionMs by remember { mutableLongStateOf(0L) }
    var previewDurationMs by remember { mutableLongStateOf(0L) }

    fun releaseRecorder() {
        recorder?.runCatching { reset() }
        recorder?.runCatching { release() }
        recorder = null
        recording = false
        paused = false
    }

    fun startRecording() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            error = "Microphone permission is required to record a voice post."
            return
        }

        val file = File(context.cacheDir, "fynx_voice_\${System.currentTimeMillis()}.m4a")
        var candidate: MediaRecorder? = null
        try {
            candidate = MediaRecorder()
            candidate.setAudioSource(MediaRecorder.AudioSource.MIC)
            candidate.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            candidate.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            candidate.setAudioEncodingBitRate(128_000)
            candidate.setAudioSamplingRate(44_100)
            candidate.setOutputFile(file.absolutePath)
            candidate.prepare()
            candidate.start()
        } catch (cause: Throwable) {
            candidate?.runCatching { reset() }
            candidate?.runCatching { release() }
            file.delete()
            recorder = null
            recording = false
            paused = false
            hasRecording = false
            previewReady = false
            previewLoading = false
            error = cause.message?.takeIf { it.isNotBlank() } ?: "Microphone could not start."
            return
        }

        outputFile?.takeIf { it != file }?.delete()
        previewPlayer = null
        previewReady = false
        previewLoading = false
        previewDurationMs = 0L
        previewPositionMs = 0L
        recorder = candidate
        outputFile = file
        recording = true
        paused = false
        hasRecording = false
        elapsedMs = 0L
        playing = false
        seeking = false
        error = null
    }

    fun stopRecording() {
        val active = recorder ?: return
        val stopSucceeded = runCatching { active.stop() }.isSuccess
        active.runCatching { reset() }
        active.runCatching { release() }
        recorder = null
        recording = false
        paused = false
        val file = outputFile
        val validFile = stopSucceeded && file?.isFile == true && file.length() > 0L
        hasRecording = validFile
        elapsedMs = elapsedMs.coerceAtMost(MAX_VOICE_POST_DURATION_MS)
        playing = false
        seeking = false
        previewPositionMs = 0L
        previewDurationMs = 0L
        previewReady = false
        previewLoading = validFile
        if (validFile) {
            error = null
        } else {
            file?.delete()
            outputFile = null
            previewLoading = false
            error = "The recording could not be saved. Please try again."
        }
    }

    fun discard() {
        releaseRecorder()
        previewPlayer = null
        outputFile?.delete()
        outputFile = null
        hasRecording = false
        previewReady = false
        previewLoading = false
        playing = false
        seeking = false
        elapsedMs = 0L
        previewPositionMs = 0L
        previewDurationMs = 0L
        error = null
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording()
        else error = "Microphone permission is required to record a voice post."
    }

    LaunchedEffect(outputFile, hasRecording) {
        val file = outputFile?.takeIf { hasRecording && it.isFile && it.length() > 0L }
        if (file == null) {
            previewPlayer = null
            previewReady = false
            previewLoading = false
            previewDurationMs = 0L
            previewPositionMs = 0L
            return@LaunchedEffect
        }

        previewLoading = true
        previewReady = false
        previewPositionMs = 0L
        previewDurationMs = 0L
        playing = false
        var candidate: MediaPlayer? = null
        try {
            val next = MediaPlayer()
            candidate = next
            next.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            next.setOnPreparedListener { readyPlayer ->
                if (previewPlayer === readyPlayer && outputFile == file && hasRecording) {
                    val duration = readyPlayer.duration.toLong().coerceAtLeast(0L)
                    previewDurationMs = duration
                    previewPositionMs = 0L
                    previewLoading = false
                    previewReady = duration > 0L
                    if (duration <= 0L) error = "The recording could not be played. Record again."
                    else error = null
                }
            }
            next.setOnCompletionListener { completed ->
                playing = false
                previewPositionMs = 0L
                runCatching { completed.seekTo(0) }
            }
            next.setOnErrorListener { failedPlayer, _, _ ->
                if (previewPlayer === failedPlayer) {
                    playing = false
                    previewReady = false
                    previewLoading = false
                    previewPositionMs = 0L
                    error = "The recording could not be played. Record again."
                    previewPlayer = null
                }
                true
            }
            next.setDataSource(file.absolutePath)
            previewPlayer = next
            next.prepareAsync()
        } catch (cause: Throwable) {
            candidate?.runCatching { setOnPreparedListener(null) }
            candidate?.runCatching { setOnCompletionListener(null) }
            candidate?.runCatching { setOnErrorListener(null) }
            candidate?.runCatching { release() }
            if (previewPlayer === candidate) previewPlayer = null
            previewReady = false
            previewLoading = false
            playing = false
            error = cause.message?.takeIf { it.isNotBlank() } ?: "The recording could not be played. Record again."
        }
    }

    DisposableEffect(previewPlayer) {
        val activePlayer = previewPlayer
        onDispose {
            runCatching { activePlayer?.setOnPreparedListener(null) }
            runCatching { activePlayer?.setOnCompletionListener(null) }
            runCatching { activePlayer?.setOnErrorListener(null) }
            runCatching { activePlayer?.release() }
        }
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

    LaunchedEffect(playing, previewPlayer, previewReady, seeking) {
        val active = previewPlayer ?: return@LaunchedEffect
        while (playing && previewReady && !seeking) {
            delay(120L)
            val position = runCatching { active.currentPosition.toLong() }.getOrNull() ?: break
            previewPositionMs = position.coerceIn(0L, previewDurationMs)
            if (previewDurationMs > 0L && position >= previewDurationMs) {
                playing = false
                previewPositionMs = 0L
                break
            }
        }
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
                Text(
                    when {
                        recording && paused -> "Recording paused"
                        recording -> "Recording your voice…"
                        hasRecording && previewLoading -> "Preparing your voice recording preview…"
                        hasRecording && previewReady -> "Preview your voice before publishing. Your recording is not uploaded until you press Post."
                        hasRecording -> "This recording could not be previewed. Record again before using it."
                        else -> "Record up to 2 minutes, preview it, then choose Use recording."
                    }
                )
                if (recording) {
                    Text("\${formatVoiceTime(elapsedMs)} / \${formatVoiceTime(MAX_VOICE_POST_DURATION_MS)}")
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
                    Text("Voice recording • \${formatVoiceTime(previewDurationMs)}")
                    if (previewLoading) {
                        CircularProgressIndicator()
                    } else if (previewReady && previewDurationMs > 0L) {
                        Slider(
                            value = previewPositionMs.toFloat().coerceIn(0f, previewDurationMs.toFloat()),
                            onValueChange = { value ->
                                seeking = true
                                previewPositionMs = value.toLong().coerceIn(0L, previewDurationMs)
                            },
                            onValueChangeFinished = {
                                runCatching { previewPlayer?.seekTo(previewPositionMs.coerceIn(0L, previewDurationMs).toInt()) }
                                    .onFailure { error = "The recording could not be moved to that position." }
                                seeking = false
                            },
                            valueRange = 0f..previewDurationMs.toFloat(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("\${formatVoiceTime(previewPositionMs)} / \${formatVoiceTime(previewDurationMs)}")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = previewReady && !previewLoading && previewPlayer != null,
                            onClick = {
                                val player = previewPlayer ?: return@OutlinedButton
                                runCatching {
                                    if (playing || player.isPlaying) {
                                        player.pause()
                                        playing = false
                                    } else {
                                        if (previewDurationMs > 0L && previewPositionMs >= previewDurationMs) {
                                            player.seekTo(0)
                                            previewPositionMs = 0L
                                        }
                                        player.start()
                                        playing = true
                                    }
                                }.onFailure {
                                    playing = false
                                    error = "The recording could not be played. Record again."
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text(if (playing) "Pause preview" else "▶ Preview") }
                        OutlinedButton(
                            onClick = {
                                discard()
                                startRecording()
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Record again") }
                    }
                } else {
                    Button(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                startRecording()
                            } else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        Modifier.fillMaxWidth()
                    ) { Text("Start recording") }
                }
                error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (hasRecording && !recording) {
                Button(
                    enabled = previewReady && !previewLoading && previewPlayer != null && previewDurationMs > 0L &&
                        outputFile?.let { it.isFile && it.length() > 0L } == true,
                    onClick = {
                        val file = outputFile?.takeIf { it.isFile && it.length() > 0L }
                        if (file != null && previewReady && previewDurationMs > 0L) {
                            onRecorded(Uri.fromFile(file))
                            previewPlayer = null
                            outputFile = null
                            hasRecording = false
                            previewReady = false
                            previewLoading = false
                            playing = false
                        }
                    }
                ) { Text("Use recording") }
            }
        },
        dismissButton = { TextButton(onClick = { discard(); onDismiss() }) { Text("Cancel") } }
    )
}

private fun formatVoiceTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1000L).toInt()
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
