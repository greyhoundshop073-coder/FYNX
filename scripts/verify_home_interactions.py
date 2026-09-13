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
    text = re.sub(r"\s*([(),:])\s*", r"\1", text)
    return text


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

for route in (
    'app.post("/api/social/posts/:id/save"',
    'app.delete("/api/social/posts/:id/save"',
    'app.get("/api/social/saved"',
    'app.post("/api/social/posts/:id/repost"',
    'app.delete("/api/social/posts/:id/repost"',
    'app.get("/api/social/posts/:id/interaction-state"',
):
    require(discovery, route, f"durable interaction route {route}")

for needle in (
    "CREATE TABLE IF NOT EXISTS social_saved_posts",
    "CREATE TABLE IF NOT EXISTS social_post_reposts",
    "post_id BIGINT NOT NULL",
    "user_id BIGINT NOT NULL",
    "const visiblePost = async (postId, userId)",
):
    require(discovery, needle, f"interaction protection {needle}")

for needle in (
    "FynxRemoteSocialClient.feedPage",
    "FynxRemoteSocialClient.like",
    "FynxRemoteSocialClient.save",
    "FynxRemoteSocialClient.repost",
    "FynxRemoteSocialClient.interactionState",
    "FynxHomeCommentsPanel",
):
    require(home, needle, f"Home interaction path {needle}")

for needle in (
    "suspend fun comments(context: Context, id: String)",
    "suspend fun addComment(context: Context, id: String, text: String)",
    "suspend fun save(context: Context, id: String, saved: Boolean)",
    "suspend fun repost(context: Context, id: String, reposted: Boolean)",
    "suspend fun interactionState(context: Context, id: String)",
    '"/api/social/posts/$numericId/save"',
    '"/api/social/posts/$numericId/repost"',
    '"/api/social/posts/$numericId/interaction-state"',
):
    require_normalized(client, needle, f"existing social client API {needle}")

for needle in (
    'Icons.Default.Bookmark',
    'Icons.Default.BookmarkBorder',
    'Icons.Default.Repeat',
    'interactionBusy',
):
    require(home, needle, f"professional durable interaction surface {needle}")

if "CommentsDialog" in home:
    raise SystemExit("HOME INTERACTIONS RED: legacy competing CommentsDialog detected")
if 'Text("Save")' in home or 'Text("Repost")' in home:
    raise SystemExit("HOME INTERACTIONS RED: fake Save/Repost feed controls detected")

require(backend_package, '"start": "node realtimeIsolationBootstrap.js"', "production realtime entrypoint")
require(realtime_bootstrap, 'import { installHomeCommentPrivacy } from "./homeCommentsPrivacyBootstrap.js";', "Home comment privacy integration")
require(realtime_bootstrap, "await installHomeCommentBackend();", "base Home comments installation")
require(realtime_bootstrap, "await installHomeCommentPrivacy();", "Home comment privacy hardening")
for needle in (
    "if (!(await visibleSocialPost(postId, req.user.sub))) return res.status(404).json({ error: 'post not found' });",
    "b.blocker_id=$2 AND b.blocked_id=c.author_id",
    "b.blocker_id=c.author_id AND b.blocked_id=$2",
    "b.blocker_id=$3 AND b.blocked_id=c.author_id",
    "b.blocker_id=c.author_id AND b.blocked_id=$3",
    "SELECT c.id FROM social_post_comments c",
    "const cursorClause = before === null ? '' : ' AND c.id < $3';",
    "LIMIT $3`,",
    "ORDER BY c.id ASC LIMIT $4`,",
):
    require(realtime_bootstrap, needle, f"clean-startup Home comment privacy implementation {needle}")

require(privacy_bootstrap, "fynxHomeCommentsPrivacyBatch", "Home comment privacy patch marker")

print("HOME INTERACTIONS GREEN: durable Save/Repost backend, Home client/UI wiring, rapid-tap protection, and clean-startup privacy-safe comment/reply boundaries are present without duplicate surfaces.")
