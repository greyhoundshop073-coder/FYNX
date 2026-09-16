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
r5b_verifier = read('scripts/verify_r5b_status.py')
fcm_verifier = read('scripts/verify_fcm_notifications.py')
ai_security = read('scripts/verify_ai_security.py')
media_privacy = read('backend/mediaPrivacy.js')

# This chat owns media consistency, notifications/settings integration and the AI layer.
# Marketplace, Groups, Calls, Trust & Safety, Admin/Business and R6-G are owned by the other chat.
# They must not be allowed to turn this-chat's gate red.
check('remote media uses the authenticated central downloader', 'FynxBackendClient.downloadToFile' in remote_media and 'MAX_REMOTE_MEDIA_BYTES' in remote_media)
check('remote media is account scoped', 'FynxAuthStore.accountStorageKey(context)' in remote_media and 'FynxBackendClient.hasAccessToken(context)' in remote_media)
check('Status timeline uses the shared remote audio renderer', 'FynxRemoteAudio(it)' in timeline)
check('R5B audio verifier recognizes the shared download helper', 'downloadRemoteMedia(context, resolvedUrl, target)' in r5b_verifier and 'p.setDataSource(finalFile.absolutePath)' in r5b_verifier)
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
