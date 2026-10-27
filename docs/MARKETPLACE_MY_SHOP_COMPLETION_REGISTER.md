# FYNX Marketplace & My Shop — Engineering Completion Register

**Last updated:** 2026-10-27  
**Owner/workstream:** Marketplace and My Shop only  
**Canonical APK integration target:** `integration/fynx-single-apk-20261013`  
**Baseline observed before this batch:** `c0977f263e8883efab3ff31fedf2edb613bfa978`  
**Status:** Active engineering register; full Marketplace certification is not yet complete.

This is the current evidence log for the Marketplace/My Shop completion work. The historical push map remains in [MARKETPLACE_PUSH_REGISTER_1_TO_8.md](MARKETPLACE_PUSH_REGISTER_1_TO_8.md), which in practice records pushes 1–20 and prior release-gate notes. This register does not replace those documents or erase their earlier claims; it records the current state and requires fresh proof before declaring completion.

## Scope and ownership boundaries

Included: Marketplace discovery, listings and media, product detail, public seller storefront, private My Shop, inventory, cart/checkout, orders and receipts, seller funds/payout presentation, delivery/driver handover, buyer protection, navigation and related validation/loading/error/retry behaviour.

Excluded from direct modification: Avatar/Profile, Chat/Groups, Status/Story and general network/backend remediation assigned to other workstreams. API contracts may be inspected to isolate dependencies, but shared backend infrastructure, deployments, credentials, database settings and billing are not changed by this work.

All Android Marketplace changes must land in the canonical integration branch above. Feature branches and their CI results are not proof of canonical inclusion or final-APK certification.

## Current branch and active work

- Canonical branch existed at the baseline commit above when this batch began. Recheck the live head before merging; concurrent work may advance it.
- Working branch: `fix/marketplace-sell-form-category-duplication-20261026`.
- UI fix commit: `e2b41282679d00122ae02201778e8af9b81e2809` — remove duplicate category selector from the product-publishing form.
- Regression-test commit: `a892a453ceec56029339a65b317c7d66ff67f7da` — add a check to `scripts/verify_marketplace_seller_flow.py` ensuring the active sell dialog contains the category-choice list once.
- CI evidence from the initial PR run: Android Build failed at `scripts/verify_marketplace_seller_flow.py` on the stale check that demanded a second category list after the location guidance. The new no-duplicate assertion passed. The existing verifier compares the primary Category selector against discovery correctly, but also incorrectly required the duplicate selector to remain; this assertion has been removed while keeping the discovery-vs-primary-selector category contract. Corrective verifier commit: pending in the new branch head; rerun CI before marking passed.

## Verified source paths (static code inspection, not live end-to-end proof)

| Journey / component | Evidence in current source | What the evidence proves | What remains to prove |
|---|---|---|---|
| Product creation and media | `FynxMarketplacePanel.kt` `MarketplaceSellDialog` calls `FynxRemoteSocialClient.createMarketplaceListing`; that method uploads validated media through `FynxProductionMessaging.uploadMedia`, then posts `/api/marketplace/listings`. | The code path and media/listing request wiring exist. Listing creation treats the create response as authoritative and owner-inventory read-back as best effort. | Actual authenticated upload, database persistence, returned listing visibility and image retrieval with a working backend. |
| Marketplace discovery | `FynxMarketplacePanel.reload()` requests `/api/marketplace/discovery`; on failure it falls back to `/api/marketplace/listings` and communicates the fallback. | The active UI has a discovery request, a fallback path, timeout messaging, and request-generation checks. | Live route availability, pagination under real data, and complete seller-created-product visibility. |
| Product card/detail media | `FynxRemoteSocialClient.MarketplaceListing`, `FynxMarketplaceClient.Listing` and the corresponding media renderers carry media IDs and MIME types. Existing seller-flow checks assert those links. | Media-ID/type parsing and UI rendering hooks exist in source. | Device verification of image/video upload, authorized retrieval, and correct rendering for actual stored media. |
| Private My Shop inventory | `FynxMarketplaceSellerCenterPanel.refresh()` requests `/api/marketplace/my-listings`; it separately requests shop profile, payout-account and money-summary data. | The dashboard is split into inventory, shop profile, orders, delivery/drivers and earnings/payout destinations; it has load/error states. | Authenticated endpoint compatibility and account-scoped persistence on the deployed backend. |
| Public seller storefront | `FynxMarketplaceSellerStoreDialog` loads seller-scoped listings and requests `/api/marketplace/shops/:username`. It shows independent loading, failure, retry and empty states. | The client avoids silently substituting a partial global catalogue as a seller's complete catalogue. | Live public-shop route availability and accurate shop identity for real sellers. |
| Orders, fulfillment and buyer protection | `FynxRemoteSocialClient`, `FynxMarketplaceSellerOrders.kt`, `FynxMarketplaceOrderLifecycle.kt` and existing lifecycle/fulfillment verifiers contain buyer/seller order, driver, evidence and completion paths. | Source and contract checks exist; this is not an assertion that every flow works in production. | Review active versus orphaned PR code, CI, live API transitions, receipt correctness, and runtime flow verification. |

