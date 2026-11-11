from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
composer = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxHomeSocialHubPanel.kt").read_text(encoding="utf-8")
catalogue = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMusicCatalogueClient.kt").read_text(encoding="utf-8")
legacy = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMusicLibraryClient.kt").read_text(encoding="utf-8")
client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMultiMediaPostClient.kt").read_text(encoding="utf-8")
models = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxRemoteSocialClient.kt").read_text(encoding="utf-8")
announcements = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxAnnouncementsPanel.kt").read_text(encoding="utf-8")
app = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxApp.kt").read_text(encoding="utf-8")
music_admin = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMusicAdminPanel.kt").read_text(encoding="utf-8")
feed = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt").read_text(encoding="utf-8")
playback = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxCatalogueMusicPlayback.kt").read_text(encoding="utf-8")
media_cache = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMediaCache.kt").read_text(encoding="utf-8")
home_picker = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxHomeSocialHubPanel.kt").read_text(encoding="utf-8")
status_picker = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxStatusHubPanel.kt").read_text(encoding="utf-8")
picker_surface = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMusicPickerSurface.kt").read_text(encoding="utf-8")
song_page = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMusicSongDetailSurface.kt").read_text(encoding="utf-8")
routes = (ROOT / "backend/socialRoutes.js").read_text(encoding="utf-8")

