#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
def read(path): return (ROOT / path).read_text(encoding='utf-8')

def contains_call(source, function, argument):
    pattern = rf'{re.escape(function)}\s*\(\s*it\s*,\s*"{re.escape(argument)}"'
    return re.search(pattern, source) is not None

def contains_remote_audio_call(source):
    return re.search(r'FynxRemoteAudio\s*\(\s*it\s*(?:,|\))', source) is not None

def contains_remote_audio_renderer(source):
    return (
        'downloadRemoteMedia(context, resolvedUrl, target)' in source
        and 'MediaPlayer()' in source
        and re.search(r'(?<![A-Za-z0-9_])setDataSource\(finalFile\.absolutePath\)', source) is not None
    )

checks=[]
def require(label, condition): checks.append((label, bool(condition)))
server=read('backend/server.js'); management=read('backend/statusManagementRoutes.js'); scale=read('backend/scalability.js'); interactions=read('backend/statusInteractionRoutes.js')
client=read('app/src/main/java/com/fynx/app/ui/FynxStatusClient.kt'); foundation=read('app/src/main/java/com/fynx/app/ui/FynxStatusFoundation.kt')
composer=read('app/src/main/java/com/fynx/app/ui/FynxStatusComposerPanel.kt'); mature=read('app/src/main/java/com/fynx/app/ui/FynxMatureStatusComposerPanel.kt')
timeline=read('app/src/main/java/com/fynx/app/ui/FynxStatusTimelinePanel.kt'); hub=read('app/src/main/java/com/fynx/app/ui/FynxStatusHubPanel.kt'); stories=read('app/src/main/java/com/fynx/app/ui/StoriesPanel.kt')
share=read('app/src/main/java/com/fynx/app/ui/FynxShare.kt'); deeplink=read('app/src/main/java/com/fynx/app/ui/FynxDeepLink.kt'); marketplace=read('app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt')
remote_media=read('app/src/main/java/com/fynx/app/ui/FynxRemoteMedia.kt'); camera=read('app/src/main/java/com/fynx/app/ui/FynxCameraCapturePanel.kt')
media_privacy=read('backend/mediaPrivacy.js')

require('backend Status schema + expiry','CREATE TABLE IF NOT EXISTS statuses' in server and 'expires_at TIMESTAMPTZ NOT NULL' in server)
require('backend media ownership','message_media' in server and 'owner_id=$2' in server)
require('backend Status privacy','private_status' in server and 'expires_at > NOW()' in server)
require('backend Status media authorization','EXISTS (SELECT 1 FROM statuses s WHERE s.media_id = mm.id' in server and "s.private_status = FALSE" in server and "f.status = 'accepted'" in server and "blocks b" in server)
require('backend status media expires before delivery','s.expires_at > NOW()' in server)
require('backend status media blocked-user denial','s.owner_id = $2 OR s.private_status = FALSE OR EXISTS' in server and 'NOT EXISTS (SELECT 1 FROM blocks' in server)
require('owner-only Status deletion','DELETE FROM statuses WHERE id=$1 AND owner_id=$2' in management)
require('management route authentication','jwt.verify(token, JWT_SECRET)' in management)
require('management route production wiring','registerStatusManagementRoutes({ app });' in scale and './statusManagementRoutes.js' in scale)
require('status interactions route wiring','registerStatusInteractionRoutes({ app });' in scale and './statusInteractionRoutes.js' in scale)
require('status interaction tables','status_views' in interactions and 'status_likes' in interactions and 'status_reactions' in interactions and 'status_replies' in interactions)
require('status interaction visibility guard','expires_at > NOW()' in interactions and 'friendships' in interactions and 'blocks' in interactions)
require('authenticated status view','app.post(\'/api/statuses/:statusId/view\'' in interactions and 'jwt.verify(token, JWT_SECRET)' in interactions)
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
require('Status foundation carries catalogue music','musicCatalogueId: Long?' in foundation and 'musicDurationMs: Long' in foundation)
require('Status client sends catalogue music','put("musicCatalogueId", status.musicCatalogueId ?: JSONObject.NULL)' in client)
require('Status client parses catalogue music','musicCatalogueId=musicCatalogueId' in client and 'musicTitle=' in client and 'musicArtist=' in client)
require('Status backend stores catalogue music','music_catalogue_id BIGINT' in server and 'music_title TEXT' in server and 'music_duration_ms BIGINT' in server)
require('Status backend validates published music','fynx_music_catalogue' in server and 'c.active=TRUE' in server and 'music selection is not published in the FYNX catalogue' in server)
require('Status backend preserves catalogue music duration','musicDurationMs = Math.max(0, Number(music.rows[0].duration_ms) || 0)' in server)
require('Add Status Music opens catalogue','onMusic = { showStatusMusicPicker = true }' in hub and 'FynxMusicCatalogueClient.listPublished(context, statusMusicSearch)' in hub)
require('Add Status selected music reaches composer','selectedStatusMusic' in hub and 'initialMusic = selectedStatusMusic' in hub)
require('Status composer carries selected music','selectedMusic?.id' in mature and 'musicCatalogueId = selectedMusic?.id' in mature)
require('Status composer previews selected music','FynxRemoteAudio("/api/social/music/catalogue/"' in mature and 'Remove music' in mature)
require('Status viewer renders attached music','status.musicCatalogueId' in timeline and 'FynxRemoteAudio("/api/social/music/catalogue/"' in timeline)
require('Status viewer uses catalogue music duration','musicDurationMs.coerceAtLeast(1_000L)' in timeline and 'kotlin.math.max' in timeline)

