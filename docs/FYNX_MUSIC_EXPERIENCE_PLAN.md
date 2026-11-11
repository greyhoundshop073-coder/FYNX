# FYNX Music Experience — Product and Engineering Plan

**Status:** Active implementation plan; not a declaration that the full music experience is complete.  
**Canonical source inspected:** `integration/fynx-single-apk-20261013` at `43839989b1abd8037db0c2af202208ac46944819`.  
**Research checked:** 11 November 2026.  
**Delivery rule:** Feature PRs target the canonical branch. Never publish a partial APK as the shared FYNX APK.

## Product goal

Upgrade the existing official FYNX music catalogue into one coherent social-music experience. Do not create a second catalogue, local-upload path for ordinary users, or competing playback implementation.

The catalogue remains controlled by the official FYNX OWNER. Ordinary users can discover, preview and reuse active official tracks. Voice notes, Voice Status and original video audio remain separate audio types.

## Source facts verified at the canonical SHA

- `FynxMusicCatalogueTrack` currently carries a catalogue `id`, audio `mediaId`, title, artist and duration.
- The authenticated catalogue endpoint returns active tracks; preview is served by authenticated music-media routes.
- Home's composer already browses the official catalogue, previews tracks, attaches a selected track, and shows the selection before publishing.
- `FynxMultiMediaPostClient` sends the selected catalogue track's `mediaId` to the existing server route. The server resolves that media asset against the active catalogue and replaces client-supplied title, artist and duration with authoritative catalogue metadata.
- **Bug found:** Home's composer enables a music-only post, and the server accepts it, but the Android post client rejected blank-caption/no-media posts before considering the selected catalogue track.
- **Permission mismatch found:** the Admin Control Center displayed `FynxMusicAdminPanel` to both OWNER and ADMIN, while backend write routes enforce OWNER-only access. The UI must match the server's authority. Marketplace seasonal promotion controls are a separate permission surface and must retain their existing OWNER/ADMIN access.
- The Announcement Center did not yet expose the OWNER music tools in its own surface.
- Home's feed player is a per-post `MediaPlayer` card with no album artwork or song-detail navigation. It calls synchronous `prepare()` after cache download, and each post card owns a separate player instance.
- The current music implementation push replaces that per-card Home playback with `FynxCatalogueMusicPlayback`: one app-scoped official-catalogue player, asynchronous `prepareAsync()`, Compose-observable playback state, explicit Android audio-focus requests, pause/duck/resume handling, and release on track switches/errors/completion. The feed card no longer constructs or prepares its own player. Voice-note playback remains on the separate generic audio path. Until the backend adds a catalogue ID to Home post payloads, the Home player uses the existing media ID as its temporary playback key; this is not a substitute for the backend identity migration.
- `FynxRemoteAudio` is a generic per-composable audio player used for remote audio and also for Status catalogue previews/playback. It must not be replaced wholesale by a music-only coordinator because voice notes and recorded speech use the same generic surface.
- Status already has catalogue selection and preview; its composer stores a catalogue track ID separately from voice media.
- The current camera capture flow hands a photo/video back to the composer. `FynxMediaComposer` is a simple media review surface; the canonical source does not contain a dedicated video soundtrack timeline or a full video editor with audio mixing controls. Music can be attached in the Home/Status composer after capture. A future timeline is a new capability and must wait for a real render/publish pipeline.

## Identity model — do not conflate identifiers

- **Catalogue track ID (`track.id`):** permanent official identity used for song pages, post attribution, reuse, moderation and analytics.
- **Media asset ID (`track.mediaId`):** storage/playback locator for the audio bytes.

Current Home post storage primarily persists the media reference. A backend migration is required before claiming the catalogue ID is explicitly persisted on every Home post. The migration should add or populate a catalogue-track reference, backfill by the existing unique media mapping, keep legacy reads working during rollout, and validate that a selected track is still active at publish time. Do not change this contract only on Android.

## Research-derived product decisions

