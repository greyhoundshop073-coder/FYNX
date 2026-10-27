package com.fynx.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale
import java.util.UUID

internal data class FynxMarketplaceCheckoutQuote(
    val id: String,
    val expiresAt: String,
    val listingId: String,
    val sellerDisplayName: String,
    val productTitle: String,
    val quantity: Int,
    val unitPrice: Double,
    val currency: String,
    val fulfillmentMethod: String,
    val subtotal: Double,
    val deliveryFee: Double,
    val marketplaceFee: Double,
    val marketplaceFeeBuyer: Double,
    val discountAmount: Double,
    val total: Double,
    val protectionText: String
)

internal fun marketplaceQuoteExpired(expiresAt: String, now: Instant = Instant.now()): Boolean = runCatching { !Instant.parse(expiresAt).isAfter(now) }.getOrDefault(true)

internal data class FynxMarketplaceCheckoutAddress(
    val name: String,
    val phone: String,
    val address: String,
    val city: String,
    val state: String,
    val country: String
)


private const val MARKETPLACE_CHECKOUT_ATTEMPTS_PREFS = "fynx_marketplace_checkout_attempts"
private const val MARKETPLACE_CHECKOUT_ORDER_ID_PREFIX = "order_id_"
private const val MARKETPLACE_CHECKOUT_ORDER_TIME_PREFIX = "order_time_"
private const val MARKETPLACE_CHECKOUT_ATTEMPT_LIMIT = 50
private const val MARKETPLACE_CHECKOUT_ATTEMPT_MAX_AGE_MS = 14L * 24 * 60 * 60 * 1000

/**
 * Fingerprints the exact single-order intent without persisting the bearer token or raw delivery
 * details. The server's order ID is idempotent, so an uncertain network result can safely be
 * retried with the same key after the checkout dialog is reopened.
 */
internal fun marketplaceCheckoutIntentFingerprint(
    accessToken: String,
    listingId: String,
    quantity: Int,
    fulfillmentMethod: String,
    address: FynxMarketplaceCheckoutAddress?
): String {
    require(accessToken.isNotBlank()) { "A signed-in account is required for checkout." }
    require(listingId.isNotBlank()) { "A product is required for checkout." }
    require(quantity > 0) { "Quantity must be at least 1." }
    val method = fulfillmentMethod.trim().uppercase(Locale.US)
    require(method == "DELIVERY" || method == "PICKUP") { "Choose delivery or pickup." }
    if (method == "DELIVERY") require(address != null) { "Delivery address is required." }

    val payload = JSONObject().apply {
        put("accountScope", marketplaceCheckoutAccountScope(accessToken.trim()))
        put("listingId", listingId.trim())
        put("quantity", quantity)
        put("fulfillmentMethod", method)
        if (method == "DELIVERY") {
            val value = requireNotNull(address)
            // Match the backend's existing input bounds before hashing the intent.
            put("name", value.name.trim().take(120))
            put("phone", value.phone.trim().take(40))
            put("address", value.address.trim().take(300))
            put("city", value.city.trim().take(120))
            put("state", value.state.trim().take(120))
            put("country", value.country.trim().take(120))
        }
    }
    return marketplaceCheckoutSha256(payload.toString())
}

private fun marketplaceCheckoutAccountScope(accessToken: String): String {
    // FYNX access tokens are JWTs with a stable user subject. Use that subject only to
    // namespace a local retry key; never treat this unverified payload as an auth decision.
    // If the token is opaque/malformed, fall back to hashing the token itself.
    val subject = runCatching {
        val payloadSegment = accessToken.split('.').getOrNull(1).orEmpty()
        if (payloadSegment.isBlank()) "" else {
            val payload = java.util.Base64.getUrlDecoder().decode(payloadSegment)
            JSONObject(String(payload, Charsets.UTF_8)).optString("sub").trim()
        }
    }.getOrNull()?.takeIf { it.isNotBlank() && it != "null" }
    return marketplaceCheckoutSha256(subject?.let { "subject:" + it } ?: "token:" + accessToken.trim())
}

private fun marketplaceCheckoutSha256(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(Locale.US, byte.toInt() and 0xff) }