## Known dependencies and branch hygiene

1. **Shop-profile and saved-product backend compatibility:** PR [#363](https://github.com/greyhoundshop073-coder/FYNX/pull/363) is open against `main`. Its description states that authenticated/persistent `GET/PATCH /api/marketplace/my-shop`, public shop lookup, saved-products routes and seller-owned listing reactivation were missing from the deployed backend. The PR also records Neon quota exhaustion as a blocker for live DB end-to-end verification. This is a separate backend workstream; do not claim those endpoints are deployed or change that backend work here.
2. **Driver custody/evidence:** PRs [#189](https://github.com/greyhoundshop073-coder/FYNX/pull/189) and [#191](https://github.com/greyhoundshop073-coder/FYNX/pull/191) remain open/draft on non-canonical base branches at audit time. The canonical branch already contains related seller-order and driver code, so compare exact file/commit ancestry before deciding what remains; do not merge either PR blindly or mark all of its scope as absent.
3. **Receipt/evidence roadmap:** PR [#270](https://github.com/greyhoundshop073-coder/FYNX/pull/270) remains open/draft as a roadmap/checklist. Existing receipt and accounting implementation/tests must be evaluated independently of that document’s status.
4. **Canonical integration drift:** The canonical branch must be re-read before every merge. A passing feature-branch workflow does not prove the canonical branch contains that commit.
5. **Build and runtime limits:** No local Git clone/build was available during this audit environment because direct GitHub network resolution was unavailable. GitHub-hosted CI is the designated verification path for this batch. No APK is declared built or verified by this register update.

## Additional source-level dependency found during this pass

- **Defensive delivery-evidence cleanup gap on local-driver reassignment (not proven reachable through current routes):** in `backend/marketplaceCompletion.js`, the assignment route deletes `marketplace_delivery_evidence` only when `String(prior.driver_id || '') !== String(resolvedDriverId || '')`. Two `SELLER_LOCAL` assignments both have null driver IDs, even when their contact/vehicle/pickup details change, so that comparison alone would not clear evidence. However, inspection of all current producers of `marketplace_delivery_evidence` shows that the existing records are written as part of local arrival after handover, handover itself, or driver delivery; the reassignment route rejects assignments after `handover_at` or `delivered_at` is set. **Therefore this is a defensive/legacy-data cleanup gap, not a confirmed active user-facing defect in the current route flow.** No backend change is made from this chat.
  - **Ownership:** Marketplace backend route; leave implementation to the dedicated backend/network workstream.
  - **Recommended follow-up there:** add a regression test for local-to-local reassignment with no custody, and ensure any legacy evidence associated with a replaced assignment is either safely versioned or cleaned up without weakening the custody lock.
  - **Current status:** documented for the backend owner; not patched or claimed as a verified runtime failure.
- **Source-only limitations:** the active client/backend source contains substantial protected checkout, seller inventory, receipt signing, dispute, and delivery functionality with structural verifier coverage. This is not a substitute for running the real API against the database and actual Android runtime.

## Batch ledger

| Batch | Change / evidence | CI | Canonical branch | Latest APK |
|---|---|---|---|---|
| P0 — baseline inspection | Confirmed canonical baseline, open Marketplace-related PRs, active sell-form implementation, current verifier and Android workflow trigger configuration. | Inspected workflow configuration; this was not a test run. | Baseline observed at `c0977f263e8883efab3ff31fedf2edb613bfa978`. | Not verified in this batch. |
| P1 — duplicate category selector | Removed the duplicate category-chip row from `MarketplaceSellDialog`; retained the one primary selector; corrected the stale verifier clause and added an exactly-one-selector regression assertion. UI commit: `e2b41282679d00122ae02201778e8af9b81e2809`; initial verifier commit: `a892a453ceec56029339a65b317c7d66ff67f7da`; corrective verifier commit: `c4a61f5a360b8ade34ec29ecd37345b77fabc31b`. PR [#364](https://github.com/greyhoundshop073-coder/FYNX/pull/364) merged as canonical commit `439177956dc655c86a65edd4ec65f36f693f3c72`. | Android Build passed on source-equivalent feature head `5dc7e23a7f1c14156273951fedf2edb613bfa978` (run [#40372237924](https://github.com/greyhoundshop073-coder/FYNX/actions/runs/40372237924)); Backend CI and Chat/Group parity passed on that head (runs #40372245748 and #40372245599). Canonical push workflow runs #40373949131 (Android), #40373949613 (Backend) and #40373949615 (Layout) are being checked separately. | Merged into canonical APK branch. | Feature-branch artifact exists but is not the final APK; canonical artifact/runtime proof pending. |
| P2 — Saved Products state hydration | Fix in branch `fix/marketplace-saved-product-state-20261026`, commit `67d9ff364a06537561a2fbbfcfb1974c0cdeeb3d`: account-scoped state, authoritative saved-products hydration, read-before-toggle when state is unknown, and generation guards against stale responses. First CI exposed two obsolete source-contract assertions requiring direct SAVE/UNSAVE calls inside the UI file; the implementation now routes these through a safer remote toggle helper. Verifiers are being updated to assert the actual helper contract and preserve the existing API method requirements. | First P2 Android verifier failure: obsolete seller-flow assertion. Backend CI failed only its matching shop-profile source assertion. New contract assertions require the UI’s safe helper, its read-before-mutation path, and SAVE/UNSAVE APIs to remain present. Awaiting rerun. | Not merged. | Not verified. |

## Required acceptance gates (do not mark complete without evidence)

- [x] P1 no-duplicate-selector regression assertion passes in CI on source-equivalent commit 9410147ed0fcdda037caf74aa11f40ae16be26c4 (Android Build run #40370797130); the stale category expectation was removed, not weakened.
- [ ] `scripts/verify_marketplace_seller_flow.py` passes in CI after P2 verifier updates; first P2 run failed on an obsolete direct-call string check, not a reported Kotlin compilation or runtime test.
- [ ] Marketplace integration/security and financial contract gates pass. The current P2 backend verifier failure was the obsolete direct-call assertion; rerun it after the assertion is updated.
- [ ] Android build/test/lint workflow passes for the reviewed change; inspect skipped steps, not only the overall conclusion. The P1 canonical run failed at Gradle distribution download (`services.gradle.org` read timeout), after Marketplace’s 107 seller-flow checks passed; rerun to determine whether this was transient infrastructure.
- [x] Confirm P1 merge commit 439177956dc655c86a65edd4ec65f36f693f3c72 was the canonical integration head when P2 work began; recheck live head before merging again.
- [ ] Build/locate APK from the canonical integration commit and confirm artifact metadata points to that exact source SHA.
- [ ] Runtime-smoke Marketplace discovery, new listing and image visibility, My Shop inventory/profile, storefront, cart/checkout, orders/receipts, delivery and buyer-protection paths.
- [ ] After the independent backend/database issue is restored, repeat a limited live integration pass for affected endpoints and transaction state transitions.
- [ ] Update this register with exact PR/merge commit, workflow run URLs/results, APK artifact identity, and remaining verified blockers.

**Completion rule:** a UI component, source-level assertion, passing unit/static test, green feature-branch workflow, deployed backend route, and final APK certification are different evidence states. Record each separately.


## Current re-audit addendum — 2026-10-26 (supersedes stale pending statuses above)

**Audited canonical head:** `b62b05918bfae55e1d16fa9f7db16720bb8565ce`  
**Audit note:** the historical P2 status above predates later canonical changes and must not be read as the current state.

- **Saved Products Android state hydration:** current canonical source in `FynxMarketplacePanel.kt` hydrates the signed-in account's saved products, resolves the authoritative state before a toggle when local state is unknown, blocks repeated taps for the same listing, and guards against stale initial reads overwriting a toggle. The matching checks in `scripts/verify_marketplace_seller_flow.py` are present on the audited head. This is source/contract evidence, not a live Saved Products API test.
- **Latest canonical workflow — Android Build:** [run #40376673475](https://github.com/greyhoundshop073-coder/FYNX/actions/runs/40376673475), source SHA `b62b05918bfae55e1d16fa9f7db16720bb8565ce`, conclusion **failure**. Fast build/test/lint and full build/test/lint steps passed. The 43-test Android instrumentation run had one failure: `FynxNetworkRecoveryContractTest.debugAndroidRuntimeCanReachLiveRailwayBackend` received HTTP 404 with `Application not found` from the configured Railway URL. The emulator also briefly reported `device offline` during startup but proceeded to run the tests. The final recorded test failure is the backend reachability assertion, not a Marketplace assertion. Backend/network ownership is separate; this chat must not change backend configuration to mask it.
- **Other workflows at the same canonical SHA:** [Backend CI #40376673468](https://github.com/greyhoundshop073-coder/FYNX/actions/runs/40376673468) passed; [Chat Group Parity + Layout Safety #40376673774](https://github.com/greyhoundshop073-coder/FYNX/actions/runs/40376673774) passed. These are workflow results only and do not certify Marketplace live runtime.
- **APK artifact:** workflow run #40376673475 uploaded artifact `FYNX-debug-apk-b62b05918bfae55e1d16fa9f7db16720bb8565ce` (47,982,877 bytes; artifact archive digest `sha256:719f77cf0bd1713df36ca6b557088ec71cb90663cb57266c38d04451aab06f40`). This identifies an APK artifact built from the exact canonical SHA, but the workflow is red because instrumentation failed. The archive digest is not the APK file's own SHA-256. **Do not call this a fully certified final APK** until the relevant runtime gate is green and the APK file hash is independently recorded.
- **Open backend dependency:** PR [#363](https://github.com/greyhoundshop073-coder/FYNX/pull/363) remains open against `main`, outside this Android Marketplace workstream. Its stated live DB verification blocker is Neon quota exhaustion. Do not claim its routes are deployed.
- **Canonical source status:** saved-product state hydration and the sell-form single-category selector fix are present in the current canonical history. No new Marketplace code was changed by this evidence-only addendum. The next engineering batch must continue from this audited head and investigate any remaining concrete Android-side defects; it must not repeat P1/P2 as if those fixes were absent.

### Re-audit acceptance status

- [x] Canonical integration branch and exact current SHA confirmed.
- [x] Saved Products state-hydration implementation and matching source-contract checks inspected.
- [x] Latest Android workflow steps and failing instrumentation assertion inspected.
- [x] Exact-commit APK artifact existence and artifact archive digest recorded.
- [ ] Latest Android workflow green after the backend/network owner restores the configured endpoint.
- [ ] APK binary SHA-256 and install/runtime verification recorded.
- [ ] Live authenticated Marketplace flows verified against restored backend/database.


## Re-audit addendum — 2026-10-27 (Marketplace/My Shop runtime evidence)

**Canonical starting head:** `1ddb70d7e2a82c92aa19f7578afd5caabc14b9c8`  
**Feature branch:** `fix/marketplace-runtime-visual-capture-20261027`  
**Purpose:** recover genuine Marketplace/My Shop visual evidence when an unrelated or infrastructure-dependent instrumentation assertion fails, without weakening the failed test or changing backend/network ownership.

### Exact current Android failure evidence

- Canonical Android workflow run [#40454293485](https://github.com/greyhoundshop073-coder/FYNX/actions/runs/40454293485), run 8379, checked out `1ddb70d7e2a82c92aa19f7578afd5caabc14b9c8`.
- Fast build/test/lint and full build/test/lint completed successfully. Marketplace seller-flow, checkout fee, protection, integration/security and Smart Marketplace verifiers all passed.
- Instrumentation ran 43 tests; exactly one failed: `FynxNetworkRecoveryContractTest.debugAndroidRuntimeCanReachLiveRailwayBackend`. The observed response was HTTP 404 `Application not found` from Railway request `WZooeglnSv2lKzL19fVATg`. This is a backend endpoint/reachability failure owned by the separate backend/network workstream; it is not a Marketplace UI test failure and is not patched here.
- Backend CI [#40454293520](https://github.com/greyhoundshop073-coder/FYNX/actions/runs/40454293520) passed at the same SHA. Chat Group Parity + Layout Safety [#40454293319](https://github.com/greyhoundshop073-coder/FYNX/actions/runs/40454293319) also passed at the same SHA; this is reported for provenance only, not as Marketplace certification.
- The workflow did upload APK artifact [`FYNX-debug-apk-1ddb70d7e2a82c92aa19f7578afd5caabc14b9c8`](https://github.com/greyhoundshop073-coder/FYNX/actions/runs/40454293485) and visual artifact [`FYNX-runtime-visual-check-1ddb70d7e2a82c92aa19f7578afd5caabc14b9c8`](https://github.com/greyhoundshop073-coder/FYNX/actions/runs/40454293485). The visual ZIP was inspected: it contained only instrumentation logcat, activity and package dumps—no PNG screenshots or UI hierarchy XML. The emulator activity dump ended on the Android launcher.
- Root cause in the evidence pipeline: `scripts/run_android_runtime_certification.sh` only invoked `scripts/verify_authenticated_runtime_navigation.py` when `connectedDebugAndroidTest` returned zero. A red test therefore skipped all screen navigation/capture even though the workflow’s artifact step still ran. The APK artifact exists, but is not runtime-certified while instrumentation is red.

### P3 — Marketplace/My Shop runtime evidence recovery

- Proposed repair: introduce a read-only, Marketplace/My Shop-only capture mode that records genuine adb screenshots and UIAutomator hierarchies for Marketplace discovery; Product Details only when an actual priced listing is visible; the publishing form without submission; private My Shop Overview, Shop profile, Listings, Orders, Delivery & drivers, and Earnings & payouts where the screen exposes their real controls.
- On instrumentation failure, the runner should attempt this focused capture but preserve the original instrumentation exit status. A capture attempt must not change a red test to green, create sample products/orders, submit payment, or claim backend success.
- Status: merged via PR #373 as canonical commit `1a52b42ab44474efbe1a1ad0bc08048340c9657f`; all PR-head Android build/test/lint, the visual-capture regression verifier, Backend CI, and the repository layout/parity check were green on `a0cebc5f2a234f54dc2d3896200574e854ca7547`. Post-merge canonical workflow run `40460202455` was still reported in progress with screenshot/build steps pending at this audit; screenshot coverage remains unverified until that run produces an artifact with real PNG/XML files. Live listing/detail data and authenticated backend operations remain independently unverified while the Railway 404 / backend deployment issue is unresolved.


## P4 — My Shop refresh and order-read reliability

**Starting canonical SHA:** `1a52b42ab44474efbe1a1ad0bc08048340c9657f`  
**Feature branch:** `fix/marketplace-myshop-refresh-reliability-20261027`  
**Scope:** Marketplace/My Shop Android-side response handling only. No backend routes, database schema, auth, shared network configuration, Avatar/Profile, Chat/Groups, or Status/Story files are changed.

### Verified source defects
- My Shop refresh had no response-generation guard. If refresh A returned after refresh B, an older seller inventory, shop profile, or payout-account response could overwrite B’s newer state.
- Shop-profile and payout-account GET parsing occurred inside `onSuccess` callbacks instead of `mapCatching`. Malformed JSON could throw out of the coroutine before the loading flags were reset.
- Inventory failures retained the previous listing rows while the overview still displayed their count.
- A failed payout-account refresh left the previous account object rendered—including a previously verified state—next to the error message.
- Shop-profile save had the same unguarded response-parse pattern and could leave `shopSaving` set when a malformed successful response threw.
- Seller-order parsing also occurred in `onSuccess` and had no response-generation guard. The dialog’s read-only “Refresh” additionally called the parent callback, triggering a redundant second My Shop refresh and several unnecessary GET requests.

### P4 repair and regression tests
- Add generation guards for My Shop refreshes, earnings, and seller-order reads; trigger the earnings refresh immediately so old figures are hidden while profile/inventory/payout requests are still loading.
- Parse seller shop/payout/order response JSON inside `Result.mapCatching`, so malformed server responses follow the explicit failure path.
- On failed inventory refresh, clear previous rows and label the count unavailable. On failed payout-account refresh, clear old account/verification data.
- Disable refresh controls while their respective request is loading; remove the read-only seller-order Refresh callback that redundantly reloads the whole parent screen.
- Extend the existing Marketplace seller-flow regression verifier with assertions for each behaviour above.

**Status:** merged through [PR #374](https://github.com/greyhoundshop073-coder/FYNX/pull/374) as canonical commit `1f2a06b65545ef327dad78ebde9bbb622daa69a5`. The exact PR-head Android Build workflow [#40461195663](https://github.com/greyhoundshop073-coder/FYNX/actions/runs/40461195663) passed the fast build/test/lint and Marketplace source-contract gates. Full build/runtime instrumentation were skipped on the pull-request event; no live-backend or device-runtime claim is made from that run.


## P5 — Fail-closed seller-order refresh and financial field validation

**Starting canonical SHA:** `1f2a06b65545ef327dad78eb9bbb622daa69a5`
**Feature branch:** `fix/marketplace-seller-orders-fail-closed-20261027`
**Scope:** Marketplace seller-order Android UI and the existing Marketplace source-contract verifier only. No backend, database, authentication, shared network configuration, Avatar/Profile, Chat/Groups, or Status/Story files are changed.

### Verified root causes
- `FynxMarketplaceSellerOrders.load()` previously retained already-rendered orders when a later refresh failed. The order details view can expose delivery-driver assignment, handover and other fulfillment actions, so old status must not remain actionable after a failed read.
- The selected-order dialog was not invalidated when a refresh started or failed. This could leave an old order detail surface open after the order list was no longer trustworthy.
- The parser accepted missing or malformed `totalAmount` as `0.0` and accepted missing `status` as a blank value, which could render a false zero total or misleading fallback state.

### P5 repair
- Clear the previous order list and selected-order dialog as soon as a new read begins; clear them again on failure. Loading/error/empty states now replace stale actionable order data.
- Reject non-finite or negative order totals, missing order status, and missing currency as an invalid response, using the existing `mapCatching` failure path.
- Normalize the currency to uppercase and give a blank fulfillment method the existing safe DELIVERY default.
- Add regression assertions that guard stale-order clearing and malformed financial/status fields without removing existing Marketplace checks.

**Verification status:** the source/test changes are staged on the feature branch. Exact-head CI must pass before merge. Live orders, delivery transitions and financial data remain unverified until a backend is available; this batch does not depend on or modify that backend.
