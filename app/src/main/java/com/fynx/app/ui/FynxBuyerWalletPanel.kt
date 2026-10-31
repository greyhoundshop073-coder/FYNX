package com.fynx.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalUriHandler
import java.text.SimpleDateFormat
import java.util.Locale
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.launch

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
    val instant = runCatching { Instant.parse(raw) }
        .recoverCatching { OffsetDateTime.parse(raw).toInstant() }
        .getOrNull() ?: return "Date unavailable"
    return SimpleDateFormat("dd MMM yyyy · hh:mm a", Locale.US).format(Date.from(instant))
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
    var activeAction by remember { mutableStateOf("HOME") }
    var fundingAmount by remember { mutableStateOf("") }
    var fundingEmail by remember { mutableStateOf("") }
    var fundingResult by remember { mutableStateOf<FynxWalletFundingResult?>(null) }
    var withdrawalResult by remember { mutableStateOf<FynxWalletWithdrawal?>(null) }
    var actionBusy by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var fundingIdempotencyKey by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var withdrawalIdempotencyKey by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var banks by remember { mutableStateOf(emptyList<FynxWalletBank>()) }
    var payoutAccounts by remember { mutableStateOf(emptyList<FynxWalletPayoutAccount>()) }
    var selectedBankCode by remember { mutableStateOf("") }
    var bankAccountNumber by remember { mutableStateOf("") }
    var verifiedAccountName by remember { mutableStateOf<String?>(null) }
    var selectedPayoutAccountId by remember { mutableStateOf("") }
    var pinConfigured by remember { mutableStateOf(false) }
    var newPin by remember { mutableStateOf("") }
    var currentPin by remember { mutableStateOf("") }
    var withdrawalPin by remember { mutableStateOf("") }
    var withdrawalOtp by remember { mutableStateOf("") }
    val uriHandler = LocalUriHandler.current
    var summary by remember { mutableStateOf<FynxBuyerWalletSummary?>(null) }
    var activity by remember { mutableStateOf(emptyList<FynxBuyerWalletActivity>()) }
    var summaryLoading by remember { mutableStateOf(true) }
    var activityLoading by remember { mutableStateOf(true) }
    var summaryError by remember { mutableStateOf<String?>(null) }
    var activityError by remember { mutableStateOf<String?>(null) }
    var refreshVersion by remember { mutableIntStateOf(0) }
    var balanceVisible by remember { mutableStateOf(true) }
    var activityFilter by remember { mutableStateOf("ALL") }
    var selectedActivity by remember { mutableStateOf<FynxBuyerWalletActivity?>(null) }
    var showWithdrawalGuide by remember { mutableStateOf(false) }
    var hasMoreActivity by remember { mutableStateOf(false) }
    var loadingMoreActivity by remember { mutableStateOf(false) }
    var activityMoreError by remember { mutableStateOf<String?>(null) }
    var nextActivityCursor by remember { mutableStateOf<FynxBuyerWalletActivityCursor?>(null) }
    var activityRequestGeneration by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refreshVersion, activityFilter) {
        activityRequestGeneration += 1
        val requestGeneration = activityRequestGeneration
        summaryLoading = true
        activityLoading = true
        summaryError = null
        activityError = null
        activityMoreError = null
        loadingMoreActivity = false
        summary = null
        activity = emptyList()
        hasMoreActivity = false
        nextActivityCursor = null

        try {
            val walletResult = FynxBuyerWalletClient.loadSummary(context)
            if (requestGeneration != activityRequestGeneration) return@LaunchedEffect
            if (walletResult.isFailure) {
                summaryError = "Your wallet could not be loaded. Check your connection and try again."
                return@LaunchedEffect
            }

            summary = walletResult.getOrThrow()
            summaryLoading = false

            val historyResult = FynxBuyerWalletClient.loadTransactions(context, direction = activityFilter)
            if (requestGeneration == activityRequestGeneration) {
                if (historyResult.isSuccess) {
                    val page = historyResult.getOrThrow()
                    activity = page.transactions
                    hasMoreActivity = page.hasMore
                    nextActivityCursor = page.nextCursor
                } else {
                    activityError = "Your wallet activity could not be loaded. Check your connection and try again."
                }
            }
        } finally {
            if (requestGeneration == activityRequestGeneration) {
                summaryLoading = false
                activityLoading = false
            }
        }
    }

    val loading = summaryLoading || activityLoading
    val wallet = summary
    val isWalletReady = wallet != null && wallet.walletStatus == "ACTIVE" &&
        wallet.canFund && wallet.canSpend && wallet.canWithdraw
    val canShowBalance = wallet != null && wallet.availableBalance != null &&
        wallet.walletStatus == "ACTIVE"
    // The backend filters the complete history before applying its page cursor.
    val visibleActivity = activity

    fun loadMoreActivity() {
        val cursor = nextActivityCursor ?: return
        if (!hasMoreActivity || loadingMoreActivity || loading) return
        val requestGeneration = activityRequestGeneration
        activityMoreError = null
        scope.launch {
            loadingMoreActivity = true
            try {
                FynxBuyerWalletClient.loadTransactions(context, before = cursor, direction = activityFilter)
                    .onSuccess { page ->
                        if (requestGeneration == activityRequestGeneration) {
                            activity = (activity + page.transactions).distinctBy { it.id }
                            hasMoreActivity = page.hasMore
                            nextActivityCursor = page.nextCursor
                        }
                    }
                    .onFailure {
                        if (requestGeneration == activityRequestGeneration) {
                            activityMoreError = "More wallet activity could not be loaded. Check your connection and try again."
                        }
                    }
            } finally {
                // A request from an older filter/refresh must not clear the current request's spinner.
                if (requestGeneration == activityRequestGeneration) {
                    loadingMoreActivity = false
                }
            }
        }
    }

    fun runWalletAction(action: suspend () -> Unit) {
        scope.launch {
            actionBusy = true
            actionError = null
            try {
                action()
            } catch (error: Exception) {
                actionError = error.message ?: "Wallet action could not be completed."
            } finally {
                actionBusy = false
            }
        }
    }

    LaunchedEffect(activeAction) {
        if (activeAction == "WITHDRAW") {
            actionError = null
            FynxBuyerWalletClient.loadBanks(context)
                .onSuccess { banks = it }
                .onFailure { actionError = it.message ?: "Bank list is not available yet." }
            FynxBuyerWalletClient.loadPayoutAccounts(context)
                .onSuccess { accounts ->
                    payoutAccounts = accounts
                    if (selectedPayoutAccountId.isBlank()) selectedPayoutAccountId = accounts.firstOrNull()?.id.orEmpty()
                }
                .onFailure { /* Provider-gated until withdrawal approval. */ }
            FynxBuyerWalletClient.walletPinConfigured(context)
                .onSuccess { pinConfigured = it }
                .onFailure { /* PIN status may be unavailable before the wallet backend is enabled. */ }
        }
    }

    if (activeAction != "HOME") {
        FynxWalletActionPanel(
            screen = activeAction,
            amount = fundingAmount,
            onAmountChange = {
                if (fundingAmount != it) {
                    fundingAmount = it
                    fundingIdempotencyKey = UUID.randomUUID().toString()
                    withdrawalIdempotencyKey = UUID.randomUUID().toString()
                    if (fundingResult?.status != "SUCCEEDED") fundingResult = null
                    withdrawalResult = null
                }
            },
            email = fundingEmail,
            onEmailChange = {
                if (fundingEmail != it) {
                    fundingEmail = it
                    fundingIdempotencyKey = UUID.randomUUID().toString()
                }
            },
            availableBalance = wallet?.availableBalance,
            withdrawalFee = wallet?.withdrawalFee ?: 0.0,
            fundingResult = fundingResult,
            withdrawalResult = withdrawalResult,
            actionError = actionError,
            actionBusy = actionBusy,
            onStartTransfer = {
                runWalletAction {
                    FynxBuyerWalletClient.startBankTransfer(
                        context, fundingAmount.toDoubleOrNull() ?: 0.0, fundingEmail, fundingIdempotencyKey
                    ).onSuccess { fundingResult = it }
                     .onFailure { actionError = it.message ?: "Bank transfer details could not be generated." }
                }
            },
            onStartCheckout = { method ->
                runWalletAction {
                    FynxBuyerWalletClient.startFundingCheckout(
                        context, fundingAmount.toDoubleOrNull() ?: 0.0, fundingEmail, method, fundingIdempotencyKey
                    ).onSuccess { result ->
                        fundingResult = result
                        result.checkoutUrl?.let { uri ->
                            runCatching { uriHandler.openUri(uri) }
                                .onFailure { actionError = "Secure payment checkout was created, but could not be opened. Use Open payment checkout." }
                        }
                    }.onFailure { actionError = it.message ?: "Secure payment checkout could not be created." }
                }
            },
            onRefreshFunding = {
                runWalletAction {
                    val funding = fundingResult
                    val withdrawal = withdrawalResult
                    if (activeAction == "WITHDRAW" && withdrawal != null) {
                        FynxBuyerWalletClient.loadWithdrawalStatus(context, withdrawal.reference)
                            .onSuccess {
                                withdrawalResult = it
                                if (it.status == "SUCCEEDED" || it.status == "FAILED" || it.status == "REVERSED") refreshVersion++
                            }
                            .onFailure { actionError = it.message ?: "Withdrawal status could not be refreshed." }
                    } else if (funding != null) {
                        FynxBuyerWalletClient.loadFundingStatus(context, funding.reference)
                            .onSuccess {
                                fundingResult = it
                                if (it.status == "SUCCEEDED" || it.status == "FAILED" || it.status == "BLOCKED") refreshVersion++
                            }
                            .onFailure { actionError = it.message ?: "Funding status could not be refreshed." }
                    }
                }
            },
            onOpenCheckout = { uri -> runCatching { uriHandler.openUri(uri) }.onFailure { actionError = "Unable to open the secure payment page." } },
            banks = banks,
            savedPayoutAccounts = payoutAccounts,
            selectedBankCode = selectedBankCode,
            onSelectBank = {
                selectedBankCode = it
                verifiedAccountName = null
            },
            bankAccountNumber = bankAccountNumber,
            onBankAccountNumberChange = {
                bankAccountNumber = it
                verifiedAccountName = null
            },
            verifiedAccountName = verifiedAccountName,
            onVerifyBankAccount = {
                runWalletAction {
                    FynxBuyerWalletClient.resolveBankAccount(context, bankAccountNumber, selectedBankCode)
                        .onSuccess { verifiedAccountName = it }
                        .onFailure { actionError = it.message ?: "The bank could not verify this account." }
                }
            },
            onSavePayoutAccount = {
                runWalletAction {
                    FynxBuyerWalletClient.savePayoutAccount(context, bankAccountNumber, selectedBankCode, banks.firstOrNull { it.code == selectedBankCode }?.name.orEmpty())
                        .onSuccess { account ->
                            payoutAccounts = (listOf(account) + payoutAccounts.filterNot { it.id == account.id })
                            selectedPayoutAccountId = account.id
                            bankAccountNumber = ""
                            verifiedAccountName = null
                            actionError = null
                        }
                        .onFailure { actionError = it.message ?: "Verified bank account could not be saved." }
                }
            },
            selectedPayoutAccountId = selectedPayoutAccountId,
            onSelectPayoutAccount = { selectedPayoutAccountId = it },
            pinConfigured = pinConfigured,
            newPin = newPin,
            onNewPinChange = { newPin = it.filter(Char::isDigit).take(6) },
            currentPin = currentPin,
            onCurrentPinChange = { currentPin = it.filter(Char::isDigit).take(6) },
            onSavePin = {
                runWalletAction {
                    FynxBuyerWalletClient.saveWalletPin(context, newPin, currentPin.takeIf { pinConfigured })
                        .onSuccess {
                            pinConfigured = true
                            newPin = ""
                            currentPin = ""
                            actionError = null
                        }
                        .onFailure { actionError = it.message ?: "Wallet PIN could not be saved." }
                }
            },
            withdrawalPin = withdrawalPin,
            onWithdrawalPinChange = { withdrawalPin = it.filter(Char::isDigit).take(6) },
            withdrawalOtp = withdrawalOtp,
            onWithdrawalOtpChange = { withdrawalOtp = it.filter(Char::isDigit).take(8) },
            onStartWithdrawal = {
                runWalletAction {
                    FynxBuyerWalletClient.startWithdrawal(
                        context, fundingAmount.toDoubleOrNull() ?: 0.0, selectedPayoutAccountId,
                        withdrawalPin, withdrawalIdempotencyKey
                    ).onSuccess {
                        withdrawalResult = it
                        withdrawalPin = ""
                        withdrawalOtp = ""
                    }.onFailure { actionError = it.message ?: "Withdrawal request could not be started." }
                }
            },
            onFinalizeWithdrawal = {
                val existing = withdrawalResult
                if (existing != null) runWalletAction {
                    FynxBuyerWalletClient.finalizeWithdrawal(context, existing.reference, withdrawalOtp)
                        .onSuccess {
                            withdrawalResult = it
                            withdrawalOtp = ""
                            if (it.status == "SUCCEEDED") refreshVersion++
                        }
                        .onFailure { actionError = it.message ?: "Withdrawal OTP could not be verified." }
                }
            },
            onBack = {
                activeAction = if (activeAction == "TRANSFER" || activeAction == "CARD" || activeAction == "USSD") "ADD" else "HOME"
            },
            onNavigate = {
                if (it == "TRANSFER" || it == "CARD" || it == "USSD") fundingResult = null
                if (it == "WITHDRAW") withdrawalResult = null
                activeAction = it
            }
        )
        return
    }

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
                                summaryLoading -> "Checking…"
                                canShowBalance && balanceVisible -> fynxWalletMoney(wallet.availableBalance ?: 0.0)
                                canShowBalance -> "₦ ••••••"
                                summaryError != null -> "Unavailable"
                                else -> "—"
                            },
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            when {
                                summaryLoading -> "Checking your wallet status securely"
                                canShowBalance -> "Cleared funds available to use"
                                summaryError != null -> "Balance unavailable until your connection is restored"
                                else -> "Your wallet balance will appear here after activation"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.88f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        OutlinedButton(
                            onClick = { activeAction = "ADD" },
                            enabled = true,
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
                            onClick = { activeAction = "WITHDRAW" },
                            enabled = true,
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
        if (summaryLoading || activityLoading) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(10.dp))
                    Text(
                        if (summaryLoading) "Checking your wallet…" else "Loading wallet activity…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
        if (!summaryLoading && !isWalletReady && summaryError == null) {
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
                    explanation = "Held for an order or an active review.",
                    visible = balanceVisible,
                    modifier = Modifier.weight(1f)
                )
                FynxWalletMetric(
                    label = "Withdrawal pending",
                    amount = wallet?.withdrawalPendingBalance,
                    explanation = "Sent for withdrawal and awaiting final confirmation.",
                    visible = balanceVisible,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Understand your money", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Available is your cleared wallet balance. Reserved money is tied to an order or review, so it cannot be spent or withdrawn. A pending withdrawal is already being processed and is not part of your available balance.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { showWithdrawalGuide = true }, contentPadding = PaddingValues(0.dp)) {
                        Text("How bank withdrawals will work")
                    }
                }
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
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = activityFilter == "ALL", onClick = { activityFilter = "ALL" }, label = { Text("All") })
                FilterChip(selected = activityFilter == "IN", onClick = { activityFilter = "IN" }, label = { Text("Money in") })
                FilterChip(selected = activityFilter == "OUT", onClick = { activityFilter = "OUT" }, label = { Text("Money out") })
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
        if (visibleActivity.isEmpty() && !loading && summaryError == null && activityError == null) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = FynxDesign.SlateGrey, modifier = Modifier.size(28.dp))
                        Text(
                            when (activityFilter) {
                                "IN" -> "No money received yet"
                                "OUT" -> "No money sent yet"
                                else -> "No wallet activity yet"
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            when (activityFilter) {
                                "IN" -> "Confirmed incoming funds and refunds will appear here when they are recorded."
                                "OUT" -> "Wallet payments and withdrawals will appear here when those services are enabled."
                                else -> "When your wallet is activated, money received, purchases, refunds and withdrawals will appear here."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        items(visibleActivity, key = { it.id }) { transaction ->
            FynxWalletActivityRow(transaction, amountsVisible = balanceVisible) { selectedActivity = transaction }
        }
        if (activityMoreError != null) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(activityMoreError.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { loadMoreActivity() }, enabled = !loadingMoreActivity) { Text("Retry loading more") }
                }
            }
        }
        if (hasMoreActivity) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = { loadMoreActivity() }, enabled = !loadingMoreActivity && !loading) {
                        if (loadingMoreActivity) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(8.dp))
                            Text("Loading more…")
                        } else {
                            Text("Load more activity")
                        }
                    }
                }
            }
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

    selectedActivity?.let { transaction ->
        val direction = when {
            transaction.walletDelta > 0.0 -> "+"
            transaction.walletDelta < 0.0 -> "−"
            else -> ""
        }
        val detailAccent = when {
            transaction.walletDelta > 0.0 && transaction.status == "SUCCEEDED" -> FynxDesign.ClassicGreen
            transaction.status == "FAILED" || transaction.status == "BLOCKED" -> FynxDesign.CrimsonRed
            else -> MaterialTheme.colorScheme.onSurface
        }
        AlertDialog(
            onDismissRequest = { selectedActivity = null },
            title = { Text(fynxWalletTransactionTitle(transaction.type)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (balanceVisible) direction + fynxWalletMoney(transaction.amount) else "••••••",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = detailAccent
                    )
                    Text("Status: " + transaction.status.lowercase(Locale.US).replace('_', ' ').replaceFirstChar { it.uppercase() })
                    Text("Date: " + fynxWalletActivityDate(transaction.createdAt))
                    if (transaction.description.isNotBlank()) Text("Details: " + transaction.description)
                    Text("Fee: " + if (balanceVisible) fynxWalletMoney(transaction.fee) else "••••••")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Reference: " + transaction.reference, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("FYNX wallet transaction reference", transaction.reference))
                            Toast.makeText(context, "Transaction reference copied", Toast.LENGTH_SHORT).show()
                        }) { Text("Copy") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedActivity = null }) { Text("Close") }
            }
        )
    }

    if (showWithdrawalGuide) {
        AlertDialog(
            onDismissRequest = { showWithdrawalGuide = false },
            title = { Text("Withdrawing your wallet balance") },
            text = {
                Text(
                    "The planned bank withdrawal flow will let you send eligible, cleared available funds to a verified bank account. Before you confirm, FYNX will show the withdrawal amount, any provider fee and the expected status. Reserved order funds and withdrawals already being processed cannot be withdrawn again. This feature is not live yet; no money moves from this screen today."
                )
            },
            confirmButton = {
                TextButton(onClick = { showWithdrawalGuide = false }) { Text("Got it") }
            }
        )
    }
}

@Composable
private fun FynxWalletMetric(
    label: String,
    amount: Double?,
    explanation: String,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (visible) amount?.let(::fynxWalletMoney) ?: "—" else "••••••",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(explanation, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FynxWalletActivityRow(
    transaction: FynxBuyerWalletActivity,
    amountsVisible: Boolean,
    onClick: () -> Unit
) {
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
        onClick = onClick,
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
                    if (amountsVisible) sign + fynxWalletMoney(transaction.amount) else "••••••",
                    fontWeight = FontWeight.SemiBold,
                    color = accent
                )
                if (transaction.fee > 0.0 && amountsVisible) {
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
