package com.fynx.app.ui

/** Final chat integration contracts; backend/payment execution is intentionally deferred. */
enum class FynxPresence { ONLINE, OFFLINE, AWAY }
enum class FynxSafetyAction { BLOCK, REPORT }

data class FynxChatSafetyState(
    val username: String,
    val blocked: Boolean = false,
    val reported: Boolean = false
)

data class FynxChatGiftIntent(
    val conversationId: String,
    val giftId: String,
    val amount: Double,
    val currency: String
)

data class FynxChatPaymentIntent(
    val conversationId: String,
    val amount: Double,
    val currency: String,
    val reference: String
)

data class FynxChatMarketplaceShare(
    val conversationId: String,
    val productId: String,
    val title: String,
    val priceText: String
)

object FynxChatBatch3 {
    fun presenceLabel(presence: FynxPresence): String = when (presence) {
        FynxPresence.ONLINE -> "Online"
        FynxPresence.OFFLINE -> "Offline"
        FynxPresence.AWAY -> "Away"
    }

    fun applySafety(state: FynxChatSafetyState, action: FynxSafetyAction): FynxChatSafetyState =
        when (action) {
            FynxSafetyAction.BLOCK -> state.copy(blocked = true)
            FynxSafetyAction.REPORT -> state.copy(reported = true)
        }

    fun createGiftIntent(conversationId: String, giftId: String, amount: Double, currency: String): FynxChatGiftIntent? =
        if (conversationId.isBlank() || giftId.isBlank() || amount <= 0.0 || currency.isBlank()) null
        else FynxChatGiftIntent(conversationId, giftId, amount, currency)

    fun createPaymentIntent(conversationId: String, amount: Double, currency: String, reference: String): FynxChatPaymentIntent? =
        if (conversationId.isBlank() || amount <= 0.0 || currency.isBlank() || reference.isBlank()) null
        else FynxChatPaymentIntent(conversationId, amount, currency, reference)

    fun shareMarketplaceProduct(conversationId: String, productId: String, title: String, priceText: String): FynxChatMarketplaceShare? =
        if (conversationId.isBlank() || productId.isBlank() || title.isBlank()) null
        else FynxChatMarketplaceShare(conversationId, productId, title, priceText)
}