/** Keeps failed/ambiguous order attempts bounded and removes them only after the server confirms an order. */
internal fun marketplaceCheckoutOrderId(context: Context, intentFingerprint: String): String {
    require(intentFingerprint.matches(Regex("[a-f0-9]{64}"))) { "Invalid checkout intent fingerprint." }
    val preferences = context.getSharedPreferences(MARKETPLACE_CHECKOUT_ATTEMPTS_PREFS, Context.MODE_PRIVATE)
    val idKey = MARKETPLACE_CHECKOUT_ORDER_ID_PREFIX + intentFingerprint
    val timeKey = MARKETPLACE_CHECKOUT_ORDER_TIME_PREFIX + intentFingerprint
    val now = System.currentTimeMillis()
    val cutoff = now - MARKETPLACE_CHECKOUT_ATTEMPT_MAX_AGE_MS
    val existing = preferences.getString(idKey, null)
    val existingAt = preferences.getLong(timeKey, 0L)
    if (existing != null && existingAt >= cutoff && runCatching { UUID.fromString(existing) }.isSuccess) {
        preferences.edit().putLong(timeKey, now).apply()
        return existing
    }

    val allIds = preferences.all.keys.filter { it.startsWith(MARKETPLACE_CHECKOUT_ORDER_ID_PREFIX) }
    val retained = allIds
        .filter { it != idKey && preferences.getLong(MARKETPLACE_CHECKOUT_ORDER_TIME_PREFIX + it.removePrefix(MARKETPLACE_CHECKOUT_ORDER_ID_PREFIX), 0L) >= cutoff }
        .sortedBy { preferences.getLong(MARKETPLACE_CHECKOUT_ORDER_TIME_PREFIX + it.removePrefix(MARKETPLACE_CHECKOUT_ORDER_ID_PREFIX), 0L) }
        .takeLast(MARKETPLACE_CHECKOUT_ATTEMPT_LIMIT - 1)
        .toSet()
    val editor = preferences.edit()
    allIds.filterNot { it in retained }.forEach { key ->
        val fingerprint = key.removePrefix(MARKETPLACE_CHECKOUT_ORDER_ID_PREFIX)
        editor.remove(key).remove(MARKETPLACE_CHECKOUT_ORDER_TIME_PREFIX + fingerprint)
    }
    val created = UUID.randomUUID().toString()
    editor.putString(idKey, created).putLong(timeKey, now).apply()
    return created
}

/**
 * An order creation response can still be PAYMENT_PENDING. Keep its idempotency key so closing
 * checkout or retrying failed payment initialization reuses the same server order. Clear the key
 * only after the authoritative payment-verification path reports success for this order.
 */
internal fun clearMarketplaceCheckoutOrderIdForOrder(context: Context, orderId: String) {
    if (orderId.isBlank()) return
    val preferences = context.getSharedPreferences(MARKETPLACE_CHECKOUT_ATTEMPTS_PREFS, Context.MODE_PRIVATE)
    val editor = preferences.edit()
    var changed = false
    preferences.all.keys.filter { it.startsWith(MARKETPLACE_CHECKOUT_ORDER_ID_PREFIX) }.forEach { key ->
        if (preferences.getString(key, null) == orderId) {
            val fingerprint = key.removePrefix(MARKETPLACE_CHECKOUT_ORDER_ID_PREFIX)
            editor.remove(key)
                .remove(MARKETPLACE_CHECKOUT_ORDER_TIME_PREFIX + fingerprint)
            changed = true
        }
    }
    if (changed) editor.commit()
}

