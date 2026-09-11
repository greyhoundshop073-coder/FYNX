#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
def read(path): return (ROOT / path).read_text(encoding='utf-8')

checks=[]
def require(label, condition): checks.append((label, bool(condition)))
server=read('backend/server.js'); management=read('backend/statusManagementRoutes.js'); scale=read('backend/scalability.js')
client=read('app/src/main/java/com/fynx/app/ui/FynxStatusClient.kt'); foundation=read('app/src/main/java/com/fynx/app/ui/FynxStatusFoundation.kt')
composer=read('app/src/main/java/com/fynx/app/ui/FynxStatusComposerPanel.kt'); timeline=read('app/src/main/java/com/fynx/app/ui/FynxStatusTimelinePanel.kt')
hub=read('app/src/main/java/com/fynx/app/ui/FynxStatusHubPanel.kt'); stories=read('app/src/main/java/com/fynx/app/ui/StoriesPanel.kt')
require('backend Status schema + expiry','CREATE TABLE IF NOT EXISTS statuses' in server and 'expires_at TIMESTAMPTZ NOT NULL' in server)
require('backend media ownership','message_media' in server and 'owner_id=$2' in server)
require('backend Status privacy','private_status' in server and 'expires_at > NOW()' in server)
require('owner-only Status deletion','DELETE FROM statuses WHERE id=$1 AND owner_id=$2' in management)
require('management route authentication','jwt.verify(token, JWT_SECRET)' in management)
require('management route production wiring','registerStatusManagementRoutes({ app });' in scale and './statusManagementRoutes.js' in scale)
require('client sends audience','put("audience", status.audience.name)' in client)
require('client supports deletion','FynxBackendClient.delete(context, "/api/statuses/${statusId}")' in client)
require('audience model exists','enum class FynxStatusAudience' in foundation)
require('real image preview','ImageView' in composer and 'setImageURI' in composer)
require('real video preview','VideoView' in composer and 'setVideoURI' in composer)
require('voice recording','MediaRecorder' in composer and 'FYNX_STATUS_MAX_VOICE_DURATION_MS' in composer)
require('friends-only wording','Friends only' in composer and 'Only me' not in composer)
require('single backend Status hub','FynxStatusTimelinePanel()' in hub and 'StoriesPanel()' not in hub)
require('legacy Stories wrapper','FynxStatusTimelinePanel()' in stories and 'SharedPreferences' not in stories)
require('status viewer image','FynxRemoteMedia(it, "image"' in timeline)
require('status viewer video','FynxRemoteMedia(it, "video"' in timeline)
require('status viewer voice','FynxRemoteAudio(it)' in timeline)
require('owner delete UI','FynxStatusClient.delete(context, status.id)' in timeline)
require('24 hour viewer expiry','FYNX_STATUS_EXPIRY_MS' in timeline)
failed=[label for label,ok in checks if not ok]
for label,ok in checks: print(('GREEN' if ok else 'RED')+' - '+label)
if failed:
    print('\nR5B verification failed:')
    for item in failed: print(' - '+item)
    sys.exit(1)
print(f'\nR5B Status/Stories security + presentation gate GREEN ({len(checks)} checks)')
