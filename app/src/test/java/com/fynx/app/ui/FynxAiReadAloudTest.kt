package com.fynx.app.ui

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

class FynxAiReadAloudTest {
    @Test
    fun responseUsesAudioWithoutToolsOrConversationWrites() {
        val event = JSONObject(FynxAiReadAloud.buildResponseEvent("Hello, Ada. Your order is ready."))
        val response = event.getJSONObject("response")
        val part = response.getJSONArray("input").getJSONObject(0)
            .getJSONArray("content").getJSONObject(0)
        val pcmFormat = response.getJSONObject("audio").getJSONObject("output").getJSONObject("format")
        assertEquals("response.create", event.getString("type"))
        assertEquals("none", response.getString("conversation"))
        assertEquals("audio", response.getJSONArray("output_modalities").getString(0))
        assertEquals("audio/pcm", pcmFormat.getString("type"))
        assertEquals(24_000, pcmFormat.getInt("rate"))
        assertEquals("none", response.getString("tool_choice"))
        assertEquals(0, response.getJSONArray("tools").length())
        assertEquals("input_text", part.getString("type"))
        assertEquals("Hello, Ada. Your order is ready.", part.getString("text"))
        assertTrue(response.getString("instructions").contains("verbatim"))
        assertTrue(response.getString("instructions").contains("natural conversational voice"))
    }

    @Test
    fun rejectsBlankText() {
        try {
            FynxAiReadAloud.buildResponseEvent("   ")
            throw AssertionError("Blank response text must be rejected.")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun convertsRealPcmDeltasIntoBoundedWaveformLevels() {
        val pcm = ByteBuffer.allocate(24_000 * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(24_000) { index ->
            val sample = if (index % 2 == 0) 12_000 else -12_000
            pcm.putShort(sample.toShort())
        }
        val encoded = Base64.getEncoder().encodeToString(pcm.array())
        val levels = FynxAiReadAloud.appendPcmWaveform(emptyList(), encoded)
        assertTrue(levels.isNotEmpty())
        assertTrue(levels.size <= 120)
        assertTrue(levels.all { it in 0.10f..1f })
        assertEquals(1_000L, FynxAiReadAloud.pcmDurationMillis(encoded))
    }

    @Test
    fun silenceHasLowStableWaveformAndZeroDurationForEmptyData() {
        val silence = ByteArray(480 * 2)
        val encoded = Base64.getEncoder().encodeToString(silence)
        val levels = FynxAiReadAloud.appendPcmWaveform(emptyList(), encoded)
        assertTrue(levels.isNotEmpty())
        assertTrue(levels.all { it == 0.10f })
        assertEquals(0L, FynxAiReadAloud.pcmDurationMillis(""))
    }

    @Test
    fun formatsPlaybackTimeAsMinutesAndSeconds() {
        assertEquals("00:00", FynxAiReadAloud.formatPlaybackTime(0L))
        assertEquals("01:03", FynxAiReadAloud.formatPlaybackTime(63_999L))
        assertEquals("02:00", FynxAiReadAloud.formatPlaybackTime(120_000L))
    }

    @Test
    fun resumesAfterRecognizedWords() {
        assertEquals("Today is Friday.", FynxAiReadAloud.remainingText(
            "Hello, Ada. Today is Friday.", "Hello, Ada."
        ))
    }

    @Test
    fun keepsTheFullTextWhenNothingMatches() {
        assertEquals("The order has shipped.", FynxAiReadAloud.remainingText(
            "The order has shipped.", "unmatched transcript"
        ))
    }

    @Test
    fun ignoresPunctuationImmediatelyAfterTheLastSpokenWord() {
        assertEquals(
            "The remaining words.",
            FynxAiReadAloud.remainingText("Hello, Ada. The remaining words.", "Hello, Ada.")
        )
    }

    @Test
    fun returnsEmptyWhenTheTranscriptReachedTheEnd() {
        assertEquals("", FynxAiReadAloud.remainingText(
            "Thank you, Ada.", "Thank you, Ada."
        ))
    }
}
