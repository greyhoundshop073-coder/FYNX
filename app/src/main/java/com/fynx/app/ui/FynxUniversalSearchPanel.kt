package com.fynx.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Account-scoped universal FYNX search. It reuses the existing authenticated
 * people, marketplace and social-feed APIs rather than creating duplicate data systems.
 */
@Composable
fun FynxUniversalSearchPanel(onOpenProfile: (String) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf("All") }
    var people by remember { mutableStateOf(emptyList<FynxSocialClient.User>()) }
    var listings by remember { mutableStateOf(emptyList<FynxRemoteSocialClient.MarketplaceListing>()) }
    var posts by remember { mutableStateOf(emptyList<FynxRemoteSocialClient.RemotePost>()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun search() {
        val q = query.trim()
        if (q.length < 2) {
            people = emptyList(); listings = emptyList(); posts = emptyList()
            message = "Enter at least 2 characters to search FYNX."
            return
        }
        scope.launch {
            loading = true
            message = null
            val peopleResult = FynxSocialClient.searchUsers(context, q)
            val listingResult = FynxRemoteSocialClient.listings(context, query = q)
            val postMatches = mutableListOf<FynxRemoteSocialClient.RemotePost>()
            var offset = 0
            var more = true
            var pages = 0
            var postError: Throwable? = null
            while (more && pages < 5 && postMatches.size < 50) {
                val result = FynxRemoteSocialClient.feedPage(context, limit = 20, offset = offset, useCache = false)
                result.onSuccess { page ->
                    postMatches += page.posts.filter { post ->
                        post.text.contains(q, ignoreCase = true) ||
                            post.authorUsername.contains(q, ignoreCase = true) ||
                            post.authorDisplayName.contains(q, ignoreCase = true)
                    }
                    more = page.hasMore
                    offset += page.posts.size
                    pages++
                    if (page.posts.isEmpty()) more = false
                }.onFailure {
                    postError = it
                    more = false
                }
            }
            people = peopleResult.getOrElse { emptyList() }
            listings = listingResult.getOrElse { emptyList() }
            posts = postMatches.distinctBy { it.id }
            message = listOfNotNull(
                peopleResult.exceptionOrNull()?.message,
                listingResult.exceptionOrNull()?.message,
                postError?.message
            ).firstOrNull()
            loading = false
        }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Search FYNX", style = MaterialTheme.typography.headlineSmall)
        Text("Find people, marketplace listings and recent social posts using real FYNX data.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(120) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            placeholder = { Text("Search people, posts or products") },
            trailingIcon = { TextButton(onClick = ::search) { Text("Search") } },
            shape = FynxDesign.ControlShape
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("All", "People", "Posts", "Marketplace").forEach { value ->
                FilterChip(selected = tab == value, onClick = { tab = value }, label = { Text(value) })
            }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        if (loading) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                if (tab == "All" || tab == "People") {
                    item { Text("People", style = MaterialTheme.typography.titleMedium) }
                    if (people.isEmpty()) item { Text("No matching people.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                    items(people.take(20), key = { "person_${it.id.ifBlank { it.username }}" }) { person ->
                        Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .55f)), shape = FynxDesign.CardShape) {
                            Row(Modifier.fillMaxWidth().padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                                FynxRemoteProfileAvatar(person.profilePhotoMediaId, person.displayName.ifBlank { person.username }, Modifier.size(42.dp))
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(person.displayName.ifBlank { person.username }, style = MaterialTheme.typography.titleSmall)
                                    Text("@${person.username.removePrefix("@")}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                }
                                OutlinedButton(onClick = { onOpenProfile(person.username) }, shape = FynxDesign.ControlShape) { Text("Profile") }
                            }
                        }
                    }
                }
                if (tab == "All" || tab == "Posts") {
                    item { Text("Posts", style = MaterialTheme.typography.titleMedium) }
                    if (posts.isEmpty()) item { Text("No matching recent posts.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                    items(posts.take(30), key = { "post_${it.id}" }) { post ->
                        Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .55f)), shape = FynxDesign.CardShape) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(post.authorDisplayName.ifBlank { post.authorUsername }, style = MaterialTheme.typography.titleSmall)
                                Text("@${post.authorUsername.removePrefix("@")}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                if (post.text.isNotBlank()) Text(post.text.take(1000), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                if (tab == "All" || tab == "Marketplace") {
                    item { Text("Marketplace", style = MaterialTheme.typography.titleMedium) }
                    if (listings.isEmpty()) item { Text("No matching marketplace listings.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                    items(listings.take(30), key = { "listing_${it.id}" }) { listing ->
                        Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .55f)), shape = FynxDesign.CardShape) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(listing.title, style = MaterialTheme.typography.titleSmall)
                                Text("${listing.currency} ${String.format(java.util.Locale.US, "%,.2f", listing.price)}", style = MaterialTheme.typography.titleMedium)
                                Text(listing.storeName.ifBlank { listing.sellerDisplayName }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                if (listing.location.isNotBlank()) Text(listing.location, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
