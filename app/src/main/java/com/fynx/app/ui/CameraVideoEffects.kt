@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@file:OptIn(androidx.media3.common.util.UnstableApi::class)
package com.fynx.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
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
import com.google.mlkit.genai.common.audio.AudioSource
import com.google.mlkit.genai.speechrecognition.SpeechRecognition
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerResponse
import com.google.mlkit.genai.speechrecognition.speechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.speechRecognizerRequest
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.OutputStream
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


/**
 * Verify the finished MP4 before the editor accepts the export. The check decodes frames, keeps
 * the source video track and any source audio track, and confirms a non-Natural look changed pixels.
 */
internal fun verifyFynxVideoEditOutput(sourceUri: Uri, outputUri: Uri, style: CameraVideoStyle) {
    val sourceFile = sourceUri.path?.let(::File) ?: error("The original video file is unavailable.")
    val outputFile = outputUri.path?.let(::File) ?: error("The edited video file is unavailable.")
    require(sourceFile.isFile && sourceFile.length() > 1024L) { "The original video file is unavailable." }
    require(outputFile.isFile && outputFile.length() > 1024L) { "Video export produced an empty file." }

    fun trackTypes(file: File): Set<String> {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            val types = mutableSetOf<String>()
            for (index in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty()
                when {
                    mime.startsWith("video/") -> types += "video"
                    mime.startsWith("audio/") -> types += "audio"
                }
            }
            types
        } finally {
            extractor.release()
        }
    }

    val sourceTracks = trackTypes(sourceFile)
    val outputTracks = trackTypes(outputFile)
    require("video" in sourceTracks && "video" in outputTracks) {
        "Video export did not produce a readable video track."
    }
    require("audio" !in sourceTracks || "audio" in outputTracks) {
        "Video export did not preserve the original audio track."
    }

    fun firstUsableFrame(file: File): android.graphics.Bitmap {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            val atUs = durationMs.coerceAtMost(2_000L) * 500L
            retriever.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: error("Video export could not decode a preview frame.")
        } finally {
            retriever.release()
        }
    }

    val sourceFrame = firstUsableFrame(sourceFile)
    val outputFrame = firstUsableFrame(outputFile)
    try {
        require(outputFrame.width > 0 && outputFrame.height > 0) {
            "Video export produced an invalid preview frame."
        }
        if (style != CameraVideoStyle.NATURAL) {
            val width = minOf(sourceFrame.width, outputFrame.width)
            val height = minOf(sourceFrame.height, outputFrame.height)
            val stepX = max(1, width / 32)
            val stepY = max(1, height / 32)
            var totalDifference = 0L
            var samples = 0
            var y = 0
            while (y < height) {
                var x = 0
                while (x < width) {
                    val before = sourceFrame.getPixel(x, y)
                    val after = outputFrame.getPixel(x, y)
                    totalDifference += kotlin.math.abs(android.graphics.Color.red(before) - android.graphics.Color.red(after))
                    totalDifference += kotlin.math.abs(android.graphics.Color.green(before) - android.graphics.Color.green(after))
                    totalDifference += kotlin.math.abs(android.graphics.Color.blue(before) - android.graphics.Color.blue(after))
                    samples++
                    x += stepX
                }
                y += stepY
            }
            val meanDifference = if (samples == 0) 0.0 else totalDifference.toDouble() / (samples * 3.0)
            require(meanDifference > 0.5) {
                "The selected ${style.label} style did not visibly change the exported video."
            }
        }
    } finally {
        sourceFrame.recycle()
        outputFrame.recycle()
    }
}

internal suspend fun generateFynxVideoTranscript(context: Context, videoUri: Uri): String {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) error("Automatic captions need Android 12 or newer on this device.")
    val video = videoUri.path?.let(::File) ?: error("The video file is unavailable.")
    val audio = File(context.cacheDir, "fynx_caption_audio_" + System.currentTimeMillis() + ".wav")
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

