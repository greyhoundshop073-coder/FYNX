package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp

enum class FynxAuthPage { WELCOME, REGISTER, VERIFY, LOGIN }

@Composable
fun FynxAuthGate(onAuthenticated: (String) -> Unit) {
    val context = LocalContext.current
    var page by remember { mutableStateOf(if (FynxAuthStore.hasAccount(context)) FynxAuthPage.LOGIN else FynxAuthPage.WELCOME) }
    var displayName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    FynxTheme {
        Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
            Card(modifier = Modifier.fillMaxWidth(), shape = FynxDesign.LargeCardShape) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(Modifier.size(72.dp), shape = CircleShape, color = FynxDesign.SelectedContainer) {
                        Icon(Icons.Default.Lock, contentDescription = "FYNX account security", Modifier.padding(20.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("FYNX", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        when (page) {
                            FynxAuthPage.WELCOME -> "Connect. Share. Discover."
                            FynxAuthPage.REGISTER -> "Create your FYNX account"
                            FynxAuthPage.VERIFY -> "Verify your phone number"
                            FynxAuthPage.LOGIN -> "Welcome back"
                        },
                        color = FynxDesign.TextSecondary
                    )
                    Spacer(Modifier.height(22.dp))
                    when (page) {
                        FynxAuthPage.WELCOME -> {
                            Button(onClick = { error = null; page = FynxAuthPage.REGISTER }, Modifier.fillMaxWidth(), shape = FynxDesign.ControlShape) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Create account")
                            }
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(onClick = { error = null; page = FynxAuthPage.LOGIN }, Modifier.fillMaxWidth(), shape = FynxDesign.ControlShape) {
                                Icon(Icons.Default.Login, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Log in")
                            }
                        }
                        FynxAuthPage.REGISTER -> {
                            FynxAuthField(displayName, { displayName = it }, "Display name")
                            Spacer(Modifier.height(10.dp))
                            FynxAuthField(username, { username = it.replace(" ", "").removePrefix("@") }, "Username", "@")
                            Spacer(Modifier.height(10.dp))
                            FynxAuthField(phone, { phone = it.filter { c -> c.isDigit() || c == '+' } }, "Phone number", keyboardType = KeyboardType.Phone)
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                            Spacer(Modifier.height(14.dp))
                            Button(onClick = {
                                error = when {
                                    displayName.trim().length < 2 -> "Enter your name."
                                    username.length < 3 -> "Username must be at least 3 characters."
                                    phone.count { it.isDigit() } < 7 -> "Enter a valid phone number."
                                    else -> null
                                }
                                if (error == null) page = FynxAuthPage.VERIFY
                            }, Modifier.fillMaxWidth(), shape = FynxDesign.ControlShape) { Text("Continue") }
                            TextButton(onClick = { error = null; page = FynxAuthPage.WELCOME }) { Text("Back") }
                        }
                        FynxAuthPage.VERIFY -> {
                            Text("Enter the 6-digit verification code.", color = FynxDesign.TextSecondary)
                            Spacer(Modifier.height(10.dp))
                            FynxAuthField(code, { code = it.filter(Char::isDigit).take(6) }, "Verification code", keyboardType = KeyboardType.Number)
                            Text("Verification service will be connected to the production backend before launch.", color = FynxDesign.TextSecondary, style = MaterialTheme.typography.bodySmall)
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                            Spacer(Modifier.height(14.dp))
                            Button(onClick = {
                                if (code.length != 6) error = "Enter the 6-digit code."
                                else { FynxAuthStore.saveAccount(context, displayName.trim(), username.trim(), phone.trim()); onAuthenticated(username.trim()) }
                            }, Modifier.fillMaxWidth(), shape = FynxDesign.ControlShape) { Text("Verify and enter FYNX") }
                            TextButton(onClick = { error = null; page = FynxAuthPage.REGISTER }) { Text("Back") }
                        }
                        FynxAuthPage.LOGIN -> {
                            FynxAuthField(username, { username = it.replace(" ", "").removePrefix("@") }, "Username", "@")
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                            Spacer(Modifier.height(14.dp))
                            Button(onClick = {
                                val saved = FynxAuthStore.storedUsername(context)
                                if (saved.isNullOrBlank()) error = "No FYNX account is registered on this device."
                                else if (!username.equals(saved, ignoreCase = true)) error = "That username does not match the account on this device."
                                else { error = null; FynxAuthStore.save(context, saved); onAuthenticated(saved) }
                            }, Modifier.fillMaxWidth(), shape = FynxDesign.ControlShape) { Text("Log in") }
                            TextButton(onClick = { error = null; page = FynxAuthPage.REGISTER }) { Text("Create a new account") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FynxAuthField(value: String, onValueChange: (String) -> Unit, label: String, prefix: String? = null, keyboardType: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, prefix = prefix?.let { { Text(it) } }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = keyboardType), modifier = Modifier.fillMaxWidth(), shape = FynxDesign.ControlShape)
}
