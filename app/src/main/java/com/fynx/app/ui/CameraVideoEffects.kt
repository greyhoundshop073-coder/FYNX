@file:OptIn(androidx.media3.common.util.UnstableApi::class)
package com.fynx.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import androidx.media3.common.Effect
import androidx.media3.transformer.Effects
import androidx.media3.common.MediaItem
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.AudioSource
import com.google.mlkit.genai.speechrecognition.SpeechRecognition
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerResponse
import com.google.mlkit.genai.speechrecognition.speechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.speechRecognizerRequest
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

enum class CameraVideoStyle(val label: String) {
    NATURAL("Natural"), CINEMATIC("Cinematic"), WARM_FILM("Warm Film"),
    VINTAGE("Vintage"), BLACK_AND_WHITE("B&W"), VIVID("Vivid")
}
internal data class CameraCaptionCue(val startMs: Long, val endMs: Long, val text: String)

/** Phrase timing is approximate because whole-file transcription does not return word timestamps. */
internal fun buildCameraCaptionCues(transcript: String, durationMs: Long): List<CameraCaptionCue> {
    val words = transcript.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty() || durationMs <= 0L) return emptyList()
    val chunks = mutableListOf<String>()
    val current = mutableListOf<String>()
    for (word in words) {
        current += word
        if (current.size >= 6 || (current.size >= 3 && (word.endsWith(".") || word.endsWith("?") || word.endsWith("!")))) {
            chunks += current.joinToString(" ")
            current.clear()
        }
    }
    if (current.isNotEmpty()) chunks += current.joinToString(" ")
    return chunks.mapIndexed { index, text ->
        val start = durationMs * index / chunks.size
        val end = durationMs * (index + 1) / chunks.size
        CameraCaptionCue(start, max(start + 1L, end), text)
    }
}

private class TimedCameraTextOverlay(private val cues: List<CameraCaptionCue>) : TextOverlay() {
    private val settings = StaticOverlaySettings.Builder()
        .setBackgroundFrameAnchor(0f, 0.82f).setOverlayFrameAnchor(0f, 1f).setScale(0.76f, 0.76f).build()
    override fun getText(presentationTimeUs: Long): SpannableString {
        val atMs = presentationTimeUs / 1000L
        val text = SpannableString(cues.firstOrNull { atMs >= it.startMs && atMs < it.endMs }?.text.orEmpty())
        if (text.isNotEmpty()) {
            text.setSpan(ForegroundColorSpan(Color.WHITE), 0, text.length, 0)
            text.setSpan(BackgroundColorSpan(0xD9000000.toInt()), 0, text.length, 0)
            text.setSpan(RelativeSizeSpan(0.72f), 0, text.length, 0)
            text.setSpan(StyleSpan(Typeface.BOLD), 0, text.length, 0)
        }
        return text
    }
    override fun getOverlaySettings(presentationTimeUs: Long) = settings
}

internal suspend fun applyFynxVideoEdits(context: Context, sourceUri: Uri, style: CameraVideoStyle, captionText: String = ""): Uri =
    suspendCancellableCoroutine { continuation ->
        val sourceFile = sourceUri.path?.let(::File)
        if (sourceFile == null || !sourceFile.exists() || sourceFile.length() == 0L) {
            continuation.resumeWithException(IllegalArgumentException("The video file is unavailable."))
            return@suspendCancellableCoroutine
        }
        val outputFile = File(context.cacheDir, "fynx_video_edit_" + System.currentTimeMillis() + ".mp4")
        val effects = mutableListOf<Effect>()
        when (style) {
            CameraVideoStyle.NATURAL -> Unit
            CameraVideoStyle.CINEMATIC -> {
                effects += Contrast(0.28f)
                effects += HslAdjustment.Builder().adjustSaturation(-14f).adjustLightness(-3f).build()
            }
            CameraVideoStyle.WARM_FILM -> effects += HslAdjustment.Builder().adjustHue(7f).adjustSaturation(9f).adjustLightness(3f).build()
            CameraVideoStyle.VINTAGE -> effects += HslAdjustment.Builder().adjustHue(-8f).adjustSaturation(-28f).adjustLightness(2f).build()
            CameraVideoStyle.BLACK_AND_WHITE -> effects += RgbFilter.createGrayscaleFilter()
            CameraVideoStyle.VIVID -> effects += HslAdjustment.Builder().adjustSaturation(22f).adjustLightness(4f).build()
        }
        val cleanCaption = captionText.trim().replace(Regex("\\s+"), " ")
        if (cleanCaption.isNotBlank()) {
            val retriever = MediaMetadataRetriever()
            val duration = try {
                retriever.setDataSource(sourceFile.absolutePath)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            } finally { retriever.release() }
            val cues = buildCameraCaptionCues(cleanCaption, duration)
            if (cues.isNotEmpty()) effects += OverlayEffect(listOf(TimedCameraTextOverlay(cues)))
        }
        try {
            val edited = EditedMediaItem.Builder(MediaItem.fromUri(sourceUri)).setEffects(Effects(emptyList(), effects)).build()
            val transformer = Transformer.Builder(context).addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (continuation.isActive) continuation.resume(Uri.fromFile(outputFile))
                }
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    outputFile.delete()
                    if (continuation.isActive) continuation.resumeWithException(exportException)
                }
            }).build()
            continuation.invokeOnCancellation { transformer.cancel(); outputFile.delete() }
            transformer.start(edited, outputFile.absolutePath)
        } catch (failure: Throwable) {
            outputFile.delete()
            if (continuation.isActive) continuation.resumeWithException(failure)
        }
    }

