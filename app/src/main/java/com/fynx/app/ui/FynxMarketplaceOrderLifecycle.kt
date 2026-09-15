package com.fynx.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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

@Composable
fun FynxMarketplaceOrderLifecycle(
    context: Context,
    order: FynxRemoteSocialClient.MarketplaceOrder,
    onChanged: () -> Unit,
    onClose: () -> Unit
) {
    var showFulfillment by remember { mutableStateOf(order.status == "PAID") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    if (showFulfillment) {
        MarketplaceFulfillmentDialog(
            context = context,
            order = order,
            busy = busy,
            error = error,
            onSubmit = { method, name, phone, address, city, state, country, note ->
                val normalizedMethod = method.trim().uppercase()
                val missingDelivery = normalizedMethod == "DELIVERY" &&
                    listOf(name, phone, address, city, state, country).any { it.trim().isBlank() }
                when {
                    normalizedMethod !in setOf("DELIVERY", "PICKUP") -> {
                        error = "Choose delivery or pickup before continuing."
                    }
                    normalizedMethod == "DELIVERY" && missingDelivery -> {
                        error = "Complete all delivery address and contact fields."
                    }
                    else -> {
                        busy = true
                        error = null
                        scope.launch {
                            FynxRemoteSocialClient.setMarketplaceFulfillment(
                                context,
                                order.id,
                                normalizedMethod,
                                name.trim(),
                                phone.trim(),
                                address.trim(),
                                city.trim(),
                                state.trim(),
                                country.trim(),
                                note.trim().take(500)
                            )
                                .onSuccess { showFulfillment = false; onChanged() }
                                .onFailure { error = it.message ?: "Fulfillment could not be saved." }
                            busy = false
                        }
                    }
                }
            },
            onClose = onClose
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text("Order ${order.status}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(order.productTitle.ifBlank { "FYNX order" })
                Text("${order.currency} ${"%.2f".format(order.totalAmount)}")
                Text("Fulfillment: ${order.fulfillmentMethod.ifBlank { "Not selected" }}")
                order.trackingReference?.takeIf { it.isNotBlank() }?.let { Text("Tracking: $it") }
                FynxMarketplaceOrderTimeline(context = context, orderId = order.id)
                when (order.status) {
                    "PAID" -> Text("Choose delivery or pickup so the seller can fulfill the order.")
                    "SHIPPED" -> Text("The seller marked this order as shipped. Confirm only after the order reaches you.")
                    "INSPECTION" -> Text("You have a 48-hour inspection window. Complete the order only when the product is correct and in acceptable condition.")
                    "COMPLETED" -> Text("Order completed. Payment is eligible for seller payout release when the protected settlement rules are satisfied.")
                    else -> Text("This order is protected by FYNX marketplace status controls.")
                }
                error?.let { Text(it) }
            }
        },
        confirmButton = {
            when (order.status) {
                "PAID" -> Button(onClick = { error = null; showFulfillment = true }) { Text("Choose fulfillment") }
                "SHIPPED" -> Button(enabled = !busy, onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        FynxRemoteSocialClient.confirmMarketplaceDelivery(context, order.id)
                            .onSuccess { onChanged() }
                            .onFailure { error = it.message ?: "Delivery confirmation failed." }
                        busy = false
                    }
                }) { if (busy) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Confirm received") }
                "INSPECTION" -> Button(enabled = !busy, onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        FynxRemoteSocialClient.completeMarketplaceOrder(context, order.id)
                            .onSuccess { onChanged() }
                            .onFailure { error = it.message ?: "Order could not be completed." }
                        busy = false
                    }
                }) { if (busy) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Complete order") }
                else -> Spacer(Modifier.size(1.dp))
            }
        },
        dismissButton = { TextButton(onClick = onClose, enabled = !busy) { Text("Close") } }
    )
}

@Composable
private fun MarketplaceFulfillmentDialog(
    context: Context,
    order: FynxRemoteSocialClient.MarketplaceOrder,
    busy: Boolean,
    error: String?,
    onSubmit: (String, String, String, String, String, String, String, String) -> Unit,
    onClose: () -> Unit
) {
    var method by remember { mutableStateOf(if (order.fulfillmentMethod == "PICKUP") "PICKUP" else if (order.deliveryAvailable) "DELIVERY" else "PICKUP") }
    var name by remember { mutableStateOf(order.shippingAddress?.optString("name").orEmpty()) }
    var phone by remember { mutableStateOf(order.shippingAddress?.optString("phone").orEmpty()) }
    var address by remember { mutableStateOf(order.shippingAddress?.optString("address").orEmpty()) }
    var city by remember { mutableStateOf(order.shippingAddress?.optString("city").orEmpty()) }
    var state by remember { mutableStateOf(order.shippingAddress?.optString("state").orEmpty()) }
    var country by remember { mutableStateOf(order.shippingAddress?.optString("country").orEmpty()) }
    var note by remember { mutableStateOf(order.buyerNote.take(500)) }

    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text("Receive your order") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Choose an available fulfillment method. Your protected order remains governed by the existing FYNX order lifecycle.")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (order.deliveryAvailable) FilterChip(method == "DELIVERY", { method = "DELIVERY" }, label = { Text("Delivery") })
                    if (order.pickupAvailable) FilterChip(method == "PICKUP", { method = "PICKUP" }, label = { Text("Pickup") })
                }
                if (method == "DELIVERY") {
                    OutlinedTextField(name, { name = it.take(120) }, label = { Text("Full name") }, singleLine = true, enabled = !busy)
                    OutlinedTextField(phone, { phone = it.take(40) }, label = { Text("Phone") }, singleLine = true, enabled = !busy)
                    OutlinedTextField(address, { address = it.take(300) }, label = { Text("Delivery address") }, minLines = 2, enabled = !busy)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(city, { city = it.take(100) }, label = { Text("City") }, singleLine = true, enabled = !busy, modifier = Modifier.weight(1f))
                        OutlinedTextField(state, { state = it.take(100) }, label = { Text("State") }, singleLine = true, enabled = !busy, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(country, { country = it.take(100) }, label = { Text("Country") }, singleLine = true, enabled = !busy)
                }
                OutlinedTextField(note, { note = it.take(500) }, label = { Text("Note to seller (optional)") }, minLines = 2, enabled = !busy)
                error?.let { Text(it) }
                Spacer(Modifier.height(2.dp))
            }
        },
        confirmButton = {
            Button(enabled = !busy, onClick = { onSubmit(method, name, phone, address, city, state, country, note) }) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Save fulfillment")
            }
        },
        dismissButton = { TextButton(onClick = onClose, enabled = !busy) { Text("Cancel") } }
    )
}
