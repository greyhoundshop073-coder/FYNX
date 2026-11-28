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
`FynxTheme` derives status/navigation bar icon appearance from FYNX's effective Light/Dark appearance and applies it on recomposition through `WindowInsetsControllerCompat`. This covers a user selecting Light while Android itself remains in Dark mode. A focused JUnit test guards the light/dark mapping. Runtime verification is still required to prove actual window flags and readability on a device/emulator.

## Protected workstreams
- **Profile / Settings / Notifications — PR #586:** do not overwrite `ProfilePanel.kt`, `NotificationPanel.kt`, account/email settings, or its Settings shell-state work. Preserve Settings → Profile → Home; re-audit after integration.
- **Chat / Groups — PR #588:** do not overwrite `ChatsPanel.kt`, `FynxGroupsPanel.kt`, group settings, conversations, stores, or offline recovery.
- **Calls / Marketplace Live Proof:** inspect current canonical implementation and active calls PR before touching `FynxApp.kt`, `FynxGroupsPanel.kt`, or call routes.
- **Marketplace / Home / Status / Admin / Wallet / Camera:** no feature behavior is in scope; preserve their owners and active PRs.

Coordination comments were posted on PRs #602 and #586 before implementation. Chat/Groups source remains untouched.

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
