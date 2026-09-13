from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise SystemExit(f"HOME INTERACTIONS RED: missing {label}: {needle}")


def normalize_source(text: str) -> str:
    """Normalize harmless Kotlin whitespace around punctuation and type separators."""
    text = " ".join(text.split())
    text = re.sub(r"\s*([(),:])\s*", r"\1", text)
    return text


def require_normalized(text: str, needle: str, label: str) -> None:
    normalized_text = normalize_source(text)
    normalized_needle = normalize_source(needle)
    if normalized_needle not in normalized_text:
        raise SystemExit(f"HOME INTERACTIONS RED: missing {label}: {needle}")


discovery = read("backend/discoveryRoutes.js")
home = read("app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt")
comments_panel = read("app/src/main/java/com/fynx/app/ui/FynxHomeCommentsPanel.kt")
client = read("app/src/main/java/com/fynx/app/ui/FynxRemoteSocialClient.kt")
privacy_bootstrap = read("backend/homeCommentsPrivacyBootstrap.js")
backend_package = read("backend/package.json")

# Durable Save/Repost must remain server-backed; never replace these with UI-only state.
for route in (
    'app.post("/api/social/posts/:id/save"',
    'app.delete("/api/social/posts/:id/save"',
    'app.get("/api/social/saved"',
    'app.post("/api/social/posts/:id/repost"',
    'app.delete("/api/social/posts/:id/repost"',
    'app.get("/api/social/posts/:id/interaction-state"',
):
    require(discovery, route, f"durable interaction route {route}")

# Persistence must be account-scoped and protected by the backend's real visibility helper.
for needle in (
    "CREATE TABLE IF NOT EXISTS social_saved_posts",
    "CREATE TABLE IF NOT EXISTS social_post_reposts",
    "post_id BIGINT NOT NULL",
    "user_id BIGINT NOT NULL",
    "const visiblePost = async (postId, userId)",
):
    require(discovery, needle, f"interaction protection {needle}")

# Home must use the authoritative feed and the single dedicated comments experience.
for needle in (
    "FynxRemoteSocialClient.feedPage",
    "FynxRemoteSocialClient.like",
    "FynxHomeCommentsPanel",
):
    require(home, needle, f"Home interaction path {needle}")

# The dedicated comments surface must continue using the existing social client APIs.
for needle in (
    "FynxRemoteSocialClient.comments",
    "FynxRemoteSocialClient.addComment",
):
    require(comments_panel, needle, f"dedicated comments client path {needle}")

# Existing comments client must remain the source used by Home; do not introduce a duplicate client.
# Kotlin permits harmless formatting differences, including omitted spaces around ':' and ','.
for needle in (
    "suspend fun comments(context: Context, id: String)",
    "suspend fun addComment(context: Context, id: String, text: String)",
):
    require_normalized(client, needle, f"existing social client API {needle}")

# The old competing comments dialog must not return alongside the dedicated surface.
if "CommentsDialog" in home:
    raise SystemExit("HOME INTERACTIONS RED: legacy competing CommentsDialog detected")

# The old fake controls must not silently return to the feed card.
if 'Text("Save")' in home or 'Text("Repost")' in home:
    raise SystemExit("HOME INTERACTIONS RED: fake Save/Repost feed controls detected")

# Comment/reply reads must apply the same symmetric block boundary as the rest of Social.
require(backend_package, '"start": "node homeCommentsPrivacyBootstrap.js"', "privacy-safe backend entrypoint")
require(privacy_bootstrap, "fynxHomeCommentsPrivacyBatch", "Home comment privacy patch marker")
require(privacy_bootstrap, "if (!(await visibleSocialPost(postId, req.user.sub))) return res.status(404).json({ error: 'post not found' });", "removed/private post boundary")
require(privacy_bootstrap, "b.blocker_id=$2 AND b.blocked_id=c.author_id", "blocked comment author filter")
require(privacy_bootstrap, "b.blocker_id=c.author_id AND b.blocked_id=$2", "reverse blocked comment author filter")
require(privacy_bootstrap, "b.blocker_id=$3 AND b.blocked_id=c.author_id", "blocked reply author filter")
require(privacy_bootstrap, "b.blocker_id=c.author_id AND b.blocked_id=$3", "reverse blocked reply author filter")
require(privacy_bootstrap, "SELECT c.id FROM social_post_comments c", "blocked parent validation")
require(privacy_bootstrap, "const cursorClause = before === null ? '' : ' AND c.id < $4';", "cursor parameter remains aligned after privacy filter")
require(privacy_bootstrap, "LIMIT $3`,", "comment page limit parameter remains aligned")
require(privacy_bootstrap, "ORDER BY c.id ASC LIMIT $4`,", "reply limit parameter remains aligned")

print("HOME INTERACTIONS GREEN: durable Save/Repost backend, dedicated Home comments/feed wiring, and privacy-safe comment/reply boundaries are present; deleted/private/blocked content is rejected or filtered without duplicate surfaces.")