internal fun parseMarketplaceCheckoutQuoteResponse(
    raw: String,
    expectedListingId: String,
    expectedCurrency: String,
    expectedQuantity: Int,
    expectedFulfillmentMethod: String,
    now: Instant = Instant.now()
): FynxMarketplaceCheckoutQuote {
    val quote = JSONObject(raw).optJSONObject("quote")
        ?: throw IllegalStateException("Checkout quote response was incomplete.")

    fun optionalText(field: String): String =
        quote.optString(field).trim().takeIf { it.isNotBlank() && it != "null" }.orEmpty()

    fun requiredText(field: String): String =
        optionalText(field).takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Checkout quote is missing $field.")

    fun amount(field: String): Double = quote.optDouble(field, Double.NaN)

    val quoteId = requiredText("id")
    val expiresAt = requiredText("expiresAt")
    if (marketplaceQuoteExpired(expiresAt, now)) {
        throw IllegalStateException("Checkout quote has expired or is invalid. Review the exact total again.")
    }
    val listingId = requiredText("listingId")
    val quantity = quote.optInt("quantity", -1)
    val currency = requiredText("currency").uppercase(Locale.US)
    val method = requiredText("fulfillmentMethod").uppercase(Locale.US)
    if (listingId != expectedListingId.trim()) {
        throw IllegalStateException("The checkout quote belongs to a different product. Review checkout again.")
    }
    if (quantity != expectedQuantity || quantity <= 0) {
        throw IllegalStateException("The checkout quantity changed. Review the exact total again.")
    }
    if (currency !in setOf("NGN", "USD") || currency != expectedCurrency.trim().uppercase(Locale.US)) {
        throw IllegalStateException("The checkout currency changed. Review the product before continuing.")
    }
    if (method !in setOf("DELIVERY", "PICKUP") || method != expectedFulfillmentMethod.trim().uppercase(Locale.US)) {
        throw IllegalStateException("The fulfillment method changed. Review checkout again.")
    }

    val unitPrice = amount("unitPrice")
    val subtotal = amount("subtotal")
    val deliveryFee = amount("deliveryFee")
    val marketplaceFee = amount("marketplaceFee")
    val marketplaceFeeBuyer = amount("marketplaceFeeBuyer")
    val discountAmount = amount("discountAmount")
    val total = amount("total")
    val amounts = listOf(unitPrice, subtotal, deliveryFee, marketplaceFee, marketplaceFeeBuyer, discountAmount, total)
    if (amounts.any { !it.isFinite() }) {
        throw IllegalStateException("The checkout quote contains an invalid amount. Review the exact total again.")
    }
    if (unitPrice <= 0.0 || subtotal <= 0.0 || deliveryFee < 0.0 || marketplaceFee < 0.0 ||
        marketplaceFeeBuyer < 0.0 || marketplaceFeeBuyer > marketplaceFee + 0.02 ||
        discountAmount < 0.0 || total <= 0.0 ||
        discountAmount > subtotal + deliveryFee + marketplaceFeeBuyer + 0.02 ||
        (method == "PICKUP" && deliveryFee > 0.02)
    ) {
        throw IllegalStateException("The checkout quote contains inconsistent prices or fees. Review the exact total again.")
    }
    val expectedSubtotal = Math.round(unitPrice * quantity * 100.0) / 100.0
    if (kotlin.math.abs(subtotal - expectedSubtotal) > 0.02) {
        throw IllegalStateException("The checkout subtotal does not match the product quantity. Review checkout again.")
    }
    val expectedTotal = Math.round((subtotal + deliveryFee + marketplaceFeeBuyer - discountAmount) * 100.0) / 100.0
    if (kotlin.math.abs(total - expectedTotal) > 0.02) {
        throw IllegalStateException("The checkout total does not match its item, delivery and fee breakdown. Review the exact total again.")
    }

    return FynxMarketplaceCheckoutQuote(
        id = quoteId,
        expiresAt = expiresAt,
        listingId = listingId,
        // The backend may omit a profile display name; its authenticated seller username remains authoritative.
        sellerDisplayName = optionalText("sellerDisplayName").ifBlank { requiredText("sellerUsername") },
        productTitle = requiredText("productTitle"),
        quantity = quantity,
        unitPrice = unitPrice,
        currency = currency,
        fulfillmentMethod = method,
        subtotal = subtotal,
        deliveryFee = deliveryFee,
        marketplaceFee = marketplaceFee,
        marketplaceFeeBuyer = marketplaceFeeBuyer,
        discountAmount = discountAmount,
        total = total,
        protectionText = quote.optJSONObject("protection")?.optString("payout").orEmpty()
    )
}

internal suspend fun requestMarketplaceCheckoutQuote(
    context: Context,
    listing: FynxRemoteSocialClient.MarketplaceListing,
    quantity: Int,
    fulfillmentMethod: String,
    address: FynxMarketplaceCheckoutAddress?
): Result<FynxMarketplaceCheckoutQuote> {
    val method = fulfillmentMethod.uppercase(Locale.US)
    val body = JSONObject().apply {
        put("listingId", listing.id.toLongOrNull() ?: return Result.failure(IllegalArgumentException("Invalid listing.")))
        put("quantity", quantity)
        put("fulfillmentMethod", method)
        if (method == "DELIVERY") {
            val a = address ?: return Result.failure(IllegalArgumentException("Delivery address is required."))
            put("shippingAddress", JSONObject().apply {
                put("name", a.name.trim())
                put("phone", a.phone.trim())
                put("address", a.address.trim())
                put("city", a.city.trim())
                put("state", a.state.trim())
                put("country", a.country.trim())
            })
        }
    }
    return FynxBackendClient.postJson(context, "/api/marketplace/checkout/quote", body.toString()).mapCatching { raw ->
        parseMarketplaceCheckoutQuoteResponse(
            raw = raw,
            expectedListingId = listing.id,
            expectedCurrency = listing.currency,
            expectedQuantity = quantity,
            expectedFulfillmentMethod = method
        )
    }
}

