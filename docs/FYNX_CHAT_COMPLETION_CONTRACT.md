# FYNX Chat Completion Contract

Repository: `greyhoundshop073-coder/FYNX`
Branch: `main`

## Purpose

This is the canonical definition of **Chat V1 COMPLETE**. It works together with `docs/FYNX_REALITY_AUDIT_CODEX_INSTRUCTIONS.md` and does not replace that document.

The purpose is to prevent repeated audits from reopening Chat because another messenger has a feature that was not part of the approved V1 completion scope.

## Locked rule

Chat may be reopened after certification only for:

1. a demonstrated regression;
2. a failed Chat acceptance test or verifier;
3. a confirmed security/privacy defect;
4. a production-critical defect;
5. an explicitly approved Chat upgrade.

A theoretical feature comparison with WhatsApp, Telegram, Signal, or another messenger is **not** by itself a reason to reopen completed Chat.

Future improvements are recorded as **UPGRADES**, not as unfinished V1 work.

## Required completion gates

### Gate 1 — Core messaging

- text send/receive
- realtime delivery
- sending/sent/delivered/read/failed lifecycle
- retry after failure
- offline/outbox behavior
- reconnect/reconciliation
- duplicate prevention
- message ordering
- history loading
- app restart recovery

### Gate 2 — Message actions

- reply
- edit
- delete
- reactions
- pin
- copy
- forward
- selection/bulk actions where implemented in V1
- permissions and server reconciliation
- state remains correct after reload/reconnect

### Gate 3 — Media and attachments

- images
- video
- audio
- voice messages
- documents
- attachment selection
- upload/download
- progress/failure/retry where applicable
- playback/viewing
- preview/presentation
- interruption/reconnect behavior
- no duplicate or corrupted message state

### Gate 4 — Chat organization

- conversation navigation
- message search
- result navigation back to the source message
- pinned messages
- saved/important messages if included in the certified V1 scope
- shared media/files/links navigation where exposed

### Gate 5 — Privacy, security and safety

- notification privacy/preview controls
- read receipts
- last-seen/presence controls
- mute controls
- block/report flows where exposed
- secure local handling
- backend authorization for protected operations
- approved encryption/security contract
- no secrets in the Android APK

Features such as disappearing messages, view-once media, Chat Lock, message requests, or other advanced privacy features remain V1 requirements only if explicitly marked required in the acceptance matrix. Otherwise they are future upgrades and must not be used to invalidate Chat V1 certification.

### Gate 6 — Group Chat

- group send/receive
- replies/reactions/media
- member/admin permissions
- mentions
- notification controls
- member management
- invite/access controls where implemented
- moderation/reporting where implemented
- history/search/navigation
- backend authority and reconciliation

### Gate 7 — Notifications and realtime

- foreground behavior
- background behavior
- mute behavior
- preview behavior
- sound/vibration policy
- push + realtime duplicate suppression
- read-state interaction
- typing/presence behavior
- account/conversation settings respected

### Gate 8 — Production certification

Before certification, verify:

- relevant source/tests/verifiers pass
- Android build succeeds
- APK is produced
- changed functionality is reachable through the real APK UI
- visible controls and states are correctly positioned
- complete user journey works
- navigation/back behavior works
- applicable backend/storage/realtime journey works
- applicable real-device journey works
- existing known-good Chat behavior remains intact
- no unrelated completed work is regressed

## Acceptance statuses

Use exactly these statuses for each gate/item:

- **GREEN / COMPLETE** — implemented, correctly integrated, APK-visible, verified, and applicable user journey passes.
- **YELLOW / INCOMPLETE** — implementation exists but the complete journey is not yet proven.
- **RED / FAILED** — concrete failure exists and must be root-caused before moving on.
- **NOT REQUIRED FOR V1** — deliberately excluded from the current Chat completion scope; track as an upgrade if useful.

## Investigation workflow

For every Chat batch:

1. Inspect live `main` and latest commit.
2. Inspect existing implementation and callers/routes.
3. Trace the user journey from entry point to completion.
4. Trace client state, realtime/API, backend authority and storage when applicable.
5. Inspect relevant tests, verifiers and CI workflows.
6. Identify the first concrete broken or missing link.
7. Define the smallest correct fix; reuse existing systems.
8. Group only related, compatible fixes into a batch.
9. Run local/static verification available.
10. Push only after the pre-push investigation is complete.
11. Inspect the resulting commit/diff after push.
12. Check Android build/test/lint and relevant verifiers.
13. Verify APK-visible behavior and applicable real-device journey.
14. Check regression against previously GREEN Chat behavior.
15. Mark the batch GREEN only when evidence supports it.

A verifier, compile result, or source-code presence is not sufficient by itself.

## Push discipline

Every Chat commit must begin with:

`FYNX-THIS-CHAT —`

Push related fixes together when they share one audited workstream. Do not combine unrelated risky changes merely to make fewer commits.

Every completed batch must leave an auditable result: what changed, why, which checks passed, and what APK/user journey was verified.

## Protection of completed Chat

Once all eight gates are GREEN, record:

```text
CHAT STATUS: GREEN / COMPLETE
CHAT LOCK: LOCKED
CERTIFIED COMMIT: <commit sha>
APK VERIFIED: YES
REGRESSION VERIFIED: YES
```

After that point, work in Social, Stories, Marketplace, Money, Calls, AI, Notifications, or another product area must use established Chat interfaces/contracts instead of modifying Chat internals unless the change genuinely belongs to Chat.

## Future upgrades

Examples that may be considered later without invalidating V1 completion:

- scheduled messages
- disappearing messages
- view-once media
- Chat Lock
- advanced cross-chat search
- richer saved-message/reminder workflows
- advanced group topics/community tools
- additional multi-device capabilities
- AI-assisted chat upgrades

A future upgrade must be explicitly approved and tracked as an upgrade. It must not silently expand the V1 definition of done.

## Final certification rule

When every required gate is GREEN and the final production certification passes, **FYNX Chat V1 is COMPLETE**.

The next development session moves to the next product area. Another chat must not reopen Chat merely to produce a new theoretical gap list.
