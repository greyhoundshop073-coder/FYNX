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

private data class FynxProduct(val id: String, val name: String, val price: String, val category: String, val seller: String, val rating: String)

@Composable
fun FynxMarketplacePanel() {
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var cartCount by remember { mutableIntStateOf(0) }
    var selectedProduct by remember { mutableStateOf<FynxProduct?>(null) }
    val products = remember {
        listOf(
            FynxProduct("1", "Wireless Headphones", "₦45,000", "Electronics", "FYNX Tech Store", "4.8"),
            FynxProduct("2", "Smart Watch", "₦38,000", "Electronics", "Nova Gadgets", "4.7"),
            FynxProduct("3", "Classic Sneakers", "₦32,000", "Fashion", "Urban FYNX", "4.9"),
            FynxProduct("4", "Travel Backpack", "₦28,000", "Fashion", "Voyage Shop", "4.6"),
            FynxProduct("5", "Desk Lamp", "₦18,500", "Home", "HomeSpace", "4.8"),
            FynxProduct("6", "Phone Stand", "₦9,500", "Home", "FYNX Essentials", "4.5")
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
                    onClick = { selectedProduct = product },
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
                        Text(product.seller, style = MaterialTheme.typography.bodySmall, color = FynxDesign.TextSecondary, maxLines = 1)
                        Text("★ " + product.rating, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
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

    selectedProduct?.let { product ->
        AlertDialog(
            onDismissRequest = { selectedProduct = null },
            title = { Text(product.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(product.price, style = MaterialTheme.typography.titleLarge)
                    Text("Sold by " + product.seller, color = FynxDesign.TextSecondary)
                    Text("★ " + product.rating + " seller rating", color = MaterialTheme.colorScheme.primary)
                    Text("Category: " + product.category)
                }
            },
            confirmButton = {
                TextButton(onClick = { cartCount++; selectedProduct = null }) { Text("Add to cart") }
            },
            dismissButton = {
                TextButton(onClick = { selectedProduct = null }) { Text("Close") }
            }
        )
    }
}