require('Status media upload uses authenticated backend client','FynxBackendClient.postJson(context, "/api/media", body)' in client)
require('Status media upload rejects unsupported types','Unsupported Status media type.' in client)
require('Status media upload size cap','12 * 1024 * 1024' in client)
require('Status publish sends media id','put("mediaId", mediaId)' in client)
require('Status composer uploads real media before publish','FynxStatusClient.uploadMedia(context, source, mime)' in composer and 'FynxStatusClient.create(context, status, mediaId)' in composer)
require('Status composer does not publish local file path','FynxStatusClient.create(context, status, mediaId)' in composer and 'contentUri = mediaId?.let { "/api/media/$it" }' in composer)

require('remote media requires authenticated account','FynxAuthStore.accountStorageKey(context) ?: return null' in remote_media and 'FynxBackendClient.hasAccessToken(context)' in remote_media)
require('remote media uses authenticated download','FynxBackendClient.downloadToFile(context, resolvedUrl, destination, MAX_REMOTE_MEDIA_BYTES)' in remote_media)
require('remote media has size cap','MAX_REMOTE_MEDIA_BYTES = 12L * 1024L * 1024L' in remote_media)
require('remote image renderer','BitmapFactory.decodeFile(target.absolutePath)' in remote_media and 'ContentScale.Crop' in remote_media)
require('remote video renderer','VideoView(ctx)' in remote_media and 'setVideoPath(file.absolutePath)' in remote_media)
require('remote audio renderer',contains_remote_audio_renderer(remote_media))
require('media privacy guard installed','app.use("/api/media", mediaGuard)' in media_privacy)
require('media privacy blocks message media','blocked_message_media' in media_privacy and 'return res.status(403).json({ error: "media unavailable" })' in media_privacy)

