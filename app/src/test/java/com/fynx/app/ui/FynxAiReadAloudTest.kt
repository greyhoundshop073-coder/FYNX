package com.fynx.app.ui

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FynxAiReadAloudTest {
    @Test
    fun responseUsesAudioWithoutToolsOrConversationWrites() {
        val event = JSONObject(FynxAiReadAloud.buildResponseEvent("Hello, Ada. Your order is ready."))
        val response = event.getJSONObject("response")
        val part = response.getJSONArray("input").getJSONObject(0)
            .getJSONArray("content").getJSONObject(0)
        assertEquals("response.create", event.getString("type"))
        assertEquals("none", response.getString("conversation"))
        assertEquals("audio", response.getJSONArray("output_modalities").getString(0))
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
    fun returnsEmptyWhenTheTranscriptReachedTheEnd() {
        assertEquals("", FynxAiReadAloud.remainingText(
            "Thank you, Ada.", "Thank you, Ada."
        ))
    }
}
