package com.fynx.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Trust Passport presentation built from the existing real seller reputation data. */
@Composable
internal fun FynxMarketplaceTrustPassport(
    sellerUsername: String,
    reputation: FynxMarketplaceClient.SellerReputation,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Trust Passport", style = MaterialTheme.typography.titleMedium)
                    Text("${reputation.tier} • ${reputation.completionRate.coerceIn(0.0, 100.0)}% completion")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TrustStat("Rating", String.format("%.1f", reputation.averageRating))
                TrustStat("Reviews", reputation.reviewCount.toString())
                TrustStat("Sales", reputation.successfulSales.toString())
            }
            Text("${reputation.totalOrders} orders • Rank ${reputation.rank.takeIf { it > 0 } ?: "—"}")
            Text(
                "View seller profile",
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .clickable { onOpenProfile(sellerUsername.removePrefix("@")) }
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            )
        }
    }
}

@Composable
private fun RowScope.TrustStat(label: String, value: String) {
    Column(modifier = Modifier.weight(1f)) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
