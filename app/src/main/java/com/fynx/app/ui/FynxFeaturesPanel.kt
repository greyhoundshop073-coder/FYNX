package com.fynx.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private data class FynxFeatureCardSpec(
    val route: String,
    val title: String,
    val description: String,
    val keywords: String,
    val icon: ImageVector,
    val iconColor: Color,
    val section: String,
    val roleLabel: String? = null
)

private data class FynxFeatureSection(
    val title: String,
    val cards: List<FynxFeatureCardSpec>
)

/**
 * The More directory is intentionally a local registry: rendering it does not
 * fetch data, initialize feature screens, or start background requests.
 *
 * Entries that duplicate a primary navigation destination or an existing
 * parent-screen shortcut are intentionally not repeated here:
 * - Marketplace is already a permanent bottom-navigation destination.
 * - Notifications and Privacy & Safety are in Profile > Settings.
 * - Saved Products and Seller Center / My Shop are reached from Marketplace.
 * - Calendar is represented once here; Extra Tools no longer repeats it as Events.
 */
private fun fynxMoreSections(adminRole: String?): List<FynxFeatureSection> {
    val authorizedRole = adminRole?.trim()?.uppercase()
        ?.takeIf { it == "OWNER" || it == "ADMIN" }

    val communication = listOf(
        FynxFeatureCardSpec(
            "Calls", "Voice & Video Calls", "Start or manage your FYNX calls",
            "voice video audio private group call", Icons.Default.Call, Color(0xFF0B9D83), "COMMUNICATION & COMMUNITY"
        ),
        FynxFeatureCardSpec(
            "Gifts", "Gifts", "Send a gift and view your gift activity",
            "send gift presents friends", Icons.Default.CardGiftcard, Color(0xFFE58A18), "COMMUNICATION & COMMUNITY"
        ),
        FynxFeatureCardSpec(
            "Share", "Share & Invite", "Invite people or share FYNX",
            "invite friends copy share app", Icons.Default.Share, Color(0xFF2878D0), "COMMUNICATION & COMMUNITY"
        ),
        FynxFeatureCardSpec(
            "Announcements", "Official Announcements", "Read news and updates from FYNX",
            "official news updates notices", Icons.Default.Campaign, Color(0xFFD52B65), "COMMUNICATION & COMMUNITY"
        )
    )

    val personal = listOf(
        FynxFeatureCardSpec(
            "To-Do", "To-Do", "Organize tasks and reminders",
            "tasks checklist reminders", Icons.Default.CheckCircle, Color(0xFF7B55D9), "PERSONAL TOOLS"
        ),
        FynxFeatureCardSpec(
            "Calendar", "Calendar", "Plan events and keep track of dates",
            "events schedule dates appointments", Icons.Default.CalendarMonth, Color(0xFF168BC6), "PERSONAL TOOLS"
        ),
        FynxFeatureCardSpec(
            "Extra Tools", "Extra Tools", "Quick notes and helpful FYNX guides",
            "notes guides information", Icons.Default.Build, Color(0xFFB45BC8), "PERSONAL TOOLS"
        ),
        FynxFeatureCardSpec(
            "Saved Posts", "Saved Posts", "Return to posts you have bookmarked",
            "bookmarks saved feed posts", Icons.Default.Bookmark, Color(0xFFB43A73), "PERSONAL TOOLS"
        )
    )

    val money = listOf(
        FynxFeatureCardSpec(
            "Wallet", "FYNX Wallet", "Your wallet and payment activity",
            "wallet balance payments transactions", Icons.Default.AccountBalanceWallet, Color(0xFF078E6A), "WALLET & MONEY"
        ),
        FynxFeatureCardSpec(
            "Money Tools", "Money Center", "Budgets, savings and personal money records",
            "budget savings bills subscriptions receipts expenses", Icons.Default.AccountBalanceWallet, Color(0xFF2D83B8), "WALLET & MONEY"
        )
    )

    val business = listOf(
        FynxFeatureCardSpec(
            "Business Account", "Business & Creator Profile", "Manage your professional identity",
            "business creator profile professional", Icons.Default.Storefront, Color(0xFF0F9B9B), "BUSINESS & GROWTH"
        ),
        FynxFeatureCardSpec(
            "Advertising", "Advertising", "Create and manage promotional campaigns",
            "ads campaign promotion advertise", Icons.Default.Campaign, Color(0xFFDE6B26), "BUSINESS & GROWTH"
        ),
        FynxFeatureCardSpec(
            "Advertising Dashboard", "Advertising Analytics", "Review campaign performance",
            "analytics metrics advertising results", Icons.Default.Analytics, Color(0xFF4169C8), "BUSINESS & GROWTH"
        )
    )

    val ai = listOf(
        FynxFeatureCardSpec(
            "AI", "FYNX AI Assistant", "Get help with ideas, writing and tasks",
            "artificial intelligence assistant chat", Icons.Default.AutoAwesome, Color(0xFF8D5ACF), "AI & ASSISTANCE"
        )
    )

    val sections = mutableListOf(
        FynxFeatureSection("COMMUNICATION & COMMUNITY", communication),
        FynxFeatureSection("PERSONAL TOOLS", personal),
        FynxFeatureSection("WALLET & MONEY", money),
        FynxFeatureSection("BUSINESS & GROWTH", business),
        FynxFeatureSection("AI & ASSISTANCE", ai)
    )

    if (authorizedRole != null) {
        sections += FynxFeatureSection(
            "OWNER & ADMIN",
            listOf(
                FynxFeatureCardSpec(
                    "Admin", "Owner / Admin Control Center",
                    "Manage FYNX operations and authorized tools",
                    "owner admin control center management", Icons.Default.AdminPanelSettings,
                    Color(0xFFB88622), "OWNER & ADMIN", authorizedRole
                )
            )
        )
    }
    return sections
}

