from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
checks = []

def read(path):
    p = ROOT / path
    return p.read_text(encoding='utf-8') if p.is_file() else ''

def check(name, ok):
    checks.append((name, bool(ok)))

remote_media = read('app/src/main/java/com/fynx/app/ui/FynxRemoteMedia.kt')
timeline = read('app/src/main/java/com/fynx/app/ui/FynxStatusTimelinePanel.kt')
market = read('app/src/main/java/com/fynx/app/ui/FynxMarketplaceRemotePanel.kt')
profile = read('app/src/main/java/com/fynx/app/ui/ProfilePanel.kt')
profile_client = read('app/src/main/java/com/fynx/app/ui/FynxProfileRemoteClient.kt')
prefs = read('app/src/main/java/com/fynx/app/ui/FynxPreferencesStore.kt')
r5b_verifier = read('scripts/verify_r5b_status.py')
fcm_verifier = read('scripts/verify_fcm_notifications.py')
ai_security = read('scripts/verify_ai_security.py')
media_privacy = read('backend/mediaPrivacy.js')

# This gate verifies the shared media/notification/AI integration owned by this workstream.
check('remote media uses the authenticated central downloader', 'FynxBackendClient.downloadToFile' in remote_media and 'MAX_REMOTE_MEDIA_BYTES' in remote_media)
check('remote media is account scoped', 'FynxAuthStore.accountStorageKey(context)' in remote_media and 'FynxBackendClient.hasAccessToken(context)' in remote_media)
check('Status timeline uses the shared remote audio renderer', re.search(r'FynxRemoteAudio\s*\(\s*it\s*(?:,|\))', timeline) is not None)
check('Status circles use cache only until remote profile authority arrives', 'cachedProfilePhotoId(context, status.ownerUsername)' in timeline and 'remoteProfileLoaded' in timeline and 'profilePhotoMediaId = it.profilePhotoMediaId' in timeline and 'profilePhotoMediaId ?: cachedPhotoId' not in timeline)
check('profile cold start hydrates the cached remote avatar', 'cachedProfilePhotoId(context, it)' in profile and 'remotePhotoId = remote.profilePhotoMediaId' in profile and 'remoteProfileLoaded = true' in profile)
check('remote profile success persists authoritative avatar identity', 'saveRemoteProfilePhotoId(context, normalized, profile.profilePhotoMediaId)' in profile_client)
check('chat list clears stale avatar when server photo is removed', 'else chat.copy(avatarUri = null)' in read('app/src/main/java/com/fynx/app/ui/ChatsPanel.kt'))
check('conversation keeps cached avatar only while remote profile is loading', 'var remoteProfileLoaded by remember(chat.username)' in read('app/src/main/java/com/fynx/app/ui/ConversationPanel.kt') and 'if (remoteProfileLoaded)' in read('app/src/main/java/com/fynx/app/ui/ConversationPanel.kt'))
check('conversation marks remote profile loaded after successful fetch', 'remoteProfileLoaded = true' in read('app/src/main/java/com/fynx/app/ui/ConversationPanel.kt'))
check('Marketplace seller avatar uses cache only until server authority arrives', 'cachedSellerPhotoId' in market and 'remoteSellerProfileLoaded' in market and 'sellerPhotoId = it.profilePhotoMediaId' in market)
check('identity cache is account namespaced', 'KEY_REMOTE_IDENTITY_CACHE' in prefs and 'accountNamespace(context)' in prefs)
check('identity cache is cleared at the session boundary', 'getSharedPreferences("${KEY_REMOTE_IDENTITY_CACHE}_$accountNamespace"' in prefs)
check('R5B audio verifier recognizes the shared renderer', 'def contains_remote_audio_renderer(source):' in r5b_verifier and "require('remote audio renderer',contains_remote_audio_renderer(remote_media))" in r5b_verifier)
check('media privacy guard remains installed', 'app.use("/api/media", mediaGuard)' in media_privacy)
check('FCM notification gate remains present', 'notification deep-link routing' in fcm_verifier and 'FCM verification GREEN' in fcm_verifier)
check('AI security gate remains present', 'AI provider key stays server-side' in ai_security and 'AI security gate GREEN' in ai_security)

client_sources = '\n'.join(str(p.read_text(encoding='utf-8')) for p in (ROOT / 'app/src/main/java/com/fynx/app/ui').glob('*.kt'))
check('this chat adds no obvious client API secrets', not re.search(r'sk-[A-Za-z0-9_-]{20,}|AIza[0-9A-Za-z_-]{30,}|ghp_[A-Za-z0-9]{30,}', client_sources))

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(('PASS: ' if ok else 'FAIL: ') + name)
if failed:
    raise SystemExit('FYNX this-chat workstream gate failed: ' + '; '.join(failed))
print(f'FYNX this-chat workstream gate passed ({len(checks)} checks)')

# Profile identity batch: server authority also governs Status and Marketplace after hydration.
