package com.fynx.app.ui

import android.content.Context
import org.json.JSONObject

/** Authenticated client for the FYNX AI backend. */
object AiAssistantClient {
    suspend fun sendMessage(
        context: Context,
        message: String,
        history: List<AiMessage> = emptyList(),
        conversationSummary: String = "",
        currentTask: String = ""
    ): Result<String> = runCatching {
        val historyJson = org.json.JSONArray().apply {
            history.takeLast(12).forEach { item ->
                put(JSONObject().put("role", if (item.fromUser) "user" else "assistant").put("text", item.text.trim().take(2000)))
            }
        }
        val body = JSONObject().put("message", message).put("history", historyJson).put("context", JSONObject().put("summary", conversationSummary.trim().take(900)).put("currentTask", currentTask.trim().take(160))).toString()
        val response = FynxBackendClient.postJson(context, "/api/assistant/agent", body).getOrThrow()
        JSONObject(response).optString("reply").ifBlank { throw IllegalStateException("Assistant returned an empty response") }
    }

    suspend fun improvePostCaption(context: Context, caption: String): Result<String> {
        val clean = caption.trim().take(4000)
        if (clean.isBlank()) return Result.failure(IllegalArgumentException("Caption is empty"))
        return sendMessage(context, "Improve this social-media post caption. Keep the user's original meaning and facts, make it natural, clear and engaging, and do not add invented personal details. Return only the finished caption.\n\nCaption:\n$clean")
    }

    suspend fun improveStatusText(context: Context, statusText: String): Result<String> {
        val clean = statusText.trim().take(3000)
        if (clean.isBlank()) return Result.failure(IllegalArgumentException("Status text is empty"))
        return sendMessage(
            context,
            "Improve this FYNX Status text. Preserve the user's meaning, tone and all supplied facts. Make it clear, natural and engaging without adding events, achievements, relationships, promises or personal details that the user did not provide. Return only the finished Status text.\n\nStatus text:\n$clean"
        )
    }

    suspend fun improveProfileBio(context: Context, bio: String): Result<String> {
        val clean = bio.trim().take(1000)
        if (clean.isBlank()) return Result.failure(IllegalArgumentException("Profile bio is empty"))
        return sendMessage(
            context,
            "Improve this FYNX profile bio. Keep it concise, authentic and clear. Preserve only the user's supplied identity, interests and facts; do not invent a job, location, achievements, credentials, contact details or personal history. Return only the finished bio.\n\nBio:\n$clean"
        )
    }

    suspend fun improveShopDescription(context: Context, shopName: String, description: String): Result<String> {
        val name = shopName.trim().take(120)
        val clean = description.trim().take(800)
        if (clean.isBlank() && name.isBlank()) return Result.failure(IllegalArgumentException("Add shop details first"))
        return sendMessage(
            context,
            "Improve this FYNX My Shop description using only the shop name and description supplied by its owner. Make it professional, clear and welcoming. Do not invent services, product categories, business history, certifications, guarantees, opening hours, location, contact details or delivery promises. Return only the finished shop description.\n\nShop name:\n$name\n\nCurrent description:\n$clean"
        )
    }

    suspend fun translateText(context: Context, text: String, targetLanguage: String): Result<String> {
        val cleanText = text.trim().take(8000)
        val language = targetLanguage.trim().take(80)
        if (cleanText.isBlank()) return Result.failure(IllegalArgumentException("Text is empty"))
        if (language.isBlank()) return Result.failure(IllegalArgumentException("Target language is required"))
        return sendMessage(
            context,
            "Translate the following user-provided text into $language. Preserve the original meaning, names, numbers, links, formatting intent and uncertainty. Do not add commentary, explanations, invented facts, or change the speaker's intent. Return only the translation.\n\nText:\n$cleanText"
        )
    }

    suspend fun createProductMarketingCopy(
        context: Context,
        productName: String,
        category: String,
        condition: String,
        sellerFacts: String,
        hasMedia: Boolean,
        mediaCount: Int
    ): Result<String> {
        val name = productName.trim().take(120)
        val facts = sellerFacts.trim().take(4000)
        if (name.isBlank() && facts.isBlank()) return Result.failure(IllegalArgumentException("Add product details first"))
        val mediaNote = if (hasMedia) "The seller has attached $mediaCount real product photo/video item(s). Do not claim details that cannot be established from the seller's written facts or verified product information." else "No product media is attached yet. Do not invent visual details."
        return sendMessage(
            context,
            "Create FYNX marketplace selling copy for a real product. Write one attractive but truthful product caption, followed by a short line of relevant hashtags. Make it easy to read, persuasive and suitable for social discovery. You may use common relevant hashtags, but never promise that any hashtag will make a post viral or guarantee sales. Preserve all seller facts. Never invent specifications, brand claims, prices, discounts, guarantees, availability, reviews or personal details. Do not hide uncertainty. Return only the caption and hashtag line.\n\nProduct name:\n$name\n\nCategory:\n${category.trim().take(80)}\n\nCondition:\n${condition.trim().take(40)}\n\nSeller facts:\n$facts\n\nMedia context:\n$mediaNote"
        )
    }
}
