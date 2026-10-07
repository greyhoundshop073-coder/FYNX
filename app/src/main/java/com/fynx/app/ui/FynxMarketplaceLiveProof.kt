package com.fynx.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.*

@Composable
internal fun FynxMarketplaceLiveProof(
    listing: FynxRemoteSocialClient.MarketplaceListing,
    onRequestProof: (listingId: String, sellerUsername: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCamera by remember { mutableStateOf(false) }

    if (showCamera) {
        Dialog(
            onDismissRequest = { showCamera = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            FynxCameraCapturePanel(
                liveProofMode = true,
                liveProofListingTitle = listing.title,
                liveProofSellerUsername = listing.sellerUsername,
                onCaptured = { _, _ -> },
                onLiveProofStart = {
                    showCamera = false
                    onRequestProof(listing.id, listing.sellerUsername)
                },
                onDismiss = { showCamera = false }
            )
        }
        return
    }

    Surface(modifier = modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxSize().padding(16.dp)
        ) {
            Text("Live Proof", style = MaterialTheme.typography.titleMedium)
            Text("Use the FYNX camera to prepare a live item demonstration with the seller.")
            Button(onClick = { showCamera = true }) {
                Icon(Icons.Default.Videocam, null)
                androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
                androidx.compose.material3.Text("Open Live Proof Camera")
            }
        }
    }
}
