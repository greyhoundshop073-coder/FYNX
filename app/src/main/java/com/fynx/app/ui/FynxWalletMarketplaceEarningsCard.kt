package com.fynx.app.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.util.Locale

private data class FynxWalletSellerEarning(
    val title: String,
    val currency: String,
    val productSubtotal: Double,
    val deliveryFee: Double,
    val commission: Double,
    val sellerNet: Double,
    val funds: String,
    val payoutStatus: String
)

private data class FynxWalletSellerCurrencyTotals(
    val currency: String,
    val protectedFunds: Double,
    val availableForPayout: Double,
    val payoutPending: Double,
    val paidOut: Double
)

private data class FynxWalletSellerEarningsSnapshot(
    val records: List<FynxWalletSellerEarning>,
    val totals: List<FynxWalletSellerCurrencyTotals>
)

private fun fynxWalletEarningMoney(currency: String, amount: Double): String {
    val symbol = when (currency.uppercase(Locale.US)) {
        "NGN" -> "₦"
        "USD" -> "$"
        else -> currency.uppercase(Locale.US) + " "
    }
    return symbol + String.format(Locale.US, "%,.2f", amount)
}

private fun parseFynxWalletSellerEarnings(raw: String): FynxWalletSellerEarningsSnapshot {
    val root = JSONObject(raw)
    val rows = root.optJSONArray("settlements")
        ?: throw IllegalStateException("Marketplace earnings response was incomplete.")
    val totalsRows = root.optJSONArray("totals")
    val records = buildList {
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index)
                ?: throw IllegalStateException("Marketplace earnings response was incomplete.")
            val title = row.optString("title").trim().ifBlank { "Marketplace sale" }
            val currency = row.optString("currency", "NGN").trim().uppercase(Locale.US)
            require(currency.matches(Regex("^[A-Z]{3}$"))) { "Marketplace returned an invalid currency." }
            val productSubtotal = row.optDouble("productSubtotal", 0.0)
            val deliveryFee = row.optDouble("deliveryFee", 0.0)
            val commission = row.optDouble("marketplaceFeeSeller", row.optDouble("marketplaceFee", 0.0))
            val sellerNet = row.optDouble("sellerNetAmount", Double.NaN)
            require(productSubtotal.isFinite() && productSubtotal >= 0.0 &&
                deliveryFee.isFinite() && deliveryFee >= 0.0 &&
                commission.isFinite() && commission >= 0.0 &&
                sellerNet.isFinite() && sellerNet >= 0.0) {
                "Marketplace returned invalid seller earnings."
            }
            add(
                FynxWalletSellerEarning(
                    title = title,
                    currency = currency,
                    productSubtotal = productSubtotal,
                    deliveryFee = deliveryFee,
                    commission = commission,
                    sellerNet = sellerNet,
                    funds = row.optString("funds", "UNKNOWN").uppercase(Locale.US),
                    payoutStatus = row.optString("payoutStatus", "UNKNOWN").uppercase(Locale.US)
                )
            )
        }
    }
    val totals = buildList {
        if (totalsRows != null) {
            for (index in 0 until totalsRows.length()) {
                val row = totalsRows.optJSONObject(index)
                    ?: throw IllegalStateException("Marketplace earnings totals were incomplete.")
                val currency = row.optString("currency", "NGN").trim().uppercase(Locale.US)
                require(currency.matches(Regex("^[A-Z]{3}$"))) { "Marketplace returned an invalid currency." }
                val protected = row.optDouble("protectedFunds", Double.NaN)
                val available = row.optDouble("availableForPayout", Double.NaN)
                val pending = row.optDouble("payoutPending", Double.NaN)
                val paid = row.optDouble("paidOut", Double.NaN)
                require(listOf(protected, available, pending, paid).all { it.isFinite() && it >= 0.0 }) {
                    "Marketplace returned invalid earnings totals."
                }
                add(FynxWalletSellerCurrencyTotals(currency, protected, available, pending, paid))
            }
        }
    }
    return FynxWalletSellerEarningsSnapshot(
        records = records.sortedByDescending { it.funds != "UNKNOWN" },
        totals = totals
    )
}

/**
 * Seller Marketplace settlements are shown separately from the buyer wallet balance.
 * Commission is read from the server's order snapshot; no local percentage calculation
 * is used to invent a debit or seller payout amount.
 */
@Composable
internal fun FynxWalletMarketplaceEarningsCard(refreshKey: Int) {
    val context = LocalContext.current
    var localRefresh by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var snapshot by remember { mutableStateOf<FynxWalletSellerEarningsSnapshot?>(null) }

    LaunchedEffect(refreshKey, localRefresh) {
        loading = true
        error = null
        val result = FynxBackendClient.get(context, "/api/marketplace/settlement/seller/summary")
            .mapCatching(::parseFynxWalletSellerEarnings)
        result.onSuccess { snapshot = it }
            .onFailure {
                snapshot = null
                error = it.message ?: "Marketplace earnings could not be loaded."
            }
        loading = false
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = FynxDesign.CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Marketplace earnings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Your sale proceeds and FYNX commission, shown separately.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { localRefresh++ }, enabled = !loading) {
                    Text("Refresh")
                }
            }
            Text(
                "These seller settlements are separate from your personal Wallet balance. Protected sales are not available to withdraw until their order and payout conditions are met.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when {
                loading -> CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                error != null -> {
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { localRefresh++ }) { Text("Try again") }
                }
                snapshot == null || (snapshot?.records.isNullOrEmpty() && snapshot?.totals.isNullOrEmpty()) -> {
                    Text(
                        "Your Marketplace sale earnings and commission will appear here after your first sale.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    snapshot?.totals.orEmpty().forEach { total ->
                        Text(total.currency, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Protected", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(fynxWalletEarningMoney(total.currency, total.protectedFunds), fontWeight = FontWeight.SemiBold)
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Available for payout", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(fynxWalletEarningMoney(total.currency, total.availableForPayout), fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Payout pending", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(fynxWalletEarningMoney(total.currency, total.payoutPending), fontWeight = FontWeight.SemiBold)
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Paid out", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(fynxWalletEarningMoney(total.currency, total.paidOut), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    if (snapshot?.records.orEmpty().isNotEmpty()) {
                        Text("Recent sales", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        snapshot?.records.orEmpty().take(5).forEach { record ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(record.title, fontWeight = FontWeight.SemiBold)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("FYNX commission", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "−" + fynxWalletEarningMoney(record.currency, record.commission),
                                        color = Color(0xFFDC143C),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Your net earnings", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        fynxWalletEarningMoney(record.currency, record.sellerNet),
                                        color = Color(0xFF008000),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    "Funds: ${record.funds.lowercase(Locale.US)} · payout: ${record.payoutStatus.lowercase(Locale.US)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.size(4.dp))
                        }
                    }
                }
            }
        }
    }
}
