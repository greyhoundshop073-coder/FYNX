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
for needle in ('Icons.Default.Favorite','Icons.Default.ChatBubbleOutline','Icons.Default.Bookmark','Icons.Default.BookmarkBorder','Icons.Default.Repeat','Icons.Default.MoreHoriz','interactionBusy','feedRequestInFlight','lastFeedRequestAt','FEED_REFRESH_DEBOUNCE_MS','posts = posts.filterNot { it.id == id }','deletePost = null','AlertDialog(','sharePost(context, post)'):
    require(home, needle, f"Home reliability surface {needle}")
for needle in ('feedPage(context, limit = 20, offset = 0','feedPage(context, limit = 20, offset = posts.size','val existing = posts.map { it.id }.toSet()','filterNot { it.id in existing }','if (!loading && hasMore)'):
    require(home, needle, f"feed recovery/pagination {needle}")
for needle in ('FEED_CACHE_TTL_MS','readCachedFeed(context)','readStaleCachedFeed(context)','if (safeOffset == 0 && remote.isFailure)'):
    require(client, needle, f"offline feed recovery {needle}")
for needle in ('onCommentCountChanged','expandedReplies','parentCommentId','nextCursor'):
    require(comments_panel, needle, f"comment/reply lifecycle {needle}")
for needle in ('post.mediaUrl?.let','RemoteSocialMedia','VideoView','rememberLazyListState','focusedVideoPostId','playbackActive','setVolume','VolumeOff','VolumeUp','FilledIconButton','Fullscreen','"Open video full screen"'):
    require(home, needle, f"post media behavior {needle}")
if 'MediaController' in home:
    raise SystemExit("HOME INTERACTIONS RED: legacy Android MediaController must not compete with FYNX feed controls")
require(home, 'LazyColumn(state = feedListState', "single feed state owns vertical scrolling")
require(home, 'snapshotFlow { feedListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to feedListState.layoutInfo.totalItemsCount }', "automatic near-bottom feed pagination")
for needle in ('onOpenAuthorProfile','FynxRemoteSocialClient.follow','FynxDiscoveryClient.recordEngagement','Intent.ACTION_SEND'):
    require(home, needle, f"identity/share behavior {needle}")
require(home, 'hydrateInteractionStates(page.posts)', "interaction state re-entry hydration")
require(home, 'interactionStates = emptyMap()', "interaction state reset before re-hydration")
if "CommentsDialog" in home:
    raise SystemExit("HOME INTERACTIONS RED: legacy competing CommentsDialog detected")
if 'Text("Save")' in home or 'Text("Repost")' in home:
    raise SystemExit("HOME INTERACTIONS RED: fake Save/Repost feed controls detected")
for needle in ('MaterialTheme.colorScheme','FynxDesign.LargeCardShape','key = "feed_header"','key = "feed_loading"','key = "feed_loading_more"','key = "feed_error"','key = "feed_empty"','"Create Post"','"Like"','"Comment"','"Post options"','onDismissRequest =','enabled = !feedRequestInFlight'):
    require(home, needle, f"Home 4F polish/integration surface {needle}")
if 'feed_load_more' in home or 'Load more posts' in home:
    raise SystemExit("HOME INTERACTIONS RED: Home feed must load the next page automatically; manual Load more control remains")
if '"Refresh feed"' in home:
    raise SystemExit("HOME INTERACTIONS RED: Home feed must not expose a manual refresh button")
require(home, 'text = { Text(if (interactionState.saved) "Remove from saved" else "Save post") }', "Home 4F save state label")
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

require(backend_package, '"start": "node --import ./renderScalabilityPreload.js realtimeIsolationBootstrap.js"', "production realtime entrypoint")
require(realtime_bootstrap, 'import { installHomeCommentPrivacy } from "./homeCommentsPrivacyBootstrap.js";', "Home comment privacy integration")
require(realtime_bootstrap, "await installHomeCommentBackend();", "base Home comments installation")
require(realtime_bootstrap, "await installHomeCommentPrivacy();", "Home comment privacy hardening")
for needle in ("if (!(await visiblePost(postId, req.user.sub))) return res.status(404).json({ error: 'post not found' });","b.blocker_id=$2 AND b.blocked_id=c.author_id","b.blocker_id=c.author_id AND b.blocked_id=$2","b.blocker_id=$3 AND b.blocked_id=c.author_id","b.blocker_id=c.author_id AND b.blocked_id=$3","SELECT c.id FROM social_post_comments c","const cursorClause = before === null ? '' : ' AND c.id < $4';","LIMIT $3`,","ORDER BY c.id ASC LIMIT $4`,"):
    require(realtime_bootstrap, needle, f"clean-startup Home comment privacy implementation {needle}")
