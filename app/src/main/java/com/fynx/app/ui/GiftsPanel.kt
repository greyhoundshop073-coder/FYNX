package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.UUID

data class FynxGift(
    val id: String,
    val name: String,
    val emoji: String,
    val description: String,
    val value: Int,
    val rarity: String
)

private val fynxGiftCatalog = listOf(
    FynxGift("fynx_flower", "FYNX Flower", "🌸", "A gentle gesture of friendship", 5, "Common"),
    FynxGift("coffee", "Coffee", "☕", "A warm virtual treat", 10, "Common"),
    FynxGift("love_heart", "Love Heart", "❤️", "Send a little love", 25, "Common"),
    FynxGift("fynx_star", "FYNX Star", "⭐", "Celebrate someone special", 50, "Uncommon"),
    FynxGift("butterfly", "Butterfly", "🦋", "A bright gift for a beautiful moment", 75, "Uncommon"),
    FynxGift("golden_rose", "Golden Rose", "🌹", "A special FYNX gesture of admiration", 100, "Rare"),
    FynxGift("fire_heart", "Fire Heart", "🔥", "Show bold appreciation", 250, "Rare"),
    FynxGift("mystery_box", "Mystery Gift", "🎁", "A surprise for someone special", 500, "Epic"),
    FynxGift("trophy", "Trophy", "🏆", "Celebrate an achievement", 750, "Epic"),
    FynxGift("royal_ring", "Royal Ring", "💍", "A timeless gift for someone special", 1000, "Legendary"),
    FynxGift("crown", "Crown", "👑", "Celebrate someone who stands out", 2500, "Legendary"),
    FynxGift("fynx_diamond", "FYNX Diamond", "💎", "A premium FYNX symbol of appreciation", 5000, "Ultra"),
    FynxGift("fynx_galaxy", "FYNX Galaxy", "💫", "A legendary FYNX gift", 10000, "Ultra")
)

private fun findFynxGift(id: String): FynxGift? = fynxGiftCatalog.firstOrNull { it.id == id }

@Composable
fun GiftsPanel(
    recipientName: String? = null,
    onGiftSelected: (FynxGift) -> Unit = {}
) {
    val context = LocalContext.current
    val authUsername = FynxAuthStore.storedUsername(context)?.let { if (it.startsWith("@")) it else "@$it" }
    val actualProfiles = remember(context) {
        FynxFriendsStore(context).load().filterNot { it.username.equals(authUsername, ignoreCase = true) }
    }
    val initialRecipient = remember(recipientName, actualProfiles) {
        actualProfiles.firstOrNull {
            it.displayName.equals(recipientName, ignoreCase = true) ||
                it.username.equals(recipientName, ignoreCase = true)
        }
    }
    var selectedRecipient by remember { mutableStateOf(initialRecipient) }
    var selectedGift by remember { mutableStateOf<FynxGift?>(null) }
    var confirmationOpen by remember { mutableStateOf(false) }
    var preparedTransfer by remember { mutableStateOf<FynxGiftTransfer?>(null) }
    var historyVersion by remember { mutableIntStateOf(0) }
    var historyTab by remember { mutableStateOf("Sent") }
    val historyStore = remember(context) { FynxGiftHistoryStore(context, ::findFynxGift) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🎁", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Send a gift", style = MaterialTheme.typography.titleLarge)
                Text(
                    selectedRecipient?.let { "To ${it.displayName}" } ?: "Choose a recipient",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
