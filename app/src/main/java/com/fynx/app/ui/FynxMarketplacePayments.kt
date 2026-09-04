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

    return FynxBackendClient.postJson(
        context,
        "/api/marketplace/orders/$orderId/payment",
        JSONObject().put("customerEmail", email).toString()
    ).mapCatching { raw ->
        val o = JSONObject(raw)
        val authorizationUrl = o.optString("authorizationUrl").trim()
        val reference = o.optString("reference").trim()
        require(authorizationUrl.startsWith("https://")) { "Payment provider returned an invalid checkout URL." }
        require(reference.isNotBlank()) { "Payment provider returned no payment reference." }
        FynxMarketplacePayment(
            authorizationUrl = authorizationUrl,
            accessCode = o.optString("accessCode").takeIf { it.isNotBlank() },
            reference = reference,
            amount = o.optDouble("amount"),
            currency = o.optString("currency", "NGN")
        )
    }
}

internal fun openMarketplaceCheckout(context: Context, authorizationUrl: String): Result<Unit> = runCatching {
    context.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(authorizationUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

internal suspend fun verifyMarketplacePayment(
    context: Context,
    reference: String
): Result<String> {
    val encoded = Uri.encode(reference.trim())
    return FynxBackendClient.get(
        context,
        "/api/marketplace/payments/verify/$encoded"
    ).mapCatching { raw ->
        val o = JSONObject(raw)
        require(o.optBoolean("verified")) { "Payment has not been verified yet." }
        o.optJSONObject("order")?.optString("status").orEmpty().ifBlank { "PAID" }
    }
}
