package com.fynx.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private fun fynxWalletMoney(amount: Double): String =
    "₦" + String.format(Locale.US, "%,.2f", amount)

private fun fynxWalletTransactionTitle(type: String): String = when (type) {
    "FUNDING" -> "Money received"
    "MARKETPLACE_PAYMENT" -> "Marketplace purchase"
    "WITHDRAWAL" -> "Bank withdrawal"
    "REFUND" -> "Refund received"
    "REVERSAL" -> "Transaction reversal"
    else -> "Wallet adjustment"
}

private fun fynxWalletIsIncoming(transaction: FynxBuyerWalletActivity): Boolean =
    transaction.walletDelta > 0.0

private fun fynxWalletActivityDate(value: String): String {
    val raw = value.trim()
    if (raw.isBlank()) return "Date unavailable"
    val parsed = runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.parse(raw.take(19))
    }.getOrNull() ?: return "Date unavailable"
    return SimpleDateFormat("dd MMM yyyy · hh:mm a", Locale.US).format(parsed)
}

private fun fynxWalletStatusTitle(wallet: FynxBuyerWalletSummary?): String =
    when (wallet?.walletStatus) {
        "NOT_SETUP" -> "Wallet setup is not available yet"
        "PENDING_PROVIDER" -> "Your wallet is being prepared"
        "RESTRICTED" -> "Wallet access is restricted"
        "CLOSED" -> "This wallet is closed"
        "ACTIVE" -> "Some wallet features are not available yet"
        else -> "Your wallet is being prepared"
    }

