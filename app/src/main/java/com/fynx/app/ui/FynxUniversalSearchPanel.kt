package com.fynx.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Account-scoped universal FYNX search using the existing authenticated people, marketplace and social-feed APIs. */
@Composable
fun FynxUniversalSearchPanel(onOpenProfile: (String) -> Unit = {}, onOpenPost: (String) -> Unit = {}, onOpenListing: (String) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf("All") }
    var people by remember { mutableStateOf(emptyList<FynxSocialClient.User>()) }
    var listings by remember { mutableStateOf(emptyList<FynxRemoteSocialClient.MarketplaceListing>()) }
    var posts by remember { mutableStateOf(emptyList<FynxRemoteSocialClient.RemotePost>()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var peopleFailed by remember { mutableStateOf(false) }
    var postsFailed by remember { mutableStateOf(false) }
    var listingsFailed by remember { mutableStateOf(false) }
    var searchGeneration by remember { mutableIntStateOf(0) }

    fun search(searchQuery: String = query, expectedGeneration: Int? = null) {
        val q = searchQuery.trim()
        val generation = expectedGeneration ?: ++searchGeneration
        if (generation != searchGeneration) return
        if (q.length < 2) {
            people = emptyList()
            listings = emptyList()
            posts = emptyList()
            loading = false
            peopleFailed = false
            postsFailed = false
            listingsFailed = false
            message = if (q.isEmpty()) null else "Enter at least 2 characters to search FYNX."
            return
        }
        scope.launch {
            loading = true
            message = null
            val (peopleResult, listingResult, postResult) = coroutineScope {
                val peopleTask = async { FynxSocialClient.searchUsers(context, q) }
                val listingsTask = async { FynxRemoteSocialClient.marketplaceDiscoveryPage(context, query = q, category = "All", limit = 30, offset = 0).map { it.listings } }
                val postsTask = async { FynxRemoteSocialClient.searchPosts(context, q, limit = 50, offset = 0) }
                Triple(peopleTask.await(), listingsTask.await(), postsTask.await())
            }
            if (generation != searchGeneration) return@launch
            people = peopleResult.getOrElse { emptyList() }
            listings = listingResult.getOrElse { emptyList() }
            posts = postResult.getOrElse { FynxRemoteSocialClient.FeedPage(emptyList(), false) }.posts.distinctBy { it.id }
            peopleFailed = peopleResult.isFailure
            listingsFailed = listingResult.isFailure
            postsFailed = postResult.isFailure
            message = listOfNotNull(peopleResult.exceptionOrNull()?.message, listingResult.exceptionOrNull()?.message, postResult.exceptionOrNull()?.message).firstOrNull()
            loading = false
        }
    }

    LaunchedEffect(query) {
        val q = query.trim()
        // Invalidate in-flight requests immediately when the query changes, not
        // after the debounce, so a slower old response cannot overwrite new input.
        val generation = ++searchGeneration
        if (q.length < 2) {
            people = emptyList()
            listings = emptyList()
            posts = emptyList()
            loading = false
            peopleFailed = false
            postsFailed = false
            listingsFailed = false
            message = if (q.isEmpty()) null else "Enter at least 2 characters to search FYNX."
            return@LaunchedEffect
        }
        people = emptyList()
        listings = emptyList()
        posts = emptyList()
        peopleFailed = false
        listingsFailed = false
        postsFailed = false
        loading = true
        message = null
        delay(350L)
        if (generation != searchGeneration) return@LaunchedEffect
        search(q, generation)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Search FYNX", style = MaterialTheme.typography.headlineSmall)
        Text("Suggestions update as you type. Choose a result to open that person, post, or product.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(value = query, onValueChange = { query = it.take(80) }, modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") }, placeholder = { Text("Search people, posts or products") }, trailingIcon = { TextButton(onClick = { search() }, enabled = query.trim().length >= 2) { Text("Search") } }, shape = FynxDesign.ControlShape)
        if (query.trim().length >= 2 && !loading && (people.isNotEmpty() || posts.isNotEmpty() || listings.isNotEmpty())) {
            Card(
                modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .55f)),
                shape = FynxDesign.CardShape
            ) {
                Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Suggestions", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (tab == "All" || tab == "People") people.take(2).forEach { person ->
                        Row(Modifier.fillMaxWidth().clickable { onOpenProfile(person.username) }.padding(horizontal = 8.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(person.displayName.ifBlank { "@${person.username}" }, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text("Person", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (tab == "All" || tab == "Posts") posts.take(2).forEach { post ->
                        Row(Modifier.fillMaxWidth().clickable { onOpenPost(post.id) }.padding(horizontal = 8.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(post.text.ifBlank { post.mediaType?.let { "${it.replaceFirstChar(Char::uppercase)} post" } ?: "Post by @${post.authorUsername}" }.take(72), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            Text("Post", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (tab == "All" || tab == "Marketplace") listings.take(2).forEach { listing ->
                        Row(Modifier.fillMaxWidth().clickable { onOpenListing(listing.id) }.padding(horizontal = 8.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(listing.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            Text("Product", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("All", "People", "Posts", "Marketplace").forEach { value -> FilterChip(selected = tab == value, onClick = { tab = value }, label = { Text(value) }) } }
        message?.let { Text(it, color = if (it.contains("failed", true) || it.contains("unavailable", true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        if (loading) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                if (tab == "All" || tab == "People") {
                    item { Text("People", style = MaterialTheme.typography.titleMedium) }
                    if (people.isEmpty() && query.trim().length >= 2) item { Text(if (peopleFailed) "People search unavailable. Check your connection and try again." else "No matching people.", color = if (peopleFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                    items(people.take(20), key = { "person_${it.id.ifBlank { it.username }}" }) { person ->
                        Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .55f)), shape = FynxDesign.CardShape) {
                            Row(Modifier.fillMaxWidth().padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                                FynxRemoteProfileAvatar(person.profilePhotoMediaId, person.displayName.ifBlank { person.username }, Modifier.size(42.dp), ownerUsername = person.username)
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) { Text(person.displayName.ifBlank { person.username }, style = MaterialTheme.typography.titleSmall); Text("@${person.username.removePrefix("@")}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                                OutlinedButton(onClick = { onOpenProfile(person.username) }, shape = FynxDesign.ControlShape) { Text("Profile") }
                            }
                        }
                    }
                }
                if (tab == "All" || tab == "Posts") {
                    item { Text("Posts", style = MaterialTheme.typography.titleMedium) }
                    if (posts.isEmpty() && query.trim().length >= 2) item { Text(if (postsFailed) "Post search unavailable. Check your connection and try again." else "No matching recent posts.", color = if (postsFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                    items(posts.take(30), key = { "post_${it.id}" }) { post ->
                        Card(Modifier.fillMaxWidth().clickable { onOpenPost(post.id) }, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .55f)), shape = FynxDesign.CardShape) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(post.authorDisplayName.ifBlank { post.authorUsername }, style = MaterialTheme.typography.titleSmall); Text("@${post.authorUsername.removePrefix("@")}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall); if (post.text.isNotBlank()) Text(post.text.take(1000), style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
                if (tab == "All" || tab == "Marketplace") {
                    item { Text("Marketplace", style = MaterialTheme.typography.titleMedium) }
                    if (listings.isEmpty() && query.trim().length >= 2) item { Text(if (listingsFailed) "Marketplace search unavailable. Check your connection and try again." else "No matching marketplace listings.", color = if (listingsFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                    items(listings.take(30), key = { "listing_${it.id}" }) { listing ->
                        Card(Modifier.fillMaxWidth().clickable { onOpenListing(listing.id) }, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .55f)), shape = FynxDesign.CardShape) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(listing.title, style = MaterialTheme.typography.titleSmall); Text("${listing.currency} ${String.format(java.util.Locale.US, "%,.2f", listing.price)}", style = MaterialTheme.typography.titleMedium); Text(listing.storeName.ifBlank { listing.sellerDisplayName }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall); if (listing.location.isNotBlank()) Text(listing.location, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
    }
}
