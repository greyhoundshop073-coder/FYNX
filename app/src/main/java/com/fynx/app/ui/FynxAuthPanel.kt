package com.fynx.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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

    val background = Brush.verticalGradient(
        colors = listOf(Color(0xFF070F1B), Color(0xFF101B39), Color(0xFF26125A), Color(0xFF082B55))
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .imePadding()
            .padding(horizontal = 22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (page == FynxAuthPage.WELCOME || page == FynxAuthPage.LOGIN) {
                Spacer(Modifier.height(18.dp))
                Text("FYNX", color = Color.White, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(6.dp))
                Text("Connect • Share • Trade • Grow", color = Color.White.copy(alpha = .88f), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("Your world. All in one place.", color = Color.White.copy(alpha = .68f), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(28.dp))
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFF0C1728).copy(alpha = .82f),
                tonalElevation = 0.dp
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (page == FynxAuthPage.REGISTER || page == FynxAuthPage.VERIFY) {
                        Text("FYNX", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (page == FynxAuthPage.REGISTER) "Create your FYNX account" else "Finish your account setup",
                            color = Color.White.copy(alpha = .72f)
                        )
                        Spacer(Modifier.height(20.dp))
                    }

                    when (page) {
                        FynxAuthPage.WELCOME -> {
                            Button(
                                onClick = { error = null; page = FynxAuthPage.REGISTER },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(28.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238AF2))
                            ) { Text("Create Account", fontWeight = FontWeight.SemiBold) }
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { error = null; page = FynxAuthPage.LOGIN },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(28.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF6A4CFF))
                            ) { Text("Sign In", fontWeight = FontWeight.SemiBold) }
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
                            }, Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(26.dp), enabled = !busy) { Text("Continue") }
                            TextButton(onClick = { error = null; page = FynxAuthPage.WELCOME }, enabled = !busy) { Text("Back") }
                        }
                        FynxAuthPage.VERIFY -> {
                            Text("Your account will be created securely on the FYNX server. Phone/SMS verification is not being faked here; it will be connected before public launch.", color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.bodySmall)
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                            Spacer(Modifier.height(14.dp))
                            Button(onClick = {
                                busy = true
                                error = null
                                scope.launch { finish(FynxRemoteAuthClient.register(context, displayName.trim(), username.trim(), phone.trim(), password)) }
                            }, Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(26.dp), enabled = !busy) { Text(if (busy) "Creating account…" else "Create and enter FYNX") }
                            TextButton(onClick = { error = null; page = FynxAuthPage.REGISTER }, enabled = !busy) { Text("Back") }
                        }
                        FynxAuthPage.LOGIN -> {
                            Text("Welcome back", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(18.dp))
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
                            }, Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(26.dp), enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238AF2))) { Text(if (busy) "Signing in…" else "Sign In") }
                            TextButton(onClick = { error = null; page = FynxAuthPage.REGISTER }, enabled = !busy, colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = .8f))) { Text("Create a new account") }
                        }
                    }
                }
            }
            if (page == FynxAuthPage.WELCOME) {
                Spacer(Modifier.height(20.dp))
                Text("Secure • Social • Marketplace • AI Powered", color = Color.White.copy(alpha = .55f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun FynxAuthField(value: String, onValueChange: (String) -> Unit, label: String, prefix: String? = null, keyboardType: KeyboardType = KeyboardType.Text, password: Boolean = false) {
    var passwordVisible by remember(label) { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        prefix = prefix?.let { { Text(it) } },
        singleLine = true,
        visualTransformation = if (password && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        trailingIcon = if (password) {
            { TextButton(onClick = { passwordVisible = !passwordVisible }) { Text(if (passwordVisible) "Hide" else "Show") } }
        } else null,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF238AF2),
            unfocusedBorderColor = Color.White.copy(alpha = .18f),
            focusedLabelColor = Color(0xFF65B6FF),
            unfocusedLabelColor = Color.White.copy(alpha = .6f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = Color(0xFF65B6FF)
        )
    )
}
