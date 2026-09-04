package com.fynx.app.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale

private suspend fun submitMarketplaceReview(context: Context, orderId: String, rating: Int, comment: String): Result<Unit> = runCatching {
    FynxBackendClient.postJson(context, "/api/marketplace/orders/${orderId}/review", JSONObject().apply {
        put("rating", rating)
        put("comment", comment.trim().take(1000))
    }.toString()).getOrThrow()
    Unit
}

private suspend fun loadSellerReputation(context: Context, username: String): Result<Pair<Double, Int>> = runCatching {
    val raw = FynxBackendClient.get(context, "/api/marketplace/sellers/${username.removePrefix("@").trim().lowercase()}/reputation").getOrThrow()
    val r = JSONObject(raw).optJSONObject("reputation") ?: throw IllegalStateException("Seller reputation unavailable")
    r.optDouble("averageRating", 0.0) to r.optInt("reviewCount", 0)
}

@Composable
fun FynxMarketplaceReviews(context: Context, orders: List<FynxRemoteSocialClient.MarketplaceOrder>, onChanged: () -> Unit = {}) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }) { Text("Seller reviews") }
    if (!open) return
    val completed = orders.filter { it.status == "COMPLETED" }
    var selected by remember { mutableStateOf<FynxRemoteSocialClient.MarketplaceOrder?>(null) }
    var ratings by remember { mutableStateOf<Map<String, Pair<Double, Int>>>(emptyMap()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(completed.map { it.id to it.sellerUsername }) {
        completed.forEach { order ->
            val seller = order.sellerUsername.orEmpty()
            if (seller.isNotBlank() && !ratings.containsKey(seller)) {
                loadSellerReputation(context, seller).onSuccess { ratings = ratings + (seller to it) }
            }
        }
    }
    AlertDialog(
        onDismissRequest = { open = false },
        title = { Text("Seller reviews") },
        text = {
            if (completed.isEmpty()) Text("Completed marketplace orders will appear here when they are ready for review.")
            else LazyColumn(Modifier.heightIn(max = 430.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(completed, key = { it.id }) { order ->
                    val seller = order.sellerUsername.orEmpty()
                    val reputation = ratings[seller]
                    Card(onClick = { selected = order }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(order.productTitle.ifBlank { "FYNX order" }, style = MaterialTheme.typography.titleMedium)
                            Text("Seller: ${seller.ifBlank { "Seller" }}")
                            if (reputation != null) Text("★ ${String.format(Locale.US, "%.1f", reputation.first)} • ${reputation.second} reviews", style = MaterialTheme.typography.bodySmall)
                            Text("Tap to leave a review", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } }
    )
    selected?.let { order ->
        var rating by remember(order.id) { mutableIntStateOf(5) }
        var comment by remember(order.id) { mutableStateOf("") }
        var busy by remember(order.id) { mutableStateOf(false) }
        var localError by remember(order.id) { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { if (!busy) selected = null },
            title = { Text("Review seller") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(order.productTitle.ifBlank { "FYNX order" }, style = MaterialTheme.typography.titleMedium)
                    Text("Rating: $rating / 5")
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (1..5).forEach { value -> FilterChip(selected = rating == value, onClick = { rating = value }, label = { Text("$value ★") }) }
                    }
                    OutlinedTextField(value = comment, onValueChange = { comment = it.take(1000) }, label = { Text("Comment (optional)") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                    localError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(enabled = !busy, onClick = {
                    busy = true; localError = null
                    scope.launch {
                        submitMarketplaceReview(context, order.id, rating, comment)
                            .onSuccess { selected = null; onChanged() }
                            .onFailure { localError = it.message ?: "Review could not be submitted." }
                        busy = false
                    }
                }) { if (busy) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Submit review") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { selected = null }) { Text("Cancel") } }
        )
    }
}
