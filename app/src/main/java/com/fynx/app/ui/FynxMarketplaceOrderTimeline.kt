package com.fynx.app.ui

import android.content.Context
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private data class MarketplaceTimelineEvent(
    val eventType: String,
    val fromStatus: String?,
    val toStatus: String?,
    val createdAt: String?
)

private data class MarketplaceTimelineEvidence(
    val type: String,
    val value: String,
    val createdAt: String?
)

@Composable
fun FynxMarketplaceOrderTimeline(
    context: Context,
    orderId: String
) {
    var loading by remember(orderId) { mutableStateOf(true) }
    var error by remember(orderId) { mutableStateOf<String?>(null) }
    var events by remember(orderId) { mutableStateOf<List<MarketplaceTimelineEvent>>(emptyList()) }
    var deliveryEvidence by remember(orderId) { mutableStateOf<List<MarketplaceTimelineEvidence>>(emptyList()) }

    LaunchedEffect(orderId) {
        loading = true
        error = null
        val result = withContext(Dispatchers.IO) {
            FynxBackendClient.get(context, "/api/marketplace/orders/$orderId/timeline")
        }
        result.onSuccess { raw ->
            runCatching {
                val root = JSONObject(raw)
                val array = root.optJSONArray("events") ?: JSONArray()
                val parsedEvents = buildList {
                    for (i in 0 until array.length()) {
                        val event = array.getJSONObject(i)
                        add(
                            MarketplaceTimelineEvent(
                                event.optString("eventType", "ORDER_UPDATE"),
                                event.optString("fromStatus").takeIf { it.isNotBlank() },
                                event.optString("toStatus").takeIf { it.isNotBlank() },
                                event.optString("createdAt").takeIf { it.isNotBlank() }
                            )
                        )
                    }
                }
                val evidenceArray = root.optJSONArray("deliveryEvidence") ?: JSONArray()
                val parsedEvidence = buildList {
                    for (i in 0 until evidenceArray.length()) {
                        val item = evidenceArray.getJSONObject(i)
                        add(
                            MarketplaceTimelineEvidence(
                                item.optString("type"),
                                item.optString("value"),
                                item.optString("createdAt").takeIf { it.isNotBlank() }
                            )
                        )
                    }
                }
                parsedEvents to parsedEvidence
            }.onSuccess { (parsedEvents, parsedEvidence) ->
                events = parsedEvents
                deliveryEvidence = parsedEvidence
            }.onFailure { error = "Order timeline could not be read." }
        }.onFailure { error = it.message ?: "Order timeline could not be loaded." }
        loading = false
    }

    val latestException = events.asReversed().firstOrNull {
        it.eventType == "FULFILLMENT_FAILED_DELIVERY" || it.eventType == "FULFILLMENT_RETURNED"
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Order progress")
        latestException?.let { event ->
            when (event.eventType) {
                "FULFILLMENT_FAILED_DELIVERY" -> Text("Delivery exception: the seller reported a failed delivery attempt. Your protected payment is not released because of this event.")
                "FULFILLMENT_RETURNED" -> Text("Return exception: the seller marked the delivery for return. Your protected payment remains under the existing FYNX protection process.")
            }
        }
        when {
            loading -> Row { CircularProgressIndicator(Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("Loading…") }
            error != null -> Text(error!!)
            events.isEmpty() -> Text("No fulfillment events have been recorded yet.")
            else -> events.forEach { event ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(formatTimelineEvent(event.eventType))
                    event.toStatus?.let { Text("Order status: ${formatTimelineStatus(it)}") }
                    event.createdAt?.let { formatTimelineDate(it)?.let { readable -> Text(readable) } }
                }
                Spacer(Modifier.height(2.dp))
            }
        }
        if (!loading && error == null && deliveryEvidence.isNotEmpty()) {
            Text("Delivery evidence")
            deliveryEvidence.forEach { evidence ->
                when (evidence.type) {
                    "RECIPIENT_NAME" -> Text("Recipient: ${evidence.value}")
                    "DELIVERY_NOTE" -> Text("Driver note: ${evidence.value}")
                    "HANDOVER_NOTE" -> Text("Seller handover note: ${evidence.value}")
                    "PHOTO_REFERENCE" -> Text("Photo evidence attached securely")
                    else -> Text("${evidence.type.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }}: ${evidence.value}")
                }
                evidence.createdAt?.let { formatTimelineDate(it)?.let { readable -> Text(readable) } }
            }
        }
    }
}

private fun formatTimelineEvent(value: String): String = when (value.uppercase()) {
    "ORDER_CREATED" -> "Order placed"
    "PAYMENT_INITIALIZED" -> "Payment started"
    "PAYMENT_CONFIRMED" -> "Payment confirmed"
    "PAYMENT_FAILED" -> "Payment failed"
    "FULFILLMENT_PREPARING" -> "Seller is preparing the order"
    "FULFILLMENT_DISPATCHED" -> "Order handed to the driver"
    "FULFILLMENT_IN_TRANSIT" -> "Order is on the way"
    "FULFILLMENT_DELIVERED" -> "Driver marked the order delivered"
    "FULFILLMENT_READY_FOR_PICKUP" -> "Order is ready for pickup"
    "FULFILLMENT_PICKUP_COMPLETED" -> "Pickup completed"
    "FULFILLMENT_INSPECTION" -> "Waiting for buyer confirmation"
    "FULFILLMENT_COMPLETED" -> "Order completed"
    "FULFILLMENT_FAILED_DELIVERY" -> "Delivery attempt failed"
    "FULFILLMENT_RETURNED" -> "Order marked for return"
    "DISPUTE_OPENED" -> "Buyer opened a protection case"
    "DISPUTE_RESOLVED" -> "Protection case resolved"
    else -> value.removePrefix("FULFILLMENT_").replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}

private fun formatTimelineStatus(value: String): String = when (value.uppercase()) {
    "PAYMENT_PENDING" -> "Awaiting payment"
    "PAID" -> "Payment confirmed"
    "PREPARING" -> "Being prepared"
    "DISPATCHED" -> "Handed to driver"
    "IN_TRANSIT" -> "On the way"
    "DELIVERED" -> "Marked delivered"
    "READY_FOR_PICKUP" -> "Ready for pickup"
    "PICKUP_COMPLETED" -> "Pickup completed"
    "INSPECTION" -> "Waiting for buyer confirmation"
    "COMPLETED" -> "Completed"
    "FAILED_DELIVERY" -> "Delivery attempt failed"
    "RETURNED" -> "Marked for return"
    else -> value.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}

private fun formatTimelineDate(value: String): String? {
    val input = value.trim()
    if (input.isEmpty()) return null
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSX",
        "yyyy-MM-dd'T'HH:mm:ssX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'"
    )
    for (pattern in patterns) {
        try {
            val parser = SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val position = ParsePosition(0)
            val parsed: Date? = parser.parse(input, position)
            if (parsed != null && position.index == input.length) {
                return SimpleDateFormat("EEE, d MMM yyyy · h:mm a", Locale.getDefault())
                    .apply { timeZone = TimeZone.getDefault() }
                    .format(parsed)
            }
        } catch (_: IllegalArgumentException) {
            // Try the next supported ISO timestamp shape.
        }
    }
    return null
}
