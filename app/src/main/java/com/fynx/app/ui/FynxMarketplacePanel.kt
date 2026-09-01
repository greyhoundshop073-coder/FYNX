package com.fynx.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class FynxProduct(val id: String, val name: String, val price: String, val category: String)

@Composable
fun FynxMarketplacePanel() {
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var cartCount by remember { mutableIntStateOf(0) }
    val products = remember {
        listOf(
            FynxProduct("1", "Wireless Headphones", "₦45,000", "Electronics"),
            FynxProduct("2", "Smart Watch", "₦38,000", "Electronics"),
            FynxProduct("3", "Classic Sneakers", "₦32,000", "Fashion"),
            FynxProduct("4", "Travel Backpack", "₦28,000", "Fashion"),
            FynxProduct("5", "Desk Lamp", "₦18,500", "Home"),
            FynxProduct("6", "Phone Stand", "₦9,500", "Home")
        )
    }
    val categories = listOf("All", "Electronics", "Fashion", "Home")
    val visible = products.filter { (selectedCategory == "All" || it.category == selectedCategory) && it.name.contains(query, true) }

    Column(Modifier.fillMaxSize().background(FynxDesign.Background).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Marketplace", style = MaterialTheme.typography.headlineSmall)
                Text("Discover products from FYNX sellers", color = FynxDesign.TextSecondary)
            }
            BadgedBox(badge = { if (cartCount > 0) Badge { Text(cartCount.toString()) } }) {
                IconButton(onClick = {}) { Icon(Icons.Default.ShoppingBag, "Cart") }
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            query,
            { query = it },
            Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, "Search") },
            placeholder = { Text("Search products…") },
            shape = FynxDesign.ControlShape
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    label = { Text(category) },
                    shape = FynxDesign.ControlShape
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(visible, key = { it.id }) { product ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = FynxDesign.CardShape,
                    colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface),
                    border = BorderStroke(1.dp, FynxDesign.Outline)
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Box(
                            Modifier.fillMaxWidth().height(120.dp).background(FynxDesign.SurfaceRaised, FynxDesign.ControlShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ShoppingBag, "Product", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(38.dp))
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(product.name, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                        Text(product.category, style = MaterialTheme.typography.bodySmall, color = FynxDesign.TextSecondary)
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(product.price, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = { cartCount++ }) { Icon(Icons.Default.AddShoppingCart, "Add to cart") }
                        }
                    }
                }
            }
        }
    }
}
