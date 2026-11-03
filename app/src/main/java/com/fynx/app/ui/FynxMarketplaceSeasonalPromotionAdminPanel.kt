package com.fynx.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private fun marketplaceSeasonalTimeZone(): TimeZone = TimeZone.getTimeZone("Africa/Lagos")

private fun marketplaceSeasonalDateFormat(pattern: String) =
    SimpleDateFormat(pattern, Locale.US).apply {
        timeZone = marketplaceSeasonalTimeZone()
        isLenient = false
    }

private fun parseMarketplaceSeasonalDateTime(value: String): Date? =
    runCatching { marketplaceSeasonalDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").parse(value.trim()) }
        .getOrNull()

private fun formatMarketplaceSeasonalDateTime(value: String): String =
    parseMarketplaceSeasonalDateTime(value)?.let {
        marketplaceSeasonalDateFormat("EEE, dd MMM yyyy  ·  HH:mm").format(it) + " WAT"
    } ?: "Choose date and time"

private fun marketplaceSeasonalWindowIsValid(startsAt: String, endsAt: String): Boolean {
    val start = parseMarketplaceSeasonalDateTime(startsAt) ?: return false
    val end = parseMarketplaceSeasonalDateTime(endsAt) ?: return false
    val duration = end.time - start.time
    return duration > 0L && duration <= 45L * 24L * 60L * 60L * 1000L
}

private fun showMarketplaceSeasonalDateTimePicker(
    context: Context,
    currentValue: String,
    onSelected: (String) -> Unit
) {
    val zone = marketplaceSeasonalTimeZone()
    val calendar = Calendar.getInstance(zone, Locale.US)
    parseMarketplaceSeasonalDateTime(currentValue)?.let { calendar.time = it }
    DatePickerDialog(
        context,
        { _, year, month, day ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, day)
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    calendar.set(Calendar.HOUR_OF_DAY, hour)
                    calendar.set(Calendar.MINUTE, minute)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    onSelected(marketplaceSeasonalDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").format(calendar.time))
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
            ).show()
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    ).show()
}

/** Owner/Admin controls for variable-date holiday and seasonal Marketplace slides. */
@Composable
fun FynxMarketplaceSeasonalPromotionAdminPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var promotionType by remember { mutableStateOf("SEASONAL") }
    var occasionKey by remember { mutableStateOf("") }
    var headline by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var actionLabel by remember { mutableStateOf("Explore Marketplace") }
    var destinationType by remember { mutableStateOf("MARKETPLACE") }
    var destinationValue by remember { mutableStateOf("") }
    var artworkUri by remember { mutableStateOf<Uri?>(null) }
    var artworkMediaId by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
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
        Text("Marketplace campaigns", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Schedule FYNX announcements, special offers and celebrations. Use confirmed local dates for variable-date events such as Eid. Campaigns are independent of paid seller advertising and Best Seller rankings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("Campaign type", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("SEASONAL", "ANNOUNCEMENT", "OFFER").forEach { type ->
                val label = when (type) {
                    "ANNOUNCEMENT" -> "Announcement"
                    "OFFER" -> "Special offer"
                    else -> "Seasonal"
                }
                if (promotionType == type) {
                    Button(onClick = { promotionType = type }, modifier = Modifier.weight(1f), enabled = !saving) {
                        Text(label, maxLines = 1)
                    }
                } else {
                    OutlinedButton(onClick = { promotionType = type; message = null; error = null }, modifier = Modifier.weight(1f), enabled = !saving) {
                        Text(label, maxLines = 1)
                    }
                }
            }
        }
        OutlinedTextField(
            value = occasionKey,
            onValueChange = { occasionKey = it.trim().lowercase().take(60); message = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Campaign key") },
            supportingText = { Text("Example: eid-al-fitr-2027, fynx-update-2026 or summer-offer") },
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
            label = { Text(when (promotionType) { "ANNOUNCEMENT" -> "Announcement details"; "OFFER" -> "Special offer details"; else -> "Celebration write-up" }) },
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
        Text("Button destination", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Choose where the banner opens. Product IDs are checked against active, in-stock listings before the campaign is saved.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("MARKETPLACE", "CATEGORY", "PRODUCT", "SHOP").forEach { type ->
                val label = when (type) {
                    "CATEGORY" -> "Category"
                    "PRODUCT" -> "Product"
                    "SHOP" -> "Shop"
                    else -> "Marketplace"
                }
                if (destinationType == type) {
                    Button(onClick = { destinationType = type; destinationValue = ""; message = null; error = null }, modifier = Modifier.weight(1f), enabled = !saving) {
                        Text(label, maxLines = 1)
                    }
                } else {
                    OutlinedButton(onClick = { destinationType = type; destinationValue = ""; message = null; error = null }, modifier = Modifier.weight(1f), enabled = !saving) {
                        Text(label, maxLines = 1)
                    }
                }
            }
        }
        if (destinationType != "MARKETPLACE") {
            OutlinedTextField(
                value = destinationValue,
                onValueChange = {
                    destinationValue = when (destinationType) {
                        "PRODUCT" -> it.filter(Char::isDigit).take(20)
                        "SHOP" -> it.trim().removePrefix("@").lowercase().filter { char -> char.isLetterOrDigit() || char == '_' }.take(32)
                        else -> it.take(60)
                    }
                    message = null
                    error = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(when (destinationType) {
                    "CATEGORY" -> "Marketplace category"
                    "PRODUCT" -> "Product listing ID"
                    else -> "Seller username"
                }) },
                supportingText = { Text(when (destinationType) {
                    "CATEGORY" -> "Use the same category name shown in Marketplace, for example Electronics or Fashion."
                    "PRODUCT" -> "Paste the numeric listing ID from the product record."
                    else -> "Enter the seller's FYNX username, without @."
                }) },
                singleLine = true,
                enabled = !saving
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Campaign artwork (optional)", style = MaterialTheme.typography.labelLarge)
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
        Text("Campaign timing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Choose the confirmed local date and time. FYNX publishes and expires this campaign automatically using Nigeria time (WAT, UTC+01:00).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            onClick = {
                showMarketplaceSeasonalDateTimePicker(context, startsAt) {
                    startsAt = it
                    message = null
                    error = null
                    if (endsAt.isBlank()) endsAt = it
                }
            },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("STARTS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(formatMarketplaceSeasonalDateTime(startsAt), fontWeight = FontWeight.SemiBold)
            }
        }
        OutlinedButton(
            onClick = {
                showMarketplaceSeasonalDateTimePicker(context, endsAt.ifBlank { startsAt }) {
                    endsAt = it
                    message = null
                    error = null
                }
            },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("ENDS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(formatMarketplaceSeasonalDateTime(endsAt), fontWeight = FontWeight.SemiBold)
            }
        }
        if (startsAt.isNotBlank() && endsAt.isNotBlank() && !marketplaceSeasonalWindowIsValid(startsAt, endsAt)) {
            Text(
                "End time must be after the start time. A campaign can run for a maximum of 45 days.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
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
                        context, promotionType, occasionKey, headline, body, actionLabel,
                        destinationType, destinationValue, savedArtworkId, startsAt, endsAt
                    ).onSuccess {
                        message = "${promotionType.lowercase().replaceFirstChar { it.uppercase() }} campaign scheduled. It will appear automatically inside its start/end window."
                        occasionKey = ""
                        headline = ""
                        body = ""
                        actionLabel = "Explore Marketplace"
                        destinationType = "MARKETPLACE"
                        destinationValue = ""
                        artworkUri = null
                        artworkMediaId = ""
                        startsAt = ""
                        endsAt = ""
                        refresh()
                    }.onFailure {
                        error = marketplaceSafeError(it, "Marketplace campaign could not be saved.")
                    }
                    saving = false
                }
            },
            enabled = !saving && occasionKey.isNotBlank() && headline.trim().length >= 3 &&
                (destinationType == "MARKETPLACE" || destinationValue.isNotBlank()) &&
                (destinationType != "PRODUCT" || destinationValue.toLongOrNull()?.let { it > 0L } == true) &&
                (destinationType != "SHOP" || destinationValue.matches(Regex("[a-z0-9_]{3,32}"))) &&
                marketplaceSeasonalWindowIsValid(startsAt, endsAt),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (saving) CircularProgressIndicator() else Text("Schedule campaign")
        }
        message?.let { Text(it, color = FynxDesign.ClassicGreen, style = MaterialTheme.typography.bodySmall) }
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
            Text("No Marketplace campaigns have been scheduled.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            campaigns.forEach { campaign ->
                val now = System.currentTimeMillis()
                val startDate = parseMarketplaceSeasonalDateTime(campaign.startsAt)
                val endDate = parseMarketplaceSeasonalDateTime(campaign.endsAt)
                val campaignStatus = when {
                    !campaign.active -> "PAUSED"
                    startDate == null || endDate == null -> "CHECK DATES"
                    now < startDate.time -> "SCHEDULED"
                    now >= endDate.time -> "ENDED"
                    else -> "LIVE"
                }
                val campaignStatusColor = when (campaignStatus) {
                    "LIVE" -> FynxDesign.ClassicGreen.copy(alpha = 0.16f)
                    "ENDED" -> MaterialTheme.colorScheme.surfaceVariant
                    "PAUSED", "CHECK DATES" -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.primaryContainer
                }
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
                                color = campaignStatusColor
                            ) {
                                Text(
                                    campaignStatus,
                                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        Text("${campaign.promotionType}  •  ${campaign.occasionKey}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        Text(
                            "Destination: ${campaign.destinationType}" +
                                campaign.destinationValue.takeIf { it.isNotBlank() }?.let { "  •  $it" }.orEmpty(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text("Starts: ${formatMarketplaceSeasonalDateTime(campaign.startsAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Ends: ${formatMarketplaceSeasonalDateTime(campaign.endsAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
