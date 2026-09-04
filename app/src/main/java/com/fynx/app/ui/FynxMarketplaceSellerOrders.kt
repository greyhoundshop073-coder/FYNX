package com.fynx.app.ui

import android.content.Context
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
    val address: JSONObject?,
    val tracking: String?
)

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
                                address = o.optJSONObject("shippingAddress"),
                                tracking = o.optString("trackingReference").takeIf { it.isNotBlank() }
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
        var busy by remember(order.id) { mutableStateOf(false) }
        var message by remember(order.id) { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { if (!busy) selected = null },
            title = { Text(order.status) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(order.title, style = MaterialTheme.typography.titleMedium)
                    Text("Buyer: ${order.buyerName}")
                    Text("Total: ${order.currency} ${String.format(Locale.US, "%,.2f", order.total)}")
                    if (order.fulfillment == "DELIVERY") {
                        val a = order.address
                        if (a != null) Text("Deliver to: ${a.optString("name")} • ${a.optString("phone")}\n${a.optString("address")}${a.optString("city").let { if (it.isBlank()) "" else ", $it" }}")
                        else Text("Waiting for buyer delivery details.")
                    } else Text("Buyer selected pickup.")
                    if (order.status == "PAID") OutlinedTextField(tracking, { tracking = it }, label = { Text("Tracking reference (optional)") }, singleLine = true)
                    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                when (order.status) {
                    "PAID" -> Button(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            FynxBackendClient.postJson(context, "/api/marketplace/orders/${order.id}/ship", JSONObject().put("trackingReference", tracking.trim()).toString())
                                .onSuccess { selected = null; load(); onChanged() }
                                .onFailure { message = it.message ?: "Could not mark order as shipped." }
                            busy = false
                        }
                    }) { if (busy) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Mark shipped") }
                    else -> TextButton(onClick = { selected = null }) { Text("Done") }
                }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { selected = null }) { Text("Close") } }
        )
    }
}
