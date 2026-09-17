from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]

def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

def require(text: str, needle: str, label: str) -> None:
    if needle not in text and needle.replace('`', '\\`') not in text:
        raise SystemExit(f"HOME INTERACTIONS RED: missing {label}: {needle}")

def normalize_source(text: str) -> str:
    text = " ".join(text.split())
    return re.sub(r"\s*([(),:])\s*", r"\1", text)

def require_normalized(text: str, needle: str, label: str) -> None:
    if normalize_source(needle) not in normalize_source(text):
        raise SystemExit(f"HOME INTERACTIONS RED: missing {label}: {needle}")

discovery = read("backend/discoveryRoutes.js")
home = read("app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt")
home_shell = read("app/src/main/java/com/fynx/app/ui/HomePanel.kt")
visible_updates = read("app/src/main/java/com/fynx/app/ui/FynxVisibleUpdatesPanel.kt")
ai_panel = read("app/src/main/java/com/fynx/app/ui/FynxAiAssistantPanel.kt")
ai_client = read("app/src/main/java/com/fynx/app/ui/AiAssistantClient.kt")
comments_panel = read("app/src/main/java/com/fynx/app/ui/FynxHomeCommentsPanel.kt")
client = read("app/src/main/java/com/fynx/app/ui/FynxRemoteSocialClient.kt")
saved_panel = read("app/src/main/java/com/fynx/app/ui/FynxSavedPostsPanel.kt")
privacy_bootstrap = read("backend/homeCommentsPrivacyBootstrap.js")
realtime_bootstrap = read("backend/realtimeIsolationBootstrap.js")
backend_package = read("backend/package.json")

for route in ('app.post("/api/social/posts/:id/save"','app.delete("/api/social/posts/:id/save"','app.get("/api/social/saved"','app.post("/api/social/posts/:id/repost"','app.delete("/api/social/posts/:id/repost"','app.get("/api/social/posts/:id/interaction-state"'):
    require(discovery, route, f"durable interaction route {route}")
for needle in ("CREATE TABLE IF NOT EXISTS social_saved_posts","CREATE TABLE IF NOT EXISTS social_post_reposts","post_id BIGINT NOT NULL","user_id BIGINT NOT NULL","const visiblePost = async (postId, userId)"):
    require(discovery, needle, f"interaction protection {needle}")
for needle in ("FynxRemoteSocialClient.feedPage","FynxRemoteSocialClient.like","FynxRemoteSocialClient.save","FynxRemoteSocialClient.repost","FynxRemoteSocialClient.interactionState","FynxRemoteSocialClient.follow","FynxRemoteSocialClient.deletePost","FynxHomeCommentsPanel"):
    require(home, needle, f"Home interaction path {needle}")
for needle in ("suspend fun comments(context: Context, id: String)","suspend fun addComment(context: Context, id: String, text: String)","suspend fun addReply(context: Context, postId: String, parentCommentId: String, text: String)","suspend fun save(context: Context, id: String, saved: Boolean)","suspend fun repost(context: Context, id: String, reposted: Boolean)","suspend fun interactionState(context: Context, id: String)",'"/api/social/posts/$numericId/save"','"/api/social/posts/$numericId/repost"','"/api/social/posts/$numericId/interaction-state"'):
    require_normalized(client, needle, f"existing social client API {needle}")
for needle in ('Icons.Default.Favorite','Icons.Default.ChatBubbleOutline','Icons.Default.Bookmark','Icons.Default.BookmarkBorder','Icons.Default.Repeat','Icons.Default.MoreHoriz','Icons.Default.Refresh','interactionBusy','feedRequestInFlight','lastFeedRequestAt','FEED_REFRESH_DEBOUNCE_MS','posts = posts.filterNot { it.id == id }','deletePost = null','AlertDialog(','sharePost(context, post)'):
    require(home, needle, f"Home reliability surface {needle}")
for needle in ('feedPage(context, limit = 20, offset = 0','feedPage(context, limit = 20, offset = posts.size','val existing = posts.map { it.id }.toSet()','filterNot { it.id in existing }','if (!loading && hasMore)'):
    require(home, needle, f"feed recovery/pagination {needle}")
for needle in ('FEED_CACHE_TTL_MS','readCachedFeed(context)','readStaleCachedFeed(context)','if (safeOffset == 0 && remote.isFailure)'):
    require(client, needle, f"offline feed recovery {needle}")
