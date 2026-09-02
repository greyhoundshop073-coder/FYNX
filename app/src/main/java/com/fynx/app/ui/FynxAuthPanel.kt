package com.fynx.app.ui

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseTooManyRequestsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

enum class FynxAuthPage { WELCOME, REGISTER, VERIFY, LOGIN }

@Composable
fun FynxAuthGate(onAuthenticated: (String) -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    var page by remember { mutableStateOf(if (FynxAuthStore.hasAccount(context)) FynxAuthPage.LOGIN else FynxAuthPage.WELCOME) }
    var displayName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun completeAuthentication(credential: PhoneAuthCredential) {
        busy = true
        error = null
        FirebaseAuth.getInstance().signInWithCredential(credential)
            .addOnCompleteListener { task ->
                busy = false
                if (task.isSuccessful) {
                    FynxAuthStore.saveAccount(context, displayName.trim(), username.trim(), phone.trim())
                    onAuthenticated(username.trim())
                } else {
                    error = task.exception?.localizedMessage ?: "We could not verify that code. Please try again."
                }
            }
    }

    fun requestVerification(resend: Boolean = false) {
        val currentActivity = activity
        val normalizedPhone = normalizeFynxPhone(phone)
        error = when {
            currentActivity == null -> "FYNX could not access the phone verification screen. Please try again."
            normalizedPhone == null -> "Enter your phone number with the country code, for example +2348012345678."
            else -> null
        }
        if (error != null) return

        busy = true
        info = "Sending your verification code…"
        val auth = FirebaseAuth.getInstance()
        val builder = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(normalizedPhone!!)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(currentActivity!!)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    info = "Phone number verified automatically."
                    completeAuthentication(credential)
                }

                override fun onVerificationFailed(exception: FirebaseException) {
                    busy = false
                    info = null
                    error = when (exception) {
                        is FirebaseAuthInvalidCredentialsException -> "That phone number is not valid. Check the country code and try again."
                        is FirebaseTooManyRequestsException -> "Too many verification requests. Please wait a while before trying again."
                        else -> exception.localizedMessage ?: "We could not send the verification code. Please try again."
                    }
                }

                override fun onCodeSent(
                    sentVerificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken,
                ) {
                    verificationId = sentVerificationId
                    resendToken = token
                    busy = false
                    info = "A 6-digit verification code was sent by SMS."
                    code = ""
                    page = FynxAuthPage.VERIFY
                }
            })

        if (resend && resendToken != null) {
            builder.setForceResendingToken(resendToken!!)
        }

        PhoneAuthProvider.verifyPhoneNumber(builder.build())
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
                            FynxAuthPage.VERIFY -> "Verify your phone number"
                            FynxAuthPage.LOGIN -> "Welcome back"
                        },
                        color = FynxDesign.TextSecondary
                    )
                    Spacer(Modifier.height(22.dp))

                    when (page) {
                        FynxAuthPage.WELCOME -> {
                            Button(onClick = { error = null; info = null; page = FynxAuthPage.REGISTER }, Modifier.fillMaxWidth(), shape = FynxDesign.ControlShape) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null)
                                Spacer(Modifier.width(8.dp)); Text("Create account")
                            }
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(onClick = { error = null; info = null; page = FynxAuthPage.LOGIN }, Modifier.fillMaxWidth(), shape = FynxDesign.ControlShape) {
                                Icon(Icons.Default.Login, contentDescription = null)
                                Spacer(Modifier.width(8.dp)); Text("Log in")
                            }
                        }
                        FynxAuthPage.REGISTER -> {
                            FynxAuthField(displayName, { displayName = it }, "Display name")
                            Spacer(Modifier.height(10.dp))
                            FynxAuthField(username, { username = it.replace(" ", "").removePrefix("@") }, "Username", "@")
                            Spacer(Modifier.height(10.dp))
                            FynxAuthField(phone, { phone = it.filter { c -> c.isDigit() || c == '+' } }, "Phone number", keyboardType = KeyboardType.Phone)
                            Text("We'll send a one-time SMS code to verify this number.", color = FynxDesign.TextSecondary, style = MaterialTheme.typography.bodySmall)
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    error = when {
                                        displayName.trim().length < 2 -> "Enter your name."
                                        username.length < 3 -> "Username must be at least 3 characters."
                                        normalizeFynxPhone(phone) == null -> "Enter your phone number with the country code, for example +2348012345678."
                                        else -> null
                                    }
                                    if (error == null) requestVerification()
                                },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                                shape = FynxDesign.ControlShape
                            ) { Text(if (busy) "Sending code…" else "Continue") }
                            TextButton(enabled = !busy, onClick = { error = null; info = null; page = FynxAuthPage.WELCOME }) { Text("Back") }
                        }
                        FynxAuthPage.VERIFY -> {
                            Text("Enter the 6-digit verification code.", color = FynxDesign.TextSecondary)
                            Spacer(Modifier.height(10.dp))
                            FynxAuthField(code, { code = it.filter(Char::isDigit).take(6) }, "Verification code", keyboardType = KeyboardType.Number)
                            info?.let { Text(it, color = FynxDesign.TextSecondary, style = MaterialTheme.typography.bodySmall) }
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    val id = verificationId
                                    if (code.length != 6) {
                                        error = "Enter the 6-digit code."
                                    } else if (id.isNullOrBlank()) {
                                        error = "Your verification session expired. Please request a new code."
                                    } else {
                                        completeAuthentication(PhoneAuthProvider.getCredential(id, code))
                                    }
                                },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                                shape = FynxDesign.ControlShape
                            ) { Text(if (busy) "Verifying…" else "Verify and enter FYNX") }
                            TextButton(enabled = !busy, onClick = { requestVerification(resend = true) }) { Text("Resend code") }
                            TextButton(enabled = !busy, onClick = { error = null; info = null; page = FynxAuthPage.REGISTER }) { Text("Change phone number") }
                        }
                        FynxAuthPage.LOGIN -> {
                            FynxAuthField(username, { username = it.replace(" ", "").removePrefix("@") }, "Username", "@")
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                            Spacer(Modifier.height(14.dp))
                            Button(onClick = {
                                val saved = FynxAuthStore.storedUsername(context)
                                if (saved.isNullOrBlank()) {
                                    error = "No FYNX account is registered on this device."
                                } else if (!username.equals(saved, ignoreCase = true)) {
                                    error = "That username does not match the account on this device."
                                } else {
                                    error = null
                                    FynxAuthStore.save(context, saved)
                                    onAuthenticated(saved)
                                }
                            }, Modifier.fillMaxWidth(), shape = FynxDesign.ControlShape) { Text("Log in") }
                            TextButton(onClick = { error = null; info = null; page = FynxAuthPage.REGISTER }) { Text("Create a new account") }
                        }
                    }
                }
            }
        }
    }
}

private fun normalizeFynxPhone(raw: String): String? {
    val compact = raw.trim().replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
    if (compact.startsWith("+")) {
        return compact.takeIf { it.drop(1).all(Char::isDigit) && it.length in 9..16 }
    }
    if (compact.startsWith("00")) {
        val international = "+${compact.drop(2)}"
        return international.takeIf { it.drop(1).all(Char::isDigit) && it.length in 9..16 }
    }
    // Convenient support for Nigerian local mobile numbers while keeping E.164 for Firebase.
    if (compact.length == 11 && compact.startsWith("0") && compact.all(Char::isDigit)) {
        return "+234${compact.drop(1)}"
    }
    return null
}

@Composable
private fun FynxAuthField(value: String, onValueChange: (String) -> Unit, label: String, prefix: String? = null, keyboardType: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        prefix = prefix?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
        shape = FynxDesign.ControlShape
    )
}