internal fun parseProtectedMarketplaceCheckoutOrderResponse(
    raw: String,
    expectedListingId: String,
    expectedCurrency: String,
    expectedQuantity: Int,
    expectedFulfillmentMethod: String
): FynxRemoteSocialClient.MarketplaceOrder {
    val order = JSONObject(raw).optJSONObject("order")
        ?: throw IllegalStateException("Protected checkout response was incomplete.")

    fun optionalText(field: String): String =
        order.optString(field).trim().takeIf { it.isNotBlank() && it != "null" }.orEmpty()

    fun requiredText(field: String): String =
        optionalText(field).takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Protected order is missing $field.")

    val id = requiredText("id")
    if (runCatching { UUID.fromString(id) }.isFailure) {
        throw IllegalStateException("Protected order returned an invalid order ID.")
    }
    val listingId = requiredText("listingId")
    val quantity = order.optInt("quantity", -1)
    val currency = requiredText("currency").uppercase(Locale.US)
    val method = requiredText("fulfillmentMethod").uppercase(Locale.US)
    if (listingId != expectedListingId.trim()) {
        throw IllegalStateException("Protected order belongs to a different product. Review checkout again.")
    }
    if (quantity != expectedQuantity || quantity <= 0) {
        throw IllegalStateException("Protected order quantity does not match checkout. Review checkout again.")
    }
    if (currency !in setOf("NGN", "USD") || currency != expectedCurrency.trim().uppercase(Locale.US)) {
        throw IllegalStateException("Protected order currency does not match checkout. Review checkout again.")
    }
    if (method !in setOf("DELIVERY", "PICKUP") || method != expectedFulfillmentMethod.trim().uppercase(Locale.US)) {
        throw IllegalStateException("Protected order fulfillment does not match checkout. Review checkout again.")
    }

    val unitPrice = order.optDouble("unitPrice", Double.NaN)
    val productSubtotal = order.optDouble("productSubtotal", Double.NaN)
    val deliveryFee = order.optDouble("deliveryFee", Double.NaN)
    val marketplaceFee = order.optDouble("marketplaceFee", Double.NaN)
    val marketplaceFeeBuyer = order.optDouble("marketplaceFeeBuyer", Double.NaN)
    val marketplaceFeeSeller = order.optDouble("marketplaceFeeSeller", Double.NaN)
    val discountAmount = order.optDouble("discountAmount", Double.NaN)
    val totalAmount = order.optDouble("buyerTotal", order.optDouble("totalAmount", Double.NaN))
    val amounts = listOf(
        unitPrice, productSubtotal, deliveryFee, marketplaceFee,
        marketplaceFeeBuyer, marketplaceFeeSeller, discountAmount, totalAmount
    )
    if (amounts.any { !it.isFinite() }) {
        throw IllegalStateException("Protected order contains an invalid price or fee. Do not continue to payment.")
    }
    if (unitPrice <= 0.0 || productSubtotal <= 0.0 || deliveryFee < 0.0 ||
        marketplaceFee < 0.0 || marketplaceFeeBuyer < 0.0 || marketplaceFeeSeller < 0.0 ||
        discountAmount < 0.0 || totalAmount <= 0.0 ||
        marketplaceFeeBuyer > marketplaceFee + 0.02 ||
        kotlin.math.abs(marketplaceFee - marketplaceFeeBuyer - marketplaceFeeSeller) > 0.02 ||
        discountAmount > productSubtotal + deliveryFee + marketplaceFeeBuyer + 0.02 ||
        (method == "PICKUP" && deliveryFee > 0.02)
    ) {
        throw IllegalStateException("Protected order contains inconsistent prices or fees. Do not continue to payment.")
    }
    val expectedSubtotal = Math.round(unitPrice * quantity * 100.0) / 100.0
    if (kotlin.math.abs(productSubtotal - expectedSubtotal) > 0.02) {
        throw IllegalStateException("Protected order subtotal does not match the product quantity. Review checkout again.")
    }
    val expectedTotal = Math.round((productSubtotal + deliveryFee + marketplaceFeeBuyer - discountAmount) * 100.0) / 100.0
    if (kotlin.math.abs(totalAmount - expectedTotal) > 0.02) {
        throw IllegalStateException("Protected order total does not match its fee breakdown. Do not continue to payment.")
    }

    val status = requiredText("status").uppercase(Locale.US)
    val product = order.optJSONObject("product")
    val productTitle = product?.optString("title").orEmpty().trim()
        .takeIf { it.isNotBlank() && it != "null" }
        ?: requiredText("productTitle")
    return FynxRemoteSocialClient.MarketplaceOrder(
        id = id,
        buyerId = optionalText("buyerId"),
        sellerId = optionalText("sellerId"),
        listingId = listingId,
        quantity = quantity,
        unitPrice = unitPrice,
        deliveryFee = deliveryFee,
        totalAmount = totalAmount,
        currency = currency,
        productTitle = productTitle,
        sellerUsername = product?.optString("sellerUsername")?.trim()?.takeIf { it.isNotBlank() && it != "null" },
        status = status,
        trackingReference = null,
        fulfillmentMethod = method,
        shippingAddress = order.optJSONObject("shippingAddress"),
        buyerNote = optionalText("buyerNote"),
        inspectionDeadline = null,
        deliveryAvailable = product?.optBoolean("deliveryAvailable", false) ?: false,
        pickupAvailable = product?.optBoolean("pickupAvailable", false) ?: false
    )
}

