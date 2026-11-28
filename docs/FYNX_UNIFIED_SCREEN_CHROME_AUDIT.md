# FYNX Unified Screen Chrome and Navigation Audit

**Status:** implementation in progress; source changes are not yet runtime-certified.  
**Canonical branch:** `integration/fynx-single-apk-20261013`  
**Work branch:** `fix/fynx-unified-screen-chrome-20261128`  
**Base commit:** `49400debbc52aba35f6baff60c9bfc91f7bff478`

## Goal
Make FYNX feel like one coherent Android application: the status bar remains readable in every appearance mode; each active screen has one clear top header; back navigation returns to the immediate parent; and content begins below the header without repeated titles, duplicated controls, or unnecessary blank space.

## Shared rules
1. The app shell owns the primary title and back action whenever it renders a destination toolbar.
2. A child panel must not repeat the shell's title or draw a second back arrow for the same navigation level.
3. Preserve genuinely distinct navigation levels. Settings → Profile → Home remains a two-step route.
4. Android system Back follows the same immediate-parent route as the visible header back action.
5. FYNX's effective Light/Dark appearance—not only the device setting—determines status and navigation bar icon contrast.
6. Use dark icons on light surfaces and light icons on dark surfaces. Reserve system insets exactly once.
7. Keep descriptions only when they explain a meaningful limitation, safety condition, consent, or next action.
8. Preserve feature routes, role checks, data behavior, accessibility labels, and every working action.
9. Test clipping, scrolling, narrow widths, font scaling, keyboard visibility, dialogs/sheets, and touch targets.

## Changes carried forward
The following compact-screen source changes from PR #602 were selectively applied against the latest canonical source after every changed Kotlin hunk matched:
- Privacy & Safety: remove its nested back/title/subtitle and duplicate status-bar inset; the shell owns the destination toolbar.
- Business Account: use the shell title/back action instead of repeating title copy and a second back control.
- Money Center and child tools: remove repeated large titles and generic explanatory copy while retaining material payment/local-data limitations.
- More: compact search field and spacing while retaining search functionality.
- Calendar and Advertising Dashboard: remove repeated screen headings and boilerplate while preserving controls.
- Share & Invite, To-Do, and Group Invite: remove redundant headings/instructions/back affordances while preserving actions and validation.
- Update the authenticated navigation verifier to use the shell's Money Center label.

The earlier PR #602 is behind the current canonical branch and its latest Android runtime job failed when the emulator/ADB connection was unavailable. Its changes are being selectively ported; the stale branch is not being merged wholesale.

## System-bar correction
`FynxTheme` derives status/navigation bar icon appearance from FYNX's effective Light/Dark appearance and applies it on recomposition through `WindowInsetsControllerCompat`. This covers a user selecting Light while Android itself remains in Dark mode. Focused JUnit tests now cover the actual mismatch reported by the owner: FYNX Appearance = Light while Android system theme = Dark (and the inverse), plus System/Scheduled and branded dark modes. Runtime verification is still required to prove actual window flags and clock/signal/battery readability on a device/emulator.

## Protected workstreams
- **Profile / Settings / Notifications — PR #586:** do not overwrite `ProfilePanel.kt`, `NotificationPanel.kt`, account/email settings, or its Settings shell-state work. Preserve Settings → Profile → Home; re-audit after integration.
- **Chat / Groups — PR #588:** do not overwrite `ChatsPanel.kt`, `FynxGroupsPanel.kt`, group settings, conversations, stores, or offline recovery.
- **Calls / Marketplace Live Proof:** inspect current canonical implementation and active calls PR before touching `FynxApp.kt`, `FynxGroupsPanel.kt`, or call routes.
- **Marketplace / Home / Status / Admin / Wallet / Camera:** no feature behavior is in scope; preserve their owners and active PRs.

Coordination comments were posted on PRs #602 and #586 before implementation. Chat/Groups source remains untouched.


## Additional confirmed nested-header findings (owner coordination required)

- **Stories / Status:** the app shell currently renders the primary Stories header. `FynxStatusTimelinePanel` independently renders a “Status” header and calls `statusBarsPadding()`; the archive screen also has its own header/back action and status-bar inset. This can create stacked titles and duplicated top inset depending on the active child state. PR #603 owns the Status/Stories restore work and PR #586 also owns shared `FynxApp.kt` Settings state; comments were posted to both owners. Do not edit those files from this branch until the shell-state change is coordinated.
- **Money Center child tools:** the app shell shows “Money Center” with Back → More/Features. Inside `MoneyCenterPanel`, selecting a child tool renders an in-panel “← Money Center” action and Android Back → Money Center. These are different route levels, so deleting either is incorrect. The shell must receive the active child title/back state: child title with Back → Money Center; Money Center title with Back → More. `FynxApp.kt` is owned by PR #586 and this route-state coordination has been raised there. Do not silently change the route in this PR.
- **Nested feature screens generally:** audit whether a visible child Back is an identical duplicate or the only action that reaches an immediate parent. Keep the child action when it serves a different level, but collapse/replace the shell toolbar so only the active level is visible.

