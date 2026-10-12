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

private data class NetworkSnapshot(val connected: Boolean, val transport: String, val quality: String, val downKbps: Int, val upKbps: Int)

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
                Text(snapshot?.let { if (it.connected) "Connected" else "No internet connection detected" } ?: "Checking…", style = MaterialTheme.typography.headlineSmall)
                HorizontalDivider()
                NetworkDetailRow("Connection type", snapshot?.transport ?: "Checking…")
                NetworkDetailRow("Quality estimate", snapshot?.quality ?: "Checking…")
                NetworkDetailRow("Downstream estimate", snapshot?.let { if (it.downKbps > 0) "${it.downKbps} Kbps" else "Not reported" } ?: "Checking…")
                NetworkDetailRow("Upstream estimate", snapshot?.let { if (it.upKbps > 0) "${it.upKbps} Kbps" else "Not reported" } ?: "Checking…")
            }
        }
        Text("Speed figures are Android network estimates, not a live speed test. A connection can be available while a particular service is temporarily unreachable.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        ?: return NetworkSnapshot(false, "Unavailable", "Unknown", 0, 0)
    val network = manager.activeNetwork ?: return NetworkSnapshot(false, "Offline", "Offline", 0, 0)
    val caps = manager.getNetworkCapabilities(network)
        ?: return NetworkSnapshot(false, "Unknown", "Unknown", 0, 0)
    val connected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    val transport = when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
        else -> "Other"
    }
    val down = caps.linkDownstreamBandwidthKbps
    val up = caps.linkUpstreamBandwidthKbps
    val quality = when {
        !connected -> "Offline"
        down <= 0 -> "Unknown"
        down < 1000 -> "Weak"
        else -> "Good"
    }
    return NetworkSnapshot(connected, transport, quality, down, up)
}
