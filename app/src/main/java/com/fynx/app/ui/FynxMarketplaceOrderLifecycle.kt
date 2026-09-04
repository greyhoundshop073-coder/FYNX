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
                busy = true
                error = null
                scope.launch {
                    FynxRemoteSocialClient.setMarketplaceFulfillment(context, order.id, method, name, phone, address, city, state, country, note)
                        .onSuccess { showFulfillment = false; onChanged() }
                        .onFailure { error = it.message ?: "Fulfillment could not be saved." }
                    busy = false
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
                Text("Fulfillment: ${order.fulfillmentMethod}")
                order.trackingReference?.let { Text("Tracking: $it") }
                when (order.status) {
                    "PAID" -> Text("Choose how you want to receive the order.")
                    "SHIPPED" -> Text("The seller marked this order as shipped. Confirm when it reaches you.")
                    "INSPECTION" -> Text("You have a 48-hour inspection window. Complete the order when everything is correct.")
                    "COMPLETED" -> Text("Order completed. Payment is eligible for seller payout release.")
                    else -> Text("This order is protected by FYNX marketplace status controls.")
                }
                error?.let { Text(it) }
            }
        },
        confirmButton = {
            when (order.status) {
                "PAID" -> Button(onClick = { error = null; showFulfillment = true }) { Text("Choose fulfillment") }
                "SHIPPED" -> Button(enabled = !busy, onClick = {
                    busy = true; error = null
                    scope.launch {
                        FynxRemoteSocialClient.confirmMarketplaceDelivery(context, order.id)
                            .onSuccess { onChanged() }
                            .onFailure { error = it.message ?: "Delivery confirmation failed." }
                        busy = false
                    }
                }) { if (busy) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Confirm received") }
                "INSPECTION" -> Button(enabled = !busy, onClick = {
                    busy = true; error = null
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
    var note by remember { mutableStateOf(order.buyerNote) }

    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text("Receive your order") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Choose an available fulfillment method.")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (order.deliveryAvailable) FilterChip(method == "DELIVERY", { method = "DELIVERY" }, label = { Text("Delivery") })
                    if (order.pickupAvailable) FilterChip(method == "PICKUP", { method = "PICKUP" }, label = { Text("Pickup") })
                }
                if (method == "DELIVERY") {
                    OutlinedTextField(name, { name = it }, label = { Text("Full name") }, singleLine = true, enabled = !busy)
                    OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, singleLine = true, enabled = !busy)
                    OutlinedTextField(address, { address = it }, label = { Text("Delivery address") }, minLines = 2, enabled = !busy)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(city, { city = it }, label = { Text("City") }, singleLine = true, enabled = !busy, modifier = Modifier.weight(1f))
                        OutlinedTextField(state, { state = it }, label = { Text("State") }, singleLine = true, enabled = !busy, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(country, { country = it }, label = { Text("Country") }, singleLine = true, enabled = !busy)
                }
                OutlinedTextField(note, { note = it }, label = { Text("Note to seller (optional)") }, minLines = 2, enabled = !busy)
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