@Composable
fun FynxBuyerWalletPanel() {
    val context = LocalContext.current
    var summary by remember { mutableStateOf<FynxBuyerWalletSummary?>(null) }
    var activity by remember { mutableStateOf(emptyList<FynxBuyerWalletActivity>()) }
    var loading by remember { mutableStateOf(true) }
    var summaryError by remember { mutableStateOf<String?>(null) }
    var activityError by remember { mutableStateOf<String?>(null) }
    var refreshVersion by remember { mutableIntStateOf(0) }
    var balanceVisible by remember { mutableStateOf(true) }

    LaunchedEffect(refreshVersion) {
        loading = true
        summaryError = null
        activityError = null
        summary = null
        activity = emptyList()

        val walletResult = FynxBuyerWalletClient.loadSummary(context)
        if (walletResult.isFailure) {
            summaryError = "Your wallet could not be loaded. Check your connection and try again."
        } else {
            summary = walletResult.getOrThrow()
            val historyResult = FynxBuyerWalletClient.loadTransactions(context)
            if (historyResult.isSuccess) {
                activity = historyResult.getOrThrow()
            } else {
                activityError = "Your wallet activity could not be loaded. Check your connection and try again."
            }
        }
        loading = false
    }

    val wallet = summary
    val isWalletReady = wallet != null && wallet.walletStatus == "ACTIVE" &&
        wallet.canFund && wallet.canSpend && wallet.canWithdraw
    val canShowBalance = wallet != null && wallet.availableBalance != null &&
        wallet.walletStatus == "ACTIVE"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("FYNX Wallet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Your Marketplace money, organised in one secure place.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(
                    containerColor = FynxDesign.AzureBlue,
                    contentColor = Color.White
                ),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("FYNX WALLET", fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = Color.White.copy(alpha = 0.88f))
                            Text("Nigerian Naira", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.88f))
                        }
                        Icon(
                            Icons.Default.AccountBalanceWallet,
                            contentDescription = "FYNX Wallet",
                            modifier = Modifier.size(30.dp),
                            tint = Color.White
                        )
                        TextButton(onClick = { balanceVisible = !balanceVisible }) {
                            Icon(
                                if (balanceVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (balanceVisible) "Hide balance" else "Show balance",
                                tint = Color.White
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("AVAILABLE BALANCE", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.88f), letterSpacing = 1.sp)
                        Text(
                            when {
                                loading -> "Loading…"
                                canShowBalance && balanceVisible -> fynxWalletMoney(wallet?.availableBalance ?: 0.0)
                                canShowBalance -> "₦ ••••••"
                                else -> "—"
                            },
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            if (canShowBalance) "Cleared funds available to use"
                            else "Your wallet balance will appear here after activation",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.88f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        OutlinedButton(
                            onClick = {},
                            enabled = isWalletReady && wallet?.canFund == true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.8f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White,
                                disabledContentColor = Color.White.copy(alpha = 0.62f)
                            )
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text("Add money", fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = {},
                            enabled = isWalletReady && wallet?.canWithdraw == true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.8f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White,
                                disabledContentColor = Color.White.copy(alpha = 0.62f)
                            )
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text("Withdraw", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        if (loading) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(10.dp))
                    Text("Loading your wallet…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (summaryError != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Wallet unavailable", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(summaryError.orEmpty(), color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = { refreshVersion += 1 }) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Text(" Retry")
                        }
                    }
                }
            }
        }
        if (!isWalletReady && summaryError == null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(15.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = FynxDesign.AzureBlue)
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(fynxWalletStatusTitle(wallet), fontWeight = FontWeight.SemiBold)
                            Text(
                                wallet?.notice?.takeIf { it.isNotBlank() }
                                    ?: "Secure deposits, Marketplace wallet payments and withdrawals will open after the financial provider connection and required checks are complete. You can continue using the existing Marketplace checkout in the meantime.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        item {
            Text("Balance details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FynxWalletMetric(
                    label = "Reserved",
                    amount = wallet?.reservedBalance,
                    modifier = Modifier.weight(1f)
                )
                FynxWalletMetric(
                    label = "Withdrawal pending",
                    amount = wallet?.withdrawalPendingBalance,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recent activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { refreshVersion += 1 }, enabled = !loading) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Text(" Refresh")
                }
            }
        }
        if (activityError != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Activity could not load", fontWeight = FontWeight.SemiBold)
                        Text(activityError.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { refreshVersion += 1 }, enabled = !loading) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Text(" Retry")
                        }
                    }
                }
            }
        }
        if (activity.isEmpty() && !loading && summaryError == null && activityError == null) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = FynxDesign.SlateGrey, modifier = Modifier.size(28.dp))
                        Text("No wallet activity yet", fontWeight = FontWeight.SemiBold)
                        Text(
                            "When your wallet is activated, money received, purchases, refunds and withdrawals will appear here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        items(activity, key = { it.id }) { transaction ->
            FynxWalletActivityRow(transaction)
        }
        item {
            Text(
                "FYNX Wallet currently supports the NGN foundation only. No funds are moved by this preview screen.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}

@Composable
private fun FynxWalletMetric(label: String, amount: Double?, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                amount?.let(::fynxWalletMoney) ?: "—",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun FynxWalletActivityRow(transaction: FynxBuyerWalletActivity) {
    val incoming = fynxWalletIsIncoming(transaction) && transaction.status == "SUCCEEDED"
    val accent = when {
        incoming -> FynxDesign.ClassicGreen
        transaction.status == "FAILED" || transaction.status == "BLOCKED" -> FynxDesign.CrimsonRed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val sign = when {
        transaction.walletDelta > 0.0 -> "+"
        transaction.walletDelta < 0.0 -> "−"
        else -> ""
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (incoming) Color(0xFFE6F4EA) else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Icon(
                    if (incoming) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.padding(9.dp).size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(fynxWalletTransactionTitle(transaction.type), fontWeight = FontWeight.SemiBold)
                if (transaction.description.isNotBlank()) {
                    Text(
                        transaction.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    fynxWalletActivityDate(transaction.createdAt) + " · " + transaction.reference,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(transaction.status.lowercase(Locale.US).replace('_', ' ').replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = accent)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    sign + fynxWalletMoney(transaction.amount),
                    fontWeight = FontWeight.SemiBold,
                    color = accent
                )
                if (transaction.fee > 0.0) {
                    Text(
                        "Fee " + fynxWalletMoney(transaction.fee),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
