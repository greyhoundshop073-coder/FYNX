# FYNX Home — Permanent Completion Contract

## Purpose
This is the single source of truth for deciding whether the FYNX Home surface is complete.
Future Home work must not restart from a new audit or reopen already-certified areas without evidence that a contract item has regressed.

## Core product contract
Home must behave as one durable social-feed experience: open -> show cached content immediately when available -> refresh safely -> scroll continuously -> interact reliably -> preserve session -> survive offline/online changes -> return without destructive reloads.

### A. Core feed
- Authenticated feed is authoritative.
- Feed ordering is deterministic.
- Pagination cannot duplicate or skip posts.
- Refresh preserves visible content and scroll state.
- New content must not unexpectedly push the user away from their reading position.
- Feed failures do not destroy the last valid feed.

### B. Persistence and lifecycle
- Cache is account-scoped.
- Cached Home can render without network.
- Leaving and returning preserves feed/session state.
- Pull-to-refresh is non-destructive.
- App restart has a safe cached fallback.
- Network recovery reconciles safely rather than blindly replacing the feed.

### C. Request architecture
- The main feed must not depend on Status, People, Marketplace, Discovery, AI, or profile hydration to render.
- Secondary sections load independently.
- Repeated requests for the same entity are deduplicated or served from cache.
- Home opening must not create an avoidable request storm.
- Secondary failure cannot take down the core feed.

### D. Social interactions
- Like/reaction/comment/reply/save/repost/share/follow and supported moderation/audience actions have authoritative server outcomes.
- Optimistic UI has rollback on failure.
- Interaction state survives navigation and refresh.
- Offline-capable interactions, where supported, use an idempotent queue rather than duplicate writes.

### E. Media
- Images and media use persistent caching where appropriate.
- Already cached media is reused.
- Failed media does not break the feed.
- Media loading is lazy/predictive rather than downloading the entire feed at once.

### F. Video
- At most the intended active Home video plays.
- Scroll changes playback focus safely.
- Leaving Home releases playback resources.
- Returning does not create duplicate players.
- Discovery video behavior follows the same lifecycle rules.

### G. Status / Stories
- Status is integrated into Home without becoming a dependency of the main feed.
- Status/profile data is not fetched redundantly when existing cached/returned data is sufficient.
- Returning from Status preserves Home state.

### H. Discovery / People / Marketplace / AI
- Each is independently loadable.
- Each has bounded pagination/loading.
- Each can fail without destroying the main feed.
- Navigation out and back preserves Home.
- Marketplace and other modules reuse authoritative data rather than creating competing copies.

### I. Privacy and correctness
- Blocked users/content cannot remain visible merely because it was cached.
- Deleted/private content is reconciled.
- Account switching cannot leak another account's cached Home.
- Server remains authoritative for permissions and interaction state.

### J. Performance
- Main feed is fast on good and poor networks.
- No avoidable per-author request amplification.
- No avoidable duplicate Status/profile/media requests.
- Memory and video resources are released appropriately.
- Pagination and refresh are bounded.
- Feed behavior remains acceptable on lower-end Android devices.

### K. Product quality
- Home has one coherent social hierarchy.
- No unnecessary loading panels, duplicate sections, or competing feed systems.
- Empty, offline, loading, error, and partial-success states are intentional.
- UI remains usable with long feeds and mixed media.

## Mandatory lifecycle certification
A Home release is not complete until the implementation is verified through:
1. cold open;
2. cached/offline open;
3. online refresh;
4. long scroll/pagination;
5. new-post arrival without scroll jump;
6. image and video playback;
7. post interaction;
8. failed interaction and rollback;
9. leave/return;
10. app restart;
11. offline while already browsing;
12. reconnect and reconciliation;
13. Status open/return;
14. Discovery open/return;
15. Marketplace open/return;
16. Profile/navigation open/return;
17. repeated Home navigation;
18. privacy/block/delete reconciliation;
19. account isolation;
20. authenticated runtime APK verification.

## Certification rule
A static script being green is not sufficient by itself.
Each contract item must have one of:
- GREEN — implementation and evidence verified
- YELLOW — implementation exists but runtime/evidence is incomplete
- RED — broken or missing
- N/A — intentionally outside FYNX Home scope
Only when all applicable items are GREEN can Home be marked: HOME COMPLETE / CERTIFIED.

## Change rule
Future Home changes must:
1. identify the contract item being changed;
2. inspect its callers and dependencies;
3. preserve all other GREEN items;
4. use one consolidated change per logical section;
5. pass the relevant static gates;
6. pass the relevant runtime test;
7. re-run the Home certification before declaring completion.
Do not replace working Home architecture merely to simplify the code.

## Current known architectural risks
These are investigation targets, not automatic failures:
- FynxRemoteHomeSocialPanel currently owns many independent responsibilities.
- Author photo resolution can perform per-author remote lookups.
- Status and following/profile data have separate loading paths.
- Discovery has more than one video-loading/viewing path.
- The backend feed currently uses offset pagination.
- Existing Home static verification is broader than full authenticated lifecycle certification.
These must be resolved or explicitly accepted with evidence before certification.