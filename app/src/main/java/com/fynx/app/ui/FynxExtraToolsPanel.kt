package com.fynx.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class FynxQuickNote(val id: Long, val text: String)

private data class FynxGuideArticle(
    val title: String,
    val summary: String,
    val body: String
)

private val fynxGuideArticles = listOf(
    FynxGuideArticle(
        "Welcome to FYNX",
        "A simple place to connect, share, discover, shop and grow.",
        "FYNX brings everyday social and business activities together in one place. Create your profile, connect with people, share moments, chat privately, discover products and explore businesses. Start with what matters to you and use more features as you need them."
    ),
    FynxGuideArticle(
        "How FYNX works",
        "Learn the basics before you explore.",
        "Your Home area is where you can share and discover content. Chats are for private conversations and groups. Friends helps you find and connect with people. Marketplace is for discovering and selling real products. Business is for professional activity and advertising. More contains additional tools and settings."
    ),
    FynxGuideArticle(
        "Sharing and Stories",
        "Share moments with the people you choose.",
        "You can share photos, videos, captions and other supported media. Stories are designed for moments you want people to see during their active period. Before posting, check that your content is accurate, respectful and yours to share."
    ),
    FynxGuideArticle(
        "Messages and Groups",
        "Stay connected through private conversations and group spaces.",
        "Use Chats to start conversations and share supported media. Groups let people communicate around a shared purpose. Remember that anything you send to another person or group may be seen by its members, so share carefully."
    ),
    FynxGuideArticle(
        "Marketplace and Buyer Protection",
        "Buy and sell with care.",
        "When shopping, review the product details, seller information, price, condition and delivery options before purchasing. FYNX is designed to support protected transactions and problem reporting. Never send money outside the purchase flow because someone asks you to. If an order has a problem, use the available order support or dispute options."
    ),
    FynxGuideArticle(
        "Business on FYNX",
        "Build a professional presence without leaving FYNX.",
        "A Business account can help you present your business, showcase products and manage business activity. Advertising tools are for promoting real products or services. Make sure claims, prices, offers and business information are accurate before publishing."
    ),
    FynxGuideArticle(
        "FYNX AI",
        "Use AI to help with ideas and writing while you stay in control.",
        "FYNX AI can help improve captions, organize ideas and create useful selling copy from information you provide. Review suggestions before sharing them. AI can make mistakes, so do not rely on it as proof of facts, prices, identity, safety or guarantees."
    ),
    FynxGuideArticle(
        "Sounds and Creative Sharing",
        "Create and share original sounds responsibly.",
        "If you share a sound, music or other creative work, make sure you have the right to use it. Do not upload copyrighted material just because you found it somewhere online. Give appropriate credit when required and respect requests to remove content when you do not have permission."
    ),
    FynxGuideArticle(
        "Stay Safe on FYNX",
        "A few simple habits can protect your account and your money.",
        "Never share your password, verification codes or sensitive payment information with another person. Be careful with unexpected links, urgent payment requests and offers that seem too good to be true. FYNX should not be used to scam, impersonate, threaten, harass or deceive people. Report suspicious activity when you see it."
    ),
    FynxGuideArticle(
        "Before You Post",
        "A quick check can prevent problems later.",
        "Make sure the content belongs to you or you have permission to share it. Check names, prices and important details. Avoid exposing another person's private information. Be respectful, do not deliberately mislead people, and remember that public content can travel beyond the audience you expected."
    )
)

@Composable
fun FynxExtraToolsPanel(onOpenCalendar: () -> Unit) {
    var notes by remember { mutableStateOf(listOf<FynxQuickNote>()) }
    var noteText by remember { mutableStateOf("") }
    var showNoteEditor by remember { mutableStateOf(false) }
    var openArticle by remember { mutableStateOf<FynxGuideArticle?>(null) }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Extra Tools", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Useful everyday tools and simple guides for using FYNX confidently.",
            color = FynxDesign.TextSecondary
        )

        Card(
            onClick = { showNoteEditor = true },
            modifier = Modifier.fillMaxWidth(),
            shape = FynxDesign.CardShape,
            colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface),
            border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = 0.5f))
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = FynxDesign.ControlShape, color = FynxDesign.SelectedContainer) {
                    Icon(Icons.Default.EditNote, "Quick Notes", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(9.dp).size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Quick Notes", style = MaterialTheme.typography.titleMedium)
                    Text("Capture a short note without leaving FYNX.", color = FynxDesign.TextSecondary)
                }
            }
        }

        Card(
            onClick = onOpenCalendar,
            modifier = Modifier.fillMaxWidth(),
            shape = FynxDesign.CardShape,
            colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface),
            border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = 0.5f))
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = FynxDesign.ControlShape, color = FynxDesign.SelectedContainer) {
                    Icon(Icons.Default.CalendarMonth, "Events", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(9.dp).size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Events", style = MaterialTheme.typography.titleMedium)
                    Text("Jump to event planning through Calendar.", color = FynxDesign.TextSecondary)
                }
            }
        }

        Text("About FYNX", style = MaterialTheme.typography.titleLarge)
        Text(
            "Read how FYNX works, what you can do here and how to stay safe.",
            color = FynxDesign.TextSecondary
        )
        fynxGuideArticles.forEach { article ->
            Card(
                onClick = { openArticle = article },
                modifier = Modifier.fillMaxWidth(),
                shape = FynxDesign.CardShape,
                colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface),
                border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = 0.5f))
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = FynxDesign.ControlShape, color = FynxDesign.SelectedContainer) {
                        Icon(Icons.Default.Info, "Guide", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(9.dp).size(22.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(article.title, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(3.dp))
                        Text(article.summary, color = FynxDesign.TextSecondary)
                    }
                }
            }
        }

        if (notes.isNotEmpty()) {
            Text("Recent notes", style = MaterialTheme.typography.titleMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(notes, key = { it.id }) { note ->
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = FynxDesign.CardShape,
                        colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface),
                        border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = 0.4f))
                    ) {
                        Text(note.text, Modifier.padding(16.dp))
                    }
                }
            }
        }
    }

    openArticle?.let { article ->
        AlertDialog(
            onDismissRequest = { openArticle = null },
            title = { Text(article.title) },
            text = { Text(article.body) },
            confirmButton = { TextButton(onClick = { openArticle = null }) { Text("Done") } }
        )
    }

    if (showNoteEditor) {
        AlertDialog(
            onDismissRequest = { showNoteEditor = false },
            title = { Text("Quick Note") },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = FynxDesign.ControlShape,
                    placeholder = { Text("Write a note…") }
                )
            },
            dismissButton = { TextButton(onClick = { showNoteEditor = false }) { Text("Cancel") } },
            confirmButton = {
                TextButton(
                    enabled = noteText.isNotBlank(),
                    onClick = {
                        notes = listOf(FynxQuickNote(System.currentTimeMillis(), noteText.trim())) + notes
                        noteText = ""
                        showNoteEditor = false
                    }
                ) { Text("Save") }
            }
        )
    }
}
