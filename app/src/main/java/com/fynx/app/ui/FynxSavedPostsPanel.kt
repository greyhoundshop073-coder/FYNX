package com.fynx.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private data class SavedPostEntry(val post: FynxRemoteSocialClient.RemotePost, val savedAtMillis: Long)

@Composable
fun FynxSavedPostsPanel(onOpenAuthorProfile: (String) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var posts by remember { mutableStateOf<List<SavedPostEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(false) }
    var nextOffset by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshToken by remember { mutableStateOf(0) }
    var unsavingIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var mediaViewerPost by remember { mutableStateOf<FynxRemoteSocialClient.RemotePost?>(null) }
    var mediaViewerItems by remember { mutableStateOf<List<FynxPostViewerItem>>(emptyList()) }
    var openingMediaPostId by remember { mutableStateOf<String?>(null) }

    suspend fun fetchPage(offset: Int): Result<Pair<List<SavedPostEntry>, Boolean>> = runCatching {
        val raw = FynxBackendClient.get(context, "/api/social/saved?limit=50&offset=$offset").getOrThrow()
        val root = JSONObject(raw)
        val array = root.optJSONArray("posts") ?: JSONArray()
        val page = buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val mediaId = o.optString("mediaId").takeIf { it.isNotBlank() && it != "null" }
                val mediaType = o.optString("mediaType").takeIf { it.isNotBlank() && it != "null" }
                val post = FynxRemoteSocialClient.RemotePost(
                    id = o.optString("id"), authorId = o.optString("authorId"),
                    authorUsername = o.optString("authorUsername"), authorDisplayName = o.optString("authorDisplayName"),
                    text = o.optString("text"), visibility = o.optString("visibility"),
                    mediaId = mediaId, mediaType = mediaType,
                    mediaUrl = mediaId?.let { "/api/social/media/$it" },
                    timestamp = o.optLong("timestamp"), likeCount = o.optInt("likeCount"), commentCount = o.optInt("commentCount"),
                    likedByCurrentUser = o.optBoolean("likedByCurrentUser"), followedByCurrentUser = o.optBoolean("followedByCurrentUser")
                )
                if (post.id.isNotBlank() && post.id != "null") add(SavedPostEntry(post, o.optLong("savedAtMillis")))
            }
        }
        page to root.optBoolean("hasMore", page.size == 50)
    }

    LaunchedEffect(refreshToken) {
        loading = true
        error = null
        fetchPage(0)
            .onSuccess { (page, more) -> posts = page; nextOffset = page.size; hasMore = more; loading = false }
            .onFailure { cause -> error = cause.message ?: "Saved posts could not be loaded."; loading = false }
    }

    fun loadMore() {
        if (loading || loadingMore || !hasMore) return
        loadingMore = true
        error = null
        scope.launch {
            fetchPage(nextOffset)
                .onSuccess { (page, more) -> posts = (posts + page).distinctBy { it.post.id }; nextOffset += page.size; hasMore = more; loadingMore = false }
                .onFailure { cause -> error = cause.message ?: "More saved posts could not be loaded."; loadingMore = false }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = { refreshToken++ }, enabled = !loading && !loadingMore) { Icon(Icons.Default.Refresh, "Refresh saved posts") }
        }
        if (error != null && posts.isNotEmpty()) {
            Text(error!!, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = { if (hasMore && nextOffset > 0) loadMore() else refreshToken++ }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Retry") }
        }
        when {
            loading && posts.isEmpty() -> Text("Loading saved posts…", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            error != null && posts.isEmpty() -> Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(error ?: "Saved posts could not be loaded.", color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = { refreshToken++ }, enabled = !loading) { Text("Retry") }
            }
            posts.isEmpty() -> Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Icon(Icons.Default.BookmarkBorder, null)
                Spacer(Modifier.height(8.dp))
                Text("No saved posts yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Save a post from Home and it will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                items(posts, key = { it.post.id }) { entry ->
                    Card(modifier = Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(14.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text(entry.post.authorDisplayName.ifBlank { entry.post.authorUsername }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text("@${entry.post.authorUsername.removePrefix("@")} · ${entry.post.visibility}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                                }
                                IconButton(enabled = entry.post.id !in unsavingIds, onClick = {
                                    unsavingIds = unsavingIds + entry.post.id
                                    scope.launch {
                                        FynxRemoteSocialClient.save(context, entry.post.id, false)
                                            .onSuccess { posts = posts.filterNot { it.post.id == entry.post.id }; nextOffset = (nextOffset - 1).coerceAtLeast(0); hasMore = true }
                                            .onFailure { cause -> error = cause.message ?: "Post could not be removed from Saved." }
                                        unsavingIds = unsavingIds - entry.post.id
                                    }
                                }) { Icon(Icons.Default.Bookmark, if (entry.post.id in unsavingIds) "Removing saved post" else "Unsave post") }
                            }
                            if (entry.post.text.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(entry.post.text, style = MaterialTheme.typography.bodyLarge)
                            }
                            if (entry.post.mediaUrl != null && entry.post.mediaType != null) {
                                Spacer(Modifier.height(8.dp))
                                RemoteSocialMedia(path = entry.post.mediaUrl, type = entry.post.mediaType, playbackActive = false, onOpenMedia = {
                                    if (openingMediaPostId != entry.post.id) {
                                        openingMediaPostId = entry.post.id
                                        scope.launch {
                                            val listed = FynxHomePostMediaClient.list(context, entry.post.id).getOrNull().orEmpty()
                                            val viewerItems = listed.map { FynxPostViewerItem(it.id, it.mediaType, it.position, it.mediaUrl) }
                                                .ifEmpty { listOf(FynxPostViewerItem(entry.post.mediaId ?: entry.post.id, entry.post.mediaType, 0, entry.post.mediaUrl)) }
                                            mediaViewerPost = entry.post
                                            mediaViewerItems = viewerItems
                                            openingMediaPostId = null
                                        }
                                    }
                                })
                                if (openingMediaPostId == entry.post.id) Text("Opening media…", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("View @${entry.post.authorUsername.removePrefix("@")} profile", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clickable { onOpenAuthorProfile(entry.post.authorId) }.padding(vertical = 4.dp))
                        }
                    }
                }
                if (hasMore) item(key = "saved_posts_load_more") {
                    OutlinedButton(onClick = { loadMore() }, enabled = !loadingMore, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        if (loadingMore) CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp) else Text("Load more saved posts")
                    }
                }
            }
        }
    }

    val viewerPost = mediaViewerPost
    if (viewerPost != null && mediaViewerItems.isNotEmpty()) {
        FynxPostMediaViewer(
            context = context, post = viewerPost, media = mediaViewerItems,
            onDismiss = { mediaViewerPost = null; mediaViewerItems = emptyList() },
            onPostChanged = { _, _, saved, _, _, _, _ ->
                if (!saved) { posts = posts.filterNot { it.post.id == viewerPost.id }; nextOffset = (nextOffset - 1).coerceAtLeast(0); hasMore = true }
            }
        )
    }
}
