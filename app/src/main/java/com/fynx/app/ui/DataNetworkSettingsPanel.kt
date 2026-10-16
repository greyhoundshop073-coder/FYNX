package com.fynx.app.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp

private data class NetworkSnapshot(
    val hasNetwork: Boolean,
    val internetValidated: Boolean,
    val transport: String,
    val metered: String,
    val downKbps: Int,
    val upKbps: Int
)

@Composable
internal fun DataNetworkSettingsPanel(onBack: () -> Unit) {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }
    var snapshot by remember { mutableStateOf<NetworkSnapshot?>(null) }
    var backendStatus by remember { mutableStateOf("Not checked yet") }
    var backendDetail by remember { mutableStateOf("Check the live FYNX server and the authenticated API separately.") }
    var backendPassword by remember { mutableStateOf("") }
    var backendBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(refreshKey) { snapshot = readNetworkSnapshot(context) }

    fun checkBackend() {
        scope.launch {
            backendBusy = true
            val health = FynxBackendClient.health(context)
            if (health.isFailure) {
                backendStatus = "Server unreachable"
                backendDetail = health.exceptionOrNull()?.message ?: "The FYNX server health check failed."
            } else if (!FynxBackendClient.hasAccessToken(context)) {
                backendStatus = "Server reachable — account not connected"
                backendDetail = "The server responds, but this session has no access token. Connect your saved account below."
            } else {
                val identity = FynxBackendClient.get(context, "/api/me")
                if (identity.isSuccess) {
                    backendStatus = "Connected and authenticated"
                    backendDetail = "The server accepted this account token on the protected /api/me endpoint."
                } else if (FynxBackendClient.isUnauthorizedFailure(identity.exceptionOrNull()!!)) {
                    backendStatus = "Server reachable — token rejected"
                    backendDetail = "The server is online, but it rejected this session token. Reconnect the saved account below."
                } else {
                    backendStatus = "Server reachable — API check failed"
                    backendDetail = identity.exceptionOrNull()?.message ?: "The protected API check failed."
                }
            }
            backendBusy = false
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back to settings") }
            Column(Modifier.weight(1f)) {
                Text("Data & Network", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Current connection information", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { refreshKey++ }) { Icon(Icons.Default.Refresh, contentDescription = "Refresh network status") }
        }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .4f))) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Connection status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    snapshot?.let { if (it.hasNetwork) "Network available" else "No active network" } ?: "Checking…",
                    style = MaterialTheme.typography.headlineSmall
                )
                HorizontalDivider()
                NetworkDetailRow("Connection type", snapshot?.transport ?: "Checking…")
                NetworkDetailRow(
                    "Internet access",
                    snapshot?.let { when {
                        !it.hasNetwork -> "Not connected"
                        it.internetValidated -> "Verified by Android"
                        else -> "Not verified"
                    } } ?: "Checking…"
                )
                NetworkDetailRow("Data usage type", snapshot?.metered ?: "Checking…")
                NetworkDetailRow("Downstream estimate", snapshot?.let { if (it.downKbps > 0) "${it.downKbps} Kbps" else "Not reported" } ?: "Checking…")
                NetworkDetailRow("Upstream estimate", snapshot?.let { if (it.upKbps > 0) "${it.upKbps} Kbps" else "Not reported" } ?: "Checking…")
            }
        }
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .4f))
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("FYNX backend connection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(backendStatus, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(backendDetail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    onClick = { checkBackend() },
                    enabled = !backendBusy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (backendBusy) "Checking…" else "Check live backend") }

                HorizontalDivider()
                Text("Connect this saved account", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "This does not sign you out. Enter the account password once so FYNX can obtain a server token for feed, statuses, chat and Marketplace.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = backendPassword,
                    onValueChange = { backendPassword = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Account password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    enabled = !backendBusy
                )
                Button(
                    onClick = {
                        scope.launch {
                            backendBusy = true
                            val result = FynxRemoteAuthClient.connectSavedAccount(context, backendPassword)
                            result.onSuccess { account ->
                                FynxBackendClient.saveAccessToken(context, account.token)
                                FynxAuthStore.saveAccount(
                                    context,
                                    account.displayName.ifBlank { FynxAuthStore.storedDisplayName(context) ?: account.username },
                                    account.username,
                                    account.phone.ifBlank { FynxAuthStore.storedPhone(context).orEmpty() }
                                )
                                backendPassword = ""
                                val identity = FynxBackendClient.get(context, "/api/me")
                                if (identity.isSuccess) {
                                    backendStatus = "Connected and authenticated"
                                    backendDetail = "The protected /api/me endpoint accepted the account token. Feed, statuses, chat and Marketplace can now use authenticated requests."
                                } else {
                                    backendStatus = "Token received; protected API check failed"
                                    backendDetail = identity.exceptionOrNull()?.message ?: "The server did not accept the follow-up identity check."
                                }
                            }.onFailure { failure ->
                                backendStatus = "Connection not completed"
                                backendDetail = failure.message ?: "Could not authenticate this account with the FYNX server."
                            }
                            backendBusy = false
                        }
                    },
                    enabled = !backendBusy && backendPassword.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (backendBusy) "Connecting…" else "Connect account to backend") }
            }
        }

        Text(
            "Bandwidth values are Android estimates for the first network link, not measured speeds. “Verified by Android” means the system currently reports validated internet access; a particular FYNX service may still be unavailable.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NetworkDetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun readNetworkSnapshot(context: Context): NetworkSnapshot {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return NetworkSnapshot(false, false, "Unavailable", "Unknown", 0, 0)
    val network = manager.activeNetwork
        ?: return NetworkSnapshot(false, false, "Offline", "Unknown", 0, 0)
    val caps = manager.getNetworkCapabilities(network)
        ?: return NetworkSnapshot(false, false, "Unknown", "Unknown", 0, 0)

    val internetValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    val transport = when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        else -> "Other"
    }
    val metered = if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) "Unmetered" else "Metered or unknown"
    return NetworkSnapshot(
        hasNetwork = true,
        internetValidated = internetValidated,
        transport = transport,
        metered = metered,
        downKbps = caps.linkDownstreamBandwidthKbps,
        upKbps = caps.linkUpstreamBandwidthKbps
    )
}