These findings are source-confirmed, but their app-shell fixes are pending the owning workstream coordination and must be runtime-tested after integration.

## Remaining app-wide audit
- [ ] Profile → Settings: verify one visible Settings header on both entry routes; Settings Back returns to Profile; Profile Back returns Home.
- [ ] Notifications: verify Home-bell and Profile-settings entry paths independently.
- [ ] Chats / Groups: inspect repeated headings and nested back controls after PR #588 integrates.
- [ ] Home / Feed: inspect status bar, header, pinned overlays, search/discovery controls, and scroll insets.
- [ ] Friends / Contacts: inspect toolbar and nested search/selection routes.
- [ ] Status / Stories / Camera: inspect creation, archive, viewer, editing, and cancel/back paths.
- [ ] Marketplace / product details / My Shop / Seller Center: inspect buyer and seller child routes without changing checkout/listing behavior.
- [ ] Admin / Owner Control Center: inspect role-specific headers without weakening server-side authorization.
- [ ] Calls / Official Announcements: inspect nested routes and sheets after owning PRs integrate.
- [ ] Dialogs / sheets / full-screen editors: verify close/back behavior and avoid a second top-level toolbar.
- [ ] Run real emulator instrumentation on this exact branch; inspect screenshots and UI hierarchies in Light, Dark, and System appearance.
- [ ] Verify clock, signal, Wi-Fi/mobile data, and battery readability on a physical Android device.

## Acceptance evidence required
A successful compile alone is not completion. Final acceptance requires Android unit/lint/build results, full Android instrumentation and real screenshot/UI-hierarchy artifacts, explicit Light/Dark system-bar evidence, immediate-parent toolbar/system Back route checks, and confirmation that no protected workstream was overwritten.

## Exit-control rule: Back arrow versus X

A navigation destination must expose one visible exit affordance for one navigation level:
- If the screen is a pushed/nested destination and the visible action returns to its immediate parent, use the standard Back arrow.
- Do not show a second X/Close control in the same screen when it invokes the same callback or returns to the same destination as the Back arrow.
- A true modal, sheet, media viewer or editor that has no Back toolbar may use one Close/X control; Android system Back and swipe dismissal (where supported) must map to the same dismissal behavior.
- X controls that remove an attachment, clear a search query, cancel a reply, remove a selected location/music item, or dismiss a child chip are content-editing actions, not navigation exits; preserve them.
- Do not remove distinct-level navigation. If the outer shell Back and child Back lead to different parents, collapse the outer header while the child route is active rather than deleting either route.

### Source-confirmed duplicate sent for owner correction

- Private Conversation, new-conversation relationship card (ConversationPanel.kt): the primary top toolbar already has a Back arrow wired to onBack; the relationship card also had an X button wired to the exact same onBack callback. This is a genuine duplicate exit affordance. A minimal remove-X request was posted to Chat/Groups owner PR #588; preserve the main Back arrow, Android Back, and all relationship actions.
- Notifications (NotificationPanel.kt): its child header has a Back arrow while the app shell also renders a Back action for the destination. The two entry paths must be traced separately before removing one, preserving immediate-parent destinations. A coordination request was posted to PR #586.
- Status Add Status / creator: the Add Status surface has an X close action and a parent Stories/Status shell header may still be present depending on route state. PR #603 and PR #586 were asked to coordinate the visible active-level header and preserve Android Back.

Do not apply a repository-wide replacement of Close icons with Back arrows: many X icons intentionally remove media, clear a field, cancel a reply, or close a true modal where no Back toolbar exists.
### Full-screen surfaces converted to Back

These are navigation exits, not content-removal actions, so their X glyphs were replaced with the standard Back arrow while preserving the existing close/dismiss callbacks and Android Back handling:
- Home Comments full-screen panel: Back arrow in the left header; refresh remains on the right; Android Back still calls the same close callback.
- Home post-media viewer: Back arrow replaces the viewer X; media paging and all post actions remain unchanged.
- Home video discovery page and immersive discovery viewer: Back arrow replaces X; the immersive arrow is positioned at the top-left with status-bar inset.
- Home fullscreen media viewer: Back arrow is positioned at the top-left with the status-bar inset.
- Other user's profile post viewer: Back arrow replaces the page-dismiss X.

Content-action X controls remain intact (remove media, clear search, cancel reply, remove location/music, etc.). The Chat conversation duplicate exit remains assigned to PR #588, and Status Add Status header coordination remains assigned to PR #603/#586.
- The profile post swipe viewer explicitly maps Android system Back to the same `onClose` destination as its visible Back arrow (`ProfilePostSwipeViewer`).