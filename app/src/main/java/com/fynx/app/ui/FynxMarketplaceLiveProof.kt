package com.fynx.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

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
                onLiveProofStart = {
                    showCamera = false
                    onRequestProof(listing.id, listing.sellerUsername)
                },
                onDismiss = { showCamera = false }
            )
        }
        return
    }

    androidx.compose.material3.Surface(modifier = modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxSize().padding(16.dp)
        ) {
            androidx.compose.material3.Text("Live Proof", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            androidx.compose.material3.Text("Use the FYNX camera to prepare a live item demonstration with the seller.")
            androidx.compose.material3.Button(onClick = { showCamera = true }) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Videocam, null)
                androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
                androidx.compose.material3.Text("Open Live Proof Camera")
            }
        }
    }
}
