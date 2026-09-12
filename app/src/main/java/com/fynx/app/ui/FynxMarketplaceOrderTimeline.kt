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

@Composable
fun FynxMarketplaceOrderTimeline(
    context: Context,
    orderId: String
) {
    var loading by remember(orderId) { mutableStateOf(true) }
    var error by remember(orderId) { mutableStateOf<String?>(null) }
    var events by remember(orderId) { mutableStateOf<List<MarketplaceTimelineEvent>>(emptyList()) }

    LaunchedEffect(orderId) {
        loading = true
        error = null
        val result = withContext(Dispatchers.IO) {
            FynxBackendClient.get(context, "/api/marketplace/orders/$orderId/timeline")
        }
        result.onSuccess { raw ->
            runCatching {
                val array = JSONObject(raw).optJSONArray("events") ?: JSONArray()
                buildList {
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
            }.onSuccess { parsed -> events = parsed }
                .onFailure { error = "Order timeline could not be read." }
        }.onFailure { error = it.message ?: "Order timeline could not be loaded." }
        loading = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Order progress")
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
    }
}

private fun formatTimelineEvent(value: String): String = value
    .removePrefix("FULFILLMENT_")
    .replace('_', ' ')
    .lowercase()
    .replaceFirstChar { it.uppercase() }
