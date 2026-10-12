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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    LaunchedEffect(refreshKey) { snapshot = readNetworkSnapshot(context) }

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
