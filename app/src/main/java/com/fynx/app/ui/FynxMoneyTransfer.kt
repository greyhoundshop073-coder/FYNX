package com.fynx.app.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private const val TRANSFER_PREFS = "fynx_money_transfers"
private const val TRANSFERS_KEY = "records"

enum class FynxMoneyTransferType { ACCOUNT_TRANSFER, PAYMENT }
enum class FynxMoneyTransferStatus { COMPLETED, CANCELLED }

data class FynxMoneyTransfer(
    val id: Long,
    val type: FynxMoneyTransferType,
    val fromAccountId: Long? = null,
    val toAccountId: Long? = null,
    val recipient: String = "",
    val title: String,
    val amount: Double,
    val date: String,
    val status: FynxMoneyTransferStatus = FynxMoneyTransferStatus.COMPLETED
)

object FynxMoneyTransferStore {
    fun load(context: Context): List<FynxMoneyTransfer> = runCatching {
        val raw = context.getSharedPreferences(TRANSFER_PREFS, Context.MODE_PRIVATE)
            .getString(TRANSFERS_KEY, "[]") ?: "[]"
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val type = runCatching { FynxMoneyTransferType.valueOf(o.optString("type")) }.getOrNull() ?: continue
                val status = runCatching { FynxMoneyTransferStatus.valueOf(o.optString("status", "COMPLETED")) }
                    .getOrDefault(FynxMoneyTransferStatus.COMPLETED)
                val title = o.optString("title").trim()
                val amount = o.optDouble("amount", Double.NaN)
                if (title.isNotBlank() && amount.isFinite() && amount > 0) {
                    add(FynxMoneyTransfer(
                        id = o.optLong("id", i.toLong()),
                        type = type,
                        fromAccountId = if (o.has("fromAccountId") && !o.isNull("fromAccountId")) o.optLong("fromAccountId") else null,
                        toAccountId = if (o.has("toAccountId") && !o.isNull("toAccountId")) o.optLong("toAccountId") else null,
                        recipient = o.optString("recipient"),
                        title = title,
                        amount = amount,
                        date = o.optString("date"),
                        status = status
                    ))
                }
            }
        }
    }.getOrDefault(emptyList())

    fun save(context: Context, values: List<FynxMoneyTransfer>) {
        val array = JSONArray()
        values.takeLast(100).forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("type", item.type.name)
                if (item.fromAccountId != null) put("fromAccountId", item.fromAccountId) else put("fromAccountId", JSONObject.NULL)
                if (item.toAccountId != null) put("toAccountId", item.toAccountId) else put("toAccountId", JSONObject.NULL)
                put("recipient", item.recipient)
                put("title", item.title)
                put("amount", item.amount)
                put("date", item.date)
                put("status", item.status.name)
            })
        }
        context.getSharedPreferences(TRANSFER_PREFS, Context.MODE_PRIVATE).edit()
            .putString(TRANSFERS_KEY, array.toString()).apply()
    }

    fun add(context: Context, item: FynxMoneyTransfer) {
        save(context, load(context) + item)
    }

    fun cancel(context: Context, id: Long) {
        save(context, load(context).map { if (it.id == id) it.copy(status = FynxMoneyTransferStatus.CANCELLED) else it })
    }
}

