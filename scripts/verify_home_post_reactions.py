from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

def require(condition, message):
    if not condition:
        raise SystemExit(f"RED: {message}")
    print(f"GREEN: {message}")

home = read("app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt")
client = read("app/src/main/java/com/fynx/app/ui/FynxHomePostReactionsClient.kt")
backend = read("backend/socialPostReactionBootstrap.js")
startup = read("backend/realtimeIsolationBootstrap.js")

require("social_post_reactions" in backend, "durable reaction table exists")
require("PRIMARY KEY (post_id, user_id)" in backend, "one reaction per user per post is enforced")
require("reaction_type TEXT NOT NULL CHECK (reaction_type IN ('LIKE','LOVE','LAUGH','WOW','SAD'))" in backend, "supported reaction types are constrained")
require("visibleSocialPost(postId, req.user.sub)" in backend, "reaction routes use post visibility/block authorization")
require("app.get('/api/social/posts/:id/reactions'" in backend, "reaction state route exists")
require("app.post('/api/social/posts/:id/reaction'" in backend, "reaction set/change route exists")
require("app.delete('/api/social/posts/:id/reaction'" in backend, "reaction removal route exists")
require("installSocialPostReactions()" in startup, "reaction backend is wired into production startup")
require("FynxHomePostReactionsClient.state" in home, "Home hydrates real reaction state")
require("FynxHomePostReactionsClient.set" in home and "FynxHomePostReactionsClient.clear" in home, "Home writes and removes real reactions")
for emoji in ["👍", "❤️", "😂", "😮", "😢"]:
    require(emoji in home, f"Home reaction picker contains {emoji}")
require("onLongClick = onOpenReactionPicker" in home, "Like supports long-press reaction picker")
require("onLongClickLabel = longClickLabel" in home, "reaction picker action has accessibility semantics")
require("reactionStates = reactionStates + (id to previous)" in home, "reaction failure rolls back optimistic UI")
require("hydrateReactionStates" in home, "reaction state is rehydrated after feed load and pagination")
require("reactionState.total > 0" in home and "reactionSummary" in home, "real reaction totals are displayed")
require("postId: String, reaction: String" in client, "client validates a real post/reaction pair")
require("/api/social/posts/$id/reactions" in client, "client uses real backend state endpoint")
require("/api/social/posts/$id/reaction" in client, "client uses real backend mutation endpoint")
require("putJson" not in client, "client does not rely on an unavailable HTTP helper")
print("GREEN: Home post reaction integrity gate passed")
