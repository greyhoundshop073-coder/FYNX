package com.fynx.app.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.Locale

private fun walletActionMoney(amount: Double?): String =
    amount?.takeIf { it.isFinite() && it >= 0.0 }?.let { "₦" + String.format(Locale.US, "%,.2f", it) } ?: "Unavailable"

@Composable
internal fun FynxWalletActionPanel(
    screen: String,
    amount: String,
    onAmountChange: (String) -> Unit,
    availableBalance: Double?,
    canWithdraw: Boolean,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val title = when (screen) {
        "ADD" -> "Add Money"
        "TRANSFER" -> "Bank Transfer"
        "CARD" -> "Debit Card"
        "USSD" -> "USSD Payment"
        "WITHDRAW" -> "Withdraw Money"
        else -> "Wallet"
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back to Wallet")
                }
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }
        when (screen) {
            "ADD" -> {
                item {
                    Text(
                        "Choose how you want to add money to your FYNX Wallet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item { WalletActionAmountField(amount, onAmountChange, "Amount to add") }
                item { WalletAmountStatus(amount = amount, availableBalance = null, isWithdrawal = false) }
                item {
                    WalletMethodCard(
                        icon = "bank",
                        title = "Dedicated bank transfer",
                        description = "Your personal FYNX account number and transfer reference.",
                        onClick = { onNavigate("TRANSFER") }
                    )
                }
                item {
                    WalletMethodCard(
                        icon = "card",
                        title = "Debit card",
                        description = "Pay using a supported bank card.",
                        onClick = { onNavigate("CARD") }
                    )
                }
                item {
                    WalletMethodCard(
                        icon = "phone",
                        title = "USSD and other methods",
                        description = "Use an available bank payment option.",
                        onClick = { onNavigate("USSD") }
                    )
                }
                item {
                    WalletActionNotice(
                        title = "Funding is being prepared",
                        message = "Payment methods will become active after FYNX connects an approved provider and verifies payment notifications. No payment is started from this screen."
                    )
                }
            }
            "TRANSFER" -> {
                item { FynxWalletFundingAccountPanel(amount = amount) }
            }
            "CARD" -> {
                item {
                    WalletMethodUnavailable(
                        title = "Card funding is not active yet",
                        message = "A secure card checkout will open through the approved payment provider. FYNX will only credit your wallet after the provider confirms the payment."
                    )
                }
            }
            "USSD" -> {
                item {
                    WalletMethodUnavailable(
                        title = "USSD payments are not active yet",
                        message = "Supported banks and payment instructions will be shown here after the provider integration is enabled."
                    )
                }
            }
            "WITHDRAW" -> {
                item {
                    FynxWalletWithdrawalPanel(
                        amount = amount,
                        onAmountChange = onAmountChange,
                        availableBalance = availableBalance,
                        canWithdraw = canWithdraw
                    )
                }
            }
            else -> item { WalletActionNotice("Wallet option unavailable", "Return to your Wallet and choose an available option.") }
        }
        item { Spacer(Modifier.size(10.dp)) }
    }
}


