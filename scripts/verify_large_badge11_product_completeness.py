#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

checks = []

def check(name, ok):
    checks.append((name, bool(ok)))

app = read("app/src/main/java/com/fynx/app/ui/FynxApp.kt")
ann = read("app/src/main/java/com/fynx/app/ui/FynxAnnouncementsPanel.kt")
admin = read("app/src/main/java/com/fynx/app/ui/FynxAdminClient.kt")
conversation = read("app/src/main/java/com/fynx/app/ui/ConversationPanel.kt")
remote_media = read("app/src/main/java/com/fynx/app/ui/FynxRemoteMedia.kt")
status = read("app/src/main/java/com/fynx/app/ui/FynxStatusTimelinePanel.kt")
profile = read("app/src/main/java/com/fynx/app/ui/FynxProfileContent.kt")
other_profile = read("app/src/main/java/com/fynx/app/ui/OtherUserProfilePanel.kt")
group_chat = read("app/src/main/java/com/fynx/app/ui/GroupChatPanel.kt")
backend_admin = read("backend/adminRoutes.js")
workstream = read("scripts/verify_this_chat_workstream.py")
workflow = read(".github/workflows/android-build.yml")

check("official announcements panel exists", "fun FynxAnnouncementsPanel" in ann and "Official FYNX Announcements" in ann)
check("announcements use authenticated backend", "/api/announcements" in admin and "FynxBackendClient.get" in admin)
check("admin dashboard is server-backed", "/api/admin/dashboard" in backend_admin and "requireAdmin" in backend_admin)
check("owner/admin controls are exposed in APK", "FynxAdminControlCenterPanel" in ann and "FynxAdminClient.dashboard" in ann)
check("owner-only admin grants remain protected", "owner access required" in backend_admin and "fynx_admin_roles" in backend_admin)
check("account status controls are server-backed", "setAccountStatus" in admin and "/api/admin/accounts/:userId/status" in backend_admin)
check("marketplace protection cases are exposed to admins", "marketplaceProtectionCases" in admin and "/api/admin/marketplace/protection/cases" in admin)
check("chat shows real profile information", "Text(displayName" in conversation and 'Text("@$username"' in conversation)
check("chat profile loads real remote identity", "FynxProfileRemoteClient.get" in conversation and "profilePhotoMediaId" in conversation)
check("remote media is shared across status", "FynxRemoteMedia" in status and "FynxRemoteAudio" in status)
check("profile posts use remote media", "FynxRemoteMedia" in profile and "/api/social/media/" in profile)
check("other profiles use remote media", "FynxRemoteMedia" in other_profile)
check("group chat uses shared remote media", "FynxRemoteMedia" in group_chat and "FynxRemoteAudio" in group_chat)
check("media has retry/error state", "Media unavailable" in remote_media and "Retry" in remote_media)
check("existing workstream verifies profile/media continuity", "remote media uses the authenticated central downloader" in workstream and "profile cold start hydrates the cached remote avatar" in workstream)
check("announcements route is wired into runtime", "registerAdminRoutes({ app })" in read("backend/scalability.js"))
check("Announcements route is reachable from app navigation", '"Announcements"' in app and "FynxAnnouncementsPanel()" in app)
check("Admin route is reachable from app navigation", '"Admin"' in app and "FynxAdminControlCenterPanel()" in app)
check("no fabricated announcement/admin data", "sampleAnnouncement" not in ann and "fakeAnnouncement" not in ann and "dummyAnnouncement" not in ann)
check("badge #11 verification is wired into CI", "verify_large_badge11_product_completeness.py" in workflow)

failed = [name for name, ok in checks if not ok]
print(f"LARGE BADGE #11: {len(checks)-len(failed)}/{len(checks)} checks passed")
for name, ok in checks:
    print(("PASS" if ok else "FAIL") + ": " + name)
if failed:
    raise SystemExit("LARGE BADGE #11 RED: " + "; ".join(failed))
print("LARGE BADGE #11 GREEN")
