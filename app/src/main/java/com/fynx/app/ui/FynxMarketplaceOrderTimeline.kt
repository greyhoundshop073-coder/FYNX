package com.fynx.app.ui

import android.content.Context
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
                    event.toStatus?.let { Text("Status: ${it.replace('_', ' ')}") }
                    event.createdAt?.let { Text(it.replace('T', ' ').substringBefore('.')) }
                }
                Spacer(Modifier.height(2.dp))
            }
        }
        if (!loading && error == null && deliveryEvidence.isNotEmpty()) {
            Text("Delivery evidence")
            deliveryEvidence.forEach { evidence ->
                val label = when (evidence.type) {
                    "RECIPIENT_NAME" -> "Recipient"
                    "DELIVERY_NOTE" -> "Driver note"
                    "HANDOVER_NOTE" -> "Seller handover note"
                    "PHOTO_REFERENCE" -> "Photo reference"
                    else -> evidence.type.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
                }
                Text("$label: ${evidence.value}")
                evidence.createdAt?.let { Text(it.replace('T', ' ').substringBefore('.')) }
            }
        }
    }
}

private fun formatTimelineEvent(value: String): String = value
    .removePrefix("FULFILLMENT_")
    .replace('_', ' ')
    .lowercase()
    .replaceFirstChar { it.uppercase() }
