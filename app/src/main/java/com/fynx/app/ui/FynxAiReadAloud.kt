package com.fynx.app.ui

import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.Locale
import kotlin.math.sqrt

/** Read a stored answer through FYNX's server-configured realtime voice, without microphone input. */
internal object FynxAiReadAloud {
    const val OUTPUT_SAMPLE_RATE = 24_000
    private const val PCM_BYTES_PER_SAMPLE = 2

    fun buildResponseEvent(text: String): String {
        require(text.isNotBlank()) { "There is no response text to read." }
        val inputMessage = JSONObject()
            .put("type", "message")
            .put("role", "user")
            .put(
                "content",
                JSONArray().put(JSONObject().put("type", "input_text").put("text", text))
            )
        val response = JSONObject()
            .put("conversation", "none")
            .put("input", JSONArray().put(inputMessage))
            .put("output_modalities", JSONArray().put("audio"))
            .put(
                "audio",
                JSONObject().put(
                    "output",
                    JSONObject().put(
                        "format",
                        JSONObject().put("type", "audio/pcm").put("rate", OUTPUT_SAMPLE_RATE)
                    )
                )
            )
            .put("tool_choice", "none")
            .put("tools", JSONArray())
            .put(
                "instructions",
                "Read the supplied FYNX Assistant response verbatim, from beginning to end. " +
                    "Do not summarize, paraphrase, omit, add a greeting, or add commentary. " +
                    "Use a warm, confident, natural conversational voice at a steady, clear pace. " +
                    "Use natural pauses at punctuation and pronounce names, dates, amounts, and numbers clearly."
            )
        return JSONObject().put("type", "response.create").put("response", response).toString()
    }

    /** Convert one PCM 24 kHz delta into short RMS waveform samples for the inline player. */
    fun appendPcmWaveform(current: List<Float>, base64AudioDelta: String, maxBars: Int = 120): List<Float> {
        if (base64AudioDelta.isBlank() || maxBars <= 0) return current.takeLast(maxBars.coerceAtLeast(0))
        val bytes = runCatching { Base64.getDecoder().decode(base64AudioDelta) }.getOrNull() ?: return current
        val sampleCount = bytes.size / PCM_BYTES_PER_SAMPLE
        if (sampleCount == 0) return current
        val barCount = minOf(maxBars, maxOf(1, sampleCount / 240))
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val appended = ArrayList<Float>(barCount)
        for (bar in 0 until barCount) {
            val start = (bar.toLong() * sampleCount / barCount).toInt()
            val end = (((bar + 1L) * sampleCount / barCount).toInt()).coerceAtLeast(start + 1)
            var sumSquares = 0.0
            var count = 0
            for (sampleIndex in start until minOf(end, sampleCount)) {
                val sample = buffer.getShort(sampleIndex * PCM_BYTES_PER_SAMPLE).toInt()
                val normalized = sample / 32768.0
                sumSquares += normalized * normalized
                count++
            }
            val rms = if (count == 0) 0.0 else sqrt(sumSquares / count)
            appended += (rms * 3.5).coerceIn(0.10, 1.0).toFloat()
        }
        val combined = current + appended
        if (combined.size <= maxBars) return combined
        val compacted = ArrayList<Float>(maxBars)
        for (bucket in 0 until maxBars) {
            val start = (bucket.toLong() * combined.size / maxBars).toInt()
            val end = (((bucket + 1L) * combined.size / maxBars).toInt()).coerceAtLeast(start + 1)
            var sum = 0f
            var count = 0
            for (index in start until minOf(end, combined.size)) {
                sum += combined[index]
                count++
            }
            compacted += if (count == 0) 0.10f else (sum / count).coerceIn(0.10f, 1f)
        }
        return compacted
    }

