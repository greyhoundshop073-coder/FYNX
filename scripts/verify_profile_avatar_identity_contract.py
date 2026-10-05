#!/usr/bin/env python3
"""Guard the single profile-avatar identity contract across major FYNX surfaces."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "app/src/main/java/com/fynx/app/ui"

REQUIRED = {
    "FynxRemoteMedia.kt": ["fun FynxRemoteProfileAvatar(", "FynxProfileRemoteClient.cachedProfilePhotoId"],
    "ProfilePanel.kt": ["FynxRemoteProfileAvatar(", "FynxProfileRemoteClient"],
    "OtherUserProfilePanel.kt": ["FynxRemoteProfileAvatar(", "FynxProfileRemoteClient"],
    "FriendsPanel.kt": ["FynxRemoteProfileAvatar(", "profilePhotoMediaId"],
    "FynxContactsPanel.kt": ["FynxRemoteProfileAvatar(", "profilePhotoMediaId"],
    "ChatsPanel.kt": ["FynxRemoteProfileAvatar(", "FynxProfileRemoteClient"],
    "NotificationPanel.kt": ["FynxRemoteProfileAvatar(", "FynxProfileRemoteClient"],
    "FynxHomeCommentsPanel.kt": ["FynxRemoteProfileAvatar(", "profilePhotoMediaId"],
    "FynxStatusTimelinePanel.kt": ["FynxRemoteProfileAvatar(", "profilePhotoMediaId"],
    "FynxMarketplacePanel.kt": ["FynxProfileRemoteClient", "photoId"],
    "FynxProfileRemoteClient.kt": ["profilePhotoMediaId", "saveRemoteProfilePhotoId"],
    "FynxPreferencesStore.kt": ["loadRemoteProfilePhotoId", "saveRemoteProfilePhotoId"],
}

errors = []
for name, needles in REQUIRED.items():
    path = UI / name
    if not path.is_file():
        errors.append(f"missing required avatar integration file: {name}")
        continue
    text = path.read_text(encoding="utf-8")
    for needle in needles:
        if needle not in text:
            errors.append(f"{name}: missing avatar contract marker: {needle}")

# The profile client must persist the server-authoritative value, including removal.
client = (UI / "FynxProfileRemoteClient.kt").read_text(encoding="utf-8")
if "FynxPreferencesStore.saveRemoteProfilePhotoId(context, normalized, profile.profilePhotoMediaId)" not in client:
    errors.append("profile GET does not persist the server-authoritative avatar id")
if "FynxPreferencesStore.saveRemoteProfilePhotoId(context, profile.username, profile.profilePhotoMediaId)" not in client:
    errors.append("profile UPDATE does not persist the server-authoritative avatar id")

if errors:
    print("PROFILE AVATAR CONTRACT RED")
    for error in errors:
        print(f"- {error}")
    raise SystemExit(1)

print("PROFILE AVATAR CONTRACT GREEN")
print(f"Verified {len(REQUIRED)} identity surfaces use the shared remote-avatar contract.")
print("Verified profile GET/UPDATE persist the server-authoritative profilePhotoMediaId, including null removal.")