### Song destination

Use the strongest reusable pattern from YouTube Shorts: the audio chip opens a song library page, which can preview the official track, show eligible creations using that track, and start a new creation with the same sound. YouTube's official creator guide documents the song library page, preview, top creations and creation button: https://artists.youtube/intl/en-US/resources/shorts-for-artists/.

Instagram Reels similarly connects audio discovery with a creation action and attribution for original audio: https://about.fb.com/news/2020/08/introducing-instagram-reels/ and https://about.fb.com/news/2023/08/music-and-collabs-on-instagram/.

FYNX song page should include:
- Artwork, title, artist, duration and genre/description when available.
- A single explicit play/pause control and a clear playback state.
- A prominent **Use this song** action.
- Public, eligible FYNX posts using the exact same catalogue track.
- Optional save/favourite action only when a real persistence path exists.
- A truthful empty state when there are no public creations.
- No private post, private profile detail, blocked-user content or hidden account activity.

### Composer and editor reuse

All entry points should use one selected-track model and one handoff contract. Opening a composer with a song must preserve the existing caption, selected media, media order, crop/edit state and draft. The selected official catalogue ID remains unchanged; no audio re-upload or duplicate catalogue row is created.

The shared picker should support search by title/artist, genre filters when the catalogue supports them, preview, selected state, remove/replace, and clear loading/error/empty states.

Photo posts may use a soundtrack without forcing a video timeline. Video editing may add trim/start offset and separate original-audio/music volume controls only when the current editor and publish pipeline can encode and persist those settings. Do not show fake controls.

### Licensing and business use

TikTok's official guidance distinguishes its Commercial Music Library from other music and warns that ordinary music licences may not cover brand/product/service promotion: https://support.tiktok.com/en/business-and-creator/creator-and-business-accounts/commercial-use-of-music-on-tiktok?lang=en and https://ads.tiktok.com/resources/help/article/how-to-use-the-commercial-music-library.

FYNX must not label every track commercially cleared by default. Backend metadata should eventually express rights/usage scope, territories, start/end dates and permitted contexts (personal social, organic business, paid promotion, etc.). Until rights are verified, do not promise that catalogue music can be used in Marketplace ads or paid commercial promotions. This is a licensing/legal blocker, not something a UI toggle can solve.

## App-wide surface map

| Surface | User purpose | Required behavior |
|---|---|---|
| Home feed post | Identify and explore a soundtrack | Small circular artwork beside attribution; rotate only while playing; tap opens exact song page |
| Home composer | Add official music to a post | Shared picker; preserve draft/media; support music-only post |
| Photo/carousel composer | Give still media a soundtrack | Select track without imposing video editing controls |
| Future video-editing extension | Add soundtrack to edited clips | No dedicated soundtrack timeline exists in the current canonical app. Add trim/start offset and independent source/music levels only with a real render/publish pipeline |
| Status composer | Share music with a photo/video/text status | Same picker and identity; retain selected track while returning to composer |
| Music-only Status | Share a track as Status | Cover, title, artist and track playback; no voice-note substitution |
| Camera capture | Capture photo/video for a post | Capture returns to the existing composer; attach catalogue music there using the shared picker |
| Discovery | Find tracks and public creations | Popular/recent/genre views based on real, eligible usage; exact song page |
| User profile | Show optional public music activity | Respect profile visibility, post audience and block rules |
| Announcement Center | Official catalogue management entry | OWNER-only access, server role checked |
| Admin Control Center | Administrative catalogue management | OWNER-only music controls; keep unrelated Admin permissions separate |
| Voice notes / Voice Status | Personal recorded speech | Remain separate from catalogue music and use speech audio attributes |

## Playback architecture

Use one app-scoped catalogue playback coordinator rather than independent players in every feed card and picker. It owns the active track, playback state, player lifecycle and audio focus. UI surfaces subscribe to that state. Keep it catalogue-specific: do not replace the generic `FynxRemoteAudio` speech/voice path. Replace music call sites with the catalogue player, then explicitly pause catalogue playback when voice notes, calls or audible video take over.

