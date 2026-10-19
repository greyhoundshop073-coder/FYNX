package com.fynx.app.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale

private data class SellerOrderRow(
    val id: String,
    val buyerName: String,
    val title: String,
    val total: Double,
    val currency: String,
    val status: String,
    val fulfillment: String,
    val fulfillmentStatus: String,
    val address: JSONObject?,
    val tracking: String?,
    val driverId: String?,
    val driverType: String,
    val localDriverName: String?,
    val localDriverPhone: String?,
    val pickupPoint: String?,
    val pickupInstructions: String?,
    val vehicleType: String?,
    val vehicleColor: String?,
    val vehiclePlate: String?,
    val vehicleNote: String?,
    val driverApprovalActive: Boolean,
    val driverAcceptedAt: String?,
    val driverHandoverAt: String?,
    val driverDeliveredAt: String?
)

private fun money(currency: String, amount: Double): String = "$currency ${String.format(Locale.US, "%,.2f", amount)}"

private fun fulfillmentLabel(state: String): String = when (state) {
    "PAID" -> "Paid — ready to prepare"
    "PREPARING" -> "Preparing"
    "DISPATCHED" -> "Dispatched"
    "IN_TRANSIT" -> "In transit"
    "DELIVERED" -> "Delivered — waiting for buyer inspection"
    "READY_FOR_PICKUP" -> "Ready for pickup"
    "PICKUP_COMPLETED" -> "Pickup completed"
    "INSPECTION" -> "Buyer inspection"
    "COMPLETED" -> "Completed"
    "FAILED_DELIVERY" -> "Failed delivery — protected"
    "RETURNED" -> "Returned — protected"
    else -> state.ifBlank { "Pending" }
}

