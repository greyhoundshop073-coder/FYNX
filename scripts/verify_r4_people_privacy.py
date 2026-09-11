from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

checks = []
def check(name, ok):
    checks.append((name, bool(ok)))

profile = read("backend/profileRoutes.js")
privacy = read("backend/privacyRoutes.js")
server = read("backend/server.js")
scalability = read("backend/scalability.js")
social_hardening = read("backend/socialHardening.js")
profile_client = read("app/src/main/java/com/fynx/app/ui/FynxProfileRemoteClient.kt")
profile_panel = read("app/src/main/java/com/fynx/app/ui/ProfilePanel.kt")
other_profile = read("app/src/main/java/com/fynx/app/ui/OtherUserProfilePanel.kt")

check("profile routes use authenticated JWT", "const auth = (req,res,next)" in profile and "jwt.verify(token,JWT_SECRET)" in profile)
check("profile visibility is server enforced", "row.profile_visibility==='Everyone'" in profile and "row.profile_visibility==='My friends'&&friends" in profile)
check("blocked users cannot access profiles", "FROM blocks WHERE (blocker_id=$1 AND blocked_id=$2) OR (blocker_id=$2 AND blocked_id=$1)" in profile and "this profile is unavailable" in profile)
check("post visibility is server enforced on profile posts", "row.posts_visibility==='Everyone'" in profile and "posts are private" in profile)
check("follower/following counts are self-only", "const followerCount=self?" in profile and "const followingCount=self?" in profile and "connectionsVisible:self" in profile)
check("follower list endpoint is scoped to authenticated user", "app.get('/api/social/me/followers'" in profile and "WHERE f.followed_id=$1" in profile)
check("following list endpoint is scoped to authenticated user", "app.get('/api/social/me/following'" in profile and "WHERE f.follower_id=$1" in profile)
check("profile photo updates require owned media", "message_media WHERE id=$1 AND owner_id=$2" in profile and "profile photo is not owned by this account" in profile)
check("profile photo is optional and removable", "hasPhoto=Object.prototype.hasOwnProperty.call" in profile and "profile_photo_media_id=$5" in profile)
check("username changes are collision checked", "lower(username)=lower($1) AND id<>$2" in profile and "username already in use" in profile)
check("privacy settings have server-side constraints", "privacy_settings" in privacy and "OPTIONS=new Set([\"Everyone\",DEFAULT_VISIBILITY,\"Nobody\"])" in privacy)
check("privacy enforcement is registered in production bootstrap", "registerPrivacyRoutes({ app });" in scalability)
check("message privacy is enforced server-side", "messages_visibility" in privacy and "you must be friends with this user to send a message" in privacy)
check("status privacy is enforced server-side", "status_visibility" in privacy and "status posting is disabled" in privacy)
check("profile privacy guard runs before profile route", "insertBeforeRoute(app,\"get\",\"/api/social/profile/:username\"" in privacy)
check("follow relationship is unique and self-follow is blocked", "PRIMARY KEY(follower_id,followed_id)" in server and "cannot follow yourself" in server)
check("follow is blocked across mutual blocks", "FROM blocks WHERE (blocker_id=$1 AND blocked_id=$2) OR (blocker_id=$2 AND blocked_id=$1)" in server and "follow unavailable" in server)
check("follow/unfollow operations are backend-backed", "INSERT INTO social_follows" in server and "DELETE FROM social_follows" in server)
check("reverse friend requests are rejected", "reversePending" in social_hardening and "friend request already pending" in social_hardening)
check("other-user profile never exposes connection stats/lists", 'ProfileStat(\"Followers\"' not in other_profile and 'ProfileStat(\"Following\"' not in other_profile and "followerCount" not in other_profile and "followingCount" not in other_profile)
check("self profile renders real server counts and connection dialogs", 'ProfileStat("Followers"' in profile_panel and 'ProfileStat("Following"' in profile_panel and "FynxProfileRemoteClient.followers" in profile_panel and "FynxProfileRemoteClient.following" in profile_panel)
check("profile client reads server relationship/count fields", "followerCount" in profile_client and "followingCount" in profile_client and "followedByCurrentUser" in profile_client)

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS: " if ok else "FAIL: ") + name)
if failed:
    raise SystemExit("R4 people/privacy audit failed: " + "; ".join(failed))
print(f"R4 people/privacy audit passed ({len(checks)} checks)")
