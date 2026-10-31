package com.fynx.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
    email: String,
    onEmailChange: (String) -> Unit,
    availableBalance: Double?,
    withdrawalFee: Double,
    fundingResult: FynxWalletFundingResult?,
    withdrawalResult: FynxWalletWithdrawal?,
    actionError: String?,
    actionBusy: Boolean,
    onStartTransfer: () -> Unit,
    onStartCheckout: (String) -> Unit,
    onRefreshFunding: () -> Unit,
    onOpenCheckout: (String) -> Unit,
    banks: List<FynxWalletBank>,
    savedPayoutAccounts: List<FynxWalletPayoutAccount>,
    selectedBankCode: String,
    onSelectBank: (String) -> Unit,
    bankAccountNumber: String,
    onBankAccountNumberChange: (String) -> Unit,
    verifiedAccountName: String?,
    onVerifyBankAccount: () -> Unit,
    onSavePayoutAccount: () -> Unit,
    selectedPayoutAccountId: String,
    onSelectPayoutAccount: (String) -> Unit,
    pinConfigured: Boolean,
    newPin: String,
    onNewPinChange: (String) -> Unit,
    currentPin: String,
    onCurrentPinChange: (String) -> Unit,
    onSavePin: () -> Unit,
    withdrawalPin: String,
    onWithdrawalPinChange: (String) -> Unit,
    withdrawalOtp: String,
    onWithdrawalOtpChange: (String) -> Unit,
    onStartWithdrawal: () -> Unit,
    onFinalizeWithdrawal: () -> Unit,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
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
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back to Wallet") }
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }
        when (screen) {
            "ADD" -> {
                item { Text("Choose how you want to add money to your FYNX Wallet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item { WalletActionAmountField(amount, onAmountChange, "Amount to add") }
                item {
                    OutlinedTextField(
                        value = email, onValueChange = onEmailChange, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Email for payment receipt") }, placeholder = { Text("you@example.com") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )
                }
                item {
                    WalletMethodCard(
                        icon = "bank", title = "Bank transfer",
                        description = "Get a real temporary bank account for this top-up.",
                        onClick = { onNavigate("TRANSFER") }
                    )
                }
                item {
                    WalletMethodCard(
                        icon = "card", title = "Debit card",
                        description = "Continue through Paystack's secure hosted card checkout.",
                        onClick = { onNavigate("CARD") }
                    )
                }
                item {
                    WalletMethodCard(
                        icon = "phone", title = "USSD payment",
                        description = "Continue through the provider's supported USSD checkout.",
                        onClick = { onNavigate("USSD") }
                    )
                }
                item {
                    WalletActionNotice(
                        title = "Payment verification",
                        message = "FYNX credits your Wallet only after the provider confirms the exact payment. A transfer receipt or screenshot alone cannot credit your balance."
                    )
                }
            }
            "TRANSFER" -> {
                item {
                    if (fundingResult?.bankTransfer != null) {
                        val transfer = fundingResult.bankTransfer
                        Card(
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = FynxDesign.AzureBlue, contentColor = Color.White)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("ACCOUNT FOR THIS TOP-UP ONLY", color = Color.White.copy(alpha = 0.90f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Text(transfer.bankName, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("Account name", color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.labelSmall)
                                Text(transfer.accountName, color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text("Account number", color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.labelSmall)
                                Text(transfer.accountNumber, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                Text("Exact amount to transfer", color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.labelSmall)
                                Text(walletActionMoney(transfer.amount), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { walletCopy(context, transfer.accountNumber, "Account number copied") }, border = BorderStroke(1.dp, Color.White), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Copy account") }
                                    OutlinedButton(onClick = { walletCopy(context, transfer.reference, "Transfer reference copied") }, border = BorderStroke(1.dp, Color.White), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Copy reference") }
                                }
                            }
                        }
                    } else {
                        WalletActionNotice(
                            title = "Generate your transfer account",
                            message = "Paystack will issue an account number for this specific top-up. It is temporary and expires after payment or at the displayed expiry time; it is not a permanent personal account number."
                        )
                        WalletActionAmountField(amount, onAmountChange, "Amount to add")
                        OutlinedTextField(
                            value = email, onValueChange = onEmailChange, modifier = Modifier.fillMaxWidth(),
                            label = { Text("Email for payment receipt") }, placeholder = { Text("you@example.com") },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                        )
                        Button(
                            onClick = onStartTransfer, enabled = !actionBusy && amount.isNotBlank() && email.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = FynxDesign.AzureBlue)
                        ) {
                            if (actionBusy) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            else Text("Generate bank transfer details", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (fundingResult != null) {
                    item { WalletStatusCard(fundingResult.status, fundingResult.reference) }
                    item {
                        Text("Account expiry: " + (fundingResult.bankTransfer?.accountExpiresAt ?: "See transfer instructions"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onRefreshFunding, enabled = !actionBusy) { Text("Check payment status") }
                    }
                }
                item {
                    WalletStepCard(
                        title = "How your top-up is completed",
                        steps = listOf(
                            "Transfer the exact amount to the temporary account shown above.",
                            "Paystack notifies FYNX when the transfer is successful.",
                            "FYNX checks the reference, amount and currency.",
                            "Your Wallet ledger is credited once, after verification."
                        )
                    )
                }
            }
            "CARD" -> {
                item { WalletActionAmountField(amount, onAmountChange, "Amount to add") }
                item {
                    OutlinedTextField(value=email,onValueChange=onEmailChange,modifier=Modifier.fillMaxWidth(),label={Text("Email for payment receipt")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email))
                }
                item { WalletActionNotice("Secure card checkout", "FYNX will open Paystack-hosted checkout. Card details are entered with the provider, never collected or stored by FYNX.") }
                item {
                    Button(onClick={onStartCheckout("card")},enabled=!actionBusy&&amount.isNotBlank()&&email.isNotBlank(),modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),colors=ButtonDefaults.buttonColors(containerColor=FynxDesign.AzureBlue)) {
                        if(actionBusy) CircularProgressIndicator(modifier=Modifier.size(18.dp),color=Color.White,strokeWidth=2.dp) else Text("Continue to secure card checkout")
                    }
                }
                fundingResult?.let { result ->
                    item { WalletStatusCard(result.status,result.reference) }
                    item { TextButton(onClick=onRefreshFunding,enabled=!actionBusy){Text("Check payment status")} }
                    if(!result.checkoutUrl.isNullOrBlank()) item { OutlinedButton(onClick={onOpenCheckout(result.checkoutUrl)},modifier=Modifier.fillMaxWidth()){Text("Open payment checkout")} }
                }
            }
            "USSD" -> {
                item { WalletActionAmountField(amount, onAmountChange, "Amount to add") }
                item {
                    OutlinedTextField(value=email,onValueChange=onEmailChange,modifier=Modifier.fillMaxWidth(),label={Text("Email for payment receipt")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email))
                }
                item { WalletActionNotice("Provider USSD checkout", "Choose USSD in the secure provider checkout. FYNX will credit your Wallet only after the provider confirms the payment.") }
                item {
                    Button(onClick={onStartCheckout("ussd")},enabled=!actionBusy&&amount.isNotBlank()&&email.isNotBlank(),modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),colors=ButtonDefaults.buttonColors(containerColor=FynxDesign.AzureBlue)) {
                        if(actionBusy) CircularProgressIndicator(modifier=Modifier.size(18.dp),color=Color.White,strokeWidth=2.dp) else Text("Continue to USSD checkout")
                    }
                }
                fundingResult?.let { result ->
                    item { WalletStatusCard(result.status,result.reference) }
                    item { TextButton(onClick=onRefreshFunding,enabled=!actionBusy){Text("Check payment status")} }
                    if(!result.checkoutUrl.isNullOrBlank()) item { OutlinedButton(onClick={onOpenCheckout(result.checkoutUrl)},modifier=Modifier.fillMaxWidth()){Text("Open payment checkout")} }
                }
            }
            "WITHDRAW" -> {
                item {
                    Surface(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.50f),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text("AVAILABLE BALANCE",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,letterSpacing=0.8.sp)
                            Text(walletActionMoney(availableBalance),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                            Text("Only cleared funds can be withdrawn. Funds being withdrawn are held until the provider confirms the payout.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item { WalletActionAmountField(amount,onAmountChange,"Amount to withdraw") }
                item {
                    Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        Text("Saved bank accounts",fontWeight=FontWeight.SemiBold)
                        if(savedPayoutAccounts.isEmpty()) Text("No payout account has been verified yet.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        savedPayoutAccounts.forEach { account ->
                            OutlinedButton(onClick={onSelectPayoutAccount(account.id)},modifier=Modifier.fillMaxWidth(),border=BorderStroke(1.dp,if(account.id==selectedPayoutAccountId) FynxDesign.AzureBlue else MaterialTheme.colorScheme.outlineVariant)) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(account.bankName,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onSurface)
                                    Text(account.accountName+" · •••• "+account.accountLast4,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                item {
                    WalletBankAccountEditor(
                        banks=banks,selectedBankCode=selectedBankCode,onSelectBank=onSelectBank,
                        accountNumber=bankAccountNumber,onAccountNumberChange=onBankAccountNumberChange,
                        verifiedName=verifiedAccountName,onVerify=onVerifyBankAccount,onSave=onSavePayoutAccount,
                        busy=actionBusy
                    )
                }
                item {
                    Card(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Text("Wallet PIN security",fontWeight=FontWeight.SemiBold)
                            if(!pinConfigured) Text("Set a six-digit Wallet PIN before requesting a payout.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            else Text("Change your Wallet PIN only when needed. Your current PIN is required.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            if(pinConfigured) OutlinedTextField(value=currentPin,onValueChange=onCurrentPinChange,modifier=Modifier.fillMaxWidth(),label={Text("Current PIN")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword))
                            OutlinedTextField(value=newPin,onValueChange=onNewPinChange,modifier=Modifier.fillMaxWidth(),label={Text(if(pinConfigured) "New six-digit PIN" else "Set six-digit PIN")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword))
                            Button(onClick=onSavePin,enabled=!actionBusy&&newPin.length==6&&(!pinConfigured||currentPin.length==6),modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),colors=ButtonDefaults.buttonColors(containerColor=FynxDesign.AzureBlue)) {
                                Text(if(pinConfigured) "Update Wallet PIN" else "Set Wallet PIN")
                            }
                        }
                    }
                }
                item {
                    OutlinedTextField(value=withdrawalPin,onValueChange=onWithdrawalPinChange,modifier=Modifier.fillMaxWidth(),label={Text("Confirm with Wallet PIN")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword))
                }
                item {
                    Surface(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.45f),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Withdrawal amount",color=MaterialTheme.colorScheme.onSurfaceVariant);Text(walletActionMoney(amount.toDoubleOrNull()))}
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("FYNX withdrawal fee",color=MaterialTheme.colorScheme.onSurfaceVariant);Text(walletActionMoney(withdrawalFee))}
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Total wallet deduction",fontWeight=FontWeight.SemiBold);Text(walletActionMoney((amount.toDoubleOrNull()?:0.0)+withdrawalFee),fontWeight=FontWeight.Bold)}
                        }
                    }
                }
                if(withdrawalResult!=null) {
                    item { WalletStatusCard(withdrawalResult.status,withdrawalResult.reference,"Withdrawal") }
                    item { TextButton(onClick=onRefreshFunding,enabled=!actionBusy){Text("Check withdrawal status")} }
                    if(withdrawalResult.otpRequired) item {
                        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value=withdrawalOtp,onValueChange=onWithdrawalOtpChange,modifier=Modifier.fillMaxWidth(),label={Text("Provider transfer OTP")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword))
                            Button(onClick=onFinalizeWithdrawal,enabled=!actionBusy&&withdrawalOtp.length in 4..8,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=FynxDesign.AzureBlue)){Text("Confirm payout OTP")}
                        }
                    }
                }
                item {
                    Button(onClick=onStartWithdrawal,enabled=!actionBusy&&pinConfigured&&selectedPayoutAccountId.isNotBlank()&&withdrawalPin.length==6&&amount.toDoubleOrNull()?.let{it>=100&&it<=1_000_000}==true&&availableBalance!=null,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),colors=ButtonDefaults.buttonColors(containerColor=FynxDesign.AzureBlue)) {
                        if(actionBusy) CircularProgressIndicator(modifier=Modifier.size(18.dp),color=Color.White,strokeWidth=2.dp) else Text("Confirm withdrawal",fontWeight=FontWeight.SemiBold)
                    }
                }
            }
            else -> item { WalletActionNotice("Wallet option unavailable", "Return to your Wallet and choose an available option.") }
        }
        if (!actionError.isNullOrBlank()) item { WalletActionNotice("Action needs attention", actionError) }
        item { Spacer(Modifier.size(10.dp)) }
    }
}

@Composable
private fun WalletActionAmountField(amount: String, onAmountChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value=amount,onValueChange={raw->val normalized=raw.trim();if(normalized.length<=12&&normalized.matches(Regex("^\\d{0,9}(\\.\\d{0,2})?$")))onAmountChange(normalized)},
        modifier=Modifier.fillMaxWidth(),label={Text(label)},placeholder={Text("Enter amount in NGN")},
        singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),leadingIcon={Text("₦",fontWeight=FontWeight.SemiBold)},supportingText={Text("NGN wallet")}
    )
}

@Composable
private fun WalletBankAccountEditor(
    banks: List<FynxWalletBank>, selectedBankCode: String, onSelectBank: (String) -> Unit,
    accountNumber: String, onAccountNumberChange: (String) -> Unit, verifiedName: String?,
    onVerify: () -> Unit, onSave: () -> Unit, busy: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedBank=banks.firstOrNull{it.code==selectedBankCode}
    Card(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Add a bank account",fontWeight=FontWeight.SemiBold)
            OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()) { Text(selectedBank?.name?:if(banks.isEmpty()) "Bank list unavailable" else "Select your bank",modifier=Modifier.weight(1f)); Text("⌄") }
            DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
                banks.forEach { bank -> DropdownMenuItem(text={Text(bank.name)},onClick={onSelectBank(bank.code);expanded=false}) }
            }
            OutlinedTextField(value=accountNumber,onValueChange={if(it.length<=10&&it.all(Char::isDigit))onAccountNumberChange(it)},modifier=Modifier.fillMaxWidth(),label={Text("10-digit account number")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
            if(!verifiedName.isNullOrBlank()) {
                Text("Verified account name",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(verifiedName,fontWeight=FontWeight.SemiBold)
            }
            OutlinedButton(onClick=onVerify,enabled=!busy&&selectedBankCode.isNotBlank()&&accountNumber.length==10,modifier=Modifier.fillMaxWidth()) { Text("Verify bank account") }
            Button(onClick=onSave,enabled=!busy&&selectedBankCode.isNotBlank()&&accountNumber.length==10&&!verifiedName.isNullOrBlank(),modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=FynxDesign.AzureBlue)) { Text("Save verified bank account") }
        }
    }
}

@Composable
private fun WalletStatusCard(status: String, reference: String, subject: String = "Payment") {
    val upper=status.uppercase(Locale.US)
    val success=upper=="SUCCEEDED"
    val color=if(success) Color(0xFF008000) else if(upper in setOf("FAILED","BLOCKED","REVERSED")) Color(0xFFDC143C) else FynxDesign.AzureBlue
    WalletActionNotice(
        title=when(upper){"SUCCEEDED"->"$subject confirmed";"FAILED"->"$subject failed";"REVERSED"->"$subject reversed";"BLOCKED"->"$subject needs review";else->"$subject "+upper.lowercase(Locale.US)},
        message="Reference: $reference. "+when(upper){"SUCCEEDED"->"Your Wallet records have been updated."; "FAILED"->"No Wallet debit was posted. Check the details before trying again.";else->"FYNX is checking the provider status. Do not repeat the transaction until you have checked this reference."}
    )
}

private fun walletCopy(context: Context, value: String, label: String) {
    val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label,value))
    Toast.makeText(context,label,Toast.LENGTH_SHORT).show()
}

