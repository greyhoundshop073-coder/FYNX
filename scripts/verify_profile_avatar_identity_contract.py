#!/usr/bin/env python3
"""Guard the shared profile-avatar identity and invalidation contract across FYNX surfaces."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "app/src/main/java/com/fynx/app/ui"

REQUIRED = {
    "FynxRemoteMedia.kt": ["fun FynxRemoteProfileAvatar(", "FynxProfileRemoteClient.cachedProfilePhotoId", "FynxAvatarIdentityStore.revisions()"],
    "FynxAvatarIdentityStore.kt": ["object FynxAvatarIdentityStore", "fun publish("],
    "ProfilePanel.kt": ["FynxRemoteProfileAvatar(", "FynxProfileRemoteClient"],
    "OtherUserProfilePanel.kt": ["FynxRemoteProfileAvatar(", "FynxProfileRemoteClient"],
    "FriendsPanel.kt": ["FynxRemoteProfileAvatar(", "profilePhotoMediaId"],
    "FynxContactsPanel.kt": ["FynxRemoteProfileAvatar(", "profilePhotoMediaId"],
    "ChatsPanel.kt": ["FynxRemoteProfileAvatar(", "FynxProfileRemoteClient"],
    "NotificationPanel.kt": ["FynxRemoteProfileAvatar(", "FynxProfileRemoteClient"],
    "FynxHomeCommentsPanel.kt": ["FynxRemoteProfileAvatar(", "profilePhotoMediaId"],
    "FynxStatusTimelinePanel.kt": ["FynxRemoteProfileAvatar(", "profilePhotoMediaId"],
    "FynxMarketplacePanel.kt": ["FynxProfileRemoteClient", "photoId", "MarketplaceSellerCard", "FynxRemoteProfileAvatar(", "ownerUsername = l.sellerUsername", "detailSellerPhotoId"],
    "FynxMarketplaceReferenceProductComponents.kt": ["MarketplaceReferenceProductCard(", "FynxRemoteProfileAvatar(", "ownerUsername = listing.sellerUsername"],
    "FynxMarketplaceSellerStoreDialog.kt": ["FynxRemoteProfileAvatar(", "ownerUsername = username"],
    "FynxMarketplaceDetailsSearchReference.kt": ["FynxRemoteProfileAvatar(", "ownerUsername = listing.sellerUsername"],
    "HomePanel.kt": ["FynxRemoteProfileAvatar(", "FynxProfileRemoteClient"],
    "FynxRemoteHomeSocialPanel.kt": ["FynxRemoteProfileAvatar(", "authorUsername"],
    "FynxUniversalSearchPanel.kt": ["FynxRemoteProfileAvatar(", "person.profilePhotoMediaId"],
    "FynxVisibleUpdatesPanel.kt": ["FynxRemoteProfileAvatar(", "ownerPhotoIds"],
    "FynxProfileContent.kt": ["FynxRemoteProfileAvatar(", "profilePhotoId"],
    "FynxVerifiedBadge.kt": ["fun FynxAvatar(", "FynxRemoteProfileAvatar("],
    "FynxApp.kt": ["FynxRemoteProfileAvatar(", "remoteMyPhotoId", "ownerUsername = myProfile.username"],
    "FynxGroupsPanel.kt": ["FynxRemoteProfileAvatar(", "ownerUsername = member.username"],
    "ConversationPanel.kt": ["FynxRemoteProfileAvatar(", "ownerUsername = chat.username"],
    "FynxCallsPanel.kt": ["FynxRemoteProfileAvatar(", "ownerUsername = call.name", "currentUsername = authSession.username"],
    "FynxActiveCallPanel.kt": ["FynxRemoteProfileAvatar(", "ownerUsername = name", "FynxCallRemotePlaceholder(name, Modifier.fillMaxSize(), ownerUsername = name", "ownerUsername = currentUsername?.takeIf { it.isNotBlank() }"],
    "FynxProfileRemoteClient.kt": ["profilePhotoMediaId", "saveRemoteProfilePhotoId", "FynxAvatarIdentityStore.publish"],
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

client = (UI / "FynxProfileRemoteClient.kt").read_text(encoding="utf-8")
if "FynxPreferencesStore.saveRemoteProfilePhotoId(context, normalized, profile.profilePhotoMediaId)" not in client:
    errors.append("profile GET does not persist the server-authoritative avatar id")
if "FynxPreferencesStore.saveRemoteProfilePhotoId(context, profile.username, profile.profilePhotoMediaId)" not in client:
    errors.append("profile UPDATE does not persist the server-authoritative avatar id")
if client.count("FynxAvatarIdentityStore.publish(") < 2:
    errors.append("profile GET and UPDATE must both publish avatar invalidation")

preferences = (UI / "FynxPreferencesStore.kt").read_text(encoding="utf-8")
for needle in ("NO_REMOTE_PROFILE_PHOTO", "hasRemoteProfilePhotoIdEntry", "putString("):
    if needle not in preferences:
        errors.append(f"FynxPreferencesStore.kt: missing tri-state avatar cache marker: {needle}")
if "hasCachedProfilePhotoEntry" not in client:
    errors.append("profile client does not expose whether avatar cache is authoritative")

remote_media = (UI / "FynxRemoteMedia.kt").read_text(encoding="utf-8")
for needle in ("cachedOwnerPhotoExists", "cachedOwnerPhotoId", "else -> mediaId?.trim()?.takeIf { it.isNotBlank() }"):
    if needle not in remote_media:
        errors.append(f"FynxRemoteMedia.kt: missing avatar ID fallback/removal safeguard: {needle}")

if errors:
    print("PROFILE AVATAR CONTRACT RED")
    for error in errors:
        print(f"- {error}")
    raise SystemExit(1)

print("PROFILE AVATAR CONTRACT GREEN")
print(f"Verified {len(REQUIRED)} identity surfaces use the shared remote-avatar contract, including marketplace sellers, call history, and active-call video fallback.")
print("Verified profile GET/UPDATE persist the server-authoritative profilePhotoMediaId and publish invalidation, including null removal.")
