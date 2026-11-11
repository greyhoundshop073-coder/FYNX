package com.fynx.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

data class FynxBusinessDirectoryItem(
    val id: String,
    val businessName: String,
    val username: String,
    val category: String,
    val description: String,
    val location: String,
    val phone: String,
    val website: String,
    val verified: Boolean
)

object FynxBusinessDirectoryClient {
    suspend fun search(
        context: Context,
        query: String = "",
        category: String = "",
        location: String = "",
        limit: Int = 30,
        offset: Int = 0
    ): Result<Pair<List<FynxBusinessDirectoryItem>, Boolean>> {
        val url = buildString {
            append("/api/business/directory?limit=${limit.coerceIn(1, 50)}&offset=${offset.coerceAtLeast(0)}")
            if (query.isNotBlank()) append("&query=${Uri.encode(query.trim().take(80))}")
            if (category.isNotBlank()) append("&category=${Uri.encode(category.trim().take(60))}")
            if (location.isNotBlank()) append("&location=${Uri.encode(location.trim().take(120))}")
        }
        return FynxBackendClient.get(context, url).mapCatching { raw ->
            val response = JSONObject(raw)
            val array = response.optJSONArray("businesses") ?: org.json.JSONArray()
            val businesses = buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                    val name = item.optString("business_name").trim()
                    val username = item.optString("business_username").trim()
                    if (name.isBlank() || username.isBlank()) continue
                    add(
                        FynxBusinessDirectoryItem(
                            id = id,
                            businessName = name,
                            username = username,
                            category = item.optString("category"),
                            description = item.optString("description"),
                            location = item.optString("location"),
                            phone = item.optString("phone"),
                            website = item.optString("website"),
                            verified = item.optBoolean("verified")
                        )
                    )
                }
            }
            businesses to response.optBoolean("hasMore")
        }
    }
}

@Composable
fun FynxLocalBusinessesPanel(onBack: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    var businesses by remember { mutableStateOf<List<FynxBusinessDirectoryItem>>(emptyList()) }
    var selectedBusiness by remember { mutableStateOf<FynxBusinessDirectoryItem?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(false) }
    var offset by remember { mutableIntStateOf(0) }
    var requestGeneration by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val categories = listOf("All", "Food", "Fashion", "Retail", "Services", "Health", "Beauty", "Automotive", "Agriculture")

    fun load(reset: Boolean) {
        val generation = ++requestGeneration
        val requestedOffset = if (reset) 0 else offset
        scope.launch {
            if (reset) { loading = true; loadingMore = false } else loadingMore = true
            error = null
            FynxBusinessDirectoryClient.search(
                context = context,
                query = query,
                category = category.takeUnless { it == "All" }.orEmpty(),
                location = location,
                offset = requestedOffset
            ).onSuccess { (loaded, more) ->
                if (generation == requestGeneration) {
                    businesses = if (reset) loaded else businesses + loaded.filterNot { next -> businesses.any { it.id == next.id } }
                    offset = requestedOffset + loaded.size
                    hasMore = more
                }
            }.onFailure {
                if (generation == requestGeneration) error = it.message ?: "Business directory is temporarily unavailable."
            }
            if (generation == requestGeneration) {
                loading = false
                loadingMore = false
            }
        }
    }

    LaunchedEffect(query, location, category) {
        requestGeneration += 1
        delay(350)
        load(reset = true)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Marketplace")
            }
            Column(Modifier.weight(1f)) {
                Text("Local Businesses", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Discover shops and services around your area", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { load(reset = true) }, enabled = !loading) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh businesses")
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(80) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            label = { Text("Search businesses, services or names") },
            shape = RoundedCornerShape(16.dp)
        )
        OutlinedTextField(
            value = location,
            onValueChange = { location = it.take(120) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
            label = { Text("Area, city or neighbourhood") },
            shape = RoundedCornerShape(16.dp)
        )
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 8.dp)
        ) {
            items(categories, key = { it }) { item ->
                FilterChip(selected = category == item, onClick = { category = item }, label = { Text(item) })
            }
        }

        if (loading && businesses.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (businesses.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Business, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
                    Text(if (error == null) "No businesses found yet" else "Businesses could not be loaded", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(error ?: "Try another search or area. New local businesses will appear here when they publish a Business Account.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { load(reset = true) }) { Text("Try again") }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${businesses.size} businesses", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(businesses, key = { it.id }) { business ->
                    Surface(
                        onClick = { selectedBusiness = business },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        tonalElevation = 1.dp
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                    Icon(Icons.Default.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(22.dp))
                                }
                                Spacer(Modifier.size(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(business.businessName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("@${business.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (business.verified) {
                                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
                                        Text("Verified", modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                            Text(business.category, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            if (business.description.isNotBlank()) {
                                Text(business.description, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (business.location.isNotBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(business.location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text("View business details", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                if (hasMore) {
                    item(key = "business-directory-load-more") {
                        TextButton(onClick = { load(reset = false) }, enabled = !loadingMore, modifier = Modifier.fillMaxWidth()) {
                            if (loadingMore) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            else Text("Load more businesses")
                        }
                    }
                }
                error?.let { message ->
                    item(key = "business-directory-error") {
                        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    selectedBusiness?.let { business ->
        AlertDialog(
            onDismissRequest = { selectedBusiness = null },
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(business.businessName)
                    Text("@${business.username}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(business.category, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    if (business.verified) Text("✓ Verified business", color = MaterialTheme.colorScheme.primary)
                    if (business.description.isNotBlank()) Text(business.description)
                    if (business.location.isNotBlank()) Text("Location: ${business.location}")
                    if (business.phone.isNotBlank()) Text("Phone: ${business.phone}")
                    if (business.website.isNotBlank()) Text("Website: ${business.website}")
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (business.phone.isNotBlank()) TextButton(onClick = {
                        try { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${business.phone}"))) }
                        catch (_: Exception) { Toast.makeText(context, "Unable to open the phone dialler.", Toast.LENGTH_SHORT).show() }
                    }) { Icon(Icons.Default.Call, contentDescription = null); Text("Call") }
                    if (business.website.startsWith("https://", true)) TextButton(onClick = {
                        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(business.website))) }
                        catch (_: Exception) { Toast.makeText(context, "Unable to open this website.", Toast.LENGTH_SHORT).show() }
                    }) { Icon(Icons.Default.Language, contentDescription = null); Text("Website") }
                }
            },
            dismissButton = { TextButton(onClick = { selectedBusiness = null }) { Text("Close") } }
        )
    }
}