@Composable
private fun FynxWalletWithdrawalPanel(
    amount: String,
    onAmountChange: (String) -> Unit,
    availableBalance: Double?,
    canWithdraw: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var banks by remember { mutableStateOf<List<FynxWalletPayoutBank>>(emptyList()) }
    var selectedBank by remember { mutableStateOf<FynxWalletPayoutBank?>(null) }
    var bankMenuExpanded by remember { mutableStateOf(false) }
    var accountNumber by remember { mutableStateOf("") }
    var verifiedAccountName by remember { mutableStateOf<String?>(null) }
    var banksLoading by remember { mutableStateOf(false) }
    var security by remember { mutableStateOf<FynxWalletSecurityStatus?>(null) }
    var securityLoading by remember { mutableStateOf(false) }
    var settingPin by remember { mutableStateOf(false) }
    var changePinMode by remember { mutableStateOf(false) }
    var currentPassword by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var withdrawalPin by remember { mutableStateOf("") }
    var verifying by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var showConfirmation by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<FynxWalletWithdrawalResult?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val idempotencyKey = remember { UUID.randomUUID().toString().replace("-", "") }

    LaunchedEffect(canWithdraw) {
        if (canWithdraw) {
            securityLoading = true
            banksLoading = true
            error = null
            FynxBuyerWalletClient.loadWalletSecurity(context)
                .onSuccess { security = it }
                .onFailure {
                    security = null
                    error = "Wallet security status could not be loaded. Refresh the page and try again."
                }
            FynxBuyerWalletClient.loadPayoutBanks(context)
                .onSuccess { banks = it }
                .onFailure {
                    banks = emptyList()
                    error = "Supported banks could not be loaded. Refresh the page and try again."
                }
            securityLoading = false
            banksLoading = false
        }
    }

    val parsedAmount = amount.toBigDecimalOrNull()
    val validAmount = parsedAmount != null && parsedAmount > java.math.BigDecimal.ZERO &&
        availableBalance != null && parsedAmount.toDouble() <= availableBalance
    val canVerify = !verifying && selectedBank != null && accountNumber.matches(Regex("^\\d{10}$"))
    val hasUsablePin = security?.hasWithdrawalPin == true && security?.isLocked != true &&
        withdrawalPin.matches(Regex("^\\d{6}$"))
    val canSubmit = canWithdraw && hasUsablePin && validAmount && selectedBank != null &&
        accountNumber.matches(Regex("^\\d{10}$")) && !verifiedAccountName.isNullOrBlank() &&
        !submitting && result == null

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("AVAILABLE BALANCE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.8.sp)
                Text(walletActionMoney(availableBalance), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Only cleared funds can be withdrawn. Reserved and pending funds are excluded.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (!canWithdraw) {
            WalletActionNotice(
                title = "Bank withdrawals are not enabled yet",
                message = "This screen will open after FYNX completes provider go-live, payout approval, custody and compliance checks, and confirms secure transfer settings. No bank details are submitted and no withdrawal is requested while this gate is closed."
            )
        } else {
            if (securityLoading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Checking withdrawal security…", style = MaterialTheme.typography.bodySmall)
                }
            } else if (security == null) {
                WalletActionNotice(
                    title = "Wallet security could not be verified",
                    message = "FYNX cannot safely continue until it can confirm your wallet PIN status. Go back to Wallet and retry the connection."
                )
            } else if (security?.isLocked == true) {
                WalletActionNotice(
                    title = "Withdrawals temporarily locked",
                    message = "FYNX has locked wallet withdrawals after repeated incorrect PIN attempts. Try again after the lock period ends."
                )
            } else if (security?.hasWithdrawalPin != true || changePinMode) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(
                            if (security?.hasWithdrawalPin == true) "Change your wallet withdrawal PIN" else "Set your wallet withdrawal PIN",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "This six-digit PIN is separate from your login. Confirm your current FYNX password before saving it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = currentPassword,
                            onValueChange = { currentPassword = it.take(128) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Current FYNX password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                        )
                        OutlinedTextField(
                            value = newPin,
                            onValueChange = { newPin = it.filter(Char::isDigit).take(6) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Create six-digit wallet PIN") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                        )
                        OutlinedTextField(
                            value = confirmPin,
                            onValueChange = { confirmPin = it.filter(Char::isDigit).take(6) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Confirm wallet PIN") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                        )
                        Button(
                            onClick = {
                                settingPin = true
                                error = null
                                scope.launch {
                                    FynxBuyerWalletClient.setWithdrawalPin(context, currentPassword, newPin, confirmPin)
                                        .onSuccess {
                                            FynxBuyerWalletClient.loadWalletSecurity(context).onSuccess { security = it }
                                            currentPassword = ""
                                            newPin = ""
                                            confirmPin = ""
                                            changePinMode = false
                                            Toast.makeText(context, "Wallet withdrawal PIN saved", Toast.LENGTH_SHORT).show()
                                        }
                                        .onFailure { error = it.message ?: "Wallet PIN could not be saved." }
                                    settingPin = false
                                }
                            },
                            enabled = !settingPin && currentPassword.length >= 8 &&
                                newPin.matches(Regex("^\\d{6}$")) && newPin == confirmPin,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(if (settingPin) "Saving PIN…" else "Save wallet PIN")
                        }
                    }
                }
            } else {
                security?.let { policy ->
                    val maximum = policy.maximumPerTransfer
                    val daily = policy.dailyAmountLimit
                    if (maximum != null && daily != null) {
                        Text(
                            "Withdrawal limit: up to " + walletActionMoney(maximum) +
                                " per transfer · " + walletActionMoney(daily) +
                                " per day · " + (policy.dailyCountLimit?.toString() ?: "—") + " requests per day.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedTextField(
                    value = withdrawalPin,
                    onValueChange = { withdrawalPin = it.filter(Char::isDigit).take(6) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Wallet withdrawal PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                TextButton(onClick = {
                    changePinMode = true
                    withdrawalPin = ""
                    error = null
                }) { Text("Change wallet PIN") }
                Text(
                    "Your PIN is checked by FYNX before cleared funds can be reserved for a bank transfer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text("Choose your bank", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            if (banksLoading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Loading supported Nigerian banks…", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                androidx.compose.foundation.layout.Box {
                    OutlinedButton(
                        onClick = { bankMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        enabled = banks.isNotEmpty()
                    ) {
                        Text(selectedBank?.name ?: "Select bank", modifier = Modifier.weight(1f))
                        Text("⌄")
                    }
                    DropdownMenu(
                        expanded = bankMenuExpanded,
                        onDismissRequest = { bankMenuExpanded = false }
                    ) {
                        banks.forEach { bank ->
                            DropdownMenuItem(
                                text = { Text(bank.name) },
                                onClick = {
                                    selectedBank = bank
                                    bankMenuExpanded = false
                                    verifiedAccountName = null
                                }
                            )
                        }
                    }
                }
                if (banks.isEmpty() && error == null) {
                    Text("No bank list is available from the provider.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            OutlinedTextField(
                value = accountNumber,
                onValueChange = {
                    accountNumber = it.filter(Char::isDigit).take(10)
                    verifiedAccountName = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Account number") },
                placeholder = { Text("Enter your 10-digit account number") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedButton(
                onClick = {
                    verifying = true
                    error = null
                    scope.launch {
                        FynxBuyerWalletClient.verifyPayoutAccount(
                            context,
                            selectedBank?.code.orEmpty(),
                            accountNumber
                        ).onSuccess { verifiedAccountName = it }
                            .onFailure {
                                verifiedAccountName = null
                                error = it.message ?: "Bank account could not be verified."
                            }
                        verifying = false
                    }
                },
                enabled = canVerify,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (verifying) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                }
                Text(if (verifying) "Verifying account…" else "Verify bank account")
            }
            verifiedAccountName?.let { verified ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = FynxDesign.ClassicGreen.copy(alpha = 0.09f),
                    border = BorderStroke(1.dp, FynxDesign.ClassicGreen.copy(alpha = 0.35f))
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Verified account name", color = FynxDesign.ClassicGreen, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text(verified, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Account ending in " + accountNumber.takeLast(4), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            WalletActionAmountField(amount, onAmountChange, "Amount to withdraw")
            WalletAmountStatus(amount = amount, availableBalance = availableBalance, isWithdrawal = true)
            WalletActionNotice(
                title = "Withdrawal fees and confirmation",
                message = "The amount you enter is the amount FYNX requests the provider to transfer. Provider-side fees are not guessed or silently added to your wallet amount. FYNX confirms the provider response before changing the final payout status."
            )

            Button(
                onClick = { showConfirmation = true },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Review withdrawal", fontWeight = FontWeight.SemiBold)
            }
            result?.let { completed ->
                WalletActionNotice(
                    title = when (completed.status) {
                        "SUCCEEDED" -> "Withdrawal completed"
                        "FAILED" -> "Withdrawal failed"
                        "REVERSED" -> "Withdrawal reversed"
                        "BLOCKED" -> "Withdrawal needs review"
                        else -> "Withdrawal processing"
                    },
                    message = completed.message.ifBlank {
                        "Reference " + completed.reference + ". Bank " +
                            completed.bankName + " •••• " + completed.accountLast4 + "."
                    }
                )
            }
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        statusMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        if (canWithdraw) {
            Text(
                "Every request is linked to your signed-in account, checked against cleared NGN balance and processed through a unique reference. Unconfirmed transfers remain reserved until the provider gives FYNX a final result.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!submitting) showConfirmation = false },
            title = { Text("Confirm bank withdrawal") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Amount: ₦" + (parsedAmount?.toPlainString() ?: amount))
                    Text("Bank: " + selectedBank?.name.orEmpty())
                    Text("Account name: " + verifiedAccountName.orEmpty())
                    Text("Account number: •••• " + accountNumber.takeLast(4))
                    Text(
                        "FYNX will reserve only this amount. The request will show as processing until Paystack confirms the bank transfer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !submitting,
                    onClick = {
                        submitting = true
                        error = null
                        scope.launch {
                            FynxBuyerWalletClient.requestWithdrawal(
                                context = context,
                                amount = parsedAmount?.toDouble() ?: 0.0,
                                bankCode = selectedBank?.code.orEmpty(),
                                bankName = selectedBank?.name.orEmpty(),
                                accountNumber = accountNumber,
                                idempotencyKey = idempotencyKey,
                                pin = withdrawalPin
                            ).onSuccess {
                                result = it
                                withdrawalPin = ""
                                statusMessage = it.reference.takeIf { ref -> ref.isNotBlank() }?.let { ref -> "Reference: " + ref }
                                showConfirmation = false
                                if (it.status in setOf("PENDING", "PROCESSING")) {
                                    Toast.makeText(context, "Withdrawal request submitted", Toast.LENGTH_SHORT).show()
                                }
                            }.onFailure {
                                error = it.message ?: "Withdrawal could not be submitted."
                                FynxBuyerWalletClient.loadWalletSecurity(context).onSuccess { security = it }
                                withdrawalPin = ""
                            }
                            submitting = false
                        }
                    }
                ) { Text(if (submitting) "Submitting…" else "Confirm withdrawal") }
            },
            dismissButton = {
                TextButton(enabled = !submitting, onClick = { showConfirmation = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun FynxWalletFundingAccountPanel(amount: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var account by remember { mutableStateOf<FynxWalletFundingAccount?>(null) }
    var loading by remember { mutableStateOf(true) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }

    fun reload() {
        loading = true
        error = null
        scope.launch {
            FynxBuyerWalletClient.loadFundingAccount(context)
                .onSuccess { account = it }
                .onFailure {
                    account = null
                    error = "Funding account status could not be loaded. Check your connection and retry."
                }
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Your dedicated FYNX funding account",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Transfers to the account assigned to you are matched by the payment provider and credited only after FYNX verifies the payment.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("Checking your funding account…", style = MaterialTheme.typography.bodySmall)
            }
        } else if (account?.status == "ACTIVE" && account?.accountNumber != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = FynxDesign.AzureBlue, contentColor = Color.White)
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("YOUR PERSONAL FYNX ACCOUNT", color = Color.White.copy(alpha = 0.88f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(account?.bankName.orEmpty(), color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(account?.accountNumber.orEmpty(), color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Account name: " + account?.accountName.orEmpty(), color = Color.White.copy(alpha = 0.94f), style = MaterialTheme.typography.bodySmall)
                    Text("NGN · Dedicated transfer account", color = Color.White.copy(alpha = 0.88f), style = MaterialTheme.typography.labelSmall)
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("FYNX funding account number", account?.accountNumber.orEmpty()))
                            Toast.makeText(context, "Account number copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.8f)),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) { Text("Copy account number") }
                }
            }
            if (amount.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Amount you plan to transfer", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("₦$amount", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("The provider-confirmed amount, not this entry, determines your wallet credit.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            WalletActionNotice(
                title = "How your transfer is credited",
                message = "Make a transfer to the exact account details above. FYNX waits for Paystack's signed notification, verifies the transaction, then records the credit in your wallet ledger. Do not use a different account number."
            )
        } else {
            val current = account
            val statusMessage = when (current?.status) {
                "REQUESTING" -> "Your account request has started. Refresh to check its status."
                "ASSIGNING" -> "Paystack is assigning your account. It will appear here after confirmation."
                "FAILED" -> "The provider could not assign your account. You may submit a new request when the provider is available."
                "RESTRICTED" -> "Account assignment needs additional provider review. Contact FYNX support before trying again."
                else -> "A real account number will only appear after the payment provider assigns it."
            }
            WalletActionNotice(
                title = "Dedicated account not ready",
                message = statusMessage
            )
            if (current?.canRequest == true) {
                Text("Account-holder details", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it.take(80) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("First name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it.take(80) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Last name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.take(160) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Email address") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.take(20) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Phone number") },
                    placeholder = { Text("+234…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Checkbox(checked = consent, onCheckedChange = { consent = it })
                    Text(
                        "I consent to FYNX sharing these details with Paystack to create and manage my dedicated funding account.",
                        modifier = Modifier.padding(top = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = {
                        submitting = true
                        error = null
                        scope.launch {
                            FynxBuyerWalletClient.requestFundingAccount(
                                context, email, firstName, lastName, phone, consent
                            ).onSuccess {
                                account = it
                                if (it.status == "ASSIGNING" || it.status == "REQUESTING") {
                                    Toast.makeText(context, "Account request submitted", Toast.LENGTH_SHORT).show()
                                }
                            }.onFailure {
                                error = it.message ?: "Your funding account could not be requested."
                            }
                            submitting = false
                        }
                    },
                    enabled = !submitting && consent && firstName.trim().length >= 2 &&
                        lastName.trim().length >= 2 && email.contains("@") && phone.filter(Char::isDigit).length >= 10,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (submitting) {
                        androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.size(8.dp))
                        Text("Requesting account…")
                    } else {
                        Text("Request dedicated account")
                    }
                }
            } else {
                WalletActionNotice(
                    title = "Provider setup is still pending",
                    message = "This account service will open after FYNX completes provider go-live, custody and compliance approval, and configures its secure account-encryption key. No account details are being invented or collected before then."
                )
            }
            OutlinedButton(onClick = { reload() }, enabled = !loading && !submitting, modifier = Modifier.fillMaxWidth()) {
                Text("Refresh account status")
            }
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        WalletStepCard(
            title = "Funding protection",
            steps = listOf(
                "FYNX requests a personal account from the approved provider.",
                "You transfer money from your bank to the assigned account.",
                "The provider sends a signed payment confirmation.",
                "FYNX verifies the account, amount and currency before crediting your wallet."
            )
        )
    }
}

@Composable
private fun WalletActionAmountField(
    amount: String,
    onAmountChange: (String) -> Unit,
    label: String
) {
    OutlinedTextField(
        value = amount,
        onValueChange = { raw ->
            val normalized = raw.trim()
            if (normalized.length <= 12 && normalized.matches(Regex("^\\d{0,9}(\\.\\d{0,2})?$"))) {
                onAmountChange(normalized)
            }
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text("Enter amount in NGN") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        leadingIcon = { Text("₦", fontWeight = FontWeight.SemiBold) },
        supportingText = { Text("NGN wallet") }
    )
}

@Composable
private fun WalletAmountStatus(amount: String, availableBalance: Double?, isWithdrawal: Boolean) {
    val parsed = amount.toBigDecimalOrNull()
    val message = when {
        amount.isBlank() -> "Enter an amount in NGN."
        parsed == null || parsed <= java.math.BigDecimal.ZERO -> "Enter a valid amount greater than zero."
        availableBalance != null && parsed.toDouble() > availableBalance ->
            "This amount is above your verified available balance."
        isWithdrawal && availableBalance == null && parsed > java.math.BigDecimal.ZERO ->
            "Your available balance must be verified before a withdrawal can be submitted."
        else -> "Amount entered. The payment provider will confirm availability and fees before any money moves."
    }
    val invalid = amount.isNotBlank() && (
        parsed == null || parsed <= java.math.BigDecimal.ZERO ||
            (availableBalance != null && parsed.toDouble() > availableBalance) ||
            (isWithdrawal && availableBalance == null && parsed > java.math.BigDecimal.ZERO)
    )
    Text(
        message,
        color = if (invalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun WalletReadOnlyBankField(label: String, placeholder: String) {
    OutlinedTextField(
        value = "",
        onValueChange = {},
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        enabled = false,
        singleLine = true,
        trailingIcon = { Icon(Icons.Default.Info, contentDescription = "Available after provider setup") }
    )
}

@Composable
private fun WalletMethodCard(
    icon: String,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val image = when (icon) {
                "card" -> Icons.Default.CreditCard
                "phone" -> Icons.Default.PhoneAndroid
                else -> Icons.Default.AccountBalanceWallet
            }
            Icon(image, contentDescription = null, tint = FynxDesign.AzureBlue, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun WalletMethodUnavailable(title: String, message: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WalletActionNotice(title, message)
        Text(
            "This option will open only after provider setup, verification and test payments are complete.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun WalletActionNotice(title: String, message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Info, contentDescription = null, tint = FynxDesign.AzureBlue)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun WalletStepCard(title: String, steps: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            steps.forEachIndexed { index, step ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = RoundedCornerShape(50), color = FynxDesign.AzureBlue.copy(alpha = 0.12f)) {
                        Text("${index + 1}", modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = FynxDesign.AzureBlue, fontWeight = FontWeight.Bold)
                    }
                    Text(step, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
