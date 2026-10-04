# FYNX Chat Push Runbook

Use this runbook for every Chat completion batch. It is designed to move quickly without bypassing investigation or risking already-working Chat behavior.

## Before coding

1. Confirm the live `main` HEAD.
2. Read `docs/FYNX_REALITY_AUDIT_CODEX_INSTRUCTIONS.md`.
3. Read `docs/FYNX_CHAT_COMPLETION_CONTRACT.md`.
4. Read `docs/FYNX_CHAT_COMPLETION_MATRIX.md`.
5. Inspect only the relevant Chat implementation, callers, tests, verifiers and workflows needed for the selected gate.
6. Trace the real user journey and identify the first broken/missing link.
7. Do not change GREEN items without a concrete reason.

## Batch strategy

Use the smallest **coherent** batch, not the smallest possible commit.

Good batches:

- core message reliability + its direct tests
- search + result navigation + its direct tests
- media lifecycle + its direct tests
- group permission/reconciliation fixes + their direct tests
- notification/realtime fixes + their direct tests

Bad batches:

- unrelated Chat + Marketplace + Social changes
- speculative refactors
- replacing working systems because a new implementation is easier
- adding features only to satisfy a theoretical comparison

## Verification before push

Run the relevant local/static tests and verifiers. Inspect the exact changed files and their callers. Confirm that the fix uses existing FYNX systems rather than creating duplicates.

## Push

Every Chat commit message begins with:

`FYNX-THIS-CHAT —`

Push only a meaningful, coherent, investigated batch.

## Verification after push

1. Inspect the resulting commit and diff.
2. Confirm only intended files changed.
3. Check Android build/test/lint/CI status.
4. Check backend verification when backend behavior changed.
5. Check the relevant Chat verifier(s).
6. Build the APK.
7. Verify the changed feature is reachable and visible in the APK.
8. Verify the complete journey: entry -> interaction -> result -> back/return state.
9. Verify at least the directly affected existing Chat journeys.
10. If realtime/media/camera/notifications/lifecycle behavior is involved, use the applicable real-device journey.
11. If anything is RED, stop and investigate the root cause before another feature or batch.

## Speed rule

Fast progress comes from batching compatible, already-investigated work and using focused verification. Do not skip verification to make a push faster.

## Completion

Only update a matrix item to GREEN after post-push evidence supports it. After all required gates are GREEN, perform the final Chat regression certification and lock Chat.
