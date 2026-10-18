package com.fynx.app.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Checkbox
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
    var showDispute by remember { mutableStateOf(false) }
    var disputeDetails by remember { mutableStateOf("") }
    var disputeReason by remember { mutableStateOf("OTHER") }
    var evidenceValue by remember { mutableStateOf("") }
    var evidenceFeedback by remember { mutableStateOf<String?>(null) }
    var confirmOrderMatch by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val photoEvidencePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            busy = true
            error = null
            evidenceFeedback = null
            scope.launch {
                val uploaded = FynxProductionMessaging.uploadMedia(context, uri)
                if (uploaded.isFailure) {
                    error = uploaded.exceptionOrNull()?.message ?: "Photo upload failed."
                } else {
                    val media = uploaded.getOrThrow()
                    val allowedImageTypes = setOf("image/jpeg", "image/png", "image/webp", "image/gif")
                    if (media.mimeType.trim().lowercase() !in allowedImageTypes) {
                        error = "Choose a JPEG, PNG, WebP or GIF image."
                    } else {
                        FynxRemoteSocialClient.submitMarketplaceOrderEvidence(context, order.id, "PHOTO", media.id)
                            .onSuccess { evidenceFeedback = "Photo evidence uploaded and attached to this order." }
                            .onFailure { error = it.message ?: "Photo evidence could not be attached." }
                    }
                }
                busy = false
            }
        }
    }

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
        title = { Text(if (showDispute) "Report a problem" else "Order ${order.status}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showDispute) {
                    Text("Choose the problem that best matches your order. Opening a dispute places protected funds on hold while the case is reviewed.")
                    listOf("ITEM_NOT_RECEIVED" to "Not received", "WRONG_ITEM" to "Wrong item", "DAMAGED" to "Damaged", "NOT_AS_DESCRIBED" to "Not as described", "SUSPECTED_SCAM" to "Suspected scam", "OTHER" to "Other").chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { (code, label) -> FilterChip(selected = disputeReason == code, onClick = { disputeReason = code }, label = { Text(label) }, enabled = !busy) }
                        }
                    }
                    OutlinedTextField(
                        value = disputeDetails,
                        onValueChange = { disputeDetails = it.take(4000) },
                        label = { Text("What happened?") },
                        minLines = 4,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("Supporting evidence (optional)", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                    OutlinedTextField(value = evidenceValue, onValueChange = { evidenceValue = it.take(4000) }, label = { Text("Evidence note or message/tracking reference") }, minLines = 2, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    evidenceFeedback?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.primary) }
                    error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            photoEvidencePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    ) { Text(if (busy) "Uploading..." else "Add photo evidence") }
                    TextButton(enabled = !busy && evidenceValue.trim().isNotBlank(), onClick = {
                        busy = true
                        error = null
                        evidenceFeedback = null
                        scope.launch {
                            FynxRemoteSocialClient.submitMarketplaceOrderEvidence(context, order.id, "NOTE", evidenceValue.trim())
                                .onSuccess { evidenceValue = ""; evidenceFeedback = "Evidence submitted to this order." }
                                .onFailure { error = it.message ?: "Evidence could not be submitted." }
                            busy = false
                        }
                    }) { Text(if (busy) "Submitting..." else "Submit evidence") }
                } else {
                    Text(order.productTitle.ifBlank { "FYNX order" })
                    Text("${order.currency} ${"%.2f".format(order.totalAmount)}")
                    Text("Fulfillment: ${order.fulfillmentMethod.ifBlank { "Not selected" }}")
                    order.trackingReference?.takeIf { it.isNotBlank() }?.let { Text("Tracking: $it") }
                    FynxMarketplaceOrderTimeline(context = context, orderId = order.id)
                    when (order.status) {
                        "PAID" -> Text("Choose delivery or pickup so the seller can fulfill the order.")
                        "SHIPPED" -> Text("The seller marked this order as shipped. Confirm only after the order reaches you.")
                        "INSPECTION" -> {
                            Text("You have a 48-hour inspection window. Complete the order only when the product is correct and in acceptable condition.")
                            Row {
                                Checkbox(checked = confirmOrderMatch, onCheckedChange = { confirmOrderMatch = it }, enabled = !busy)
                                Text("I confirm the received item and quantity match my order.")
                            }
                        }
                        "COMPLETED" -> Text("Order completed. Payment is eligible for seller payout release when the protected settlement rules are satisfied.")
                        else -> Text("This order is protected by FYNX marketplace status controls.")
                    }
                    error?.let { Text(it) }
                }
            }
        },
        confirmButton = {
            if (showDispute) {
                Button(
                    enabled = !busy && disputeDetails.trim().isNotBlank(),
                    onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            FynxRemoteSocialClient.disputeMarketplaceOrder(
                                context,
                                order.id,
                                disputeReason,
                                disputeDetails.trim()
                            )
                                .onSuccess { onChanged() }
                                .onFailure { error = it.message ?: "The dispute could not be opened." }
                            busy = false
                        }
                    }
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Open dispute")
                }
            } else {
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
                    "INSPECTION" -> Button(enabled = !busy && confirmOrderMatch, onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            FynxRemoteSocialClient.completeMarketplaceOrder(context, order.id, true, true)
                                .onSuccess { onChanged() }
                                .onFailure { error = it.message ?: "Order could not be completed." }
                            busy = false
                        }
                    }) { if (busy) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Complete order") }
                    else -> Spacer(Modifier.size(1.dp))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (showDispute) {
                        showDispute = false
                        disputeDetails = ""
                        disputeReason = "OTHER"
                        evidenceValue = ""
                        evidenceFeedback = null
                        error = null
                    } else {
                        showDispute = true
                        error = null
                    }
                },
                enabled = !busy
            ) { Text(if (showDispute) "Back" else "Report problem") }
        }
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
