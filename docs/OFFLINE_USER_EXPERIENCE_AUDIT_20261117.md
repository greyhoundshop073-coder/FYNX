# FYNX offline / backend-expired user-experience audit — 2026-11-17

**Scope:** static source review of `integration/fynx-single-apk-20261013` at `b075bf5` (Nov 16). Not a runtime test: no Android SDK/emulator was available in the review environment (see "Limits"). Login is intentionally left untouched per `docs/FYNX_TEST_LOGIN_LOCK.md` and the owner's confirmation that it will be tightened once the backend is reconnected.

## Which build is "latest"?

- Canonical branch head reviewed: `b075bf5` (Nov 16).
- Open PR #563 (`integration/fynx-single-apk-consolidation-20261117`, head `ef66805`, Nov 17 05:41) is newer. It differs from the reviewed head in 13 files (Marketplace UI, `FynxSharedControls.kt`, Android tests, `android-build.yml`). An APK built from the canonical branch alone will not contain it.
- 164 PRs are open in total, many stale (targeting `main` or old feature branches).

## What already behaves well without a backend

| Area | Behaviour |
|---|---|
| Connection header | `FynxAppConnectionManager` reports CONNECTED only after a successful health call; retries with backoff. |
| Chat send | Durable account-scoped outbox (`FynxOfflineOutbox`/`FynxOfflineSync`), "waiting to send" labels, attachments up to 12 MB copied to app storage. |
| Wallet | Friendly copy, retry, and "Balance unavailable until your connection is restored". |
| Marketplace | Uses `marketplaceSafeError(error, fallback)` (FynxMarketplaceSellerFlowSupport.kt:168) with unit tests: raw HTTP bodies and host names are hidden. |
| Home feed | Cached snapshot kept on refresh failure; Retry card shown. |

## Gaps found

1. **Raw error text reaches users outside Marketplace.** About 100 call sites use `it.message ?: "…"` (Announcements 30, Home 14, Wallet actions 8, Camera 7, Group social 6, Advertising campaign 6, Group chat 5, others). A dead Railway host returns an HTTP error body that the backend client wraps as `FYNX backend returned HTTP <n>: <body>`; those strings can surface as-is. Recommended fix: generalise the existing `marketplaceSafeError` (do not add a parallel helper) and adopt it screen by screen.
2. **Shared UI states are unused.** `FynxUiStates.kt` (`FynxLoadingState/EmptyState/ErrorState/OfflineState`) is not referenced by any screen. Adopt in Home, Chats, Marketplace, Wallet first.
3. **Dead controls.** `onClick = {}` at `FynxAnnouncementsPanel.kt:126`, `:381` (informational chips; should be non-clickable chips) and `FynxMarketplaceReferenceHome.kt:47` (needs owner decision on intended action).
4. **No string resources.** `res/values` has only `styles.xml`; all copy is hardcoded (388 hardcoded colours, 0 `stringResource`). Blocks localisation.
5. **Structure.** `FynxMarketplacePanel.kt` ~3,970 lines; some Home logic written as very long single-line lambdas; 96 `!!` force-unwraps; navigation by string keys in a 950-line `FynxApp.kt`.
6. **Release pipeline.** No `buildTypes`/signing/R8 config; CI only builds debug APKs. The shared tester APK is a debug build (login bypass is gated on `FLAG_DEBUGGABLE`, which is correct, but release has never been built or tested here).

## Open-PR overlap map (why code edits were deferred)

Files planned for the error-message/shared-state work, and open PRs that already modify them (checked via the PR files API for a sample of PRs; **not exhaustive across all 164**):

| File | Open PRs touching it |
|---|---|
| `FynxRemoteHomeSocialPanel.kt` | #484, #349, #534 |
| `FynxAnnouncementsPanel.kt` | #436, #438, #440, #533, #536 |
| `FynxMarketplace*` panels, `FynxBuyerWalletPanel.kt` | #563 (consolidation), #502 |

Editing these now would create merge conflicts with in-flight work. Suggested order: merge/triage #563 first, then #536/#533/#438/#436/#440 (Announcements), then apply the error-mapper change on top.

## Limits of this review

- The review sandbox blocked `services.gradle.org` and `dl.google.com` (HTTP 403), so Gradle, the Android SDK, an emulator, lint and instrumentation tests could not run, and no screenshots could be captured.
- The GitHub API was only available anonymously and read-only (no PR creation or merge).
- Nothing in this document has been verified on a device.

## Recommended next steps

1. Build the exact canonical head (or #563 once reviewed) in CI and install that APK; name APKs by full commit SHA.
2. Generalise `marketplaceSafeError` into a shared helper; adopt it in non-overlapping screens first.
3. Adopt `FynxUiStates` on Home/Chats/Marketplace/Wallet failure paths.
4. Resolve the three dead controls.
5. Run a screen-by-screen backend-off walkthrough on an emulator and record results in the module completion register.
6. Add a release build type (signing, R8) and a release smoke test before any public build.
