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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private fun walletActionMoney(amount: Double?): String =
    amount?.takeIf { it.isFinite() && it >= 0.0 }?.let { "₦" + String.format(Locale.US, "%,.2f", it) } ?: "Unavailable"

@Composable
internal fun FynxWalletActionPanel(
    screen: String,
    amount: String,
    onAmountChange: (String) -> Unit,
    availableBalance: Double?,
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
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = FynxDesign.AzureBlue, contentColor = Color.White)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("YOUR PERSONAL FYNX FUNDING ACCOUNT", color = Color.White.copy(alpha = 0.88f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Text("Account number not assigned yet", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Bank name and account name will appear here after your account is created by the payment provider.", color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.bodySmall)
                            if (amount.isNotBlank()) {
                                Text("Requested funding amount", color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.labelSmall)
                                Text("₦$amount", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                item {
                    WalletActionNotice(
                        title = "Your dedicated account is not ready",
                        message = "FYNX will show your real bank name, account name, account number and transfer reference here once the provider generates them. Do not transfer money to an account number supplied outside this FYNX screen."
                    )
                }
                item {
                    WalletStepCard(
                        title = "How funding will work",
                        steps = listOf(
                            "FYNX creates a dedicated account linked to your user ID.",
                            "You transfer money from your bank using the displayed details.",
                            "The provider confirms the payment to FYNX.",
                            "FYNX verifies the confirmation and credits your wallet."
                        )
                    )
                }
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
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("AVAILABLE BALANCE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.8.sp)
                            Text(walletActionMoney(availableBalance), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("Only cleared funds can be withdrawn.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item { WalletActionAmountField(amount, onAmountChange, "Amount to withdraw") }
                item { WalletAmountStatus(amount = amount, availableBalance = availableBalance, isWithdrawal = true) }
                item {
                    WalletReadOnlyBankField(
                        label = "Select bank",
                        placeholder = "Available after secure provider connection"
                    )
                }
                item {
                    WalletReadOnlyBankField(
                        label = "Account number",
                        placeholder = "Enter your 10-digit account number when enabled"
                    )
                }
                item {
                    WalletReadOnlyBankField(
                        label = "Verified account name",
                        placeholder = "Automatically confirmed by the payment provider"
                    )
                }
                item {
                    WalletActionNotice(
                        title = "Fees and final amount",
                        message = "The payment provider must quote the exact withdrawal fee before you approve a payout. FYNX will show the amount, fee and total deduction together; no estimated fee is used on this screen."
                    )
                }
                item {
                    WalletStepCard(
                        title = "Your withdrawal destination",
                        steps = listOf(
                            "Choose a supported Nigerian bank.",
                            "Enter your account number.",
                            "FYNX verifies the account name with the provider.",
                            "Review the exact fee and total deduction before confirming."
                        )
                    )
                }
                item {
                    WalletActionNotice(
                        title = "Bank payouts are not connected yet",
                        message = "Your bank details will only be collected through a secure, connected payout flow. FYNX will not save or submit bank details on this preparation screen. No withdrawal has been requested."
                    )
                }
                item {
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant, disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    ) {
                        Text("Withdrawals not available yet", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            else -> item { WalletActionNotice("Wallet option unavailable", "Return to your Wallet and choose an available option.") }
        }
        item { Spacer(Modifier.size(10.dp)) }
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