require('legacy composer real image preview','ImageView' in composer and 'setImageURI' in composer)
require('legacy composer real video preview','VideoView' in composer and 'setVideoURI' in composer)
require('legacy composer voice recording','MediaRecorder' in composer and 'FYNX_STATUS_MAX_VOICE_DURATION_MS' in composer)
require('legacy composer friends-only wording','Friends only' in composer and 'Only me' not in composer)
require('mature composer exists','fun FynxMatureStatusComposerPanel' in mature)
require('mature Text mode','FynxStatusType.TEXT' in mature and 'Type a Status' in mature)
require('mature creation controls fit small screens','LazyRow(' in mature and 'Text(\"Stop • ${formatMatureTime(elapsed)}\")' in mature)
require('mature Photo mode','FynxStatusType.PHOTO' in mature and 'pickImage.launch' in mature)
require('mature Video mode','FynxStatusType.VIDEO' in mature and 'pickVideo.launch' in mature)
require('mature Voice mode','FynxStatusType.VOICE' in mature and 'beginMatureVoiceRecording' in mature and 'stopMatureVoiceRecording' in mature)
require('mature audience control','FynxStatusAudience.EVERYONE' in mature and 'FynxStatusAudience.FRIENDS' in mature and 'FynxStatusAudience.ONLY_ME' in mature)
require('mature visible Send action','Icons.Default.Send' in mature and 'Text(if (publishing) \"Sending…\" else \"Send\")' in mature)
require('mature Send is not a field trailing action','trailingIcon' not in mature)
require('mature text style controls consume layout','Text(\"Text style\"' in mature and 'showColors && type == FynxStatusType.TEXT' in mature and 'FormatAlignCenter' in mature)
require('mature text style applies alignment in editor','editorTextAlign = when (alignment)' in mature and 'textAlign = editorTextAlign' in mature)
require('mature text style weight matches viewer','editorWeight = if (font == FynxStatusTextFont.BOLD)' in mature and 'fontWeight = editorWeight' in mature and 'fontWeight = weight' in timeline)
require('mature text preview sizing is responsive','BoxWithConstraints' in mature and 'maxWidth.value' in mature and '.coerceIn(24f, 42f)' in mature and 'text.length > 240' in mature)\nrequire('status viewer text sizing is responsive','BoxWithConstraints' in timeline and 'maxWidth.value' in timeline and '.coerceIn(24f, 42f)' in timeline and 'text.orEmpty().length > 240' in timeline)
require('mature photo preview preserves aspect ratio','ImageView.ScaleType.FIT_CENTER' in mature and 'ImageView.ScaleType.CENTER_CROP' not in mature)
require('status viewer renders media captions','status.type != FynxStatusType.TEXT && !status.text.isNullOrBlank()' in timeline and 'bottom = 154.dp' in timeline)
require('mature text emoji control','EmojiEmotions' in mature and 'showStatusEmoji' in mature)
require('mature text editor uses responsive full view','bottom = 220.dp' in mature and 'horizontal = 12.dp' in mature and '.imePadding()' in mature and 'BoxWithConstraints' in mature)\nrequire('mature composer Android back closes safely','BackHandler(enabled = !publishing && !recording) { onClose() }' in mature)\nrequire('mature voice preview before publish','FynxRemoteAudio(mediaUri.toString()' in mature and 'Voice Status ready' in mature)
require('mature authenticated publish','FynxStatusClient.create(context, status, mediaId)' in mature and 'FynxStatusClient.uploadMedia(context, source, mime)' in mature)
require('status camera owns lifecycle and back navigation','BackHandler' in camera and 'BackHandler { if (recording == null) onDismiss() }' in camera and 'cameraProvider?.unbindAll()' in camera)
require('status camera profile identity overlay','FynxProfileRemoteClient.get(context, username)' in camera and 'profilePhotoId' in camera and 'FynxRemoteMedia("/api/media/$photoId"' in camera)
require('status camera dialog preserves full-screen surface','DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)' in hub and 'Box(Modifier.fillMaxSize())' in hub)
require('status camera returns to editable composer','fun openCapturedStatus(uri: Uri, type: String)' in hub and 'composing = true' in hub and 'initialMediaUri = selectedInitialMedia?.uri' in hub)
require('add status media multi-select is functional','selectionMode' in hub and 'selectedMediaUris' in hub and 'toggleSelected' in hub and 'finishSelection' in hub and 'Select multiple media' in hub)
require('add status Layout creates real collage media','layoutMode' in hub and 'createFynxLayoutCollage' in hub and 'Bitmap.createBitmap(1200, 1200' in hub and 'FynxStatusToolPill' in hub)
require('mature 24-hour expiry','expiresAtMillis = now + FYNX_STATUS_EXPIRY_MS' in mature)
require('mature publish validation','Write something first.' in mature and 'Add your media first.' in mature)