internal suspend fun createProtectedMarketplaceCheckoutOrder(
    context: Context,
    listing: FynxRemoteSocialClient.MarketplaceListing,
    quantity: Int,
    fulfillmentMethod: String,
    address: FynxMarketplaceCheckoutAddress?,
    orderId: String
): Result<FynxRemoteSocialClient.MarketplaceOrder> {
    val method = fulfillmentMethod.uppercase(Locale.US)
    val body = JSONObject().apply {
        put("listingId", listing.id.toLongOrNull() ?: return Result.failure(IllegalArgumentException("Invalid listing.")))
        put("quantity", quantity)
        put("fulfillmentMethod", method)
        put("orderId", orderId)
        if (method == "DELIVERY") {
            val a = address ?: return Result.failure(IllegalArgumentException("Delivery address is required."))
            put("name", a.name.trim())
            put("phone", a.phone.trim())
            put("address", a.address.trim())
            put("city", a.city.trim())
            put("state", a.state.trim())
            put("country", a.country.trim())
        }
    }
    return FynxBackendClient.postJson(context, "/api/marketplace/checkout/order", body.toString()).mapCatching { raw ->
        parseProtectedMarketplaceCheckoutOrderResponse(
            raw = raw,
            expectedListingId = listing.id,
            expectedCurrency = listing.currency,
            expectedQuantity = quantity,
            expectedFulfillmentMethod = method
        )
    }
}