require(realtime_bootstrap, "before === null ? [postId, req.user.sub, limit + 1] : [postId, req.user.sub, limit + 1, before]", "Home comment cursor parameter binding")
require(privacy_bootstrap, "fynxHomeCommentsPrivacyBatch", "Home comment privacy patch marker")
for needle in ('remember(post.id)','rememberSaveable(post.id)','DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)','.imePadding()','KeyboardActions(onSend = { send() })','FynxRemoteProfileAvatar(photo, comment.authorDisplayName.ifBlank { comment.authorUsername }','commentCount += 1','onCommentCountChanged(commentCount)'):
    require(comments_panel, needle, f"Home 4D edge-case safeguard {needle}")
require_normalized(comments_panel, 'Column(Modifier.fillMaxSize().imePadding())', "comment sheet moves above the IME as one layout")
if 'Row(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()' in comments_panel:
    raise SystemExit("HOME INTERACTIONS RED: comment composer applies IME padding twice")
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
require(visible_updates, 'onOpenAi', "Home AI entry callback")
require(visible_updates, 'Open FYNX AI', "Home AI centralized entry action")
if 'AiAssistantClient.' in visible_updates or 'aiInput' in visible_updates or 'AiAssistantClient.sendMessage' in visible_updates:
    raise SystemExit("HOME INTERACTIONS RED: Home must not execute a second AI assistant outside the dedicated FYNX AI screen")
if 'TextToSpeech' in visible_updates or 'android.speech.tts' in visible_updates:
    raise SystemExit("HOME INTERACTIONS RED: Google Android TTS must not be reintroduced into Home AI")
if 'OPENAI_API_KEY' in visible_updates or 'OPENAI_API_KEY' in ai_client:
    raise SystemExit("HOME INTERACTIONS RED: OpenAI API key must never be present in Android client code")

app_source = read("app/src/main/java/com/fynx/app/ui/FynxApp.kt")
money_center = read("app/src/main/java/com/fynx/app/ui/MoneyCenterPanel.kt")
budget = read("app/src/main/java/com/fynx/app/ui/BudgetPlannerPanel.kt")
require(app_source, '"AI Creation" -> { selected = "AI" }', "AI Creation central route")
require(app_source, '"AI Photo Editor" -> { selected = "AI" }', "AI Photo Editor central route")
require(app_source, '"Advertising AI" -> { selected = "AI" }', "Advertising AI central route")
require(app_source, 'add(Triple("AI", "FYNX AI Assistant", Icons.Default.AutoAwesome))', "single FYNX AI feature entry")
if 'add(Triple("AI Creation"' in app_source or 'add(Triple("AI Photo Editor"' in app_source or 'add(Triple("Advertising AI"' in app_source:
    raise SystemExit("HOME INTERACTIONS RED: duplicate AI feature destinations remain outside FYNX AI")
if 'FynxAiCreationPanel(' in app_source or 'FynxAiPhotoEditorPanel(' in app_source or 'FynxAdvertisingAiPanel(' in app_source:
    raise SystemExit("HOME INTERACTIONS RED: secondary AI panels are still directly rendered by the app shell")
if 'FynxMoneyAiCoachPanel(' in money_center:
    raise SystemExit("HOME INTERACTIONS RED: Money Center still renders a separate AI coach")
if 'AiAssistantClient.' in budget:
    raise SystemExit("HOME INTERACTIONS RED: Budget Planner still executes AI outside the dedicated FYNX AI screen")
require(ai_panel, 'OutlinedTextField(', "full FYNX AI typing surface")
require(ai_panel, 'FynxAiConversationClient.send(context, activeConversation, prompt', "persistent FYNX AI conversation backend path")
require(ai_panel, 'FynxAiConversationClient.create(context)', "new FYNX AI conversation creation")
require(ai_panel, 'FynxAiConversationClient.list(context)', "FYNX AI conversation history")
require(ai_panel, 'FynxAiConversationClient.uploadImage(context, uri)', "real FYNX AI image attachment upload")
require(ai_panel, 'FynxFutureIntelligencePolicy.authorize(', "full FYNX AI authorization boundary")
if 'TextToSpeech' in ai_panel or 'android.speech.tts' in ai_panel:
    raise SystemExit("HOME INTERACTIONS RED: Google Android TTS must not be reintroduced into FYNX AI")

print("HOME INTERACTIONS GREEN: Home 4D edge-case safeguards, 4E backend-backed interactions including Save/Saved Posts continuity, comments/replies, media, share/profile paths, refresh/pagination, offline recovery, rapid-tap protection, deletion confirmation, 4F design/accessibility surfaces, interaction-state re-entry, clean-startup privacy boundaries, FYNX AI single-scroll/typing/security integration, and direct Create status -> existing composer wiring are present without duplicate vertical feed surfaces or client API secrets.")