@Composable
private fun WalletMethodCard(icon: String, title: String, description: String, onClick: () -> Unit) {
    OutlinedButton(onClick=onClick,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),contentPadding=PaddingValues(14.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            val image=when(icon){"card"->Icons.Default.CreditCard;"phone"->Icons.Default.PhoneAndroid;else->Icons.Default.AccountBalance}
            Icon(image,contentDescription=null,tint=FynxDesign.AzureBlue,modifier=Modifier.size(24.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(title,color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.SemiBold)
                Text(description,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun WalletActionNotice(title:String,message:String) {
    Surface(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(15.dp),verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Info,contentDescription=null,tint=FynxDesign.AzureBlue)
            Column(verticalArrangement=Arrangement.spacedBy(5.dp)) { Text(title,fontWeight=FontWeight.SemiBold);Text(message,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun WalletStepCard(title:String,steps:List<String>) {
    Card(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            steps.forEachIndexed{index,step->
                Row(verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    Surface(shape=RoundedCornerShape(50),color=FynxDesign.AzureBlue.copy(alpha=0.12f)){Text("${index+1}",modifier=Modifier.padding(horizontal=9.dp,vertical=5.dp),color=FynxDesign.AzureBlue,fontWeight=FontWeight.Bold)}
                    Text(step,modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
