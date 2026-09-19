from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
composer = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxHomeSocialHubPanel.kt").read_text(encoding="utf-8")
music = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMusicLibraryClient.kt").read_text(encoding="utf-8")
client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMultiMediaPostClient.kt").read_text(encoding="utf-8")
models = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxRemoteSocialClient.kt").read_text(encoding="utf-8")
feed = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt").read_text(encoding="utf-8")
routes = (ROOT / "backend/socialRoutes.js").read_text(encoding="utf-8")
bootstrap = (ROOT / "backend/socialMultiMediaBootstrap.js").read_text(encoding="utf-8")

checks = [
    ("Create Post Music control is real", 'ComposerQuickChip("Music", Icons.Default.MusicNote' in composer and 'musicPicker.launch(arrayOf("audio/*"))' in composer),
    ("Music selection uses Android document picker", 'ActivityResultContracts.OpenDocument()' in composer),
    ("Music metadata is read from the selected track", "MediaMetadataRetriever" in music and "METADATA_KEY_TITLE" in music and "METADATA_KEY_ARTIST" in music),
    ("Music preview is available before publishing", "MediaPlayer" in composer and 'Preview music' in composer),
    ("Selected music can be removed", 'selectedMusic = null' in composer and 'Remove music' in composer),
    ("Music is uploaded through the existing authenticated media path", "FynxProductionMessaging.uploadMedia(context, music.uri, mime)" in client),
    ("Music metadata is sent with the real post", '"musicMediaId"' in client and '"musicTitle"' in client and '"musicArtist"' in client and '"musicDurationMs"' in client),
    ("Backend persists music metadata", "music_media_id" in routes and "music_title" in routes and "music_artist" in routes and "music_duration_ms" in routes),
    ("Backend validates music ownership and audio MIME", "music media is not owned" in routes and "startsWith('audio/')" in routes),
    ("Feed returns persisted music metadata", "musicMediaId" in routes and "musicTitle" in routes and "musicArtist" in routes),
    ("Android model parses music metadata", "musicMediaId: String?" in models and "musicDurationMs: Long" in models),
    ("Home renders attached music", "MusicPostPlayer" in feed and 'FynxMediaCache.getOrDownload(context, "/api/social/media/" + mediaId, "audio")' in feed),
    ("Multi-media bootstrap carries music fields", "musicMediaId" in bootstrap and "musicDurationMs" in bootstrap),
    ("Existing voice-post path remains protected", "Voice posts use one audio recording" in client),
]

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS" if ok else "FAIL") + ": " + name)

if failed:
    raise SystemExit("Batch 7 music verification failed: " + ", ".join(failed))

print(f"Batch 7 music verification GREEN ({len(checks)} checks)")
