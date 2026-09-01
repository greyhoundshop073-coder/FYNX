package com.fynx.app.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun AccountsWalletsPanel() {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var balanceText by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Cash") }
    var accounts by remember { mutableStateOf(FynxMoneyStore.loadAccounts(context)) }
    var pendingAccount by remember { mutableStateOf<FynxMoneyAccount?>(null) }

    val total = accounts.sumOf { it.balance }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Accounts & Wallets 🏦", style = MaterialTheme.typography.headlineSmall)
        Text("Track balances locally without connecting to a real bank.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
            Text("Combined balance", style = MaterialTheme.typography.titleMedium)
            Text(money(total), style = MaterialTheme.typography.headlineMedium)
            Text("${accounts.size} account(s)")
        } }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(name, { name = it }, label = { Text("Account / wallet name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(balanceText, { balanceText = it }, label = { Text("Starting balance") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Cash", "Bank", "Wallet").forEach { option -> FilterChip(selected = type == option, onClick = { type = option }, label = { Text(option) }) }
        }
        Button(onClick = {
            val balance = balanceText.toDoubleOrNull()
            if (name.isNotBlank() && balance != null) {
                val nextId = (accounts.maxOfOrNull { it.id } ?: 0L) + 1L
                pendingAccount = FynxMoneyAccount(nextId, name.trim(), type, balance)
            }
        }, enabled = name.isNotBlank() && balanceText.toDoubleOrNull() != null) { Text("Add Account") }
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(accounts, key = { it.id }) { account ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(account.name, style = MaterialTheme.typography.titleMedium); Text(money(account.balance)) }
                    Text(account.type, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } }
            }
        }
    }

    pendingAccount?.let { account ->
        AlertDialog(
            onDismissRequest = { pendingAccount = null },
            title = { Text("Confirm account") },
            text = { Text("Add ${account.name} with a starting balance of ${money(account.balance)}? This stays on this device.") },
            dismissButton = { TextButton(onClick = { pendingAccount = null }) { Text("Cancel") } },
            confirmButton = {
                Button(onClick = {
                    FynxMoneyStore.addAccount(context, account)
                    accounts = FynxMoneyStore.loadAccounts(context)
                    name = ""; balanceText = ""; pendingAccount = null
                }) { Text("Confirm") }
            }
        )
    }
}

private fun money(value: Double): String = String.format(Locale.US, "%.2f", value)