require('single backend Status hub','FynxStatusTimelinePanel()' in hub and 'StoriesPanel()' not in hub)
require('hub mature + creation route','FynxMatureStatusComposerPanel' in hub and 'composing = true' in hub)
require('hub camera route remains real','FynxCameraCapturePanel' in hub and 'openCapturedStatus' in hub)
require('legacy Stories wrapper','FynxStatusTimelinePanel()' in stories and 'SharedPreferences' not in stories)
require('timeline backend list','FynxStatusClient.list(context)' in timeline)
require('timeline filters expired statuses','filterNot(FynxStatus::isExpired)' in timeline)
require('timeline latest-per-owner bubbles',('visibleStatuses.groupBy { it.ownerUsername }' in timeline or 'statuses.groupBy { it.ownerUsername }' in timeline) and 'maxByOrNull { it.createdAtMillis }' in timeline)
require('timeline profile avatar lookup','FynxProfileRemoteClient.get(context, status.ownerUsername)' in timeline)
require('timeline status viewer','FynxStatusStoryViewer(' in timeline and 'DialogProperties(usePlatformDefaultWidth = false' in timeline)
require('status viewer image',contains_call(timeline, 'FynxRemoteMedia', 'image'))
require('status viewer video',contains_call(timeline, 'FynxRemoteMedia', 'video'))
require('status viewer voice',contains_remote_audio_call(timeline))
require('status viewer left-right navigation','clickable(enabled = index > 0)' in timeline and 'clickable(enabled = index < statuses.lastIndex)' in timeline)\nrequire('status viewer Android back closes','BackHandler(onBack = onDismiss)' in timeline)\nrequire('status viewer swipe down closes','totalY > 140f' in timeline and 'onDismiss()' in timeline)\nrequire('status viewer horizontal swipe navigation','totalX > 120f' in timeline and 'totalX < -120f' in timeline)
require('status viewer smooth progress segments','LinearProgressIndicator' in timeline and 'segmentIndex < index' in timeline and 'segmentIndex == index -> statusProgress' in timeline)
require('status viewer timed progress','statusViewerAutoAdvanceMs(status)' in timeline and 'delay(50L)' in timeline)
require('status viewer reply pauses progress','if (!replyFocused)' in timeline and 'onFocusChanged' in timeline)
require('status interaction controls','viewCount' in timeline and 'likeCount' in timeline and 'Reply to this Status' in timeline)
require('status emoji reaction controls','EmojiEmotions' in timeline and 'showReactionPicker' in timeline and 'listOf("❤️", "😂", "😮", "😢", "👍", "👏", "🔥", "🎉")' in timeline)
require('status external share action','FynxShareActions.share(context, FynxShareActions.statusPayload(status))' in timeline and 'fun statusPayload(status: FynxStatus)' in share)
require('status share uses Stories destination','FynxDeepLinkParser.storiesWebLink()' in share and 'Stories' in deeplink)
require('owner delete UI','FynxStatusClient.delete(context, status.id)' in timeline and 'status.ownerUsername.equals(viewerUsername, true)' in timeline)
require('24 hour viewer expiry','FYNX_STATUS_EXPIRY_MS' in timeline)

require('Marketplace share payload exists','fun marketplacePayload(' in share and 'FynxDeepLinkParser.marketplaceWebLink(listingId)' in share)
require('Marketplace share preserves real listing id','marketplaceWebLink(listingId)' in share and 'fun marketplaceWebLink(listingId:String?=null)' in deeplink)
require('Marketplace web link uses FYNX host','private const val FYNX_HOST = "fynx.app"' in deeplink and 'MARKETPLACE_PATH = "/marketplace"' in deeplink)
require('external sharing uses Android share chooser','Intent.ACTION_SEND' in share and 'Intent.createChooser(sendIntent' in share)
require('Marketplace link parses back to Marketplace','"marketplace"->if(normalizedPath.size<=2)FynxDeepLinkDestination.Marketplace(value)' in deeplink)
require('Marketplace UI share action','FynxShareActions.share(context, FynxShareActions.marketplacePayload(l.id, l.title))' in marketplace and 'Share Marketplace listing' in marketplace)

failed=[label for label,ok in checks if not ok]
for label,ok in checks: print(('GREEN' if ok else 'RED')+' - '+label)
if failed:
    print('\nR5B Status/Stories + Marketplace sharing verification failed:')
    for item in failed: print(' - '+item)
    sys.exit(1)
print(f'\nR5B Status/Stories + Marketplace sharing end-to-end gate GREEN ({len(checks)} checks)')