#!/usr/bin/env python3
"""Large Badge #2 — Real Social Creation & Connection integrity gate.

This gate verifies that the existing FYNX social creation/connection stack is
wired to authenticated backend/data paths. It deliberately rejects fake seed
users/posts/interactions as a testing shortcut.
"""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]

def read(path):
    p = ROOT / path
    if not p.exists():
        raise SystemExit(f"RED: required file missing: {path}")
    return p.read_text(encoding="utf-8")

home = read("app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt")
client = read("app/src/main/java/com/fynx/app/ui/FynxRemoteSocialClient.kt")
composer = read("app/src/main/java/com/fynx/app/ui/FynxHomeSocialHubPanel.kt")
multimedia = read("app/src/main/java/com/fynx/app/ui/FynxMultiMediaPostClient.kt")
social_routes = read("backend/socialRoutes.js")
discovery = read("backend/discoveryRoutes.js")
follow = read("backend/followRoutes.js")
reactions = read("backend/socialPostReactionBootstrap.js")
notifications = read("backend/notificationPush.js")
notification_bootstrap = read("backend/notificationBootstrap.js")
startup = read("backend/package.json")
runtime = read("scripts/verify_runtime_navigation.py")

checks = [
    ("Create Post uses the real Home social composer", "onCreatePost: () -> Unit" in home and "FynxMultiMediaPostClient.createPost" in composer),
    ("Create Post supports real authenticated media upload", "FynxProductionMessaging.uploadMedia" in multimedia),
    ("Create Post publishes through real backend social routes", '"/api/social/posts"' in client and '"/api/social/posts/multi"' in multimedia),
    ("server returns authoritative post identity", 'optString("postId")' in multimedia or 'optString("postId")' in client),
    ("post media is account-owned before publishing", "owner_id=$2" in social_routes or "owner_id=$2" in multimedia),
    ("Home feed is backend/database driven", '"/api/social/feed' in client and "FROM social_posts" in social_routes),
    ("Like is persisted against the authenticated user", "social_post_likes" in social_routes and "req.user.sub" in social_routes),
    ("Comment is persisted against the authenticated user", "social_post_comments" in social_routes and "req.user.sub" in social_routes),
    ("Save is durable and account-scoped", "social_saved_posts" in discovery and "user_id BIGINT NOT NULL" in discovery),
    ("Repost is durable and account-scoped", "social_post_reposts" in discovery and "PRIMARY KEY (post_id, user_id)" in discovery),
    ("Save/Repost enforce post visibility", "const visiblePost = async" in discovery and "visiblePost(postId, req.user.sub)" in discovery),
    ("Follow/unfollow is real and authenticated", "app.post(\"/api/social/follow/:username\", auth" in follow and "DELETE FROM social_follows" in follow),
    ("Follow blocks self-follow and blocked relationships", "cannot follow yourself" in follow and "follow unavailable" in follow),
    ("Post author identity opens the real profile route", "onOpenProfile = { openAuthorStatus" in home and "onOpenAuthorProfile" in home),
    ("Like/reaction notification hook is server-side", "queueFynxNotification" in reactions and "type: 'REACTION'" in reactions),
    ("Comment/reply notification hook is server-side", "queueFynxNotification" in notification_bootstrap and "type:'COMMENT'" in notification_bootstrap),
    ("Follow notification hook fires only for a new relationship", "queueFynxNotification" in follow and "type: \"FOLLOW\"" in follow and "inserted.rowCount > 0" in follow),
    ("Notification delivery remains server-side", "queueFynxNotification" in notifications and "FIREBASE_SERVICE_ACCOUNT_JSON" in notifications),
    ("Production startup installs social + notification wiring", "notificationBootstrap.js" in startup),
    ("Runtime diagnostic captures APK screenshots/UI hierarchies", "screencap" in runtime and "uiautomator" in runtime),
    ("Runtime diagnostic refuses to manufacture social data", "no fake application data is created" in runtime.lower() and "no fake account/data is created" in runtime.lower()),
]

# Explicit anti-fabrication guard: the badge must never be made green by adding
# hard-coded demo social records to the production verification path.
for forbidden in ("INSERT INTO users", "INSERT INTO social_posts", "INSERT INTO social_post_likes", "INSERT INTO social_follows"):
    if forbidden in runtime:
        raise SystemExit(f"RED: runtime diagnostic attempts to manufacture data: {forbidden}")

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(f"{'PASS' if ok else 'FAIL'}: {name}")

if failed:
    raise SystemExit(f"Large Badge #2 social creation/connection gate RED: {len(failed)} check(s)")

print(f"Large Badge #2 social creation/connection gate GREEN ({len(checks)} checks)")
