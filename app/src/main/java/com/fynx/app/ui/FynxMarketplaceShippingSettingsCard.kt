package com.fynx.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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

@Composable
fun FynxMarketplaceShippingSettingsCard(
    context: Context,
    listingId: String,
    deliveryAvailable: Boolean,
    pickupAvailable: Boolean
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var method by remember { mutableStateOf("SELLER_ARRANGED") }
    var note by remember { mutableStateOf("") }
    var baseFee by remember { mutableStateOf("0") }
    var additionalItemFee by remember { mutableStateOf("0") }
    var feeCap by remember { mutableStateOf("") }
    val coverage = remember { mutableStateListOf<FynxMarketplaceClient.Coverage>() }
    var showCoverageDialog by remember { mutableStateOf(false) }
    var newCountry by remember { mutableStateOf("") }
    var newState by remember { mutableStateOf("") }
    var newCity by remember { mutableStateOf("") }

    fun load() {
        scope.launch {
            loading = true
            error = null
            FynxMarketplaceClient.shippingSettings(context, listingId)
                .onSuccess { settings ->
                    method = settings.method
                    note = settings.note
                    baseFee = settings.baseFee.toString()
                    additionalItemFee = settings.additionalItemFee.toString()
                    feeCap = settings.feeCap?.toString().orEmpty()
                    coverage.clear()
                    coverage.addAll(settings.coverage)
                }
                .onFailure { error = it.message ?: "Shipping settings could not be loaded." }
            loading = false
        }
    }

    LaunchedEffect(listingId) { load() }

    Card(Modifier.fillMaxWidth(), shape = FynxDesign.CardShape) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Shipping & delivery", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Configure how this listing is delivered. FYNX does not claim a courier integration here; the seller can arrange a lawful local dispatch, courier, transport provider, or other delivery method.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (loading) {
                CircularProgressIndicator()
            } else {
                OutlinedTextField(
                    value = method,
                    onValueChange = { method = it.take(64) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Delivery method / provider") },
                    supportingText = { Text("Example: SELLER_ARRANGED, LOCAL_DISPATCH, COURIER") },
                    singleLine = true
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = baseFee,
                        onValueChange = { baseFee = it.filter { ch -> ch.isDigit() || ch == '.' }.take(16) },
                        modifier = Modifier.weight(1f),
                        label = { Text("Base fee") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = additionalItemFee,
                        onValueChange = { additionalItemFee = it.filter { ch -> ch.isDigit() || ch == '.' }.take(16) },
                        modifier = Modifier.weight(1f),
                        label = { Text("Extra item fee") },
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    value = feeCap,
                    onValueChange = { feeCap = it.filter { ch -> ch.isDigit() || ch == '.' }.take(16) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Maximum delivery fee (optional)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(500) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Delivery note") },
                    supportingText = { Text("Up to 500 characters") },
                    minLines = 2,
                    maxLines = 4
                )

                Text("Available fulfillment", fontWeight = FontWeight.SemiBold)
                Text(
                    "Delivery: ${if (deliveryAvailable) "Available" else "Disabled"} • Pickup: ${if (pickupAvailable) "Available" else "Disabled"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Delivery coverage", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = {
                        newCountry = ""
                        newState = ""
                        newCity = ""
                        showCoverageDialog = true
                    }) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add area")
                    }
                }

                if (coverage.isEmpty()) {
                    Text(
                        "No coverage rows means the server currently treats delivery as open coverage. Add rows when this listing should be restricted to specific countries, states/regions, or cities.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    coverage.forEachIndexed { index, row ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(
                                listOf(row.country, row.state, row.city).filter { it.isNotBlank() }.joinToString(" • "),
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall
                            )
                            IconButton(onClick = { coverage.removeAt(index) }) {
                                Icon(Icons.Default.Delete, "Remove coverage area")
                            }
                        }
                    }
                }

                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

                Button(
                    enabled = !saving,
                    onClick = {
                        val base = baseFee.toDoubleOrNull()
                        val extra = additionalItemFee.toDoubleOrNull()
                        val cap = feeCap.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
                        if (base == null || extra == null || base < 0 || extra < 0 || (feeCap.isNotBlank() && cap == null) || (cap != null && cap < base)) {
                            error = "Enter valid non-negative shipping fees. The cap cannot be below the base fee."
                            message = null
                            return@Button
                        }
                        saving = true
                        error = null
                        message = null
                        scope.launch {
                            FynxMarketplaceClient.saveShippingSettings(
                                context = context,
                                listingId = listingId,
                                settings = FynxMarketplaceClient.ShippingSettings(
                                    method = method,
                                    note = note,
                                    baseFee = base,
                                    additionalItemFee = extra,
                                    feeCap = cap,
                                    deliveryAvailable = deliveryAvailable,
                                    pickupAvailable = pickupAvailable,
                                    coverage = coverage.toList()
                                )
                            ).onSuccess {
                                message = "Shipping settings saved."
                            }.onFailure {
                                error = it.message ?: "Shipping settings could not be saved."
                            }
                            saving = false
                        }
                    }
                ) {
                    if (saving) CircularProgressIndicator(modifier = Modifier.width(18.dp))
                    else {
                        Icon(Icons.Default.Save, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Save shipping settings")
                    }
                }
            }
        }
    }

    if (showCoverageDialog) {
        AlertDialog(
            onDismissRequest = { showCoverageDialog = false },
            title = { Text("Add delivery coverage") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Country is required. State/region and city are optional.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(newCountry, { newCountry = it.take(100) }, label = { Text("Country") }, singleLine = true)
                    OutlinedTextField(newState, { newState = it.take(100) }, label = { Text("State / region (optional)") }, singleLine = true)
                    OutlinedTextField(newCity, { newCity = it.take(100) }, label = { Text("City (optional)") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val country = newCountry.trim()
                    if (country.isNotEmpty()) {
                        val candidate = FynxMarketplaceClient.Coverage(country, newState.trim(), newCity.trim())
                        val exists = coverage.any {
                            it.country.equals(candidate.country, true) &&
                                it.state.equals(candidate.state, true) &&
                                it.city.equals(candidate.city, true)
                        }
                        if (!exists && coverage.size < 100) coverage.add(candidate)
                        showCoverageDialog = false
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showCoverageDialog = false }) { Text("Cancel") } }
        )
    }
}
