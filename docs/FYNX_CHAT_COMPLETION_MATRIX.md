# FYNX Chat V1 Completion Matrix

This matrix is the finite worklist for certifying Chat. It must be updated from repository evidence, not assumptions.

## Status legend

- 🟢 GREEN / COMPLETE — verified implementation + integration + APK-visible behavior + applicable user journey.
- 🟡 YELLOW / INCOMPLETE — exists but needs verification or completion.
- 🔴 RED / FAILED — concrete failure found; investigate root cause before moving on.
- ⚪ NOT REQUIRED FOR V1 — explicitly excluded from V1 and tracked only as a future upgrade.

## Gates

| Gate | V1 scope | Status | Evidence / verification |
|---|---|---|---|
| 1. Core messaging | send/receive, realtime, lifecycle, offline/outbox, retry, reconciliation, ordering, duplicates, history, restart | 🟡 | Must be verified as one end-to-end lifecycle |
| 2. Message actions | reply, edit, delete, reactions, pin, copy, forward, selection and permissions in V1 scope | 🟡 | Must verify state synchronization after actions |
| 3. Media & attachments | image, video, audio, voice, documents, upload/download, failure/retry, playback/preview | 🟡 | Must verify actual APK journeys |
| 4. Organization | search/navigation, pins, saved/important messages if required, shared content navigation | 🟡 | Must verify against current implementation |
| 5. Privacy/security/safety | settings, privacy, mute, block/report where exposed, backend authority, secure handling, approved security contract | 🟡 | Security claims require evidence; no theoretical certification |
| 6. Group Chat | messaging, media, replies/reactions, permissions, mentions, notifications, member/admin behavior, history/search | 🟡 | Verify group-specific journeys and server authority |
| 7. Notifications/realtime | foreground/background, mute, previews, sounds/vibration, push+realtime dedupe, typing/presence | 🟡 | Preserve already-green notification work |
| 8. Production certification | build/tests/verifiers, APK-visible integration, navigation, real journey, regression | 🔴 | Final gate; cannot pass until Gates 1–7 are green |

## Current certification state

**Chat is NOT YET CERTIFIED COMPLETE.**

This is deliberate: the matrix was created before claiming completion. Existing implementations must be re-verified against the contract and only concrete gaps should be changed.

## Certification rule

Do not mark a gate GREEN merely because code or a verifier marker exists. Follow `docs/FYNX_REALITY_AUDIT_CODEX_INSTRUCTIONS.md`: trace the real user flow, verify the relevant backend/storage/realtime path, build the Android APK, and test the applicable journey.

When all required gates are GREEN, update this matrix with the certified commit SHA and set Chat status to `GREEN / COMPLETE` and `LOCKED`.

## Post-completion rule

After certification, Chat is not reopened for theoretical feature comparisons. Reopen only for regression, failed test, confirmed security/privacy defect, production-critical defect, or an explicitly approved Chat upgrade.


## 2026-11-13 — Group polls and events reliability batch

