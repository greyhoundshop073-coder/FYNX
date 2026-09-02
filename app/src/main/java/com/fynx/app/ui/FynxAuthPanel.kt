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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

enum class FynxAuthPage { WELCOME, REGISTER, VERIFY, LOGIN }

@Composable
fun FynxAuthGate(onAuthenticated: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf(if (FynxAuthStore.hasAccount(context)) FynxAuthPage.LOGIN else FynxAuthPage.WELCOME) }
    var displayName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun finish(result: Result<FynxRemoteAuthClient.ResultData>) {
        result.onSuccess { account ->
            FynxBackendClient.saveAccessToken(context, account.token)
            FynxAuthStore.saveAccount(context, account.displayName.ifBlank { displayName.trim() }, account.username, account.phone.ifBlank { phone.trim() })
            busy = false
            error = null
            onAuthenticated(account.username)
        }.onFailure {
            busy = false
            error = it.message?.substringAfter(": ")?.trim()?.removePrefix("{")?.removeSuffix("}") ?: "FYNX could not complete the account request."
        }
    }

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
                            FynxAuthPage.VERIFY -> "Finish your account setup"
                            FynxAuthPage.LOGIN -> "Welcome back"
                        }, color = FynxDesign.TextSecondary
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
                            Spacer(Modifier.height(10.dp))
                            FynxAuthField(password, { password = it }, "Password", keyboardType = KeyboardType.Password, password = true)
                            Spacer(Modifier.height(10.dp))
                            FynxAuthField(confirmPassword, { confirmPassword = it }, "Confirm password", keyboardType = KeyboardType.Password, password = true)
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                            Spacer(Modifier.height(14.dp))
                            Button(onClick = {
                                error = when {
                                    displayName.trim().length < 2 -> "Enter your name."
                                    username.length < 3 -> "Username must be at least 3 characters."
                                    phone.count { it.isDigit() } < 7 -> "Enter a valid phone number."
                                    password.length < 8 -> "Password must be at least 8 characters."
                                    password != confirmPassword -> "Passwords do not match."
                                    else -> null
                                }
                                if (error == null) page = FynxAuthPage.VERIFY
                            }, Modifier.fillMaxWidth(), shape = FynxDesign.ControlShape, enabled = !busy) { Text("Continue") }
                            TextButton(onClick = { error = null; page = FynxAuthPage.WELCOME }, enabled = !busy) { Text("Back") }
                        }
                        FynxAuthPage.VERIFY -> {
                            Text("Your account will be created securely on the FYNX server. Phone/SMS verification is not being faked here; it will be connected before public launch.", color = FynxDesign.TextSecondary, style = MaterialTheme.typography.bodySmall)
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                            Spacer(Modifier.height(14.dp))
                            Button(onClick = {
                                busy = true
                                error = null
                                scope.launch { finish(FynxRemoteAuthClient.register(context, displayName.trim(), username.trim(), phone.trim(), password)) }
                            }, Modifier.fillMaxWidth(), shape = FynxDesign.ControlShape, enabled = !busy) { Text(if (busy) "Creating account…" else "Create and enter FYNX") }
                            TextButton(onClick = { error = null; page = FynxAuthPage.REGISTER }, enabled = !busy) { Text("Back") }
                        }
                        FynxAuthPage.LOGIN -> {
                            FynxAuthField(username, { username = it.replace(" ", "").removePrefix("@") }, "Username", "@")
                            Spacer(Modifier.height(10.dp))
                            FynxAuthField(password, { password = it }, "Password", keyboardType = KeyboardType.Password, password = true)
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                            Spacer(Modifier.height(14.dp))
                            Button(onClick = {
                                if (username.isBlank() || password.isBlank()) error = "Enter your username and password."
                                else {
                                    busy = true
                                    error = null
                                    scope.launch { finish(FynxRemoteAuthClient.login(context, username.trim(), password)) }
                                }
                            }, Modifier.fillMaxWidth(), shape = FynxDesign.ControlShape, enabled = !busy) { Text(if (busy) "Signing in…" else "Log in") }
                            TextButton(onClick = { error = null; page = FynxAuthPage.REGISTER }, enabled = !busy) { Text("Create a new account") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FynxAuthField(value: String, onValueChange: (String) -> Unit, label: String, prefix: String? = null, keyboardType: KeyboardType = KeyboardType.Text, password: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        prefix = prefix?.let { { Text(it) } },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
        shape = FynxDesign.ControlShape
    )
}
