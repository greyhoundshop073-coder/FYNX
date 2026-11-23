package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject

private data class FynxAccountEmailSnapshot(
    val email: String,
    val verified: Boolean,
    val pendingVerification: Boolean = false
)

private data class FynxEmailAttachResult(
    val email: String,
    val verificationSent: Boolean,
    val pendingVerification: Boolean,
    val unchanged: Boolean
)

private object FynxAccountEmailRemoteClient {
    suspend fun load(context: android.content.Context): Result<FynxAccountEmailSnapshot> =
        FynxBackendClient.get(context, "/api/auth/email/status").mapCatching { body ->
            val json = JSONObject(body)
            FynxAccountEmailSnapshot(
                email = json.optString("email", ""),
                verified = json.optBoolean("emailVerified", false),
                pendingVerification = json.optBoolean("pendingEmail", false)
            )
        }

    suspend fun attach(
        context: android.content.Context,
        email: String,
        password: String
    ): Result<FynxEmailAttachResult> =
        FynxBackendClient.postJson(
            context,
            "/api/auth/email/attach",
            JSONObject().put("email", email).put("password", password).toString()
        ).mapCatching { body ->
            val json = JSONObject(body)
            FynxEmailAttachResult(
                email = json.optString("email", email),
                verificationSent = json.optBoolean("emailVerificationSent", false),
                pendingVerification = json.optBoolean("pendingEmailVerification", false),
                unchanged = json.optBoolean("unchanged", false)
            )
        }

    suspend fun resend(context: android.content.Context): Result<Unit> =
        FynxBackendClient.postJson(context, "/api/auth/email/resend", "{}").mapCatching { Unit }

    suspend fun verify(context: android.content.Context, email: String, code: String): Result<Unit> =
        FynxBackendClient.postJson(
            context,
            "/api/auth/email/verify",
            JSONObject().put("email", email).put("code", code).toString()
        ).mapCatching { body ->
            val json = JSONObject(body)
            if (!json.optBoolean("emailVerified", true) && !json.optBoolean("ok", false)) {
                throw IllegalStateException("FYNX did not confirm the email address.")
            }
            Unit
        }
}

@Composable
internal fun AccountEmailSettingsPanel(
    phoneNumber: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<FynxAccountEmailSnapshot?>(null) }
    var emailInput by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            FynxAccountEmailRemoteClient.load(context)
                .onSuccess { snapshot = it }
                .onFailure { error = it.message ?: "Email settings could not be loaded." }
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(
        Modifier.fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, enabled = !busy) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back to Account & Profile")
            }
            Column(Modifier.weight(1f)) {
                Text("Phone & Email", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Manage your email address and verification",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Phone number", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        phoneNumber.ifBlank { "No phone number is saved on this device." },
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "Your phone number is shown from the existing local account record. It is not changed here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Email address", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (loading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Loading the account email status…", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text(
                            snapshot?.email?.takeIf(String::isNotBlank) ?: "No email address is connected",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        if (snapshot != null && snapshot!!.email.isNotBlank()) {
                            Text(
                                if (snapshot!!.verified) "Verified" else if (snapshot!!.pendingVerification) "Pending verification" else "Not verified",
                                color = if (snapshot!!.verified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                    if (snapshot?.pendingVerification == true) {
                        Text(
                            "Your current verified email is still active for password recovery. It changes only after you verify the new address.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(
                        onClick = { refresh() },
                        enabled = !loading && !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Refresh email status") }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (snapshot?.email.isNullOrBlank()) "Add an email address" else "Add or change email",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it.take(254); error = null; notice = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Email address") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        enabled = !busy
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; error = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Current FYNX password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        enabled = !busy
                    )
                    Text(
                        "FYNX checks your current password before changing the email on this account. Your existing login method is unchanged.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                busy = true
                                error = null
                                notice = null
                                FynxAccountEmailRemoteClient.attach(context, emailInput.trim(), password)
                                    .onSuccess { result ->
                                        password = ""
                                        verificationCode = ""
                                        if (result.unchanged) {
                                            snapshot = FynxAccountEmailSnapshot(result.email, true)
                                            notice = "This email address is already verified. No changes were made."
                                        } else {
                                            snapshot = FynxAccountEmailSnapshot(result.email, false, result.pendingVerification)
                                            emailInput = ""
                                            notice = if (result.verificationSent && result.pendingVerification) {
                                                "A verification code was sent to ${result.email}. Your existing verified email remains active for account recovery until you confirm this new address."
                                            } else if (result.verificationSent) {
                                                "A verification code was sent to ${result.email}. Check your inbox."
                                            } else {
                                                "FYNX could not send the verification email. Your previous verified email remains active. Try again later."
                                            }
                                        }
                                    }
                                    .onFailure { error = it.message ?: "The email address could not be saved." }
                                busy = false
                            }
                        },
                        enabled = !busy && !loading && emailInput.trim().contains("@") && password.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (busy) "Saving…" else "Save email address") }
                }
            }

            if (snapshot?.email?.isNotBlank() == true && snapshot?.verified == false) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Verify your email", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Enter the six-digit code sent to ${snapshot?.email}. Codes expire after 15 minutes and can only be used once.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = verificationCode,
                            onValueChange = { verificationCode = it.filter(Char::isDigit).take(6); error = null },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Verification code") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            enabled = !busy
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    busy = true
                                    error = null
                                    FynxAccountEmailRemoteClient.verify(context, snapshot?.email.orEmpty(), verificationCode)
                                        .onSuccess {
                                            snapshot = snapshot?.copy(verified = true, pendingVerification = false)
                                            verificationCode = ""
                                            notice = "Your email address is verified."
                                        }
                                        .onFailure { error = it.message ?: "The code is invalid or expired." }
                                    busy = false
                                }
                            },
                            enabled = !busy && verificationCode.length == 6,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Verify email") }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    busy = true
                                    error = null
                                    FynxAccountEmailRemoteClient.resend(context)
                                        .onSuccess { notice = "A new verification email was requested. Check your inbox." }
                                        .onFailure { error = it.message ?: "The verification email could not be sent." }
                                    busy = false
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Resend verification email") }
                    }
                }
            }

            notice?.let {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Text(it, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(
                "Password recovery remains available from the existing Sign in screen. This page manages the contact email only; it does not replace or change your login flow.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
