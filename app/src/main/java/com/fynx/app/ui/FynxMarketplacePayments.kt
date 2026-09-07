package com.fynx.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject

internal data class FynxMarketplacePayment(
    val authorizationUrl: String,
    val accessCode: String?,
    val reference: String,
    val amount: Double,
    val currency: String
)

internal suspend fun initializeMarketplacePayment(
    context: Context,
    orderId: String,
    customerEmail: String
): Result<FynxMarketplacePayment> {
    val email = customerEmail.trim()
    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
        return Result.failure(IllegalArgumentException("Enter a valid email address."))
    }
    if (orderId.trim().isEmpty()) {
        return Result.failure(IllegalArgumentException("A valid order is required."))
    }

    return FynxBackendClient.postJson(
        context,
        "/api/marketplace/orders/${Uri.encode(orderId.trim())}/payment",
        JSONObject().put("email", email).toString()
    ).mapCatching { raw ->
        val o = JSONObject(raw)
        val authorizationUrl = o.optString("authorizationUrl").trim()
        val reference = o.optString("reference").trim()
        val amountSubunit = o.optLong("amountSubunit", -1L)
        val currency = o.optString("currency", "NGN").trim().uppercase()
        require(authorizationUrl.startsWith("https://")) { "Payment provider returned an invalid checkout URL." }
        require(reference.isNotBlank()) { "Payment provider returned no payment reference." }
        require(amountSubunit > 0L) { "Payment provider returned an invalid amount." }
        require(currency == "NGN" || currency == "USD") { "Payment provider returned an unsupported currency." }
        FynxMarketplacePayment(
            authorizationUrl = authorizationUrl,
            accessCode = o.optString("accessCode").takeIf { it.isNotBlank() },
            reference = reference,
            amount = amountSubunit / 100.0,
            currency = currency
        )
    }
}

internal fun openMarketplaceCheckout(context: Context, authorizationUrl: String): Result<Unit> = runCatching {
    val url = authorizationUrl.trim()
    require(url.startsWith("https://")) { "Invalid payment checkout URL." }
    context.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

internal suspend fun verifyMarketplacePayment(
    context: Context,
    reference: String
): Result<String> {
    val normalizedReference = reference.trim()
    if (normalizedReference.isEmpty()) {
        return Result.failure(IllegalArgumentException("A payment reference is required."))
    }
    val encoded = Uri.encode(normalizedReference)
    return FynxBackendClient.get(
        context,
        "/api/marketplace/payments/verify/$encoded"
    ).mapCatching { raw ->
        val o = JSONObject(raw)
        require(o.optBoolean("verified")) { "Payment has not been verified yet." }
        o.optJSONObject("order")?.optString("status").orEmpty().ifBlank { "PAID" }
    }
}
