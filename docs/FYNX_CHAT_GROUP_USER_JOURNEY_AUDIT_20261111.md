# FYNX Chat & Groups real-user audit — 11 November 2026

## Scope and method

This audit follows the production routes from `FynxApp.kt`, not similarly named legacy panels. The active private conversation is `ConversationPanel.kt`; the active group conversation is `FynxGroupConversationPanel` in `FynxGroupsPanel.kt`. The review traces the Chat list, message persistence/outbox, authenticated messaging clients, group membership and message routes, realtime events, notification delivery, and the Android runtime workflow.

This is a source-and-CI investigation, not a claim that a real two-account session has already been completed. The live backend probe was skipped in the prior CI evidence, and the CI runtime workflow did not run the full authenticated Chat/Groups navigation for that PR.

## What is already present in the active implementation

- Private chat history is fetched from the authenticated messages API; the local store is account-scoped and includes pending-message reconciliation to avoid losing an acknowledged send when Compose writes a stale snapshot.
- Private message actions include reply, edit, delete, reactions, pinning and forwarding. The messaging client also has a bounded read-receipt operation.
- Private outgoing messages have a persistent local outbox/retry path and server client-message IDs for idempotent recovery.
- The active group conversation uses authenticated group history/send/action endpoints and a realtime group-message event with periodic refresh as a fallback.
- Group attachments are staged in app-owned storage before upload; the current contract bounds staged files, retains failed items for retry and reuses an uploaded media ID where possible.
- The active group composer includes camera, video note, gallery, files, location, contact, poll, emoji and member mention suggestions. Group social content includes posts/polls and events.
- Group message reply, reactions, pinning and deletion are wired in the active screen. The server also has an edit endpoint, but the active screen previously did not expose it; this audit branch adds the missing action.

These are source-path observations. Each still needs runtime verification on an authenticated test account.

## Confirmed defects and repair status

| Area | Finding | Repair status in this branch |
|---|---|---|
| Group member add/remove/roles | The active Members dialog previously edited only the local group model. The server's authenticated add, promote, demote and remove endpoints were not called. A later group refresh could undo the apparent changes. | Connected to authenticated APIs; local state changes only after success. |
| Group member blocking | The Members dialog previously stored block state only in local group preferences. | Now loads and updates the account's authenticated block list. This is an account-level block, not group removal. |
| New-member history privacy | “Show chat history to new members” previously saved only a local preference. The group messages endpoint did not enforce it, and the active client merged all locally cached server messages back into the response, which could bypass the new visibility rule after a re-join. Removed members could also retain the open conversation's local cache after a 403. | Added a server-backed setting and join-time filter; after a successful refresh the client preserves only unsent outbox items, and a 403/404 now clears local group history and exits the conversation. |
| Group message editing | The edit endpoint existed and the legacy `GroupChatPanel.kt` used it, but the app routes users to `FynxGroupConversationPanel` in `FynxGroupsPanel.kt`, which had no Edit action. | Added Edit to the active message menu and save/cancel composer state. |
| Group appearance and wallpaper | The active group conversation used the general chat wallpaper store; the Group Settings appearance and Group wallpaper stores were not read by the active panel. | The active panel now applies the group appearance palette and group-specific wallpaper background. |
| Group leave, ownership and reporting | Backend endpoints existed for leave, ownership transfer and group reports, but the active Group Settings/member screens did not expose those journeys. | Added authenticated leave, owner transfer, group report and member report flows; owners must transfer ownership before leaving. |
| Group received-media saving | The Group Settings toggle saved a preference but the active group media renderer did not use it. | The active group image/video renderer now optionally saves received photos and videos into Pictures/FYNX or Movies/FYNX on Android 10+; saved items are deduplicated per account/media. |
| Group delivery indicator | Group messages were mapped as both delivered and read immediately; optimistic text messages were also initially marked delivered/read before the request completed. Group UI therefore showed a misleading double-check. | Pending messages now start undelivered/unread; successful group sends show a single server-delivered check until per-member read receipts exist. |
| Per-conversation notification delivery | FCM mapped GROUP pushes to the private Messages channel, so the global Groups preference was bypassed. Per-chat and per-group mute switches were not consulted before displaying a push banner. | Added a dedicated Groups notification channel and device-side checks for per-chat/per-group mute before showing the banner. |
| In-app chat feedback | Private sound/vibration controls were stored but not read by the incoming-message feedback call; Group message received sound existed but was not called. | Private in-app sound/vibration now respect the chat settings; group received sound is played only for the newly received event message and only when unmuted. |
| Group mention notifications | The active composer suggested members after `@`, but the backend sent only generic group notifications and did not identify the mentioned recipients. | Group message notification bootstrap now sends a distinct targeted notification to each mentioned group member.
| Test target accuracy | The R5A and final-closure checks read legacy `GroupChatPanel.kt` for active group behavior. The final-closure required-path mapping also pointed “group settings” at `FynxGroupsPanel.kt`. | Updated the checks to inspect the active group screen and actual settings panel. |
| Test enforcement | The Chat/Group parity script calculated and printed its pass/fail result before later assertions were added, so those later checks could not fail the job. | Moved the final evaluation to the end so every assertion is enforced. |
| Runtime coverage | The Chat/Groups PR path was not in the Android workflow's full runtime trigger list. Prior CI therefore did not run the authenticated Chat/Groups navigation for this workstream. | Added this repair branch to the emulator/runtime trigger list. Runtime result is still pending. |