for needle in ('onCommentCountChanged','expandedReplies','parentCommentId','nextCursor'):
    require(comments_panel, needle, f"comment/reply lifecycle {needle}")
for needle in ('post.mediaUrl?.let','RemoteSocialMedia','MediaController','VideoView'):
    require(home, needle, f"post media behavior {needle}")
for needle in ('onOpenAuthorProfile','FynxRemoteSocialClient.follow','FynxDiscoveryClient.recordEngagement','Intent.ACTION_SEND'):
    require(home, needle, f"identity/share behavior {needle}")
require(home, 'hydrateInteractionStates(page.posts)', "interaction state re-entry hydration")
require(home, 'interactionStates = emptyMap()', "interaction state reset before re-hydration")
if "CommentsDialog" in home:
    raise SystemExit("HOME INTERACTIONS RED: legacy competing CommentsDialog detected")
if 'Text("Save")' in home or 'Text("Repost")' in home:
    raise SystemExit("HOME INTERACTIONS RED: fake Save/Repost feed controls detected")
for needle in ('MaterialTheme.colorScheme','FynxDesign.LargeCardShape','key = "feed_header"','key = "feed_loading"','key = "feed_error"','key = "feed_empty"','key = "feed_load_more"','"Refresh feed"','"Create Post"','"Like"','"Comment"','"Post options"','onDismissRequest =','enabled = !feedRequestInFlight','enabled = !loadingMore && !feedRequestInFlight'):
    require(home, needle, f"Home 4F polish/integration surface {needle}")
require(home, 'label = if (interactionState.saved) "Saved" else "Save"', "Home 4F save state label")
require(home, 'label = if (interactionState.reposted) "Reposted" else "Repost"', "Home 4F repost state label")
if 'contentDescription = null' not in home and 'Icon(Icons.Default.ShoppingBag, null)' not in home:
    raise SystemExit("HOME INTERACTIONS RED: missing Home 4F decorative-icon accessibility handling")
for needle in ('FynxRemoteProfileAvatar(','profilePhotoMediaId','post.authorDisplayName.ifBlank { post.authorUsername }','post.mediaUrl?.let'):
    require(home, needle, f"real Home identity/media surface {needle}")

require(saved_panel, 'FynxBackendClient.get(context, "/api/social/saved?limit=50&offset=0")', "Saved Posts real backend retrieval")
require(saved_panel, 'FynxRemoteSocialClient.save(context, entry.post.id, false)', "Saved Posts real unsave path")
require(saved_panel, 'posts = posts.filterNot { it.post.id == entry.post.id }', "Saved Posts local reconciliation after unsave")
require(saved_panel, 'var refreshToken by remember { mutableStateOf(0) }', "Saved Posts refresh state")
require(saved_panel, 'IconButton(onClick = { refreshToken++ }, enabled = !loading)', "Saved Posts refresh control")
require(saved_panel, 'onOpenAuthorProfile(entry.post.authorId)', "Saved Posts author profile integration")
require(saved_panel, '.clickable { onOpenAuthorProfile(entry.post.authorId) }', "Saved Posts clickable author profile control")
require(saved_panel, 'import androidx.compose.foundation.clickable', "Saved Posts clickable interaction import")

require(backend_package, '"start": "node renderStartupSourceGuard.js && node --import ./renderScalabilityPreload.js realtimeIsolationBootstrap.js"', "guarded production realtime entrypoint")
require(realtime_bootstrap, 'import { installHomeCommentPrivacy } from "./homeCommentsPrivacyBootstrap.js";', "Home comment privacy integration")
require(realtime_bootstrap, "await installHomeCommentBackend();", "base Home comments installation")
require(realtime_bootstrap, "await installHomeCommentPrivacy();", "Home comment privacy hardening")
for needle in ("if (!(await visiblePost(postId, req.user.sub))) return res.status(404).json({ error: 'post not found' });","b.blocker_id=$2 AND b.blocked_id=c.author_id","b.blocker_id=c.author_id AND b.blocked_id=$2","b.blocker_id=$3 AND b.blocked_id=c.author_id","b.blocker_id=c.author_id AND b.blocked_id=$3","SELECT c.id FROM social_post_comments c","const cursorClause = before === null ? '' : ' AND c.id < $4';","LIMIT $3`,","ORDER BY c.id ASC LIMIT $4`,"):
    require(realtime_bootstrap, needle, f"clean-startup Home comment privacy implementation {needle}")
