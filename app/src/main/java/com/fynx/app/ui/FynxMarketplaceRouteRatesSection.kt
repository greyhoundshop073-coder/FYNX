package com.fynx.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private val FYNX_NIGERIAN_STATES = listOf(
    "Abia", "Adamawa", "Akwa Ibom", "Anambra", "Bauchi", "Bayelsa", "Benue",
    "Borno", "Cross River", "Delta", "Ebonyi", "Edo", "Ekiti", "Enugu", "Gombe",
    "Imo", "Jigawa", "Kaduna", "Kano", "Katsina", "Kebbi", "Kogi", "Kwara",
    "Lagos", "Nasarawa", "Niger", "Ogun", "Ondo", "Osun", "Oyo", "Plateau",
    "Rivers", "Sokoto", "Taraba", "Yobe", "Zamfara", "FCT"
)

private data class FynxMarketplaceRouteDraft(
    val destinationState: String = "Lagos",
    val destinationCity: String = "",
    val fee: String = "",
    val additionalItemFee: String = "0",
    val feeCap: String = ""
)

@Composable
internal fun NigerianStatePicker(
    value: String,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            singleLine = true,
            modifier = modifier.fillMaxWidth(),
            trailingIcon = {
                Box {
                    TextButton(onClick = { expanded = true }, enabled = enabled) {
                        Text("▼", style = MaterialTheme.typography.labelMedium)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        FYNX_NIGERIAN_STATES.forEach { state ->
                            DropdownMenuItem(
                                text = { Text(state) },
                                onClick = { onValueChange(state); expanded = false }
                            )
                        }
                    }
                }
            }
        )
    }
}

