package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SecureMoneyVaultPanel() {
    var enabled by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var enteredPin by remember { mutableStateOf("") }
    var unlocked by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var hideBalance by remember { mutableStateOf(true) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Secure Money Vault 🔐", style = MaterialTheme.typography.headlineSmall)
        Text("Protect private money information on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Vault protection", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (enabled) "PIN protection enabled" else "PIN protection disabled")
                    Switch(checked = enabled, onCheckedChange = {
                        enabled = it
                        unlocked = !it
                        message = if (!it) "Vault protection disabled" else "Create a PIN to protect the vault"
                    })
                }

                if (!enabled) {
                    Text("No PIN is stored by this screen while protection is disabled.")
                } else if (!unlocked) {
                    OutlinedTextField(enteredPin, { enteredPin = it.filter(Char::isDigit).take(6) }, label = { Text("Enter PIN") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        if (pin.isNotEmpty() && enteredPin == pin) {
                            unlocked = true
                            enteredPin = ""
                            message = "Vault unlocked"
                        } else message = "Incorrect PIN"
                    }) { Text("Unlock") }
                } else {
                    Text("Vault unlocked 🟢")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (hideBalance) "Balances hidden" else "Balances visible")
                        Switch(checked = !hideBalance, onCheckedChange = { hideBalance = !it })
                    }
                    OutlinedButton(onClick = { unlocked = false; message = "Vault locked" }) { Text("Lock Vault") }
                }

                if (enabled && pin.isEmpty()) {
                    OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(6) }, label = { Text("Create PIN (4–6 digits)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(confirmPin, { confirmPin = it.filter(Char::isDigit).take(6) }, label = { Text("Confirm PIN") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        if (pin.length in 4..6 && pin == confirmPin) {
                            unlocked = true
                            confirmPin = ""
                            message = "PIN protection enabled"
                        } else message = "PINs must match and contain 4–6 digits"
                    }) { Text("Set PIN") }
                }
                if (message.isNotEmpty()) Text(message, color = if (message.contains("Incorrect") || message.contains("must")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Security note 🛡️", style = MaterialTheme.typography.titleMedium)
                Text("This is a local UI protection layer. Real financial credentials, banking tokens, and production-grade secret storage should be added later with Android secure storage and proper authentication.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
