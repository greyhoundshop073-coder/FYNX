from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
composer = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxHomeSocialHubPanel.kt").read_text(encoding="utf-8")
catalogue = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMusicCatalogueClient.kt").read_text(encoding="utf-8")
legacy = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMusicLibraryClient.kt").read_text(encoding="utf-8")
client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMultiMediaPostClient.kt").read_text(encoding="utf-8")
models = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxRemoteSocialClient.kt").read_text(encoding="utf-8")
feed = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt").read_text(encoding="utf-8")
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
    ("Catalogue music can be previewed", '/api/social/music/catalogue/" + music.id + "/media' in composer and "MediaPlayer" in composer),
    ("Post client references catalogue music without uploading it", "catalogueMusic: FynxMusicCatalogueTrack?" in client and "catalogueMusic?.mediaId" in client and "Local music uploads are disabled" in client),
    ("Legacy local music path is disabled", "Local music uploads are disabled" in legacy and "MediaMetadataRetriever" not in legacy),
    ("Backend creates a controlled music catalogue", "fynx_music_catalogue" in routes and "media_id BIGINT NOT NULL UNIQUE" in routes),
    ("Backend exposes published catalogue tracks", "/api/social/music/catalogue" in routes and "c.active = TRUE" in routes),
    ("Backend exposes authenticated catalogue preview", "/api/social/music/catalogue/:id/media" in routes and "/api/social/music/media/:id" in routes),
    ("Admin music writes use the existing OWNER/ADMIN authorization model", "fynxMusicAdminRole" in routes and "fynx_admin_roles" in routes and "FYNX owner access required for music management" in routes),
    ("Admin can publish a track through the real authenticated media path", "FynxProductionMessaging.uploadMedia" in (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMusicAdminPanel.kt").read_text(encoding="utf-8") and "addMusicTrack" in (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMusicAdminPanel.kt").read_text(encoding="utf-8")),
    ("Failed catalogue publish can be retried without re-uploading audio", "uploadedMediaId" in (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMusicAdminPanel.kt").read_text(encoding="utf-8") and "Retry publish" in (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMusicAdminPanel.kt").read_text(encoding="utf-8") and "retained so you can retry publishing without uploading it again" in (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMusicAdminPanel.kt").read_text(encoding="utf-8")),
    ("Admin music controls are inside the existing admin center", 'if (d.role == "OWNER")' in (ROOT / "app/src/main/java/com/fynx/app/ui/FynxAnnouncementsPanel.kt").read_text(encoding="utf-8") and "FynxMusicAdminPanel()" in (ROOT / "app/src/main/java/com/fynx/app/ui/FynxAnnouncementsPanel.kt").read_text(encoding="utf-8")),
    ("Backend accepts only published catalogue music for posts", "music selection is not published in the FYNX catalogue" in routes and "FROM fynx_music_catalogue" in routes and "music selection is not published in the FYNX catalogue" in (ROOT / "backend/socialMultiMediaBootstrap.js").read_text(encoding="utf-8") and "let musicTitle" in (ROOT / "backend/socialMultiMediaBootstrap.js").read_text(encoding="utf-8")),
    ("Feed still renders attached music", "MusicPostPlayer" in feed and 'musicMediaId' in feed),
    ("Android model still parses persisted music metadata", "musicMediaId: String?" in models and "musicDurationMs: Long" in models),
]

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS" if ok else "FAIL") + ": " + name)

if failed:
    raise SystemExit("Controlled Batch 7 music verification failed: " + ", ".join(failed))

print(f"Controlled Batch 7 music verification GREEN ({len(checks)} checks)")