Rules:
- At most one FYNX catalogue track plays at a time.
- Previewing a different track replaces the prior preview.
- Leaving a temporary picker stops its preview.
- Feed scrolling never auto-starts music.
- Pausing stops artwork rotation immediately.
- Explicit background listening may continue only through an intentional, lifecycle-safe media session/foreground playback design; otherwise leaving the song destination stops playback.
- Voice notes and calls take precedence over catalogue music; voice content is typed as speech, not music.
- Video playback and catalogue music must not play over each other unexpectedly.
- Handle audio-focus loss, transient interruptions, route changes, Bluetooth/headphones, completion, errors and release.
- Avoid a second custom audio-focus implementation if the selected Media3 player can manage focus automatically.

Android audio-focus guidance: https://developer.android.com/media/optimize/audio-focus. It recommends requesting focus immediately before playback, responding to focus loss by pausing or ducking, and abandoning focus when playback ends. Android 15 also restricts focus requests for apps that are neither topmost nor running a foreground service.

## Catalogue administration model

OWNER-only management should support:
- Audio upload and authoritative title/artist/duration.
- Cover artwork and validation.
- Genre, description and optional mood/tags.
- Active/hidden/removed state with safe restoration.
- Rights/territory/usage-scope metadata.
- Real usage counts, never fabricated popularity.
- Retry publish without uploading an already-uploaded asset again.
- Audit history for publish, hide, restore and removal.

Server authorization remains authoritative on every write. Hiding controls in Android is not a security boundary. ADMIN must not gain music write access.

## Backend-dependent work reserved for reconnection

1. Add catalogue artwork/genre/description/rights metadata and authenticated upload/read paths.
2. Add explicit `music_catalogue_id` to social posts, backfill safely from the unique media mapping and preserve legacy feed reads.
3. Add a posts-by-catalogue-track endpoint that applies the same public visibility, audience, block, deletion and moderation rules as the social feed.
4. Add accurate usage counts and ranking rules based on eligible public posts; protect against duplicate counting and removed/private content.
5. Reconfirm OWNER-only role checks for publish, restore, hide and removal; test ADMIN denial on every route.
6. Validate rights and territory before allowing business/paid promotional usage.
7. Verify catalogue playback, post attribution and song discovery against a live authenticated backend.

No backend route, migration or live-data behavior is considered complete while hosting is unavailable.

## Delivery stages

1. **Foundation and security:** fix music-only Home post validation; make OWNER-only controls match backend authority; add regression checks.
2. **Shared playback and picker:** consolidate preview/playback lifecycle and selected-track state.
3. **Song destination:** build the song detail page and exact-track navigation.
4. **Home attribution and reuse:** artwork entry point, rotation while playing, Use this song handoff.
5. **Status and camera/video:** integrate shared selection without touching voice recording behavior.
6. **Discovery/profile:** public, privacy-safe music activity and real usage-based discovery.
7. **Backend contract:** artwork/metadata, explicit catalogue IDs, posts-by-song, usage counts and licensing.
8. **Certification:** Android build/lint/unit tests, security regression, runtime screenshots, offline behavior and canonical APK verification after merge.

## Acceptance criteria

- One official catalogue; no user-created duplicate official tracks.
- Catalogue track ID is preserved across song page, picker, Home, Status and editor.
- Media asset ID is used only as the playback/storage locator.
- Music-only Home post works when a valid official track is selected.
- Existing drafts and selected media survive song selection and return.
- No private or blocked content leaks through song discovery.
- Only OWNER can manage catalogue tracks; ADMIN is denied by UI and backend.
- Voice notes and Voice Status remain independent of music.
- One active FYNX catalogue player; no accidental simultaneous playback.
- Rights scope is explicit before commercial music use is offered.
- No feature is marked complete without passing CI and actual runtime verification.