@Composable
fun FynxMarketplaceSellerOrders(context: Context, onChanged: () -> Unit = {}) {
    var open by remember { mutableStateOf(false) }
    var orders by remember { mutableStateOf<List<SellerOrderRow>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<SellerOrderRow?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        loading = true
        error = null
        scope.launch {
            FynxBackendClient.get(context, "/api/marketplace/seller/orders")
                .onSuccess { raw ->
                    val array = JSONObject(raw).optJSONArray("orders")
                    orders = buildList {
                        if (array != null) for (i in 0 until array.length()) {
                            val o = array.getJSONObject(i)
                            add(SellerOrderRow(
                                id = o.optString("id"),
                                buyerName = o.optString("buyerDisplayName").ifBlank { o.optString("buyerUsername") },
                                title = o.optJSONObject("product")?.optString("title").orEmpty().ifBlank { "FYNX order" },
                                total = o.optDouble("totalAmount"),
                                currency = o.optString("currency", "NGN"),
                                status = o.optString("status"),
                                fulfillment = o.optString("fulfillmentMethod", "DELIVERY"),
                                fulfillmentStatus = o.optString("fulfillmentStatus").ifBlank { o.optString("status") },
                                address = o.optJSONObject("shippingAddress"),
                                tracking = o.optString("trackingReference").takeIf { it.isNotBlank() },
                                driverId = o.optString("driverId").takeIf { it.isNotBlank() && it != "null" },
                                driverType = o.optString("driverType").takeUnless { it.isBlank() || it == "null" } ?: "FYNX_ACCOUNT",
                                localDriverName = o.optString("localDriverName").takeIf { it.isNotBlank() && it != "null" },
                                localDriverPhone = o.optString("localDriverPhone").takeIf { it.isNotBlank() && it != "null" },
                                pickupPoint = o.optString("pickupPoint").takeIf { it.isNotBlank() && it != "null" },
                                pickupInstructions = o.optString("pickupInstructions").takeIf { it.isNotBlank() && it != "null" },
                                vehicleType = o.optString("vehicleType").takeIf { it.isNotBlank() && it != "null" },
                                vehicleColor = o.optString("vehicleColor").takeIf { it.isNotBlank() && it != "null" },
                                vehiclePlate = o.optString("vehiclePlate").takeIf { it.isNotBlank() && it != "null" },
                                vehicleNote = o.optString("vehicleNote").takeIf { it.isNotBlank() && it != "null" },
                                driverApprovalActive = o.optBoolean("driverApprovalActive", false),
                                driverAcceptedAt = o.optString("driverAcceptedAt").takeIf { it.isNotBlank() && it != "null" },
                                driverHandoverAt = o.optString("driverHandoverAt").takeIf { it.isNotBlank() && it != "null" },
                                driverDeliveredAt = o.optString("driverDeliveredAt").takeIf { it.isNotBlank() && it != "null" }
                            ))
                        }
                    }
                }
                .onFailure { error = it.message ?: "Seller orders could not load." }
            loading = false
        }
    }

    OutlinedButton(onClick = { open = true; load() }) {
        Icon(Icons.Default.LocalShipping, null, Modifier.size(18.dp))
        Spacer(Modifier.width(5.dp))
        Text("Seller orders")
    }

    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text("Seller orders") },
        text = {
            when {
                loading -> Box(Modifier.fillMaxWidth().height(100.dp), Alignment.Center) { CircularProgressIndicator() }
                error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                orders.isEmpty() -> Text("No buyer orders yet.")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 430.dp)) {
                    items(orders, key = { it.id }) { order ->
                        Card(onClick = { selected = order }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp)) {
                                Text(order.title, style = MaterialTheme.typography.titleMedium)
                                Text("${order.currency} ${String.format(Locale.US, "%,.2f", order.total)} • ${order.status}")
                                if (order.buyerName.isNotBlank()) Text("Buyer: ${order.buyerName}", style = MaterialTheme.typography.bodySmall)
                                Text(if (order.fulfillment == "PICKUP") "Pickup" else "Delivery", style = MaterialTheme.typography.bodySmall)
                                Text("Fulfillment: ${fulfillmentLabel(order.fulfillmentStatus)}", style = MaterialTheme.typography.bodySmall)
                                order.tracking?.let { Text("Tracking: $it", style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { load(); onChanged() }) { Text("Refresh") } },
        dismissButton = { TextButton(onClick = { open = false }) { Text("Close") } }
    )

    selected?.let { order ->
        var tracking by remember(order.id) { mutableStateOf(order.tracking.orEmpty()) }
        var driverUsername by remember(order.id) { mutableStateOf("") }
        var driverMode by remember(order.id) { mutableStateOf(order.driverType) }
        var localDriverName by remember(order.id) { mutableStateOf(order.localDriverName.orEmpty()) }
        var localDriverPhone by remember(order.id) { mutableStateOf(order.localDriverPhone.orEmpty()) }
        var pickupPoint by remember(order.id) { mutableStateOf(order.pickupPoint.orEmpty()) }
        var pickupInstructions by remember(order.id) { mutableStateOf(order.pickupInstructions.orEmpty()) }
        var vehicleType by remember(order.id) { mutableStateOf(order.vehicleType.orEmpty()) }
        var vehicleColor by remember(order.id) { mutableStateOf(order.vehicleColor.orEmpty()) }
        var vehiclePlate by remember(order.id) { mutableStateOf(order.vehiclePlate.orEmpty()) }
        var vehicleNote by remember(order.id) { mutableStateOf(order.vehicleNote.orEmpty()) }
        var handoverNote by remember(order.id) { mutableStateOf("") }
        var handoverPhotoMediaId by remember(order.id) { mutableStateOf<String?>(null) }
        var handoverPhotoUri by remember(order.id) { mutableStateOf<Uri?>(null) }
        var message by remember(order.id) { mutableStateOf<String?>(null) }
        val handoverPhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            handoverPhotoUri = uri
            if (uri != null) message = "Handover photo selected; it will upload when you confirm handover."
        }
        var busy by remember(order.id) { mutableStateOf(false) }
        var settlementLoading by remember(order.id) { mutableStateOf(true) }
        var settlementRetryCount by remember(order.id) { mutableStateOf(0) }
        var settlementError by remember(order.id) { mutableStateOf<String?>(null) }
        var settlement by remember(order.id) { mutableStateOf<JSONObject?>(null) }
        var payoutQueued by remember(order.id) { mutableStateOf(false) }
        var retryQueued by remember(order.id) { mutableStateOf(false) }

        LaunchedEffect(order.id, settlementRetryCount) {
            settlementLoading = true
            settlementError = null
            settlement = null
            handoverPhotoMediaId = null
            FynxBackendClient.get(context, "/api/marketplace/settlement/order/${order.id}")
                .onSuccess { raw -> settlement = JSONObject(raw) }
                .onFailure { settlementError = it.message ?: "Settlement details could not load." }
            FynxBackendClient.get(context, "/api/marketplace/orders/${order.id}/delivery-details")
                .onSuccess { raw ->
                    val evidence = JSONObject(raw).optJSONArray("evidence")
                    handoverPhotoMediaId = if (evidence == null) null else (0 until evidence.length())
                        .mapNotNull { evidence.optJSONObject(it) }
                        .lastOrNull { it.optString("type") == "PHOTO_REFERENCE" }
                        ?.optString("value")?.takeIf { it.toLongOrNull()?.let { id -> id > 0L } == true }
                }
            settlementLoading = false
        }

        fun progress(state: String, note: String = "") {
            busy = true
            scope.launch {
                FynxBackendClient.postJson(
                    context,
                    "/api/marketplace/orders/${order.id}/fulfillment-progress",
                    JSONObject().put("state", state).put("note", note).toString()
                ).onSuccess {
                    selected = null
                    load()
                    onChanged()
                }.onFailure { message = it.message ?: "Could not update fulfillment progress." }
                busy = false
            }
        }

        AlertDialog(
            onDismissRequest = { if (!busy && !payoutQueued && !retryQueued) selected = null },
            title = { Text(order.status) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(order.title, style = MaterialTheme.typography.titleMedium)
                    Text("Buyer: ${order.buyerName}")
                    Text("Order total: ${money(order.currency, order.total)}")
                    Text("Fulfillment: ${fulfillmentLabel(order.fulfillmentStatus)}", style = MaterialTheme.typography.bodyMedium)
                    if (order.fulfillmentStatus == "FAILED_DELIVERY") {
                        Text("Payment remains protected. A failed delivery does not release seller funds or bypass the existing dispute/refund controls.", style = MaterialTheme.typography.bodySmall)
                    }
                    if (order.fulfillmentStatus == "RETURNED") {
                        Text("Return recorded. Payment remains protected until the existing protection/settlement process reaches a verified outcome.", style = MaterialTheme.typography.bodySmall)
                    }
                    if (settlementLoading) CircularProgressIndicator(Modifier.size(20.dp))
                    if (!settlementLoading && settlementError != null) {
                        Text(settlementError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        TextButton(enabled = !busy, onClick = { settlementRetryCount += 1 }) { Text("Retry settlement details") }
                    }
                    settlement?.let { s ->
                        val accounting = s.optJSONObject("accounting")
                        val protection = s.optJSONObject("protection")
                        val escrow = s.optJSONObject("escrow")
                        val operations = s.optJSONArray("operations")
                        val ledger = s.optJSONArray("ledger")
                        if (accounting != null) {
                            Text("Product subtotal: ${money(accounting.optString("currency", order.currency), accounting.optDouble("productSubtotal", 0.0))}", style = MaterialTheme.typography.bodySmall)
                            Text("Delivery fee: ${money(accounting.optString("currency", order.currency), accounting.optDouble("deliveryFee", 0.0))}", style = MaterialTheme.typography.bodySmall)
                            Text("Marketplace fee: ${money(accounting.optString("currency", order.currency), accounting.optDouble("marketplaceFee", 0.0))}", style = MaterialTheme.typography.bodySmall)
                            Text("Provider fee: ${money(accounting.optString("currency", order.currency), accounting.optDouble("paymentProviderFee", 0.0))}", style = MaterialTheme.typography.bodySmall)
                            Text("Discount: ${money(accounting.optString("currency", order.currency), accounting.optDouble("discountAmount", 0.0))}", style = MaterialTheme.typography.bodySmall)
                            Text("Buyer paid: ${money(accounting.optString("currency", order.currency), accounting.optDouble("buyerTotal", 0.0))}", style = MaterialTheme.typography.bodySmall)
                            Text("Seller net: ${money(accounting.optString("currency", order.currency), accounting.optDouble("sellerNetAmount", 0.0))}", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (protection != null) Text("Protected funds: ${protection.optString("funds", "UNKNOWN")}", style = MaterialTheme.typography.bodySmall)
                        if (escrow != null) Text("Escrow: ${escrow.optString("status", "UNKNOWN")} • ${money(escrow.optString("currency", order.currency), escrow.optDouble("amount", 0.0))}", style = MaterialTheme.typography.bodySmall)
                        if (operations != null && operations.length() > 0) {
                            Text("Financial operations", style = MaterialTheme.typography.titleSmall)
                            for (i in 0 until operations.length()) {
                                val op = operations.optJSONObject(i) ?: continue
                                val ref = op.optString("provider_reference").ifBlank { "No provider reference" }
                                val reason = op.optString("failure_reason")
                                Text("${op.optString("operation_type")} • ${op.optString("status")} • ${money(op.optString("currency", order.currency), op.optDouble("amount", 0.0))}", style = MaterialTheme.typography.bodySmall)
                                if (ref != "No provider reference") Text("Provider: $ref", style = MaterialTheme.typography.bodySmall)
                                if (reason.isNotBlank()) Text("Reason: $reason", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (ledger != null && ledger.length() > 0) {
                            Text("Ledger history", style = MaterialTheme.typography.titleSmall)
                            for (i in 0 until ledger.length()) {
                                val entry = ledger.optJSONObject(i) ?: continue
                                Text("${entry.optString("entry_type")} • ${money(entry.optString("currency", order.currency), entry.optDouble("amount", 0.0))}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    MarketplaceHandoverPhotoPreview(context, order.id, handoverPhotoMediaId)
                    if (order.fulfillment == "DELIVERY") {
                        val a = order.address
                        if (a != null) Text("Deliver to: ${a.optString("name")} • ${a.optString("phone")}\n${a.optString("address")}${a.optString("city").let { if (it.isBlank()) "" else ", $it" }}")
                        else Text("Waiting for buyer delivery details.")
                    } else Text("Buyer selected pickup. Confirm the handover when the buyer receives the item.")
                    if (order.fulfillment == "DELIVERY" && order.status in setOf("PAID", "SHIPPED")) {
                        Text("Delivery driver", style = MaterialTheme.typography.titleSmall)
                        if (order.driverType == "SELLER_LOCAL") {
                            Text("Local phone driver — no FYNX account is needed.")
                            order.localDriverName?.let { Text("Driver: $it") }
                            order.localDriverPhone?.let { Text("Phone: $it") }
                            order.pickupPoint?.let { Text("Agreed pickup point: $it") }
                            order.pickupInstructions?.takeIf { it.isNotBlank() }?.let { Text("Pickup instructions: $it") }
                            val vehicleSummary = listOfNotNull(order.vehicleType, order.vehicleColor, order.vehiclePlate?.let { "Plate $it" }).joinToString(" • ")
                            if (vehicleSummary.isNotBlank()) Text("Vehicle: $vehicleSummary")
                            order.vehicleNote?.takeIf { it.isNotBlank() }?.let { Text("Vehicle note: $it") }
                            when {
                                order.driverHandoverAt != null && order.driverDeliveredAt != null -> Text("Seller reported the local driver arrived. Waiting for buyer to confirm collection.")
                                order.driverHandoverAt != null -> Text("Parcel handed to the local driver. Mark arrival only after the driver calls the buyer at the agreed point.")
                                else -> Text("Confirm handover only after the parcel is physically given to this driver.")
                            }
                        } else {
                            when {
                                order.driverId == null -> Text("No FYNX driver assigned yet.")
                                order.driverApprovalActive == false && order.driverAcceptedAt != null && order.driverHandoverAt == null -> Text("The previous driver's approval is inactive. Reassign an active approved driver before handover.")
                                order.driverHandoverAt != null && order.driverDeliveredAt != null -> Text("FYNX driver submitted delivery evidence. Waiting for buyer confirmation.")
                                order.driverHandoverAt != null -> Text("FYNX driver accepted and custody was handed over.")
                                order.driverAcceptedAt != null -> Text("FYNX driver accepted. Record handover only when the parcel is physically handed over.")
                                else -> Text("FYNX driver assignment sent. Waiting for acceptance.")
                            }
                        }
                        if (order.status in setOf("PAID", "SHIPPED") && order.driverHandoverAt == null && (order.driverType == "SELLER_LOCAL" || order.driverAcceptedAt == null || !order.driverApprovalActive)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = driverMode == "FYNX_ACCOUNT", onClick = { driverMode = "FYNX_ACCOUNT" }, label = { Text("FYNX driver") }, enabled = !busy)
                                FilterChip(selected = driverMode == "SELLER_LOCAL", onClick = { driverMode = "SELLER_LOCAL" }, label = { Text("Local phone driver") }, enabled = !busy)
                            }
                            if (driverMode == "FYNX_ACCOUNT") {
                                OutlinedTextField(driverUsername, { driverUsername = it.take(32) }, label = { Text("Approved driver username") }, singleLine = true, enabled = !busy)
                            } else {
                                Text("Use a driver who can call the buyer from an ordinary phone. Agree on a safe public pickup point with the buyer.")
                                OutlinedTextField(localDriverName, { localDriverName = it.take(120) }, label = { Text("Driver name") }, singleLine = true, enabled = !busy)
                                OutlinedTextField(localDriverPhone, { localDriverPhone = it.take(40) }, label = { Text("Driver phone number") }, singleLine = true, enabled = !busy)
                                OutlinedTextField(pickupPoint, { pickupPoint = it.take(300) }, label = { Text("Agreed bus stop / roadside pickup point") }, minLines = 2, enabled = !busy)
                                OutlinedTextField(pickupInstructions, { pickupInstructions = it.take(500) }, label = { Text("Pickup instructions (optional)") }, minLines = 2, enabled = !busy)
                                OutlinedTextField(vehicleType, { vehicleType = it.take(80) }, label = { Text("Vehicle type (optional)") }, singleLine = true, enabled = !busy)
                                OutlinedTextField(vehicleColor, { vehicleColor = it.take(80) }, label = { Text("Vehicle colour (optional)") }, singleLine = true, enabled = !busy)
                                OutlinedTextField(vehiclePlate, { vehiclePlate = it.take(40) }, label = { Text("Plate number (optional)") }, singleLine = true, enabled = !busy)
                                OutlinedTextField(vehicleNote, { vehicleNote = it.take(300) }, label = { Text("Vehicle note (optional)") }, minLines = 2, enabled = !busy)
                            }
                            Button(enabled = !busy && (if (driverMode == "FYNX_ACCOUNT") driverUsername.trim().removePrefix("@").isNotBlank() else localDriverName.trim().isNotBlank() && localDriverPhone.trim().isNotBlank() && pickupPoint.trim().isNotBlank()), onClick = {
                                busy = true
                                message = null
                                val payload = JSONObject().put("driverType", driverMode)
                                if (driverMode == "FYNX_ACCOUNT") {
                                    payload.put("driverUsername", driverUsername.trim().removePrefix("@"))
                                } else {
                                    payload.put("localDriverName", localDriverName.trim())
                                        .put("localDriverPhone", localDriverPhone.trim())
                                        .put("pickupPoint", pickupPoint.trim())
                                        .put("pickupInstructions", pickupInstructions.trim())
                                        .put("vehicleType", vehicleType.trim())
                                        .put("vehicleColor", vehicleColor.trim())
                                        .put("vehiclePlate", vehiclePlate.trim())
                                        .put("vehicleNote", vehicleNote.trim())
                                }
                                scope.launch {
                                    FynxBackendClient.postJson(context, "/api/marketplace/orders/${order.id}/driver-assignment", payload.toString())
                                        .onSuccess { selected = null; load(); onChanged() }
                                        .onFailure { message = it.message ?: "Could not save the delivery driver." }
                                    busy = false
                                }
                            }) { if (busy) CircularProgressIndicator(Modifier.size(18.dp)) else Text(when {
                order.driverType == "FYNX_ACCOUNT" && order.driverId != null && !order.driverApprovalActive && order.driverAcceptedAt != null -> "Reassign revoked driver"
                order.driverId == null && order.driverType != "SELLER_LOCAL" -> "Assign driver"
                order.driverType == "FYNX_ACCOUNT" && order.driverId != null && order.driverAcceptedAt == null -> "Replace unaccepted driver"
                else -> "Save driver details"
            }) }
                        }
                        if (order.status == "SHIPPED" && order.driverHandoverAt == null && (order.driverType == "SELLER_LOCAL" || (order.driverAcceptedAt != null && order.driverApprovalActive))) {
                            OutlinedTextField(handoverNote, { handoverNote = it.take(1000) }, label = { Text("Handover note") }, minLines = 2, enabled = !busy)
                            TextButton(
                                enabled = !busy,
                                onClick = { handoverPhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                            ) { Text(if (handoverPhotoUri == null) "Add handover photo (optional)" else "Change handover photo") }
                            handoverPhotoUri?.let { Text("A photo will upload securely when handover is confirmed.", style = MaterialTheme.typography.bodySmall) }
                            Button(enabled = !busy && handoverNote.trim().isNotBlank(), onClick = {
                                busy = true
                                message = null
                                scope.launch {
                                    var photoReference: String? = null
                                    val photoUri = handoverPhotoUri
                                    if (photoUri != null) {
                                        val uploaded = FynxProductionMessaging.uploadMedia(context, photoUri)
                                        if (uploaded.isFailure) {
                                            message = uploaded.exceptionOrNull()?.message ?: "Handover photo upload failed."
                                            busy = false
                                            return@launch
                                        }
                                        val media = uploaded.getOrThrow()
                                        val allowedImageTypes = setOf("image/jpeg", "image/png", "image/webp", "image/gif")
                                        if (media.mimeType.trim().lowercase(Locale.ROOT) !in allowedImageTypes) {
                                            message = "Choose a JPEG, PNG, WebP or GIF image."
                                            busy = false
                                            return@launch
                                        }
                                        photoReference = media.id
                                    }
                                    val payload = JSONObject().put("note", handoverNote.trim())
                                    if (photoReference != null) payload.put("photoReference", photoReference)
                                    FynxBackendClient.postJson(context, "/api/marketplace/orders/${order.id}/driver-handover", payload.toString())
                                        .onSuccess { handoverPhotoUri = null; selected = null; load(); onChanged() }
                                        .onFailure { message = it.message ?: "Could not record driver handover." }
                                    busy = false
                                }
                            }) { if (busy) CircularProgressIndicator(Modifier.size(18.dp)) else Text(if (order.driverType == "SELLER_LOCAL") "Confirm local driver handover" else "Confirm driver handover") }
                        }
                    }
                    if (order.status == "PAID" && order.fulfillment == "DELIVERY") OutlinedTextField(tracking, { tracking = it }, label = { Text("Tracking reference (optional)") }, singleLine = true)
                    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (payoutQueued || retryQueued) Text("Payout processing has been queued. FYNX will verify the provider transfer before marking it paid.", color = MaterialTheme.colorScheme.primary)
                }
            },
            confirmButton = {
                when {
                    order.status == "PAID" && order.fulfillment == "DELIVERY" && order.fulfillmentStatus == "PAID" -> Button(enabled = !busy, onClick = { progress("PREPARING") }) { if (busy) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Start preparing") }
                    order.status == "PAID" && order.fulfillment == "DELIVERY" && order.fulfillmentStatus == "PREPARING" -> Button(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            FynxBackendClient.postJson(context, "/api/marketplace/orders/${order.id}/ship", JSONObject().put("trackingReference", tracking.trim()).toString())
                                .onSuccess { selected = null; load(); onChanged() }
                                .onFailure { message = it.message ?: "Could not mark order as dispatched." }
                            busy = false
                        }
                    }) { if (busy) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Mark dispatched") }
                    order.status == "SHIPPED" && order.fulfillment == "DELIVERY" && order.fulfillmentStatus == "DISPATCHED" -> Column(horizontalAlignment = Alignment.End) {
                        Button(enabled = !busy, onClick = { progress("IN_TRANSIT") }) { if (busy) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Mark in transit") }
                        TextButton(enabled = !busy, onClick = { progress("FAILED_DELIVERY", "Seller reported a failed delivery attempt.") }) { Text("Report failed delivery") }
                    }
                    order.status == "SHIPPED" && order.fulfillment == "DELIVERY" && order.fulfillmentStatus == "IN_TRANSIT" -> Column(horizontalAlignment = Alignment.End) {
                        if (order.driverId == null || order.driverType == "SELLER_LOCAL") {
                            Button(enabled = !busy && (order.driverType != "SELLER_LOCAL" || order.driverHandoverAt != null), onClick = { progress("DELIVERED", if (order.driverType == "SELLER_LOCAL") "Local driver called the buyer at the agreed pickup point." else "Seller reported delivery.") }) { if (busy) CircularProgressIndicator(Modifier.size(18.dp)) else Text(if (order.driverType == "SELLER_LOCAL") "Report driver arrival" else "Mark delivered") }
                        } else {
                            Text("Waiting for the assigned FYNX driver to submit delivery evidence.", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(enabled = !busy, onClick = { progress("FAILED_DELIVERY", "Seller reported a failed delivery attempt.") }) { Text("Report failed delivery") }
                    }
                    order.status == "SHIPPED" && order.fulfillment == "DELIVERY" && order.fulfillmentStatus == "FAILED_DELIVERY" -> Column(horizontalAlignment = Alignment.End) {
                        Button(enabled = !busy, onClick = { progress("IN_TRANSIT", "Seller requested another delivery attempt.") }) { if (busy) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Retry delivery") }
                        TextButton(enabled = !busy, onClick = { progress("RETURNED", "Seller marked the failed delivery for return.") }) { Text("Mark returned") }
                    }
                    order.status == "PAID" && order.fulfillment == "PICKUP" && order.fulfillmentStatus == "PAID" -> Button(enabled = !busy, onClick = { progress("READY_FOR_PICKUP") }) { if (busy) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Ready for pickup") }
                    order.status == "PAID" && order.fulfillment == "PICKUP" && order.fulfillmentStatus == "READY_FOR_PICKUP" -> Button(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            FynxBackendClient.postJson(context, "/api/marketplace/orders/${order.id}/pickup-handover", JSONObject().toString())
                                .onSuccess { selected = null; load(); onChanged() }
                                .onFailure { message = it.message ?: "Could not confirm pickup handover." }
                            busy = false
                        }
                    }) { if (busy) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Confirm pickup handover") }
                    order.status == "COMPLETED" -> Button(enabled = !busy && !payoutQueued && !retryQueued, onClick = {
                        busy = true
                        scope.launch {
                            FynxBackendClient.postJson(context, "/api/marketplace/settlement/release/${order.id}", JSONObject().toString())
                                .onSuccess { payoutQueued = true; onChanged() }
                                .onFailure { message = it.message ?: "Payout is not currently eligible." }
                            busy = false
                        }
                    }) { if (busy) CircularProgressIndicator(Modifier.size(18.dp)) else Text(if (payoutQueued) "Payout queued" else "Request payout") }
                    else -> TextButton(onClick = { selected = null }) { Text("Done") }
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val hasFailedPayout = settlement?.optJSONArray("operations")?.let { ops ->
                        (0 until ops.length()).any { i ->
                            val op = ops.optJSONObject(i)
                            op != null && op.optString("operation_type") == "PAYOUT_RELEASE" && op.optString("status") == "FAILED"
                        }
                    } == true
                    if (hasFailedPayout) TextButton(enabled = !busy && !payoutQueued && !retryQueued, onClick = {
                        busy = true
                        scope.launch {
                            FynxBackendClient.postJson(context, "/api/marketplace/settlement/retry/${order.id}", JSONObject().toString())
                                .onSuccess { retryQueued = true; onChanged() }
                                .onFailure { message = it.message ?: "Payout retry is not currently available." }
                            busy = false
                        }
                    }) { Text(if (retryQueued) "Retry queued" else "Retry payout") }
                    TextButton(enabled = !busy && !payoutQueued && !retryQueued, onClick = { selected = null }) { Text("Close") }
                }
            }
        )
    }
}