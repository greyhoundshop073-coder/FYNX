package com.fynx.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Marketplace reference-board primitives.
 *
 * These components intentionally contain presentation only. They do not create
 * Marketplace data, sellers, ratings, prices, or activity. The production
 * Marketplace remains the source of truth for all real data and actions.
 */
internal object FynxMarketplaceReferenceStyle {
    // Keep reference components on the app-wide design tokens instead of a
    // second, hard-coded palette. Theme-specific runtime colors are handled by
    // FynxTheme; these tokens preserve the established FYNX visual language.
    val background = FynxDesign.Background
    val surface = FynxDesign.Surface
    val surfaceRaised = FynxDesign.SurfaceRaised
    val outline = FynxDesign.Outline
    val primary = FynxAccent.Purple.primary
    val primarySoft = FynxAccent.Purple.secondary
    val text = FynxDesign.TextPrimary
    val textMuted = FynxDesign.TextSecondary
    val radius = RoundedCornerShape(18.dp)
}

@Composable
internal fun MarketplaceReferenceSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    onCart: () -> Unit,
    cartCount: Int,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            color = FynxMarketplaceReferenceStyle.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, FynxMarketplaceReferenceStyle.outline)
        ) {
            Row(Modifier.padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, tint = FynxMarketplaceReferenceStyle.textMuted, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isBlank()) {
                        Text("Search products, sellers or categories", color = FynxMarketplaceReferenceStyle.textMuted, maxLines = 1)
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = FynxMarketplaceReferenceStyle.text),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search products, sellers or categories" }
                    )
                }
                if (value.isNotBlank()) {
                    IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Clear Marketplace search", tint = FynxMarketplaceReferenceStyle.textMuted)
                    }
                }
            }
        }
        androidx.compose.material3.BadgedBox(badge = {
            if (cartCount > 0) androidx.compose.material3.Badge { Text(cartCount.toString()) }
        }) {
            IconButton(onClick = onCart) {
                Icon(Icons.Default.ShoppingCart, "Cart", tint = FynxMarketplaceReferenceStyle.text)
            }
        }
    }
}

@Composable
internal fun MarketplaceReferenceCategoryRow(
    categories: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onNearby: () -> Unit,
    nearbySelected: Boolean,
    nearbyLabel: String = "Near me",
    nearbyLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = nearbySelected,
            onClick = onNearby,
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (nearbyLoading) {
                        androidx.compose.material3.CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.LocationOn, null, Modifier.size(15.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(nearbyLabel)
                }
            }
        )
        categories.forEach { category ->
            FilterChip(selected = selected == category, onClick = { onSelect(category) }, label = { Text(category) })
        }
    }
}

@Composable
internal fun MarketplaceReferenceSectionTitle(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), color = FynxMarketplaceReferenceStyle.text, fontWeight = FontWeight.Bold)
        if (action != null && onAction != null) {
            androidx.compose.material3.TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text(action, color = FynxMarketplaceReferenceStyle.primarySoft)
            }
        }
    }
}

@Composable
internal fun MarketplaceReferenceHeroPlaceholder(
    title: String,
    subtitle: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = FynxMarketplaceReferenceStyle.surfaceRaised
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, color = FynxMarketplaceReferenceStyle.text, fontWeight = FontWeight.Bold)
                Text(subtitle, color = FynxMarketplaceReferenceStyle.textMuted)
            }
            Icon(Icons.Default.Add, null, tint = FynxMarketplaceReferenceStyle.primarySoft, modifier = Modifier.size(34.dp))
        }
    }
}

@Composable
internal fun MarketplaceReferenceEmptyState(
    title: String,
    message: String,
    onSell: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(FynxMarketplaceReferenceStyle.surfaceRaised), Alignment.Center) {
            Icon(Icons.Default.Add, null, tint = FynxMarketplaceReferenceStyle.primarySoft, modifier = Modifier.size(34.dp))
        }
        Text(title, color = FynxMarketplaceReferenceStyle.text, fontWeight = FontWeight.Bold)
        Text(message, color = FynxMarketplaceReferenceStyle.textMuted)
        androidx.compose.material3.Button(onClick = onSell) {
            Text("Sell something")
        }
    }
}
