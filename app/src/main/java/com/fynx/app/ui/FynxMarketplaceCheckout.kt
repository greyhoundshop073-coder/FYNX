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
        val q = JSONObject(raw).getJSONObject("quote")
        FynxMarketplaceCheckoutQuote(
            id = q.optString("id"),
            expiresAt = q.optString("expiresAt"),
            listingId = q.optString("listingId"),
            sellerDisplayName = q.optString("sellerDisplayName"),
            productTitle = q.optString("productTitle"),
            quantity = q.optInt("quantity"),
            unitPrice = q.optDouble("unitPrice"),
            currency = q.optString("currency", listing.currency).trim().uppercase().ifBlank { listing.currency.trim().uppercase() },
            fulfillmentMethod = q.optString("fulfillmentMethod"),
            subtotal = q.optDouble("subtotal"),
            deliveryFee = q.optDouble("deliveryFee"),
            marketplaceFee = q.optDouble("marketplaceFee"),
            marketplaceFeeBuyer = q.optDouble("marketplaceFeeBuyer"),
            discountAmount = q.optDouble("discountAmount"),
            total = q.optDouble("total"),
            protectionText = q.optJSONObject("protection")?.optString("payout").orEmpty()
        )
    }
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
        val o = JSONObject(raw).getJSONObject("order")
        val product = o.optJSONObject("product")
        FynxRemoteSocialClient.MarketplaceOrder(
            id = o.optString("id"),
            buyerId = o.optString("buyerId"),
            sellerId = o.optString("sellerId"),
            listingId = o.optString("listingId"),
            quantity = o.optInt("quantity"),
            unitPrice = o.optDouble("unitPrice"),
            deliveryFee = o.optDouble("deliveryFee"),
            totalAmount = o.optDouble("buyerTotal", o.optDouble("totalAmount")),
            currency = o.optString("currency", listing.currency).trim().uppercase().ifBlank { listing.currency.trim().uppercase() },
            productTitle = product?.optString("title").orEmpty().ifBlank { o.optString("productTitle") },
            sellerUsername = product?.optString("sellerUsername")?.takeIf { it.isNotBlank() },
            status = o.optString("status", "PAYMENT_PENDING"),
            trackingReference = null,
            fulfillmentMethod = o.optString("fulfillmentMethod", method),
            shippingAddress = o.optJSONObject("shippingAddress"),
            buyerNote = o.optString("buyerNote"),
            inspectionDeadline = null,
            deliveryAvailable = product?.optBoolean("deliveryAvailable", false) ?: false,
            pickupAvailable = product?.optBoolean("pickupAvailable", false) ?: false
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
    var quoteRequestGeneration by remember { mutableIntStateOf(0) }
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
                OutlinedTextField(quantityText, { quantityText = it.filter(Char::isDigit); invalidateQuote() }, label = { Text("Quantity") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                quantityError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (listing.deliveryAvailable) FilterChip(selected = fulfillment == "DELIVERY", onClick = { fulfillment = "DELIVERY"; invalidateQuote() }, label = { Text("Delivery") })
                    if (listing.pickupAvailable) FilterChip(selected = fulfillment == "PICKUP", onClick = { fulfillment = "PICKUP"; invalidateQuote() }, label = { Text("Pickup") })
                }
                if (fulfillment == "DELIVERY") {
                    OutlinedTextField(name, { name = it; invalidateQuote() }, label = { Text("Full name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(phone, { phone = it; invalidateQuote() }, label = { Text("Phone") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(address, { address = it; invalidateQuote() }, label = { Text("Delivery address") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(city, { city = it; invalidateQuote() }, label = { Text("City") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(state, { state = it; invalidateQuote() }, label = { Text("State") }, singleLine = true, modifier = Modifier.weight(1f))
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
                    val stableOrderId = UUID.randomUUID().toString()
                    scope.launch {
                        createProtectedMarketplaceCheckoutOrder(
                            context,
                            listing,
                            q.quantity,
                            q.fulfillmentMethod,
                            if (q.fulfillmentMethod == "DELIVERY") FynxMarketplaceCheckoutAddress(name, phone, address, city, state, country) else null,
                            stableOrderId
                        ).onSuccess(onProtectedOrder)
                            .onFailure { message = marketplacePaymentFailureMessage(it) }
                        busy = false
                    }
                }
            ) { Text("Place protected order") }
        },
        dismissButton = { TextButton(onClick = onClose, enabled = !busy) { Text("Cancel") } }
    )
}