internal suspend fun generateFynxVideoTranscript(context: Context, videoUri: Uri): String {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) error("Automatic captions need Android 12 or newer on this device.")
    val video = videoUri.path?.let(::File) ?: error("The video file is unavailable.")
    val audio = File(context.cacheDir, "fynx_caption_audio_" + System.currentTimeMillis() + ".m4a")
    extractFynxVideoAudio(video, audio)
    val recognizer = SpeechRecognition.getClient(speechRecognizerOptions {
        locale = Locale.US
        preferredMode = SpeechRecognizerOptions.Mode.MODE_BASIC
    })
    try {
        when (recognizer.checkStatus()) {
            FeatureStatus.AVAILABLE -> Unit
            FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> {
                var downloaded = false
                recognizer.download().collect { status ->
                    when (status) {
                        DownloadStatus.DownloadCompleted -> downloaded = true
                        is DownloadStatus.DownloadFailed -> error("The on-device caption model could not be downloaded.")
                        else -> Unit
                    }
                }
                if (!downloaded) error("The on-device caption model is not ready yet. Try again.")
            }
            else -> error("Automatic captions are unavailable on this device.")
        }
        val source = AudioSource.fromFile(audio)
        try {
            val request = speechRecognizerRequest { audioSource = source }
            var transcript = ""
            recognizer.startRecognition(request).collect { response ->
                when (response) {
                    is SpeechRecognizerResponse.FinalTextResponse -> transcript = response.text
                    is SpeechRecognizerResponse.ErrorResponse -> error("Speech recognition could not transcribe this clip.")
                    else -> Unit
                }
            }
            return transcript.trim().takeIf { it.isNotBlank() } ?: error("No clear speech was detected.")
        } finally { source.close() }
    } finally {
        recognizer.close()
        audio.delete()
    }
}

private fun extractFynxVideoAudio(video: File, audio: File) {
    val extractor = MediaExtractor()
    var muxer: MediaMuxer? = null
    var started = false
    try {
        extractor.setDataSource(video.absolutePath)
        var track = -1
        var format: MediaFormat? = null
        for (index in 0 until extractor.trackCount) {
            val candidate = extractor.getTrackFormat(index)
            if (candidate.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                track = index; format = candidate; break
            }
        }
        if (track < 0 || format == null) error("This video has no audio track to caption.")
        if (format.getString(MediaFormat.KEY_MIME) != MediaFormat.MIMETYPE_AUDIO_AAC) error("This video's audio format is not supported.")
        muxer = MediaMuxer(audio.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val outputTrack = muxer.addTrack(format)
        extractor.selectTrack(track)
        muxer.start(); started = true
        val maxSize = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceIn(65536, 2097152) else 524288
        val buffer = ByteBuffer.allocateDirect(maxSize)
        val info = MediaCodec.BufferInfo()
        while (true) {
            buffer.clear()
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            info.set(0, size, extractor.sampleTime, extractor.sampleFlags)
            muxer.writeSampleData(outputTrack, buffer, info)
            if (!extractor.advance()) break
        }
        if (!audio.exists() || audio.length() == 0L) error("No audio could be extracted for captions.")
    } finally {
        if (started) runCatching { muxer?.stop() }
        runCatching { muxer?.release() }
        extractor.release()
    }
}