## Remaining gaps requiring the next repair batches

1. **Private history pagination:** the endpoint now returns the newest 200 messages (the previous query returned the oldest 200), but there is still no cursor/older-page UI. Search is limited to loaded history.
2. **Group history pagination:** the endpoint defaults to 50 (maximum 100) and the active screen has no older-page UI. Search is limited to loaded history.
3. **Notification runtime verification:** per-chat and per-group mute now suppress FCM banners on the device; the Groups channel now maps to the global Groups preference. These paths still need Android runtime verification on a real test device.
4. **In-app feedback runtime verification:** per-chat in-app sound/vibration and group received sound are now wired to their preferences, but need runtime verification. Android notification-channel sound/vibration remains subject to system channel settings.
5. **Group settings runtime verification:** group appearance, wallpaper and photo/video auto-save are now connected to the active screen, but need device verification (including Android versions below Android 10, where automatic gallery saving is not enabled).
6. **Mention notification runtime verification:** targeted group mention notifications are now generated by the backend bootstrap, but need a two-account authenticated test to prove only mentioned recipients receive the mention-specific alert.
7. **Group administration completion:** the server has leave, ownership-transfer and report endpoints, but the active client/settings journey does not expose the complete leave/transfer/report flow.
8. **Message selection:** the Chat completion contract lists message selection in V1 scope, but no multi-select state/action flow was found in the active private conversation.
9. **Real-user certification:** add authenticated tests for send/receive, retry after disconnect, edit/delete/reaction/pin, attachment upload/download/playback, read receipts, mute/push behavior, group add/remove/promote/demote, history visibility for a newly joined account, mentions, polls/events, and account isolation. A two-account test is required for cross-user delivery and group membership claims.

## Runtime evidence required before declaring completion

- The Android build, unit tests, lint and the active Chat/Groups source gates must pass on the exact PR head.
- The emulator run must execute the authenticated runtime navigation when the CI E2E credentials and backend are healthy. If credentials/backend are unavailable, report the visual run as local-only and do not call it a real-user test.
- Capture the actual active private conversation, active group conversation, member dialog, group permissions/history control, and retry/error states from the running app. Do not substitute a mock image or a legacy screen.
- Use two test accounts for cross-user delivery and membership verification. Backend-dependent checks must be explicitly marked skipped when the backend is unavailable.
- Do not merge or certify the Chat/Groups gate while any of the above remains unverified.
