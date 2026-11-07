package com.fynx.app.ui

import org.json.JSONArray
import org.json.JSONObject

/** Read a stored answer through FYNX's server-configured realtime voice, without microphone input. */
internal object FynxAiReadAloud {
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
