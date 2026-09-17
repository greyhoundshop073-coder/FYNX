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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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

private data class SavedPostEntry(
    val post: FynxRemoteSocialClient.RemotePost,
    val savedAtMillis: Long
)

@Composable
fun FynxSavedPostsPanel(
    onOpenAuthorProfile: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var posts by remember { mutableStateOf<List<SavedPostEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshToken by remember { mutableStateOf(0) }

    LaunchedEffect(refreshToken) {
        loading = true
        error = null
        FynxBackendClient.get(context, "/api/social/saved?limit=50&offset=0")
            .mapCatching { raw ->
                val root = JSONObject(raw)
                val array = root.optJSONArray("posts") ?: JSONArray()
                buildList {
                    for (i in 0 until array.length()) {
                        val o = array.getJSONObject(i)
                        val post = FynxRemoteSocialClient.RemotePost(
                            id = o.optString("id"),
                            authorId = o.optString("authorId"),
                            authorUsername = o.optString("authorUsername"),
                            authorDisplayName = o.optString("authorDisplayName"),
                            text = o.optString("text"),
                            visibility = o.optString("visibility"),
                            mediaId = o.optString("mediaId").takeIf { it.isNotBlank() && it != "null" },
                            mediaType = o.optString("mediaType").takeIf { it.isNotBlank() && it != "null" },
                            mediaUrl = null,
                            timestamp = o.optLong("timestamp"),
                            likeCount = 0,
                            commentCount = 0,
                            likedByCurrentUser = false,
                            followedByCurrentUser = false
                        )
                        add(SavedPostEntry(post, o.optLong("savedAtMillis")))
                    }
                }
            }
            .onSuccess { result -> posts = result; loading = false }
            .onFailure { cause -> error = cause.message ?: "Saved posts could not be loaded."; loading = false }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = { refreshToken++ }, enabled = !loading) {
                Icon(Icons.Default.Refresh, "Refresh saved posts")
            }
        }
        when {
            loading -> Text("Loading saved posts…", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            error != null -> Text(error!!, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            posts.isEmpty() -> Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Icon(Icons.Default.BookmarkBorder, null)
                Spacer(Modifier.height(8.dp))
                Text("No saved posts yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Save a post from Home and it will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                items(posts, key = { it.post.id }) { entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = FynxDesign.CardShape,
                        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        entry.post.authorDisplayName.ifBlank { entry.post.authorUsername },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "@${entry.post.authorUsername.removePrefix("@")} · ${entry.post.visibility}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        FynxRemoteSocialClient.save(context, entry.post.id, false)
                                            .onSuccess { posts = posts.filterNot { it.post.id == entry.post.id } }
                                    }
                                }) {
                                    Icon(Icons.Default.Bookmark, "Unsave post")
                                }
                            }
                            if (entry.post.text.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(entry.post.text, style = MaterialTheme.typography.bodyLarge)
                            }
                            if (entry.post.mediaId != null) {
                                Spacer(Modifier.height(8.dp))
                                Text("Media attached", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "View @${entry.post.authorUsername.removePrefix("@")} profile",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier
                                    .clickable { onOpenAuthorProfile(entry.post.authorId) }
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