**Delivery:** PR [#535](https://github.com/greyhoundshop073-coder/FYNX/pull/535), continuing the existing Chat/Groups branch; preceding green checkpoint `c44c6e1ecb493df2b978b7423bdcc7fea0b856e6`.

**Verified defects addressed**
- Poll history previously returned aggregate vote counts only. The API and Android model now include the authenticated member's own option selection.
- Closed polls were still clickable despite an existing server-side closed state. The screen now labels closed polls and disables voting.
- Vote failures had no visible feedback. The screen now surfaces the failure.
- Single-choice votes previously used separate pool-level delete/insert operations. Voting is now serialized with a row lock and transaction, reusing the reviewed implementation from open PR #523.
- Event end-time parsing previously allowed invalid values to reach date serialization and did not reject an end time at/before the start. Both cases are rejected.
- Pin/unpin failures are now surfaced.
- Group content creation reuses server group-permission and trust-safety checks carried forward from #523.

**Verification on this exact head:** R5A Group verifier, Android unit tests, lint, APK compilation, Backend CI and Chat/Group parity/layout must be checked from this commit's own Actions runs.

**Not certified:** no claim of live Railway/Neon poll persistence, two-account vote isolation, event RSVP cross-account behaviour or physical-device UX. Group membership/invite fixes already implemented in open PR #523 remain pending integration; do not duplicate them.


## 2026-11-13 — Group membership authority repair

The Members dialog previously changed membership and moderator roles locally without waiting for backend confirmation. The server already exposes authenticated add/promote/demote/remove endpoints and enforces group-role restrictions. The repaired dialog refreshes its roster from the server, calls those endpoints before changing the local cache, prevents self/owner removal, and refreshes account-level block state from the social API. The cache callback no longer sends a broad `syncGroup` request that could overwrite server-authoritative membership.

This work still requires exact-commit CI and authenticated two-account runtime verification.


## 2026-11-13 — Group full-history search

The active Group search previously filtered only currently loaded local messages. The authenticated server history API now accepts a bounded query, runs parameterized PostgreSQL full-text search only after membership authorization, and uses a matching GIN index. Android sends the encoded query with the stable timestamp/ID cursor, debounces requests, loads older matching pages, reports empty/error states and explicitly falls back to loaded local messages while offline. PostgreSQL integration tests cover group isolation, cursor pagination, no-match and parameter binding.


## 2026-11-13 — Group leave and ownership journey

The active Group More menu now exposes Leave group. Non-owners call the authenticated leave route and the local group/chat cache is cleared only after server success. Owners are required to choose another current member; ownership is transferred through the server transaction before the owner leaves. If transfer succeeds but leave fails, local roles are updated to the server-confirmed owner/moderator state and the user can retry leaving without re-transferring ownership. This still requires two-account live verification.


## 2026-11-13 — Group read receipts and durable conversation metadata

The active Group conversation now records read receipts per message and authenticated group member. The server creates an idempotent `fynx_group_message_reads` relation, only accepts receipt batches from current group members, excludes a sender marking their own message, and publishes updated message payloads over the existing authenticated group realtime channel. The client marks incoming messages read when the conversation opens, when realtime messages arrive, and when older history pages are loaded. The sender sees a double tick and a real `Seen by N` count; the UI no longer treats server acceptance as a group read receipt.

Local chat persistence now round-trips message deletion state, pin state, sender identity, and the last known group read-receipt usernames. This keeps the offline conversation view consistent after process restart while the server remains authoritative when online.

**Verification added:** the PostgreSQL group-history test now exercises per-member receipt uniqueness, idempotent repeats, sender exclusion and group isolation. Source contracts cover the authenticated route, receipt payload, account-bound history/realtime handling, older-page marking and local persistence. Exact-head Android and parity checks are required before certification; live two-account receipt visibility still requires runtime verification.

**Still not implemented:** group voice/video calling. The current call transport models one caller and one callee with a single remote peer connection; adding group calling requires a proper multi-participant room/signaling and media architecture, not a decorative call button. The later sections in this matrix record the subsequent implementation of the received-media gallery-save preference and the server-enforced new-member history visibility policy. Those controls and their backend authorization are now present, but their end-to-end Android behavior still requires runtime verification.


## 2026-11-13 — New-member group history privacy

The group service already stored an admin-controlled `show_history_to_new_members` permission but had not connected it to the active chat history, search, read-receipt, message-action or attachment authorization paths. The active Group settings now expose the switch. When disabled, a newly joined member can only load messages sent from their membership start time onward; the same cutoff is enforced for read receipts, replies, edits/reactions/pins/deletes and protected group-media retrieval. Existing groups retain the prior default of showing history to new members.

The PostgreSQL history test now verifies both policy states and confirms that a hidden old attachment cannot bypass the setting. CI source contracts cover the settings UI/API and each protected access path. Runtime verification with two real group accounts remains required before certification.


The group send endpoint also validates an idempotent retry against the original sender, message payload and current member history cutoff. A reused message ID can no longer return another member's message or reveal an old message hidden by the new-member history policy; mismatched retries receive HTTP 409.


## 2026-11-14 — Source-level coverage reconciliation (not runtime certification)

The current PR #535 source now includes the following controls that older audit notes had listed as missing:
- Private conversation older-history pagination uses the server's bounded history cursor and account/session boundary checks.
- Private message full-history search has server-backed cursor pagination.
- Private long-press multi-select exposes Copy and Cancel actions.
- Group conversation older-history pagination appends only server-authorized pages and carries the server history-scope boundary.
- Group full-history search has cursor pagination.
- The active group settings expose received photo/video gallery saving and the server-backed “Show previous messages to new members” policy.

These are implementation observations, not GREEN journey gates. The private history test currently checks SQL pagination/isolation with a temporary PostgreSQL fixture; it is not an authenticated HTTP/API two-account test. The group content PostgreSQL integration fixture creates distinct ADMIN, MEMBER and outsider users and exercises the authenticated route handlers, including send/reply, read receipts, idempotent retry, history search/cursor pagination, history privacy, polls and events. That verifies backend route behavior in CI, not a live Android two-account session against Railway.

The outstanding Android runtime acceptance remains: verify the active private and group screens, older-page loading, search pagination, retry after reconnect, attachments and protected retrieval, per-chat/group notification preferences, mention targeting, member/owner actions, read receipts and account switching with real app sessions. A local offline emulator group fixture may certify local group creation/message persistence only; it must not be reported as server delivery or cross-account proof.
