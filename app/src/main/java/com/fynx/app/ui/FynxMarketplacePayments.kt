package com.fynx.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URI
import java.util.Locale
import kotlinx.coroutines.delay
import org.json.JSONObject

internal data class FynxMarketplacePayment(
    val authorizationUrl: String,
    val accessCode: String?,
    val reference: String,
    val amount: Double,
    val currency: String
)

internal fun isTrustedMarketplaceCheckoutUrl(value: String): Boolean {
    val parsed = runCatching { URI(value.trim()) }.getOrNull() ?: return false
    val host = parsed.host?.lowercase(Locale.US) ?: return false
    return parsed.scheme.equals("https", ignoreCase = true) &&
        host in setOf("checkout.paystack.com", "paystack.com") &&
        parsed.userInfo == null &&
        parsed.port in setOf(-1, 443) &&
        !parsed.isOpaque
}

internal fun marketplacePaymentFailureMessage(error: Throwable): String {
    val details = generateSequence(error) { it.cause }
        .mapNotNull { it.message }
        .joinToString(" ")
    return if (details.contains("marketplace payments are not configured yet", ignoreCase = true)) {
        "Marketplace payments are not available yet. No payment was started, and your cart is unchanged. Please try again later."
    } else {
        marketplaceSafeError(error, "Could not start protected payment.")
    }
}

internal suspend fun initializeMarketplacePayment(
    context: Context,
    orderId: String,
    customerEmail: String,
    expectedAmount: Double,
    expectedCurrency: String
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
        parseMarketplacePaymentResponse(raw, expectedAmount, expectedCurrency)
    }
}

internal fun parseMarketplacePaymentResponse(
    raw: String,
    expectedAmount: Double,
    expectedCurrency: String
): FynxMarketplacePayment {
    val o = JSONObject(raw)
    val authorizationUrl = o.optString("authorizationUrl").trim()
    val reference = o.optString("reference").trim()
    val amountSubunit = o.optLong("amountSubunit", -1L)
    val currency = marketplaceRequiredOrderCurrency(o.opt("currency"))
    val normalizedExpectedCurrency = marketplaceRequiredOrderCurrency(expectedCurrency)
    require(isTrustedMarketplaceCheckoutUrl(authorizationUrl)) { "Payment provider returned an untrusted checkout URL." }
    require(reference.isNotBlank()) { "Payment provider returned no payment reference." }
    require(amountSubunit > 0L) { "Payment provider returned an invalid amount." }
    require(expectedAmount.isFinite() && expectedAmount > 0.0) { "The reviewed order amount is invalid." }
    require(currency == normalizedExpectedCurrency) { "Payment provider currency does not match the order currency." }
    val expectedAmountSubunit = java.math.BigDecimal.valueOf(expectedAmount)
        .setScale(2, java.math.RoundingMode.HALF_UP)
        .movePointRight(2)
        .longValueExact()
    require(amountSubunit == expectedAmountSubunit) { "Payment provider amount does not match the reviewed order total." }
    return FynxMarketplacePayment(
        authorizationUrl = authorizationUrl,
        accessCode = o.optString("accessCode").takeIf { it.isNotBlank() },
        reference = reference,
        amount = amountSubunit / 100.0,
        currency = currency
    )
}

internal fun openMarketplaceCheckout(context: Context, authorizationUrl: String): Result<Unit> = runCatching {
    val url = authorizationUrl.trim()
    require(isTrustedMarketplaceCheckoutUrl(url)) { "Untrusted payment checkout URL." }
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
    var lastFailure: Throwable? = null
    repeat(3) { attempt ->
        val result = FynxBackendClient.get(
            context,
            "/api/marketplace/payments/verify/$encoded"
        ).mapCatching { raw ->
            val o = JSONObject(raw)
            require(o.optBoolean("verified")) { "Payment has not been verified yet." }
            o.optJSONObject("order")?.optString("status").orEmpty().ifBlank { "PAID" }
        }
        if (result.isSuccess) return result
        lastFailure = result.exceptionOrNull()
        if (attempt < 2) delay(1_000L * (attempt + 1))
    }
    return Result.failure(lastFailure ?: IllegalStateException("Payment verification failed."))
}

internal suspend fun initializeMarketplaceCheckoutGroupPayment(
    context: Context,
    checkoutId: String,
    customerEmail: String
): Result<FynxMarketplacePayment> {
    val email = customerEmail.trim()
    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) return Result.failure(IllegalArgumentException("Enter a valid email address."))
    if (checkoutId.trim().isEmpty()) return Result.failure(IllegalArgumentException("A valid checkout is required."))
    return FynxBackendClient.postJson(
        context,
        "/api/marketplace/checkout-groups/${Uri.encode(checkoutId.trim())}/payment",
        JSONObject().put("email", email).toString()
    ).mapCatching { raw ->
        val o = JSONObject(raw)
        val authorizationUrl = o.optString("authorizationUrl").trim()
        val reference = o.optString("reference").trim()
        val amountSubunit = o.optLong("amountSubunit", -1L)
        val currency = marketplaceRequiredOrderCurrency(o.opt("currency"))
        require(isTrustedMarketplaceCheckoutUrl(authorizationUrl)) { "Payment provider returned an untrusted checkout URL." }
        require(reference.startsWith("FYNX-CHECKOUT-")) { "Payment provider returned an invalid checkout reference." }
        require(amountSubunit > 0L) { "Payment provider returned an invalid amount." }
        FynxMarketplacePayment(authorizationUrl, o.optString("accessCode").takeIf { it.isNotBlank() }, reference, amountSubunit / 100.0, currency)
    }
}

internal suspend fun verifyMarketplaceCheckoutGroupPayment(
    context: Context,
    reference: String
): Result<String> {
    val normalizedReference = reference.trim()
    if (!normalizedReference.startsWith("FYNX-CHECKOUT-")) return Result.failure(IllegalArgumentException("A valid checkout payment reference is required."))
    val encoded = Uri.encode(normalizedReference)
    var lastFailure: Throwable? = null
    repeat(3) { attempt ->
        val result = FynxBackendClient.get(context, "/api/marketplace/checkout-groups/verify/$encoded").mapCatching { raw ->
            val o = JSONObject(raw)
            require(o.optBoolean("verified")) { "Payment has not been verified yet." }
            require(o.optString("status").equals("PAID", ignoreCase = true)) { "Checkout payment is not complete yet." }
            o.optString("status").ifBlank { "PAID" }
        }
        if (result.isSuccess) return result
        lastFailure = result.exceptionOrNull()
        if (attempt < 2) delay(1_000L * (attempt + 1))
    }
    return Result.failure(lastFailure ?: IllegalStateException("Checkout payment verification failed."))
}
