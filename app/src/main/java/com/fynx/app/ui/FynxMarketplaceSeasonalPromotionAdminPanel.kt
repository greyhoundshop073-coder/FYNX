package com.fynx.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

/** Owner/Admin controls for variable-date holiday and seasonal Marketplace slides. */
@Composable
fun FynxMarketplaceSeasonalPromotionAdminPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var occasionKey by remember { mutableStateOf("") }
    var headline by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var actionLabel by remember { mutableStateOf("Explore Marketplace") }
    var artworkUri by remember { mutableStateOf<Uri?>(null) }
    var artworkMediaId by remember { mutableStateOf("") }
    val artworkPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            artworkUri = uri
            artworkMediaId = ""
            message = null
            error = null
        }
    }
    var startsAt by remember { mutableStateOf("") }
    var endsAt by remember { mutableStateOf("") }
    var campaigns by remember { mutableStateOf<List<FynxMarketplaceSeasonalPromotion>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            FynxMarketplaceSeasonalPromotionClient.listForAdmin(context)
                .onSuccess { campaigns = it }
                .onFailure { error = marketplaceSafeError(it, "Seasonal campaigns could not load.") }
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Marketplace seasonal promotions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Schedule variable-date celebrations such as Eid using the confirmed local dates for that year. This schedule is separate from paid seller advertising and does not affect Best Seller rankings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = occasionKey,
            onValueChange = { occasionKey = it.trim().lowercase().take(60); message = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Occasion key") },
            supportingText = { Text("Example: eid-al-fitr-2027") },
            singleLine = true,
            enabled = !saving
        )
        OutlinedTextField(
            value = headline,
            onValueChange = { headline = it.take(120); message = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Banner headline") },
            singleLine = true,
            enabled = !saving
        )
        OutlinedTextField(
            value = body,
            onValueChange = { body = it.take(500); message = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Celebration write-up") },
            minLines = 2,
            enabled = !saving
        )
        OutlinedTextField(
            value = actionLabel,
            onValueChange = { actionLabel = it.take(48); message = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Banner button text") },
            singleLine = true,
            enabled = !saving
        )
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Celebration artwork (optional)", style = MaterialTheme.typography.labelLarge)
            Text(
                "Choose a photo from the device. FYNX uploads it through its authenticated media service and displays it only while this scheduled campaign is active.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { artworkPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !saving
                ) { Text(if (artworkUri == null && artworkMediaId.isBlank()) "Choose photo" else "Change photo") }
                if (artworkUri != null || artworkMediaId.isNotBlank()) {
                    TextButton(onClick = { artworkUri = null; artworkMediaId = "" }, enabled = !saving) { Text("Remove photo") }
                }
            }
            when {
                artworkUri != null -> Text(
                    "Selected photo: ${artworkUri?.lastPathSegment ?: "image"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                artworkMediaId.isNotBlank() -> Text(
                    "Photo uploaded and ready to attach.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        OutlinedTextField(
            value = startsAt,
            onValueChange = { startsAt = it.take(40); message = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Starts at (ISO 8601)") },
            supportingText = { Text("Example: 2027-03-10T00:00:00+01:00 — Africa/Lagos time") },
            singleLine = true,
            enabled = !saving
        )
        OutlinedTextField(
            value = endsAt,
            onValueChange = { endsAt = it.take(40); message = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ends at (ISO 8601)") },
            supportingText = { Text("Use an explicit +01:00 offset for Nigeria. Maximum window: 45 days.") },
            singleLine = true,
            enabled = !saving
        )
        Button(
            onClick = {
                saving = true
                message = null
                error = null
                scope.launch {
                    var savedArtworkId = artworkMediaId
                    if (artworkUri != null && savedArtworkId.isBlank()) {
                        val uploaded = FynxMarketplaceSeasonalPromotionClient.uploadArtwork(context, artworkUri!!)
                        val mediaId = uploaded.getOrElse {
                            error = marketplaceSafeError(it, "Seasonal artwork upload failed.")
                            saving = false
                            return@launch
                        }
                        savedArtworkId = mediaId
                        artworkMediaId = mediaId
                    }
                    FynxMarketplaceSeasonalPromotionClient.create(
                        context, occasionKey, headline, body, actionLabel, savedArtworkId, startsAt, endsAt
                    ).onSuccess {
                        message = "Seasonal promotion scheduled. It will appear automatically inside its start/end window."
                        occasionKey = ""
                        headline = ""
                        body = ""
                        actionLabel = "Explore Marketplace"
                        artworkUri = null
                        artworkMediaId = ""
                        startsAt = ""
                        endsAt = ""
                        refresh()
                    }.onFailure {
                        error = marketplaceSafeError(it, "Seasonal promotion could not be saved.")
                    }
                    saving = false
                }
            },
            enabled = !saving && occasionKey.isNotBlank() && headline.trim().length >= 3 &&
                startsAt.isNotBlank() && endsAt.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (saving) CircularProgressIndicator() else Text("Schedule seasonal promotion")
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Scheduled campaigns", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { refresh() }, enabled = !loading && !saving) { Text("Refresh") }
        }
        if (loading && campaigns.isEmpty()) {
            CircularProgressIndicator()
        } else if (error != null && campaigns.isEmpty()) {
            Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { refresh() }) { Text("Retry") }
        } else if (campaigns.isEmpty()) {
            Text("No seasonal campaigns have been scheduled.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            campaigns.forEach { campaign ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(campaign.headline, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (campaign.active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            ) {
                                Text(
                                    if (campaign.active) "SCHEDULED" else "PAUSED",
                                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        Text(campaign.occasionKey, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        Text("Starts: ${campaign.startsAt}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Ends: ${campaign.endsAt}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        FynxMarketplaceSeasonalPromotionClient.setActive(context, campaign.id, !campaign.active)
                                            .onSuccess { refresh() }
                                            .onFailure { error = marketplaceSafeError(it, "Campaign status could not be changed.") }
                                    }
                                },
                                enabled = !saving && !loading
                            ) { Text(if (campaign.active) "Pause" else "Activate") }
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        FynxMarketplaceSeasonalPromotionClient.delete(context, campaign.id)
                                            .onSuccess { refresh() }
                                            .onFailure { error = marketplaceSafeError(it, "Campaign could not be deleted.") }
                                    }
                                },
                                enabled = !saving && !loading
                            ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
