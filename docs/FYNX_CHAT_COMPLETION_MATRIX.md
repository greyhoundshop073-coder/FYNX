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
