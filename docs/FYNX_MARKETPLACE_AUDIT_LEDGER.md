# FYNX Marketplace Audit Ledger — Stop Repeating Work

Repository: `greyhoundshop073-coder/FYNX`
Candidate branch: `build/fynx-one-apk-integrated-20261009`
Audit baseline: `346e472e75a41e218130d4c12675d4c4dffc3dde`
Related candidate PR: [#115](https://github.com/greyhoundshop073-coder/FYNX/pull/115)
Status of this document: initial evidence ledger; it is not a release certification.

## Why this ledger exists

Future sessions must use the live repository and evidence to decide what to do next. Do not repeat a feature because it appears on an old checklist, because a previous handoff mentioned it, or because a source verifier is easy to add. This ledger is a checkpoint and must be updated after every meaningful Marketplace delivery.

## Mandatory anti-loop rules

1. Read `AGENTS.md`, `.github/copilot-instructions.md`, `docs/FYNX_RELEASE_GATE.md`, this ledger, and the Marketplace master architecture before working.
2. Verify the live branch, exact head SHA, PR, changed files, and relevant workflow runs. Treat old chat summaries as leads, not truth.
3. For each proposed task, cite the exact source/test/runtime evidence for why it is unfinished. If the capability is already implemented and the applicable evidence passes, mark it implemented and do not redo it.
4. Trace the complete flow: user entry point → UI state → API/storage/backend → response/error → visible result. Trace callers and shared dependencies before editing.
5. Classify each item as:
   - **IMPLEMENTED** — source exists.
   - **AUTOMATED-CHECKED** — named automated checks pass.
   - **RUNTIME-VERIFIED** — the actual user journey was exercised on the APK/emulator/device.
   - **BACKEND-UNVERIFIED** — the client/server path cannot be certified against a reachable, configured test service.
   - **MISSING/DEFECT** — a specific absent behavior or reproducible defect has been identified.
   Never substitute one status for another.
6. Do not create source-pattern checks just to prove source exists when meaningful unit, integration, instrumentation, or runtime tests can prove behavior. Static checks may supplement but never replace behavioral evidence.
7. Do not reopen a passing feature for theoretical concerns. Reopen only for a concrete regression, failing test, newly discovered integration gap, changed dependency, or required release gate that was never run.
8. Plan one coherent, reviewable batch with a measurable user-visible outcome. Combine related safe UI, state, integration, error/retry and tests. No speculative or repetitive pushes. A narrow commit is allowed only to unblock a real required failure or prevent a regression.
9. Do not touch Home, Chat, navigation, unrelated Marketplace/backend systems, server configuration, or unrelated files while working this Marketplace scope. The candidate PR includes other workstreams; keep edits limited to the declared Marketplace task.
10. Do not weaken tests, invent listings/users/reviews/discounts, bypass authentication, or reduce payment protections to obtain green CI.
11. After a push, inspect the exact commit and diff, wait for all relevant workflows, investigate actual failing logs, verify the APK artifact is tied to that SHA, and inspect the affected runtime journey where available.
12. A session must record its starting SHA, objective, changed files, checks, artifact, runtime evidence, blockers, final status, and the exact next starting point. Do not start the next batch while a required check is red or pending.
13. Do not call the Marketplace release-ready while required APK/user-journey or backend/provider gates remain unverified. State external blockers precisely; complete all independent work that can safely be completed.

## Development-process mistakes identified in the October 12 audit

These are process failures to prevent, not claims that each corresponding app feature is defective.

- Previously described cart persistence as upcoming despite existing account-scoped restore/save logic and instrumentation tests.
- Previously described the Smart Deals carousel as unfinished despite an existing auto-scrolling horizontal product row with real listing data, media fallback, price, and product-detail navigation.
- Treated source-verifier success as if it proved complete product behavior.
- Reopened checklist items without reconciling the current source, tests, runtime artifacts, and previous commits.
- Failed to keep a durable evidence ledger with a stable starting SHA and explicit end-of-session handoff.
- Proposed repeated small verification pushes instead of prioritizing complete, meaningful user outcomes.
- Did not consistently distinguish implementation, automated checks, actual APK runtime evidence, and live backend/payment verification.

The correction is not to add more checklist-only commits. It is to use this ledger to select and verify the next real gap, and to record evidence after meaningful delivery.

## Evidence snapshot at baseline SHA

### Confirmed source and test evidence

- `FynxMarketplacePanel.kt` contains account-scoped cart state derived from the current username, restores with `readFynxMarketplaceCart`, and writes changes with `writeFynxMarketplaceCart`.
- `FynxMarketplaceCartPersistenceTest.kt` tests cart item/quantity round-trip, account isolation, and the 20-distinct-listing bound. This is a storage-helper instrumentation test; by itself it does **not** prove the full UI journey of add item → close/reopen Marketplace or app → cart remains visible.
- Cart UI source contains duplicate-listing prevention, selection by listing ID, selected-item checkout, mixed-currency blocking, stock-bounded quantity changes, and an actionable empty-cart state.
- `FynxMarketplacePanel.kt` contains a `MarketplaceSmartDealsHero` using a `LazyRow`, timed auto-scroll, active/in-stock real listing filtering, deduplication, media/fallback display, real title/price, and product-detail navigation. Do not list carousel implementation as missing again without a specific failing requirement.
- Listing pagination filters IDs already in the current list and duplicates within the incoming page. The latest commit adds a static verifier assertion for that source logic; this is not, on its own, a real multi-page runtime test.
- `FynxMarketplaceCheckout.kt` contains single-product quote/order wiring and client-side expired/malformed quote rejection. Server remains authoritative for totals and order/payment state.
- Existing backend verification scripts cover multi-checkout lifecycle, currency contract, Price Watch, settlement/payout isolation, refund/dispute isolation, and end-to-end source contracts. A script passing is evidence for the assertions it actually executes, not proof of live provider settlement.
- The Marketplace reference design and master architecture prohibit fake product data and require a canonical listing identity across product details, seller, chat, order, inventory, and feed surfaces.

### Pagination behavior test added on October 12, 2026

- Source change: extracted the existing page-merge behavior into `appendDistinctMarketplacePage` and kept the Marketplace UI calling it. This is a testability improvement; the previous inline code already deduplicated IDs.
- Added `FynxMarketplacePaginationSupportTest` for overlap across pages, duplicate IDs within one incoming page, stable first occurrence and ordering, fully overlapping pages, and empty incoming pages.
- The pagination offset still advances by the server page size, not by the number of newly displayed listings; this preserves offset semantics when server pages overlap.
- Delivery commits: `5df1cdc3301d4a95007cf3ab3147bd37f836025c` (implementation) and `de2ed408aa89bc887eee20baf154a9b3a1dc0fad` (tests). These are separate because the available repository write operation commits one file at a time; they are one logical batch, not independent feature pushes.
- **CI status: AWAITING VERIFICATION.** Do not count this batch as complete until the exact latest branch head's Android Build/unit tests and Backend CI are checked. This test does not prove live backend connectivity or a full APK journey.

### Exact CI and artifact evidence

For `346e472e75a41e218130d4c12675d4c4dffc3dde`:

- [Android Build run 38202277453](https://github.com/greyhoundshop073-coder/FYNX/actions/runs/38202277453): completed **success** after a rerun. The first attempt had an Android instrumentation dependency-resolution failure for `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4` required by the Android device provider; the rerun completed successfully. Do not conceal the retry history, but do not call the final run red.
- [Backend CI run 38202277464](https://github.com/greyhoundshop073-coder/FYNX/actions/runs/38202277464): completed **success**.
- Android Build job reports success for build/test/lint, Android instrumentation, runtime screenshot/UI-hierarchy capture, exact-SHA APK verification, and artifact upload. The runtime visual artifact exists, but a future report must not say the Marketplace journey was visually inspected unless the artifact contents and relevant screens are actually inspected.
- Artifact names include `FYNX-debug-apk-346e472e75a41e218130d4c12675d4c4dffc3dde` and `FYNX-runtime-visual-check-346e472e75a41e218130d4c12675d4c4dffc3dde`. This ties the available artifacts to the audited SHA; it does not itself certify each Marketplace screen.
- The workflow's production backend reachability step was **skipped** on this pull-request run. Therefore this run does not prove live Render/API availability or authenticated end-to-end commerce.

## Marketplace release status at baseline

| Area | Evidence-based status | What is still needed before calling it complete |
|---|---|---|
| Cart persistence | IMPLEMENTED + helper-level instrumentation checked | Verify real UI/process recreation journey on exact APK if not already covered by inspected runtime evidence |
| Cart selection, quantity and mixed-currency guards | IMPLEMENTED + static source checks | Verify complete visible cart interactions on the APK and ensure server revalidates all order inputs |
| Pagination deduplication | IMPLEMENTED + source verifier | Exercise actual page-boundary duplicates, offset/has-more behavior, search/filter changes during load, and retry in a meaningful test |
| Browse/search/category/nearby | IMPLEMENTED in source; workflow verifiers pass | Inspect actual runtime screen/evidence and verify response/error/empty/nearby journeys; backend-backed results remain unverified without reachable service |
| Product details and seller contact/storefront | Wired in source and included in integration/security checks | Verify navigation and return behavior on exact APK; real seller data requires service |
| Create listing | Form/media validation and backend request wiring exist | Live publish/reload/ownership/media journey is backend-unverified |
| Protected single/multi-product checkout | Client and backend routes/contracts exist; backend CI passes | Authenticated server test, live payment provider/webhook reconciliation and exact end-to-end financial lifecycle remain unverified |
| Orders, disputes, refunds, payout/settlement | Backend logic and static lifecycle verifiers exist | Must prove against configured test environment/provider, including idempotency, isolation, and mutually exclusive financial outcomes |
| Price Watch | UI/backend source and verifier exist | Confirm real authenticated create/remove, server-side price-change notification and notification preferences on reachable backend |
| Smart Deals carousel | IMPLEMENTED in current source | Runtime appearance and interaction should be checked from exact artifact; do not rebuild it without a concrete defect |
| APK delivery | Exact-SHA debug APK artifact exists | Inspect actual Marketplace screens/flows in the captured runtime artifact and/or install and exercise on device; artifact existence alone is insufficient |

Overall baseline: **YELLOW — substantially implemented and CI-green, but not certified release-ready**. This is not a claim that the entire Marketplace is broken or that every listed area needs new code. It identifies the boundary between current evidence and proof still required.

## Research-backed product quality standard

Use evidence-based ecommerce UX as a design reference, adapted to FYNX and its Nigerian NGN/USD context; do not copy competitor branding or add features without need.

- Baymard product-list research: https://baymard.com/research/ecommerce-product-lists — browse/filter/sort and product-list presentation are one connected product-finding journey.
- Baymard search research: https://baymard.com/research/ecommerce-search — query handling, autocomplete, results guidance, filtering, and no-results recovery matter together.
- Baymard checkout UX research: https://baymard.com/research/checkout-usability — cart behavior, validation/data persistence, fulfillment, and error recovery are part of checkout, not optional polish.
- Baymard 2025 checkout review: https://baymard.com/research-articles/current-state-of-checkout-ux — mobile checkout should keep the flow clear, quantity changes easy, fulfillment options explicit, and errors actionable.

## Next session start and finish protocol

**Start:** read this file and the mandatory architecture/release docs; verify current branch and head SHA; inspect PR #115 and the exact-SHA workflows/artifacts. Do not begin by editing code.

**First deliverable:** inspect the actual runtime artifact/screens and the current automated test coverage; create a journey-by-journey evidence map from Marketplace entry through browse → product detail → seller/storefront/contact → add-to-cart → restore cart → selected multi-product checkout → quote/order/payment hand-off. Keep server/provider steps marked unverified when the environment is unavailable.

**Implementation rule:** choose the highest-impact confirmed gap that can be completed as one coherent batch. If no code defect is established, do not manufacture one; complete the runtime evidence/verification gap and record exactly what remains blocked.

**Finish:** report exact branch/SHA, diff, relevant workflow runs, APK artifact identity, actual screens/journeys inspected, regressions checked, external blockers, and status. Update this ledger in the same coherent delivery when the evidence changes. Do not move to the next workstream while required checks are red or pending.
