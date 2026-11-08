package com.fynx.app.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal data class FynxMarketplaceAiListingCopySuggestion(
    val title: String,
    val description: String
)

/**
 * Text-only seller copy helper. It sends only product facts entered in the Sell form:
 * name, category, condition, description and the form's visible specifications.
 * Price, quantity, location, seller profile, orders and wallet data are never included.
 */
internal object FynxMarketplaceAiClient {
    suspend fun generateListingCopy(
        context: Context,
        title: String,
        category: String,
        subcategory: String,
        condition: String,
        description: String,
        specifications: List<Pair<String, String>>
    ): Result<FynxMarketplaceAiListingCopySuggestion> {
        val safeTitle = title.trim().take(FynxMarketplaceSellerFlowSupport.MAX_TITLE_LENGTH)
        if (safeTitle.length < 2) {
            return Result.failure(IllegalArgumentException("Enter a product name first."))
        }
        if (specifications.size > 12) {
            return Result.failure(IllegalArgumentException("There are too many product specifications to prepare a suggestion."))
        }
        val body = JSONObject().apply {
            put("title", safeTitle)
            put("category", category.trim())
            put("subcategory", subcategory.trim().take(100))
            put("condition", condition.trim().uppercase())
            put("description", description.trim().take(2_500))
            put("specifications", JSONArray().apply {
                specifications.forEach { (label, value) ->
                    if (label.isNotBlank() && value.isNotBlank()) {
                        put(JSONObject().put("label", label.trim().take(80)).put("value", value.trim().take(180)))
                    }
                }
            })
        }
        return FynxBackendClient.postJson(
            context,
            "/api/assistant/marketplace/listing-copy",
            body.toString()
        ).mapCatching { raw ->
            val suggestion = JSONObject(raw).optJSONObject("suggestion")
                ?: throw IllegalStateException("FYNX AI returned an incomplete listing suggestion.")
            val suggestedTitle = suggestion.optString("title").trim()
            val suggestedDescription = suggestion.optString("description").trim()
            if (suggestedTitle.length !in 2..FynxMarketplaceSellerFlowSupport.MAX_TITLE_LENGTH ||
                suggestedDescription.length !in 20..2_000) {
                throw IllegalStateException("FYNX AI returned an incomplete listing suggestion.")
            }
            FynxMarketplaceAiListingCopySuggestion(suggestedTitle, suggestedDescription)
        }
    }
}