internal fun extractFynxVideoAudio(video: File, audio: File) {
    val extractor = MediaExtractor()
    var decoder: MediaCodec? = null
    var decoderStarted = false
    val pcmFile = File(audio.parentFile, audio.nameWithoutExtension + ".pcm")
    try {
        extractor.setDataSource(video.absolutePath)
        var audioTrack = -1
        var sourceFormat: MediaFormat? = null
        for (index in 0 until extractor.trackCount) {
            val candidate = extractor.getTrackFormat(index)
            if (candidate.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                audioTrack = index
                sourceFormat = candidate
                break
            }
        }
        val audioFormat = sourceFormat
            ?: error("This video has no audio track to caption.")
        if (audioTrack < 0) error("This video has no audio track to caption.")
        val mime = audioFormat.getString(MediaFormat.KEY_MIME)
            ?: error("This video's audio track has no supported format.")

        extractor.selectTrack(audioTrack)
        val audioDecoder = MediaCodec.createDecoderByType(mime)
        decoder = audioDecoder
        audioDecoder.configure(audioFormat, null, null, 0)
        audioDecoder.start()
        decoderStarted = true

        var inputEnded = false
        var outputEnded = false
        var decodedFormat: MediaFormat? = null
        var idleAfterInputEnd = 0
        pcmFile.outputStream().buffered().use { pcmOutput ->
            val bufferInfo = MediaCodec.BufferInfo()
            while (!outputEnded) {
                if (!inputEnded) {
                    val inputIndex = audioDecoder.dequeueInputBuffer(10_000L)
                    if (inputIndex >= 0) {
                        val inputBuffer = audioDecoder.getInputBuffer(inputIndex)
                            ?: error("Audio decoder did not provide an input buffer.")
                        inputBuffer.clear()
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            audioDecoder.queueInputBuffer(
                                inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputEnded = true
                        } else {
                            check((extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED) == 0) {
                                "Encrypted audio samples cannot be captioned."
                            }
                            audioDecoder.queueInputBuffer(
                                inputIndex, 0, sampleSize, extractor.sampleTime.coerceAtLeast(0L), 0
                            )
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = audioDecoder.dequeueOutputBuffer(bufferInfo, 10_000L)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> decodedFormat = audioDecoder.outputFormat
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (inputEnded && ++idleAfterInputEnd > 3_000) {
                            error("Audio decoder did not finish converting this video's audio.")
                        }
                    }
                    else -> if (outputIndex >= 0) {
                        idleAfterInputEnd = 0
                        val outputBuffer = audioDecoder.getOutputBuffer(outputIndex)
                        if (bufferInfo.size > 0) {
                            decodedFormat = decodedFormat ?: audioDecoder.outputFormat
                            if (outputBuffer == null) {
                                error("Audio decoder returned an empty output buffer.")
                            }
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            val decodedBytes = ByteArray(bufferInfo.size)
                            outputBuffer.get(decodedBytes)
                            pcmOutput.write(decodedBytes)
                        }
                        val reachedEnd = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        audioDecoder.releaseOutputBuffer(outputIndex, false)
                        if (reachedEnd) outputEnded = true
                    }
                }
            }
        }

        val outputFormat = decodedFormat ?: audioDecoder.outputFormat
        val sampleRate = outputFormat.getIntegerOrDefault(MediaFormat.KEY_SAMPLE_RATE, 0)
        val channelCount = outputFormat.getIntegerOrDefault(MediaFormat.KEY_CHANNEL_COUNT, 0)
        val pcmEncoding = outputFormat.getIntegerOrDefault(
            MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT
        )
        require(sampleRate in 8_000..192_000 && channelCount in 1..2) {
            "The decoded audio format is not supported for captions."
        }
        require(pcmEncoding == AudioFormat.ENCODING_PCM_16BIT) {
            "The audio decoder did not produce 16-bit PCM for captions."
        }
        val dataBytes = pcmFile.length()
        val frameSize = channelCount * 2L
        require(dataBytes >= frameSize && dataBytes % frameSize == 0L) {
            "This video's audio could not be converted into complete PCM samples."
        }
        require(dataBytes <= Int.MAX_VALUE.toLong() - 36L) {
            "Audio is too long to caption on this device."
        }
        audio.outputStream().buffered().use { output ->
            writeCameraWavHeader(output, dataBytes, sampleRate, channelCount)
            pcmFile.inputStream().buffered().use { input -> input.copyTo(output) }
        }
    } catch (failure: Throwable) {
        audio.delete()
        throw failure
    } finally {
        if (decoderStarted) runCatching { decoder?.stop() }
        runCatching { decoder?.release() }
        extractor.release()
        pcmFile.delete()
    }
}

private fun MediaFormat.getIntegerOrDefault(key: String, fallback: Int): Int =
    if (containsKey(key)) getInteger(key) else fallback

private fun writeCameraWavHeader(
    output: OutputStream,
    pcmBytes: Long,
    sampleRate: Int,
    channels: Int
) {
    val blockAlign = channels * 2
    val byteRate = sampleRate * blockAlign
    output.write("RIFF".toByteArray(Charsets.US_ASCII))
    writeLittleEndian32(output, 36L + pcmBytes)
    output.write("WAVEfmt ".toByteArray(Charsets.US_ASCII))
    writeLittleEndian32(output, 16L)
    writeLittleEndian16(output, 1)
    writeLittleEndian16(output, channels)
    writeLittleEndian32(output, sampleRate.toLong())
    writeLittleEndian32(output, byteRate.toLong())
    writeLittleEndian16(output, blockAlign)
    writeLittleEndian16(output, 16)
    output.write("data".toByteArray(Charsets.US_ASCII))
    writeLittleEndian32(output, pcmBytes)
}

private fun writeLittleEndian16(output: OutputStream, value: Int) {
    output.write(value and 0xff)
    output.write((value ushr 8) and 0xff)
}

private fun writeLittleEndian32(output: OutputStream, value: Long) {
    output.write((value and 0xffL).toInt())
    output.write(((value ushr 8) and 0xffL).toInt())
    output.write(((value ushr 16) and 0xffL).toInt())
    output.write(((value ushr 24) and 0xffL).toInt())
}
