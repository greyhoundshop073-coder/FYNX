# FYNX Chat & Groups PR #523 / #535 reconciliation — 14 November 2026

## Scope and method

Compared the current heads of PR #523 and PR #535 across all 29 shared paths. This is a source-level reconciliation record, not a merge or runtime certification. PR #535 remains the working branch; PR #523 remains open and unmerged. The canonical integration branch was not modified.

- PR #535: `9fc5ee5f7d1eab296fed71c12a95df036ba7cbe2`
- PR #523: `9101174cf50f28482c3e160ae0482f37fe655be5`
- Canonical base: `integration/fynx-single-apk-20261013`

## Shared paths reviewed

1. `.github/workflows/android-build.yml`
2. `app/src/main/java/com/fynx/app/ui/ChatModels.kt`
3. `app/src/main/java/com/fynx/app/ui/ChatsPanel.kt`
4. `app/src/main/java/com/fynx/app/ui/ConversationPanel.kt`
5. `app/src/main/java/com/fynx/app/ui/FynxBackendClient.kt`
6. `app/src/main/java/com/fynx/app/ui/FynxChatStore.kt`
7. `app/src/main/java/com/fynx/app/ui/FynxFirebaseMessagingService.kt`
8. `app/src/main/java/com/fynx/app/ui/FynxGroupRemoteClient.kt`
9. `app/src/main/java/com/fynx/app/ui/FynxGroupSettingsPanel.kt`
10. `app/src/main/java/com/fynx/app/ui/FynxGroupsPanel.kt`
11. `app/src/main/java/com/fynx/app/ui/FynxInChatSound.kt`
12. `app/src/main/java/com/fynx/app/ui/FynxProductionMessaging.kt`
13. `app/src/main/java/com/fynx/app/ui/FynxRemoteMedia.kt`
14. `app/src/main/java/com/fynx/app/ui/NotificationFoundation.kt`
15. `backend/groupContentRoutes.js`
16. `backend/groupMembershipRoutes.js`
17. `backend/groupRoutes.js`
18. `backend/notificationBootstrap.js`
19. `backend/server.js`
20. `backend/test-media-route-db.mjs`
21. `backend/verify-media-object-storage.mjs`
22. `backend/verify-media-payload-cache.mjs`
23. `docs/FYNX_CHAT_COMPLETION_MATRIX.md`
24. `scripts/run_android_runtime_certification.sh`
25. `scripts/verify_authenticated_runtime_navigation.py`
26. `scripts/verify_chat_group_layout_and_parity_gate.py`
27. `scripts/verify_fcm_notifications.py`
28. `scripts/verify_r5a_groups.py`
29. `scripts/verify_this_chat_workstream.py`

## Reconciliation decisions and repairs

### Keep the newer PR #535 implementation

PR #535 carries later work that should not be replaced by the older #523 versions:

- Session-bound API requests and durable private attachment outbox/retry ordering.
- Account-scoped group creation and reconnect synchronization.
- Group read receipts and per-member `readBy` state.
- Group history-scope tokens, paginated history/search and new-member history privacy.
- Poll selection state, closed-poll handling and vote serialization.
- Group message idempotency, sender/payload checks and account-switch guards.
- Stronger protected group-media authorization, including the membership/history cutoff and prevention of an uploader bypass after membership removal.
- Group owner transfer/leave remains reachable in the active conversation's More menu; the older Settings-panel placement is not required as a duplicate control.

### Restore older protections that were absent from the newer branch

1. **Per-chat mute:** #535 checked the conversation notification preference but omitted the older `FynxPreferencesStore.isChatMuted` guard. Restored both checks in `FynxFirebaseMessagingService.kt` and added a verifier assertion.
2. **Gallery capability:** the group received-media switch in #535 was enabled on Android versions where the MediaStore saving implementation cannot operate. Restored the Android 10+ capability gate and explanatory text; added a source verifier.
3. **Outbox cleanup:** #535's `FynxChatStore.clear` removed history and sync state but omitted the outbox replacement alias. Restored removal of `outboxReplacementKey` and added a regression assertion.
4. **Revoked group access:** #535's group-history refresh failure path retained local cached history after HTTP 403/404. Restored explicit 403/404 classification, captured-account checks, local history clearing, group removal and return to Groups. Added a parity assertion.

### Runtime group-creation certification correction

The failed screenshot artifact showed that the test tapped **New group** on the Chat & Groups surface, which navigated to the dedicated Groups manager. It then incorrectly treated the manager's **Create group** affordance as proof that the creation dialog was open. The screenshot was still the manager screen, not a dialog.

The runtime flow now requires:
1. The dedicated Groups manager's unique subtitle, `Your communities in one place`.
2. Its distinct `Open create group dialog` action.
3. The actual `Group name input` field before proceeding to Description.

The active Groups manager button and dialog title now have explicit accessibility descriptions. The test no longer accepts the manager's own Create group affordance as the dialog destination.

## Latest pushed changes on PR #535

- Accessible create-dialog action and title.
- Correct two-step Groups-manager-to-dialog navigation in the runtime script.
- Regression gate for the unique dialog transition.
- Restored legacy per-chat mute enforcement.
- Android 10+ gate for received-media gallery saving.
- Regression gates for notification mute and gallery capability.
- Restored outbox replacement-alias cleanup when clearing a conversation.
- Restored 403/404 group-history cache purging under the initiating account.
- Regression gates for outbox cleanup and revoked membership privacy.

## Verification boundary and remaining work

The prior runtime run on `b32822d` executed 58 instrumentation tests (zero failures, one skipped live-backend test), but screenshot certification failed before group creation. Its actual screenshot confirms the test remained on the dedicated Groups manager. The new navigation fix has been pushed, but a fresh CI run and genuine `group-chat-inside.png` are not yet available for the new head.

The latest PR head at the time this record was written is `9fc5ee5f7d1eab296fed71c12a95df036ba7cbe2`. GitHub currently reports no check statuses or PR workflow runs for that exact head; therefore these new source repairs are **not yet CI-verified**.

Still pending:
- Fresh Android build/unit/lint/instrumentation and Chat/Groups parity/security CI on the current head.
- Actual dialog, group creation, persisted group, conversation, message send/reopen and screenshot inspection.
- Offline-to-online group synchronization on a real connected backend; current local fixture can verify only local persistence while disconnected.
- Real authenticated two-account delivery, membership/role operations, notifications, read receipts, history cutoff and protected media retrieval.
- Complete runtime coverage for private messaging and the listed group journeys.
- Multiparty group voice/video calls. Existing one-caller/one-callee transport is not group calling; a room/signaling/multi-peer media design is still required.

No changes were made to the canonical integration branch. Do not merge either PR until the exact-head checks and required runtime evidence are reviewed and the repository owner authorizes integration.
