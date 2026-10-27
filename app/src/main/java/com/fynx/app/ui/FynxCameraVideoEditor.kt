package com.fynx.app.ui

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/** A validated, source-bounded time selection for a video edit. */
data class FynxCameraVideoEditRange private constructor(
    val startMs: Long,
    val endMs: Long,
    val sourceDurationMs: Long
) {
    val clipDurationMs: Long get() = endMs - startMs

    companion object {
        const val MIN_CLIP_DURATION_MS = 1_000L

        fun normalize(startMs: Long, endMs: Long, sourceDurationMs: Long): FynxCameraVideoEditRange {
            require(sourceDurationMs >= MIN_CLIP_DURATION_MS) {
                "Video must be at least one second long to edit."
            }
            val safeEnd = endMs.coerceIn(MIN_CLIP_DURATION_MS, sourceDurationMs)
            val safeStart = startMs.coerceIn(0L, safeEnd - MIN_CLIP_DURATION_MS)
            return FynxCameraVideoEditRange(safeStart, safeEnd, sourceDurationMs)
        }
    }
}

/** Losslessly trims and/or mutes CameraX MP4 clips by remuxing their original tracks. */
object FynxCameraVideoEditor {
    private const val DEFAULT_SAMPLE_BUFFER_BYTES = 1 * 1024 * 1024
    private const val MAX_SAMPLE_BUFFER_BYTES = 16 * 1024 * 1024

    fun edit(source: File, startMs: Long, endMs: Long, muteAudio: Boolean): File {
        require(source.isFile && source.length() > 0L) { "Source video is missing or empty." }
        val metadata = MediaMetadataRetriever()
        val durationMs: Long
        val rotation: Int
        try {
            metadata.setDataSource(source.absolutePath)
            durationMs = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: error("Could not read video duration.")
            rotation = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
        } finally {
            metadata.release()
        }

        val range = FynxCameraVideoEditRange.normalize(startMs, endMs, durationMs)
        if (range.startMs == 0L && range.endMs >= durationMs && !muteAudio) return source
        val output = File(source.parentFile, "fynx_video_edited_${System.nanoTime()}.mp4")
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var muxerStopped = false
        try {
            extractor.setDataSource(source.absolutePath)
            val trackMap = IntArray(extractor.trackCount) { -1 }
            var videoInputTrack = -1
            var inputBufferBytes = DEFAULT_SAMPLE_BUFFER_BYTES

            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                val isVideo = mime.startsWith("video/")
                val isAudio = mime.startsWith("audio/")
                if (!isVideo && (!isAudio || muteAudio)) continue
                if (isVideo && videoInputTrack >= 0) continue
                trackMap[index] = (muxer ?: MediaMuxer(
                    output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                ).also { muxer = it }).addTrack(format)
                if (isVideo) videoInputTrack = index
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    inputBufferBytes = maxOf(inputBufferBytes, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                }
            }
            check(videoInputTrack >= 0) { "No supported video track was found in this clip." }
            val activeMuxer = muxer ?: error("Could not prepare the video editor.")
            if (rotation in listOf(90, 180, 270)) activeMuxer.setOrientationHint(rotation)
            for (index in trackMap.indices) if (trackMap[index] >= 0) extractor.selectTrack(index)

            activeMuxer.start()
            muxerStarted = true
            val startUs = range.startMs * 1_000L
            val endUs = range.endMs * 1_000L
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_NEXT_SYNC)
            val buffer = ByteBuffer.allocateDirect(
                inputBufferBytes.coerceIn(DEFAULT_SAMPLE_BUFFER_BYTES, MAX_SAMPLE_BUFFER_BYTES)
            )
            val info = MediaCodec.BufferInfo()
            var firstVideoSyncUs: Long? = null
            var copiedVideoSamples = 0

            while (true) {
                val inputTrack = extractor.sampleTrackIndex
                val sampleTimeUs = extractor.sampleTime
                if (inputTrack < 0 || sampleTimeUs < 0L || sampleTimeUs > endUs) break
                val flags = extractor.sampleFlags
                if (inputTrack == videoInputTrack &&
                    sampleTimeUs >= startUs &&
                    flags and MediaExtractor.SAMPLE_FLAG_SYNC != 0 &&
                    firstVideoSyncUs == null
                ) firstVideoSyncUs = sampleTimeUs

                val firstPtsUs = firstVideoSyncUs
                val outputTrack = trackMap.getOrElse(inputTrack) { -1 }
                if (firstPtsUs != null && outputTrack >= 0 && sampleTimeUs >= firstPtsUs) {
                    buffer.clear()
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize > 0) {
                        info.set(0, sampleSize, sampleTimeUs - firstPtsUs, flags)
                        activeMuxer.writeSampleData(outputTrack, buffer, info)
                        if (inputTrack == videoInputTrack) copiedVideoSamples++
                    }
                }
                if (!extractor.advance()) break
            }
            check(firstVideoSyncUs != null && copiedVideoSamples > 0) {
                "No video keyframe was available in the selected range. Choose a slightly longer clip."
            }
            activeMuxer.stop()
            muxerStopped = true
            check(output.isFile && output.length() > 0L) { "Video editor produced an empty clip." }
            return output
        } catch (failure: Exception) {
            output.delete()
            if (failure is IllegalArgumentException || failure is IllegalStateException) throw failure
            throw IllegalStateException(failure.message ?: "Video edits failed.", failure)
        } finally {
            if (muxerStarted && !muxerStopped) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }
    }
}