@Composable
fun FynxMoneyTransferPanel() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var accounts by remember { mutableStateOf(FynxMoneyStore.loadAccounts(context)) }
    var records by remember { mutableStateOf(FynxMoneyTransferStore.load(context)) }
    var selectedFrom by remember { mutableStateOf<FynxMoneyAccount?>(null) }
    var selectedTo by remember { mutableStateOf<FynxMoneyAccount?>(null) }
    var amountText by remember { mutableStateOf("") }
    var paymentRecipient by remember { mutableStateOf("") }
    var paymentTitle by remember { mutableStateOf("") }
    var paymentAmount by remember { mutableStateOf("") }
    var showTransfer by remember { mutableStateOf(false) }
    var showPayment by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<FynxMoneyTransfer?>(null) }

    fun refresh() {
        accounts = FynxMoneyStore.loadAccounts(context)
        records = FynxMoneyTransferStore.load(context)
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Transfers & Payments", style = MaterialTheme.typography.titleLarge)
                    Text("Safe local records — no real money is moved.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showTransfer = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = null)
                    Spacer(Modifier.width(6.dp)); Text("Transfer")
                }
                OutlinedButton(onClick = { showPayment = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null)
                    Spacer(Modifier.width(6.dp)); Text("Payment")
                }
            }
            if (records.isNotEmpty()) {
                Text("Recent activity", style = MaterialTheme.typography.titleMedium)
                records.asReversed().take(3).forEach { item ->
                    ListItem(
                        leadingContent = { Icon(if (item.type == FynxMoneyTransferType.ACCOUNT_TRANSFER) Icons.Default.SwapHoriz else Icons.Default.ReceiptLong, contentDescription = null) },
                        headlineContent = { Text(item.title) },
                        supportingContent = { Text("${item.status.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }} • ${item.date}") },
                        trailingContent = { Text(formatTransferMoney(item.amount)) }
                    )
                }
            }
        }
    }

    if (showTransfer) {
        AlertDialog(
            onDismissRequest = { showTransfer = false },
            title = { Text("Transfer between accounts") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccountPicker("From", selectedFrom, accounts.filter { it.id != selectedTo?.id }) { selectedFrom = it }
                    AccountPicker("To", selectedTo, accounts.filter { it.id != selectedFrom?.id }) { selectedTo = it }
                    OutlinedTextField(amountText, { amountText = it }, label = { Text("Amount") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("The transfer is recorded only after you confirm it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            dismissButton = { TextButton(onClick = { showTransfer = false }) { Text("Cancel") } },
            confirmButton = {
                Button(onClick = {
                    val amount = amountText.toDoubleOrNull()
                    val from = selectedFrom
                    val to = selectedTo
                    if (from != null && to != null && amount != null && amount > 0 && amount <= from.balance) {
                        pending = FynxMoneyTransfer(
                            id = (records.maxOfOrNull { it.id } ?: 0L) + 1L,
                            type = FynxMoneyTransferType.ACCOUNT_TRANSFER,
                            fromAccountId = from.id,
                            toAccountId = to.id,
                            title = "Transfer: ${from.name} → ${to.name}",
                            amount = amount,
                            date = "Today"
                        )
                        showTransfer = false
                    }
                }, enabled = selectedFrom != null && selectedTo != null && (amountText.toDoubleOrNull() ?: 0.0) > 0 && (amountText.toDoubleOrNull() ?: Double.MAX_VALUE) <= (selectedFrom?.balance ?: 0.0)) { Text("Review") }
            }
        )
    }

    if (showPayment) {
        AlertDialog(
            onDismissRequest = { showPayment = false },
            title = { Text("Record a payment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(paymentRecipient, { paymentRecipient = it }, label = { Text("Recipient") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(paymentTitle, { paymentTitle = it }, label = { Text("Payment description") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(paymentAmount, { paymentAmount = it }, label = { Text("Amount") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("This creates a local payment record and an expense transaction. It does not send money.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            dismissButton = { TextButton(onClick = { showPayment = false }) { Text("Cancel") } },
            confirmButton = {
                Button(onClick = {
                    val amount = paymentAmount.toDoubleOrNull()
                    if (paymentRecipient.isNotBlank() && paymentTitle.isNotBlank() && amount != null && amount > 0) {
                        pending = FynxMoneyTransfer(
                            id = (records.maxOfOrNull { it.id } ?: 0L) + 1L,
                            type = FynxMoneyTransferType.PAYMENT,
                            recipient = paymentRecipient.trim(),
                            title = paymentTitle.trim(),
                            amount = amount,
                            date = "Today"
                        )
                        showPayment = false
                    }
                }, enabled = paymentRecipient.isNotBlank() && paymentTitle.isNotBlank() && (paymentAmount.toDoubleOrNull() ?: 0.0) > 0) { Text("Review") }
            }
        )
    }

    pending?.let { item ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Confirm ${if (item.type == FynxMoneyTransferType.ACCOUNT_TRANSFER) "transfer" else "payment"}") },
            text = { Text(if (item.type == FynxMoneyTransferType.ACCOUNT_TRANSFER) "Transfer ${formatTransferMoney(item.amount)} from the selected source account to the destination account? You can cancel now before anything is recorded." else "Record ${formatTransferMoney(item.amount)} to ${item.recipient} as an expense? This remains a local FYNX record and does not send real money.") },
            dismissButton = { TextButton(onClick = { pending = null }) { Icon(Icons.Default.Cancel, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("Cancel") } },
            confirmButton = {
                Button(onClick = {
                    if (item.type == FynxMoneyTransferType.ACCOUNT_TRANSFER) {
                        val fromId = item.fromAccountId
                        val toId = item.toAccountId
                        val current = FynxMoneyStore.loadAccounts(context)
                        val from = current.firstOrNull { it.id == fromId }
                        val to = current.firstOrNull { it.id == toId }
                        if (from != null && to != null && item.amount <= from.balance && from.id != to.id) {
                            FynxMoneyStore.saveAccounts(context, current.map {
                                when (it.id) {
                                    from.id -> it.copy(balance = it.balance - item.amount)
                                    to.id -> it.copy(balance = it.balance + item.amount)
                                    else -> it
                                }
                            })
                            FynxMoneyTransferStore.add(context, item)
                        }
                    } else {
                        FynxMoneyTransferStore.add(context, item)
                        val transactionId = (FynxMoneyStore.loadTransactions(context).maxOfOrNull { it.id } ?: 0L) + 1L
                        FynxMoneyStore.addTransaction(context, FynxMoneyTransaction(transactionId, item.title, item.amount, "Expense", item.date, "Payment to ${item.recipient}"))
                    }
                    pending = null
                    selectedFrom = null; selectedTo = null; amountText = ""
                    paymentRecipient = ""; paymentTitle = ""; paymentAmount = ""
                    refresh()
                }) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Confirm")
                }
            }
        )
    }
}

@Composable
private fun AccountPicker(label: String, selected: FynxMoneyAccount?, accounts: List<FynxMoneyAccount>, onSelect: (FynxMoneyAccount) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(if (selected == null) "$label account" else "$label: ${selected.name} (${formatTransferMoney(selected.balance)})")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (accounts.isEmpty()) DropdownMenuItem(text = { Text("No other accounts") }, onClick = { expanded = false })
            accounts.forEach { account ->
                DropdownMenuItem(text = { Text("${account.name} • ${formatTransferMoney(account.balance)}") }, onClick = { onSelect(account); expanded = false })
            }
        }
    }
}

private fun formatTransferMoney(value: Double): String = String.format(Locale.US, "%.2f", value)
