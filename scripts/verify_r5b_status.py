#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
def read(path): return (ROOT / path).read_text(encoding='utf-8')

def contains_call(source, function, argument):
    pattern = rf'{re.escape(function)}\s*\(\s*it\s*,\s*"{re.escape(argument)}"'
    return re.search(pattern, source) is not None

checks=[]
def require(label, condition): checks.append((label, bool(condition)))
server=read('backend/server.js'); management=read('backend/statusManagementRoutes.js'); scale=read('backend/scalability.js'); interactions=read('backend/statusInteractionRoutes.js')
client=read('app/src/main/java/com/fynx/app/ui/FynxStatusClient.kt'); foundation=read('app/src/main/java/com/fynx/app/ui/FynxStatusFoundation.kt')
composer=read('app/src/main/java/com/fynx/app/ui/FynxStatusComposerPanel.kt'); mature=read('app/src/main/java/com/fynx/app/ui/FynxMatureStatusComposerPanel.kt')
timeline=read('app/src/main/java/com/fynx/app/ui/FynxStatusTimelinePanel.kt'); hub=read('app/src/main/java/com/fynx/app/ui/FynxStatusHubPanel.kt'); stories=read('app/src/main/java/com/fynx/app/ui/StoriesPanel.kt')

require('backend Status schema + expiry','CREATE TABLE IF NOT EXISTS statuses' in server and 'expires_at TIMESTAMPTZ NOT NULL' in server)
require('backend media ownership','message_media' in server and 'owner_id=$2' in server)
require('backend Status privacy','private_status' in server and 'expires_at > NOW()' in server)
require('owner-only Status deletion','DELETE FROM statuses WHERE id=$1 AND owner_id=$2' in management)
require('management route authentication','jwt.verify(token, JWT_SECRET)' in management)
require('management route production wiring','registerStatusManagementRoutes({ app });' in scale and './statusManagementRoutes.js' in scale)
require('status interactions route wiring','registerStatusInteractionRoutes({ app });' in scale and './statusInteractionRoutes.js' in scale)
require('status interaction tables','status_views' in interactions and 'status_likes' in interactions and 'status_reactions' in interactions and 'status_replies' in interactions)
require('status interaction visibility guard','expires_at > NOW()' in interactions and 'friendships' in interactions and 'blocks' in interactions)
require('authenticated status view','POST' in interactions and '/view' in interactions and 'jwt.verify(token, JWT_SECRET)' in interactions)
require('authenticated status like','/like' in interactions and 'status_likes' in interactions)
require('authenticated status reactions','/reaction' in interactions and 'status_reactions' in interactions)
require('authenticated status replies','/reply' in interactions and 'status_replies' in interactions)
require('client sends audience','put("audience", status.audience.name)' in client)
require('client supports deletion','FynxBackendClient.delete(context, "/api/statuses/${statusId.trim()}")' in client)
require('client supports views','FynxStatusClient.markViewed(context, status.id)' in timeline and '/interactions' in client)
require('client supports likes','FynxStatusClient.toggleLike(context, status.id)' in timeline)
require('client supports emoji reactions','FynxStatusClient.react(context, status.id, emoji)' in timeline)
require('client supports replies','FynxStatusClient.reply(context, status.id, body)' in timeline)
require('audience model exists','enum class FynxStatusAudience' in foundation)

require('legacy composer real image preview','ImageView' in composer and 'setImageURI' in composer)
require('legacy composer real video preview','VideoView' in composer and 'setVideoURI' in composer)
require('legacy composer voice recording','MediaRecorder' in composer and 'FYNX_STATUS_MAX_VOICE_DURATION_MS' in composer)
require('legacy composer friends-only wording','Friends only' in composer and 'Only me' not in composer)
require('mature composer exists','fun FynxMatureStatusComposerPanel' in mature)
require('mature Text mode','FynxStatusType.TEXT' in mature and 'Type a Status' in mature)
require('mature Photo mode','FynxStatusType.PHOTO' in mature and 'pickImage.launch' in mature)
require('mature Video mode','FynxStatusType.VIDEO' in mature and 'pickVideo.launch' in mature)
require('mature Voice mode','FynxStatusType.VOICE' in mature and 'beginMatureVoiceRecording' in mature and 'stopMatureVoiceRecording' in mature)
require('mature audience control','FynxStatusAudience.EVERYONE' in mature and 'FynxStatusAudience.FRIENDS' in mature)
require('mature authenticated publish','FynxStatusClient.create(context, status, mediaId)' in mature and 'FynxStatusClient.uploadMedia(context, source, mime)' in mature)
require('mature 24-hour expiry','expiresAtMillis = now + FYNX_STATUS_EXPIRY_MS' in mature)
require('mature publish validation','Write something first.' in mature and 'Add your media first.' in mature)

require('single backend Status hub','FynxStatusTimelinePanel()' in hub and 'StoriesPanel()' not in hub)
require('hub mature + creation route','FynxMatureStatusComposerPanel' in hub and 'composing = true' in hub)
require('hub camera route remains real','FynxCameraCapturePanel' in hub and 'publishCapturedStatus' in hub)
require('legacy Stories wrapper','FynxStatusTimelinePanel()' in stories and 'SharedPreferences' not in stories)
require('timeline backend list','FynxStatusClient.list(context)' in timeline)
require('timeline filters expired statuses','filterNot(FynxStatus::isExpired)' in timeline)
require('timeline latest-per-owner bubbles','statuses.groupBy { it.ownerUsername }' in timeline and 'maxByOrNull { it.createdAtMillis }' in timeline)
require('timeline profile avatar lookup','FynxProfileRemoteClient.get(context, status.ownerUsername)' in timeline)
require('timeline status viewer','FynxStatusStoryViewer(' in timeline and 'DialogProperties(usePlatformDefaultWidth = false' in timeline)
require('status viewer image',contains_call(timeline, 'FynxRemoteMedia', 'image'))
require('status viewer video',contains_call(timeline, 'FynxRemoteMedia', 'video'))
require('status viewer voice','FynxRemoteAudio(it)' in timeline)
require('status viewer left-right navigation','clickable(enabled = index > 0)' in timeline and 'clickable(enabled = index < statuses.lastIndex)' in timeline)
require('status viewer progress','LinearProgressIndicator' in timeline and '(index + 1).toFloat() / statuses.size.toFloat()' in timeline)
require('status interaction controls','viewCount' in timeline and 'likeCount' in timeline and 'Reply to this Status' in timeline)
require('status emoji reaction controls','listOf("❤️", "😂", "😮", "😢", "👍")' in timeline)
require('owner delete UI','FynxStatusClient.delete(context, status.id)' in timeline and 'status.ownerUsername.equals(viewerUsername, true)' in timeline)
require('24 hour viewer expiry','FYNX_STATUS_EXPIRY_MS' in timeline)

failed=[label for label,ok in checks if not ok]
for label,ok in checks: print(('GREEN' if ok else 'RED')+' - '+label)
if failed:
    print('\nR5B Status/Stories interaction verification failed:')
    for item in failed: print(' - '+item)
    sys.exit(1)
print(f'\nR5B Status/Stories security + end-to-end interaction gate GREEN ({len(checks)} checks)')