@Composable
internal fun FynxMarketplaceCheckoutDialog(
    context: Context,
    listing: FynxRemoteSocialClient.MarketplaceListing,
    onProtectedOrder: (FynxRemoteSocialClient.MarketplaceOrder) -> Unit,
    onClose: () -> Unit
) {
    var quantityText by remember { mutableStateOf("1") }
    var fulfillment by remember { mutableStateOf(if (listing.pickupAvailable) "PICKUP" else "DELIVERY") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("Nigeria") }
    var quote by remember { mutableStateOf<FynxMarketplaceCheckoutQuote?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var quoteRequestGeneration by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val quantity = quantityText.toIntOrNull() ?: 0
    val quantityError = when {
        listing.quantity <= 0 -> "This product is currently out of stock."
        quantity <= 0 -> "Choose a quantity of at least 1."
        quantity > listing.quantity -> "Maximum available quantity is ${listing.quantity}."
        else -> null
    }

    fun invalidateQuote() {
        // Invalidate in-flight responses as well as any quote already shown to the buyer.
        quoteRequestGeneration += 1
        quote = null
        message = null
    }

    fun refreshQuote() {
        quoteRequestGeneration += 1
        val requestGeneration = quoteRequestGeneration
        quote = null
        if (quantityError != null) {
            message = quantityError
            return
        }
        if (fulfillment == "DELIVERY" && (name.isBlank() || phone.isBlank() || address.isBlank())) {
            message = "Name, phone and delivery address are required."
            return
        }
        busy = true
        message = null
        scope.launch {
            val result = requestMarketplaceCheckoutQuote(
                context,
                listing,
                quantity,
                fulfillment,
                if (fulfillment == "DELIVERY") FynxMarketplaceCheckoutAddress(name, phone, address, city, state, country) else null
            )
            if (quoteRequestGeneration == requestGeneration) {
                result.onSuccess { quote = it }
                    .onFailure { message = it.message ?: "Checkout quote could not be prepared." }
            }
            busy = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text("Checkout") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(listing.title, style = MaterialTheme.typography.titleMedium)
                Text("${listing.currency} ${String.format(Locale.US, "%,.2f", listing.price)} each", color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(quantityText, { quantityText = it.filter(Char::isDigit); invalidateQuote() }, label = { Text("Quantity") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                quantityError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (listing.deliveryAvailable) FilterChip(selected = fulfillment == "DELIVERY", onClick = { fulfillment = "DELIVERY"; invalidateQuote() }, label = { Text("Delivery") }, enabled = !busy)
                    if (listing.pickupAvailable) FilterChip(selected = fulfillment == "PICKUP", onClick = { fulfillment = "PICKUP"; invalidateQuote() }, label = { Text("Pickup") }, enabled = !busy)
                }
                if (fulfillment == "DELIVERY") {
                    OutlinedTextField(name, { name = it; invalidateQuote() }, label = { Text("Full name") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(phone, { phone = it; invalidateQuote() }, label = { Text("Phone") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(address, { address = it; invalidateQuote() }, label = { Text("Delivery address") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(city, { city = it; invalidateQuote() }, label = { Text("City") }, singleLine = true, enabled = !busy, modifier = Modifier.weight(1f))
                        OutlinedTextField(state, { state = it; invalidateQuote() }, label = { Text("State") }, singleLine = true, enabled = !busy, modifier = Modifier.weight(1f))
                    }
                }
                Button(onClick = { refreshQuote() }, enabled = !busy && quantityError == null, modifier = Modifier.fillMaxWidth()) {
                    if (busy) CircularProgressIndicator(Modifier.height(18.dp)) else Text("Review exact total")
                }
                quote?.let { q ->
                    Spacer(Modifier.height(4.dp))
                    Text("Order summary", style = MaterialTheme.typography.titleSmall)
                    Text("Subtotal: ${q.currency} ${String.format(Locale.US, "%,.2f", q.subtotal)}")
                    Text("Delivery: ${q.currency} ${String.format(Locale.US, "%,.2f", q.deliveryFee)}")
                    Text("FYNX fee: ${q.currency} ${String.format(Locale.US, "%,.2f", q.marketplaceFeeBuyer)}")
                    Text("Discount: ${q.currency} ${String.format(Locale.US, "%,.2f", q.discountAmount)}")
                    Text("Total: ${q.currency} ${String.format(Locale.US, "%,.2f", q.total)}", style = MaterialTheme.typography.titleLarge)
                    Text("Payment is protected. Seller payout is released only after the buyer confirmation flow.", style = MaterialTheme.typography.bodySmall)
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                enabled = quote != null && !busy,
                onClick = {
                    val q = quote ?: return@Button
                    if (marketplaceQuoteExpired(q.expiresAt)) {
                        quote = null
                        message = "This checkout quote has expired. Review the exact total again."
                        return@Button
                    }
                    busy = true
                    message = null
                    val checkoutAddress = if (q.fulfillmentMethod == "DELIVERY") {
                        FynxMarketplaceCheckoutAddress(name, phone, address, city, state, country)
                    } else null
                    val accessToken = FynxBackendClient.accessToken(context)
                    val intentFingerprint = accessToken?.takeIf { it.isNotBlank() }?.let { token ->
                        marketplaceCheckoutIntentFingerprint(
                            token,
                            listing.id,
                            q.quantity,
                            q.fulfillmentMethod,
                            checkoutAddress
                        )
                    }
                    val stableOrderId = intentFingerprint?.let { marketplaceCheckoutOrderId(context, it) }
                        ?: UUID.randomUUID().toString()
                    scope.launch {
                        createProtectedMarketplaceCheckoutOrder(
                            context,
                            listing,
                            q.quantity,
                            q.fulfillmentMethod,
                            checkoutAddress,
                            stableOrderId
                        ).onSuccess { order ->
                            // The order may still be PAYMENT_PENDING. Keep the idempotency key
                            // until the payment-verification callback confirms payment.
                            onProtectedOrder(order)
                        }.onFailure { message = marketplacePaymentFailureMessage(it) }
                        busy = false
                    }
                }
            ) { Text("Place protected order") }
        },
        dismissButton = { TextButton(onClick = onClose, enabled = !busy) { Text("Cancel") } }
    )
}