checks = [
    ("Backend seeds six original FYNX catalogue tracks through real message_media rows", all(x in routes for x in [
        "FYNX_BUILTIN_MUSIC", "createFynxBuiltinWav", "message_media", "audio/wav",
        "fynx_music_catalogue", "FYNX_MUSIC_DURATION_SECONDS"
    ])),
    ("Built-in FYNX music is 45 seconds per track", "FYNX_MUSIC_DURATION_SECONDS = 45" in routes and "FYNX_MUSIC_DURATION_SECONDS * 1000" in routes),
    ("Built-in catalogue seed is idempotent", "SELECT id FROM fynx_music_catalogue WHERE title=$1 AND artist=$2" in routes and "if (existing.rows[0]) continue" in routes),
    ("Create Post Music control opens the FYNX catalogue", 'ComposerQuickChip("Music", Icons.Default.MusicNote' in composer and 'showMusicPicker = true' in composer),
    ("Normal users no longer get an audio document picker", 'ActivityResultContracts.OpenDocument()' not in composer and 'musicPicker.launch' not in composer),
    ("Catalogue client reads only published FYNX tracks", "FynxMusicCatalogueClient" in composer and "/api/social/music/catalogue" in catalogue and "listPublished" in catalogue),
    ("Catalogue tracks carry a server media reference", "mediaId: Long" in catalogue and 'row.optLong("mediaId"' in catalogue),
    ("Music selection can be removed", 'selectedCatalogueMusic = null' in composer),
    ("Catalogue music can be previewed", '/api/social/music/catalogue/" + music.id + "/media' in composer and "FynxRemoteAudio" in composer),
    ("Post client references catalogue music without uploading it", "catalogueMusic: FynxMusicCatalogueTrack?" in client and "catalogueMusic?.mediaId" in client and "Local music uploads are disabled" in client),
    ("Home supports music-only posts without caption or media", "if (caption.isBlank() && selected.isEmpty() && catalogueMusic == null)" in client and "official FYNX music track" in client),
    ("Legacy local music path is disabled", "Local music uploads are disabled" in legacy and "MediaMetadataRetriever" not in legacy),
    ("Backend creates a controlled music catalogue", "fynx_music_catalogue" in routes and "media_id BIGINT NOT NULL UNIQUE" in routes),
    ("Backend exposes published catalogue tracks", "/api/social/music/catalogue" in routes and "c.active = TRUE" in routes),
    ("Backend exposes authenticated catalogue preview", "/api/social/music/catalogue/:id/media" in routes and "/api/social/music/media/:id" in routes),
    ("Admin music write routes enforce OWNER-only authorization", routes.count("if (role !== 'OWNER') return res.status(403).json({ error: 'FYNX owner access required for music management' });") == 3),
    ("Admin can publish a track through the real authenticated media path", "FynxProductionMessaging.uploadMedia" in (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMusicAdminPanel.kt").read_text(encoding="utf-8") and "addMusicTrack" in (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMusicAdminPanel.kt").read_text(encoding="utf-8")),
    ("Failed catalogue publish can be retried without re-uploading audio", "uploadedMediaId" in (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMusicAdminPanel.kt").read_text(encoding="utf-8") and "Retry publish" in (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMusicAdminPanel.kt").read_text(encoding="utf-8") and "retained so you can retry publishing without uploading it again" in (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMusicAdminPanel.kt").read_text(encoding="utf-8")),
    ("Announcement Center music tools use the app shell's server-confirmed OWNER role", "ownerAccess: Boolean = false" in announcements and "FynxAnnouncementsPanel(ownerAccess = adminRole == \"OWNER\")" in app and "if (ownerAccess)" in announcements and "FynxMusicAdminPanel()" in announcements),
    ("Admin Control Center music tools are OWNER-only", 'if (d.role == "OWNER")' in announcements and "FynxMusicAdminPanel()" in announcements),
    ("Admin Control Center keeps seasonal promotion tools available to OWNER and ADMIN", 'if (d.role == "OWNER" || d.role == "ADMIN")' in announcements and "FynxMarketplaceSeasonalPromotionAdminPanel()" in announcements),
    ("Music admin UI accurately states OWNER-only management", "Only the official FYNX OWNER can add, restore or remove catalogue tracks." in music_admin),
    ("Backend accepts only published catalogue music for posts", "music selection is not published in the FYNX catalogue" in routes and "FROM fynx_music_catalogue" in routes and "music selection is not published in the FYNX catalogue" in (ROOT / "backend/socialMultiMediaBootstrap.js").read_text(encoding="utf-8") and "let musicTitle" in (ROOT / "backend/socialMultiMediaBootstrap.js").read_text(encoding="utf-8")),
    ("Feed still renders attached music", "MusicPostPlayer" in feed and 'musicMediaId' in feed),
    ("Official Home music uses one app-scoped player", "object FynxCatalogueMusicPlayback" in playback and "FynxCatalogueMusicPlayback.snapshot" in feed and "FynxCatalogueMusicPlayback.toggle" in feed),
    ("Official Home music prepares asynchronously instead of blocking the feed", "created.prepareAsync()" in playback and "created.prepare()" not in playback),
    ("Official catalogue playback requests and responds to Android audio focus", "requestAudioFocus(request)" in playback and "AUDIOFOCUS_LOSS_TRANSIENT" in playback and "AUDIOFOCUS_GAIN" in playback),
    ("Voice-note audio path remains separate from catalogue playback", "Voice notes and recorded speech intentionally keep their own audio path." in playback and "AudioPostPlayer" in feed),
    ("Home and Status use the same dedicated music picker surface", "FynxMusicPickerSurface(" in home_picker and "FynxMusicPickerSurface(" in status_picker),
    ("Music picker is a spacious glass-style full-screen library", "usePlatformDefaultWidth = false" in picker_surface and "fillMaxSize(0.96f)" in picker_surface and "Brush.linearGradient" in picker_surface and "RoundedCornerShape(30.dp)" in picker_surface),
    ("Music picker supports scrolling through many catalogue tracks", "LazyColumn(" in picker_surface and "items(tracks, key = { it.id })" in picker_surface),
    ("Music picker search, preview, retry and track selection are wired", "onSearchChange" in picker_surface and "FynxRemoteAudio(" in picker_surface and "onRetry" in picker_surface and "onSelect(track)" in picker_surface),
    ("Home catalogue search is debounced and authenticated", "kotlinx.coroutines.delay(300)" in home_picker and "FynxBackendClient.hasAccessToken(context)" in home_picker),
    ("Home feed soundtrack opens the song destination", "songDetailsOpen" in feed and "FynxMusicSongDetailSurface(" in feed and "View song details" in feed),
    ("Song destination previews through the shared catalogue player", "FynxCatalogueMusicPlayback.toggle" in song_page and '/api/social/music/media/$mediaId' in song_page),
    ("Home feed music is downloaded only after explicit play", "fun playOrPause()" in feed and "loadingAudio" in feed and "LaunchedEffect(mediaId)" not in feed[feed.find("private fun MusicPostPlayer"):feed.find("private fun formatMusicDuration", feed.find("private fun MusicPostPlayer"))]),
    ("Music cache stays inside account-scoped private app storage", 'File(context.filesDir, "$FYNX_MEDIA_CACHE_DIR/${accountCacheKey(accountKey)}")' in media_cache and "MediaStore" not in media_cache and "Environment.DIRECTORY_DOWNLOADS" not in media_cache),
    ("Music cache has a bounded local storage budget", "MAX_FYNX_MEDIA_CACHE_BYTES = 100L * 1024L * 1024L" in media_cache and "trim(directory, file)" in media_cache),
    ("Music playback has no public save or export action", "ACTION_CREATE_DOCUMENT" not in feed and "MediaStore" not in feed and "Environment.DIRECTORY_DOWNLOADS" not in feed),
    ("Feed soundtrack playback is owned by one specific post", 'soundtrackPostId = post.id' in feed and 'val ownerKey = remember(postId) { "home-post:$postId" }' in feed and "ownerKey: String? = null" in playback),
    ("A post leaving the active feed slot pauses only its own soundtrack", "FynxCatalogueMusicPlayback.pause(ownerKey)" in feed and "if (snapshot.ownerKey != ownerKey) return" in playback),
    ("A soundtrack still preparing is released when its post loses focus", "if (snapshot.isPreparing)" in playback and "releaseCurrent(resetSnapshot = true)" in playback),
    ("Inactive posts cannot start soundtrack playback", "enabled = playbackActive && !loadingAudio && !isPreparing" in feed and "if (!playbackActive || loadingAudio || isPreparing) return" in feed),
    ("A soundtrack download finishing after a scroll change cannot start stale audio", "latestPlaybackActive" in feed and "if (latestPlaybackActive) downloaded?.let" in feed),
    ("Video soundtrack downloads only after an explicit user play action", "fun startAttachedMusic()" in feed and 'FynxMediaCache.getOrDownload(context, "/api/social/music/media/$mediaId", "audio")' in feed and 'enabled = playbackActive && !soundtrackLoading' in feed and 'if (soundtrackEnabled && playing && audioFile != null)' in feed),
    ("Feed scrolling never auto-starts attached music", 'var soundtrackEnabled by remember(soundtrackMediaId) { mutableStateOf(false) }' in feed and 'if (!playbackActive) {\n            soundtrackEnabled = false' in feed),
    ("Explicit full-screen video Play starts the attached soundtrack with the video", "if (playbackActive && !fullScreenControls)" in feed and "startAttachedMusic()" in feed and "if (playbackActive && !playing && !fullScreenControls)" in feed),
    ("Video soundtrack follows the video playback clock and pauses with it", "FynxCatalogueMusicPlayback.playAt(context, mediaId, audioFile, owner, playbackPositionMs, if (muted) 0f else 0.85f)" in feed and "FynxCatalogueMusicPlayback.syncTo(owner, playbackPositionMs)" in feed),
    ("Video soundtrack drift is corrected", "fun syncTo(ownerKey: String, positionMs: Long)" in playback and "kotlin.math.abs(current.currentPosition - target) > 350" in playback),
    ("New video soundtrack seeks to the paired video clock before first playback", "initialPositionMs: Long = 0L" in playback and "prepared.setOnSeekCompleteListener" in playback and "prepared.seekTo(target)" in playback and "startPreparedPlayback(seeked, trackKey, ownerKey)" in playback and "initialPositionMs = positionMs, loopPlayback = true" in playback),
    ("Video soundtrack does not start from zero before its initial seek completes", playback.find("prepared.setOnSeekCompleteListener") < playback.find("prepared.seekTo(target)") < playback.find("private fun startPreparedPlayback") and "snapshot.isPreparing" in playback and "prepared.start()" not in playback[playback.find("created.setOnPreparedListener"):playback.find("created.setOnCompletionListener")]),
    ("Resumed video soundtrack waits for asynchronous seek completion", "pendingResumePlayer = current" in playback and "current.setOnSeekCompleteListener" in playback and "current.seekTo(target)" in playback and "seeked.start()" in playback and playback.find("current.setOnSeekCompleteListener { seeked ->") < playback.find("seeked.start()", playback.find("current.setOnSeekCompleteListener { seeked ->")) < playback.find("current.seekTo(target)", playback.find("current.setOnSeekCompleteListener { seeked ->"))),
    ("A stale resume callback cannot start another post or already-playing player", "player !== seeked || pendingResumePlayer !== seeked" in playback and "snapshot.trackKey != trackKey || snapshot.ownerKey != ownerKey" in playback and "snapshot.isPreparing || snapshot.isPlaying" in playback and "if (pendingResumePlayer === current) return" in playback),
    ("Pending resume seek is cancelled on pause and audio-focus loss", playback.count("pendingResumePlayer = null") >= 4 and "AUDIOFOCUS_LOSS_TRANSIENT -> runCatching" in playback and "AUDIOFOCUS_LOSS ->" in playback),
    ("Video soundtrack volume is set before new-player preparation and playback", "initialVolume: Float = 1f" in playback and "requestedVolume = initialVolume.coerceIn(0f, 1f)" in playback and "initialVolume = initialVolume" in playback and "applyRequestedVolume(prepared)" in playback),
    ("Muted video soundtrack is passed into playAt before audio can start", "playAt(context, mediaId, audioFile, owner, playbackPositionMs, if (muted) 0f else 0.85f)" in feed and "FynxCatalogueMusicPlayback.setVolume(owner, if (muted) 0f else 0.85f)" not in feed),
    ("Audio focus duck and gain preserve the requested soundtrack mix and mute level", "private var requestedVolume = 1f" in playback and "private var focusDuckMultiplier = 1f" in playback and "focusDuckMultiplier = 0.2f" in playback and "focusDuckMultiplier = 1f" in playback and "applyRequestedVolume(active)" in playback and "requestedVolume * focusDuckMultiplier" in playback),
    ("Pending soundtrack preparation is cancelled on transient or permanent audio-focus loss", "if (snapshot.isPreparing)" in playback and playback.count("releaseCurrent(resetSnapshot = true)") >= 2 and "return@post" in playback),
    ("Photo soundtrack control is attached to the photo surface", "MusicPostPlayer(soundtrackPostId, soundtrackMediaId" in feed and "Modifier.align(Alignment.BottomCenter)" in feed),
    ("Video posts display attached soundtrack attribution", 'contentDescription = "Attached music"' in feed),
    ("Video soundtrack attribution opens the real song destination", "var songDetailsOpen by remember(soundtrackMediaId)" in feed and "FynxMusicSongDetailSurface(" in feed and "durationMs = soundtrackDurationMs" in feed),
    ("Inline and fullscreen soundtrack owners are isolated", '"home-post:$it:inline"' in feed and '"home-post:$it:fullscreen"' in feed),
    ("Video soundtrack volume and original video volume are balanced and muted together", "playAt(context, mediaId, audioFile, owner, playbackPositionMs, if (muted) 0f else 0.85f)" in feed and "0.35f else 1f" in feed),
    ("Detached post-level soundtrack card is not rendered after media", "if (!post.musicMediaId.isNullOrBlank()) MusicPostPlayer" not in feed),
    ("Song destination preview download is user initiated", "fun loadAndPreview()" in song_page and "audioLoading" in song_page and "LaunchedEffect(mediaId)" not in song_page),
    ("Song destination hands the exact media asset to the Home composer", "FynxMusicSongNavigation.useTrack" in song_page and "FynxMusicSongNavigation.onUseTrack = handler" in home_picker and "mediaId = mediaId" in home_picker),
    ("Song reuse preserves the existing Home draft and selected media", "showComposer = true" in home_picker and "capturedUris" not in home_picker[home_picker.find("val handler:"):home_picker.find("LaunchedEffect(Unit)")]),
    ("Song destination does not fabricate artwork or public creations", "Public creations will appear here when FYNX's catalogue-linked discovery service is available." in song_page and "FYNX SOUND" in song_page),
    ("Android model still parses persisted music metadata", "musicMediaId: String?" in models and "musicDurationMs: Long" in models),
]

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS" if ok else "FAIL") + ": " + name)

if failed:
    raise SystemExit("Controlled Batch 7 music verification failed: " + ", ".join(failed))

print(f"Controlled Batch 7 music verification GREEN ({len(checks)} checks)")