require(realtime_bootstrap, "before === null ? [postId, req.user.sub, limit + 1] : [postId, req.user.sub, limit + 1, before]", "Home comment cursor parameter binding")
require(privacy_bootstrap, "fynxHomeCommentsPrivacyBatch", "Home comment privacy patch marker")
for needle in ('remember(post.id)','rememberSaveable(post.id)','DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)','.imePadding()','KeyboardActions(onSend = { send() })','FynxRemoteProfileAvatar(photo, comment.authorDisplayName.ifBlank { comment.authorUsername }','commentCount += 1','onCommentCountChanged(commentCount)'):
    require(comments_panel, needle, f"Home 4D edge-case safeguard {needle}")
for needle in ('visiblePost(postId, req.user.sub)','b.blocker_id=$2 AND b.blocked_id=c.author_id','b.blocker_id=c.author_id AND b.blocked_id=$2'):
    require(realtime_bootstrap, needle, f"Home 4D privacy boundary {needle}")
for needle in ('val previous = posts.firstOrNull { it.id == id }','optimisticLiked','onFailure {','posts = posts.map { if (it.id == id) it.copy(likedByCurrentUser = previous.likedByCurrentUser','runInteraction(id, saved','runInteraction(id, reposted'):
    require(home, needle, f"Home 4E interaction rollback/reconciliation {needle}")

require(home_shell, 'FynxRemoteHomeSocialPanel(', "Home feed host")
require_normalized(home_shell, 'header = { FynxVisibleUpdatesPanel(', "Home AI/Status header wiring")
require_normalized(home_shell, 'onCreateStatus = { showMatureStatusComposer = true }', "Home Create status -> existing composer wiring")
require(visible_updates, 'onCreateStatus: () -> Unit', "Status create callback")
require(visible_updates, 'IconButton(onClick = onCreateStatus', "Status create control action")
require(home, 'LazyColumn(', "Home single vertical scroll surface")
require_normalized(home, 'header?.let { content -> item(key = "home_ai_status") { content() } }', "AI/Status feed header item")
# Home itself owns one feed LazyColumn. Video discovery is a separate fullscreen Dialog surface,
# so its own LazyColumn must not be counted as a second Home feed scroll container.
home_feed_source = home.split('@Composable\nprivate fun VideoDiscoveryDialog', 1)[0]
if home_feed_source.count('LazyColumn(') != 1:
    raise SystemExit("HOME INTERACTIONS RED: Home feed must keep exactly one vertical LazyColumn")
require_normalized(home, 'items(items = posts, key = { it.id })', "feed posts in the shared scroll surface")
require(visible_updates, 'OutlinedTextField(', "Home AI typing input")
require(visible_updates, 'value = aiInput', "Home AI input state")
require(visible_updates, 'AiAssistantClient.sendMessage(context, prompt)', "Home AI real backend client path")
require(visible_updates, 'FynxFutureIntelligencePolicy.authorize(', "Home AI authorization boundary")
require(visible_updates, 'Icons.Default.Send', "Home AI send control")
require(visible_updates, 'Icons.Default.Mic', "Home AI voice entry")
if 'TextToSpeech' in visible_updates or 'android.speech.tts' in visible_updates:
    raise SystemExit("HOME INTERACTIONS RED: Google Android TTS must not be reintroduced into Home AI")
if 'OPENAI_API_KEY' in visible_updates or 'OPENAI_API_KEY' in ai_client:
    raise SystemExit("HOME INTERACTIONS RED: OpenAI API key must never be present in Android client code")
require(ai_panel, 'OutlinedTextField(', "full FYNX AI typing surface")
require(ai_panel, 'AiAssistantClient.sendMessage(context, prompt)', "full FYNX AI backend path")
require(ai_panel, 'FynxFutureIntelligencePolicy.authorize(', "full FYNX AI authorization boundary")
if 'TextToSpeech' in ai_panel or 'android.speech.tts' in ai_panel:
    raise SystemExit("HOME INTERACTIONS RED: Google Android TTS must not be reintroduced into FYNX AI")

print("HOME INTERACTIONS GREEN: Home 4D edge-case safeguards, 4E backend-backed interactions including Save/Saved Posts continuity, comments/replies, media, share/profile paths, refresh/pagination, offline recovery, rapid-tap protection, deletion confirmation, 4F design/accessibility surfaces, interaction-state re-entry, clean-startup privacy boundaries, FYNX AI single-scroll/typing/security integration, and direct Create status -> existing composer wiring are present without duplicate vertical feed surfaces or client API secrets.")
