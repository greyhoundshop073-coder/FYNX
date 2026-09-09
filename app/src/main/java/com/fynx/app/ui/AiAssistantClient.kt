package com.fynx.app.ui

import android.content.Context
import org.json.JSONObject

/** Authenticated client for the FYNX AI backend. */
object AiAssistantClient {
    suspend fun sendMessage(context: Context, message: String): Result<String> = runCatching {
        val body = JSONObject().put("message", message).toString()
        val response = FynxBackendClient.postJson(context, "/api/assistant", body).getOrThrow()
        JSONObject(response).optString("reply").ifBlank {
            throw IllegalStateException("Assistant returned an empty response")
        }
    }

    suspend fun improvePostCaption(context: Context, caption: String): Result<String> {
        val clean = caption.trim().take(4000)
        if (clean.isBlank()) return Result.failure(IllegalArgumentException("Caption is empty"))
        return sendMessage(
            context,
            "Improve this social-media post caption. Keep the user's original meaning and facts, make it natural, clear and engaging, and do not add invented personal details. Return only the finished caption.\n\nCaption:\n$clean"
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
        if (name.isBlank() && facts.isBlank()) {
            return Result.failure(IllegalArgumentException("Add product details first"))
        }
        val mediaNote = if (hasMedia) {
            "The seller has attached $mediaCount real product photo/video item(s). Do not claim details that cannot be established from the seller's written facts or verified product information."
        } else {
            "No product media is attached yet. Do not invent visual details."
        }
        return sendMessage(
            context,
            "Create FYNX marketplace selling copy for a real product. Write one attractive but truthful product caption, followed by a short line of relevant hashtags. Make it easy to read, persuasive and suitable for social discovery. You may use common relevant hashtags, but never promise that any hashtag will make a post viral or guarantee sales. Preserve all seller facts. Never invent specifications, brand claims, prices, discounts, guarantees, availability, reviews or personal details. Do not hide uncertainty. Return only the caption and hashtag line.\n\nProduct name:\n$name\n\nCategory:\n${category.trim().take(80)}\n\nCondition:\n${condition.trim().take(40)}\n\nSeller facts:\n$facts\n\nMedia context:\n$mediaNote"
        )
    }
}