@Composable
internal fun FynxMarketplaceRouteRatesSection(context: Context, listingId: String) {
    val scope = rememberCoroutineScope()
    val routes = remember { mutableStateListOf<FynxMarketplaceRouteDraft>() }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var originState by remember { mutableStateOf("") }
    var originCity by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("NGN") }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun loadRoutes() {
        scope.launch {
            loading = true
            error = null
            routes.clear()
            originState = ""
            originCity = ""
            currency = "NGN"
            FynxBackendClient.get(context, "/api/marketplace/listings/${listingId}/shipping/routes")
                .onSuccess { raw ->
                    runCatching {
                        val root = JSONObject(raw).getJSONObject("routes")
                        originState = root.optString("originState")
                        originCity = root.optString("originCity")
                        currency = root.optString("currency", "NGN").uppercase()
                        val rows = root.optJSONArray("rates") ?: JSONArray()
                        routes.clear()
                        for (index in 0 until rows.length()) {
                            val row = rows.optJSONObject(index) ?: continue
                            routes.add(
                                FynxMarketplaceRouteDraft(
                                    destinationState = row.optString("destinationState", "Lagos"),
                                    destinationCity = row.optString("destinationCity"),
                                    fee = if (row.has("fee")) row.optString("fee") else "",
                                    additionalItemFee = row.optString("additionalItemFee", "0"),
                                    feeCap = if (row.isNull("feeCap")) "" else row.optString("feeCap")
                                )
                            )
                        }
                    }.onFailure { error = "Saved delivery routes could not be read. Reload this section." }
                }
                .onFailure { failure -> error = failure.message?.takeIf { it.isNotBlank() } ?: "Delivery route prices could not be loaded." }
            loading = false
        }
    }

    LaunchedEffect(listingId) { loadRoutes() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Nigeria delivery routes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Set the dispatch location for this product, then set the delivery fee to each destination. All 36 states and the FCT are supported. Leave a destination city blank to set one price for the whole state; add a city or LGA for a more specific price.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Only the seller-arranged fee you configure is used. A route without a price cannot proceed to payment; FYNX will not guess or add a courier.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (loading) {
                CircularProgressIndicator()
            } else {
                Text("Seller dispatch origin", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = originCity,
                    onValueChange = { originCity = it.take(120); error = null; message = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Dispatch city / LGA") },
                    singleLine = true,
                    enabled = !saving
                )
                NigerianStatePicker(
                    value = originState,
                    label = "Dispatch state",
                    enabled = !saving,
                    onValueChange = { originState = it; error = null; message = null }
                )
                Text("Route currency: $currency", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Destination prices", fontWeight = FontWeight.SemiBold)
                    TextButton(
                        enabled = !saving && routes.size < 100,
                        onClick = { routes.add(FynxMarketplaceRouteDraft()); error = null; message = null }
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add route")
                    }
                }
                if (routes.isEmpty()) {
                    Text(
                        "No destination prices saved yet. Add the states and cities/LGAs where you can deliver.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                routes.forEachIndexed { index, route ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Route ${index + 1}", fontWeight = FontWeight.SemiBold)
                                IconButton(
                                    enabled = !saving,
                                    onClick = { routes.removeAt(index); error = null; message = null }
                                ) { Icon(Icons.Default.Delete, "Remove route") }
                            }
                            NigerianStatePicker(
                                value = route.destinationState,
                                label = "Buyer destination state",
                                enabled = !saving,
                                onValueChange = { value -> routes[index] = route.copy(destinationState = value); error = null; message = null }
                            )
                            OutlinedTextField(
                                value = route.destinationCity,
                                onValueChange = { value -> routes[index] = route.copy(destinationCity = value.take(120)); error = null; message = null },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Destination city / LGA (optional)") },
                                supportingText = { Text("Leave blank for a state-wide price.") },
                                singleLine = true,
                                enabled = !saving
                            )
                            OutlinedTextField(
                                value = route.fee,
                                onValueChange = { value -> routes[index] = route.copy(fee = value.filter { it.isDigit() || it == '.' }.take(16)); error = null; message = null },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Delivery fee ($currency)") },
                                singleLine = true,
                                enabled = !saving
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = route.additionalItemFee,
                                    onValueChange = { value -> routes[index] = route.copy(additionalItemFee = value.filter { it.isDigit() || it == '.' }.take(16)); error = null; message = null },
                                    modifier = Modifier.weight(1f),
                                    label = { Text("Extra item fee") },
                                    singleLine = true,
                                    enabled = !saving
                                )
                                OutlinedTextField(
                                    value = route.feeCap,
                                    onValueChange = { value -> routes[index] = route.copy(feeCap = value.filter { it.isDigit() || it == '.' }.take(16)); error = null; message = null },
                                    modifier = Modifier.weight(1f),
                                    label = { Text("Optional fee cap") },
                                    singleLine = true,
                                    enabled = !saving
                                )
                            }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                Button(
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val normalizedOriginCity = originCity.trim()
                        if (normalizedOriginCity.isBlank() || originState !in FYNX_NIGERIAN_STATES) {
                            error = "Enter the seller dispatch city/LGA and choose a Nigerian state."
                            message = null
                            return@Button
                        }
                        val seen = mutableSetOf<String>()
                        val rateRows = JSONArray()
                        for (route in routes) {
                            val fee = route.fee.toDoubleOrNull()
                            val extra = route.additionalItemFee.toDoubleOrNull()
                            val cap = route.feeCap.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
                            val key = route.destinationState.trim().uppercase() + "|" + route.destinationCity.trim().replace(Regex("\\s+"), " ").uppercase()
                            if (route.destinationState !in FYNX_NIGERIAN_STATES || fee == null || extra == null ||
                                fee < 0 || extra < 0 || (route.feeCap.isNotBlank() && cap == null) || (cap != null && cap < fee)) {
                                error = "Each route needs a Nigerian state, a valid delivery fee and valid optional extra-item/cap amounts."
                                message = null
                                return@Button
                            }
                            if (!seen.add(key)) {
                                error = "A destination state and city/LGA can only be added once."
                                message = null
                                return@Button
                            }
                            rateRows.put(
                                JSONObject()
                                    .put("destinationState", route.destinationState)
                                    .put("destinationCity", route.destinationCity.trim())
                                    .put("fee", fee)
                                    .put("additionalItemFee", extra)
                                    .put("feeCap", cap ?: JSONObject.NULL)
                            )
                        }
                        saving = true
                        error = null
                        message = null
                        val body = JSONObject()
                            .put("originState", originState)
                            .put("originCity", normalizedOriginCity)
                            .put("rates", rateRows)
                        scope.launch {
                            FynxBackendClient.patchJson(
                                context,
                                "/api/marketplace/listings/${listingId}/shipping/routes",
                                body.toString()
                            ).onSuccess {
                                message = "Nigeria delivery routes saved."
                                loadRoutes()
                            }.onFailure { failure ->
                                error = failure.message?.takeIf { it.isNotBlank() } ?: "Delivery route prices could not be saved."
                            }
                            saving = false
                        }
                    }
                ) {
                    if (saving) CircularProgressIndicator(modifier = Modifier.width(18.dp))
                    else {
                        Icon(Icons.Default.Save, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Save route prices")
                    }
                }
            }
        }
    }
}