@Composable
fun FynxFeaturesPanel(
    adminRole: String?,
    onSelect: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val sections = remember(adminRole) { fynxMoreSections(adminRole) }
    val normalizedQuery = query.trim()
    val visibleSections = remember(sections, normalizedQuery) {
        if (normalizedQuery.isBlank()) sections
        else sections.mapNotNull { section ->
            val matchingCards = section.cards.filter { card ->
                card.title.contains(normalizedQuery, ignoreCase = true) ||
                    card.description.contains(normalizedQuery, ignoreCase = true) ||
                    card.keywords.contains(normalizedQuery, ignoreCase = true)
            }
            matchingCards.takeIf { it.isNotEmpty() }?.let { section.copy(cards = it) }
        }
    }
    val gridState = rememberLazyGridState()

    Column(
        Modifier.fillMaxSize().padding(horizontal = 12.dp)
    ) {
        Text(
            "FYNX Features",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Access your tools in one place. Choose a feature to get started.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(10.dp))
        FynxFeatureSearchField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.testTag("fynx-features-search")
        )
        Spacer(Modifier.height(8.dp))

        if (visibleSections.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().weight(1f).testTag("fynx-features-empty"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No FYNX tools match “$normalizedQuery”.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                modifier = Modifier.fillMaxSize().testTag("fynx-features-grid"),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(
                    top = 2.dp,
                    bottom = LocalFynxBottomBarSpace.current + 12.dp
                )
            ) {
                visibleSections.forEach { section ->
                    item(
                        key = "section-${section.title}",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = "section-header"
                    ) {
                        Text(
                            section.title,
                            modifier = Modifier.fillMaxWidth()
                                .padding(top = 10.dp, bottom = 1.dp)
                                .testTag("fynx-features-section-${section.title.lowercase().replace(' ', '-').replace('&', 'and')}"),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(
                        items = section.cards,
                        key = { it.route },
                        span = { GridItemSpan(1) },
                        contentType = { "feature-card" }
                    ) { feature ->
                        FynxFeatureCard(feature = feature, onClick = { onSelect(feature.route) })
                    }
                }
            }
        }
    }
}

@Composable
private fun FynxFeatureCard(
    feature: FynxFeatureCardSpec,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = 158.dp)
            .testTag("fynx-feature-card-${feature.route}")
            .semantics {
                contentDescription = "${feature.title}. ${feature.description}"
            },
        shape = RoundedCornerShape(21.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.62f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = feature.iconColor,
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = feature.icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(23.dp)
                        )
                    }
                }
                if (feature.roleLabel != null) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        color = feature.iconColor.copy(alpha = 0.14f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            feature.roleLabel,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
            Text(
                feature.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                feature.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
