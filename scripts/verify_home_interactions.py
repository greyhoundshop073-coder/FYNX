from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]

def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise SystemExit(f"HOME INTERACTIONS RED: missing {label}: {needle}")

def normalize_source(text: str) -> str:
    text = " ".join(text.split())
    return re.sub(r"\s*([(),:])\s*", r"\1", text)

def require_normalized(text: str, needle: str, label: str) -> None:
    if normalize_source(needle) not in normalize_source(text):
        raise SystemExit(f"HOME INTERACTIONS RED: missing {label}: {needle}")

discovery = read("backend/discoveryRoutes.js")
home = read("app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt")
comments_panel = read("app/src/main/java/com/fynx/app/ui/FynxHomeCommentsPanel.kt")
client = read("app/src/main/java/com/fynx/app/ui/FynxRemoteSocialClient.kt")
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
for needle in ('MaterialTheme.colorScheme','FynxDesign.LargeCardShape','key = "feed_header"','key = "feed_loading"','key = "feed_error"','key = "feed_empty"','key = "feed_load_more"','"Refresh feed"','"Create post"','"Like"','"Comment"','"Post options"','onDismissRequest =','enabled = !feedRequestInFlight','enabled = !loadingMore && !feedRequestInFlight'):
    require(home, needle, f"Home 4F polish/integration surface {needle}")
# The upgraded feed uses visible state labels (Saved/Reposted) while preserving Save/Repost semantics.
require(home, 'label = if (interactionState.saved) "Saved" else "Save"', "Home 4F save state label")
require(home, 'label = if (interactionState.reposted) "Reposted" else "Repost"', "Home 4F repost state label")
if 'contentDescription = null' not in home and 'Icon(Icons.Default.ShoppingBag, null)' not in home:
    raise SystemExit("HOME INTERACTIONS RED: missing Home 4F decorative-icon accessibility handling")
for needle in ('FynxRemoteProfileAvatar(','profilePhotoMediaId','post.authorDisplayName.ifBlank { post.authorUsername }','post.mediaUrl?.let'):
    require(home, needle, f"real Home identity/media surface {needle}")
require(backend_package, '"start": "node realtimeIsolationBootstrap.js"', "production realtime entrypoint")
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
print("HOME INTERACTIONS GREEN: Home 4D edge-case safeguards, 4E backend-backed interactions, comments/replies, media, share/profile paths, refresh/pagination, offline recovery, rapid-tap protection, deletion confirmation, 4F design/accessibility surfaces, interaction-state re-entry, and clean-startup privacy boundaries are present without duplicate surfaces.")