    /** Duration represented by a PCM 24 kHz, signed 16-bit mono audio delta. */
    fun pcmDurationMillis(base64AudioDelta: String): Long {
        if (base64AudioDelta.isBlank()) return 0L
        val byteCount = runCatching { Base64.getDecoder().decode(base64AudioDelta).size }.getOrNull() ?: return 0L
        val sampleCount = byteCount / PCM_BYTES_PER_SAMPLE
        return sampleCount * 1000L / OUTPUT_SAMPLE_RATE
    }

    fun formatPlaybackTime(milliseconds: Long): String {
        val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1000L)
        return String.format(Locale.US, "%02d:%02d", totalSeconds / 60L, totalSeconds % 60L)
    }

    /**
     * Bound a possibly-ahead generated transcript by the measured playback position.
     * Realtime transcripts can finish generating before buffered audio finishes playing;
     * using the full transcript alone could incorrectly skip the rest on pause/resume.
     */
    fun remainingTextAtPlaybackPosition(
        source: String,
        transcript: String,
        elapsedMs: Long,
        durationMs: Long
    ): String {
        if (source.isBlank()) return ""
        if (durationMs <= 0L) return remainingText(source, transcript)
        if (elapsedMs >= durationMs) return ""

        val wordPattern = Regex("""[\p{L}\p{N}]+""")
        val sourceWords = wordPattern.findAll(source).toList()
        if (sourceWords.isEmpty()) return source
        val progress = (elapsedMs.toDouble() / durationMs.toDouble()).coerceIn(0.0, 1.0)
        val maxSpokenWords = (sourceWords.size * progress).toInt()
        if (maxSpokenWords <= 0) return source

        val transcriptWords = wordPattern.findAll(transcript).toList()
        if (transcriptWords.size < maxSpokenWords) {
            // Transcript events may lag behind the audio currently playing. In that
            // case the PCM-derived playback clock is the safer resume boundary.
            val boundary = sourceWords[maxSpokenWords - 1].range.last + 1
            return source.substring(boundary).trimStart { character ->
                character.isWhitespace() || character in ".,;:!?…—–-"
            }
        }
        val boundedTranscript = transcriptWords
            .take(maxSpokenWords)
            .joinToString(" ") { it.value }
        return remainingText(source, boundedTranscript)
    }

    /** Resume at the latest recognized word boundary without repeating the whole answer. */
    fun remainingText(source: String, transcript: String): String {
        if (source.isBlank()) return ""
        if (transcript.isBlank()) return source
        val wordPattern = Regex("""[\p{L}\p{N}]+""")
        val sourceWords = wordPattern.findAll(source).toList()
        val spokenWords = wordPattern.findAll(transcript).map { it.value.lowercase() }.toList()
        if (sourceWords.isEmpty() || spokenWords.isEmpty()) return source
        val maxSuffix = minOf(12, spokenWords.size, sourceWords.size)
        for (window in maxSuffix downTo 1) {
            val suffix = spokenWords.takeLast(window)
            var matchedEnd = -1
            for (start in 0..(sourceWords.size - window)) {
                if ((0 until window).all { offset ->
                        sourceWords[start + offset].value.lowercase() == suffix[offset]
                    }
                ) {
                    matchedEnd = maxOf(matchedEnd, sourceWords[start + window - 1].range.last + 1)
                }
            }
            if (matchedEnd >= 0) {
                return source.substring(matchedEnd).trimStart { character ->
                    character.isWhitespace() || character in ".,;:!?…—–-"
                }
            }
        }
        var commonPrefix = 0
        val limit = minOf(sourceWords.size, spokenWords.size)
        while (commonPrefix < limit &&
            sourceWords[commonPrefix].value.equals(spokenWords[commonPrefix], ignoreCase = true)
        ) commonPrefix++
        return if (commonPrefix == 0) source else
            source.substring(sourceWords[commonPrefix - 1].range.last + 1).trimStart { character ->
                character.isWhitespace() || character in ".,;:!?…—–-"
            }
    }
}
