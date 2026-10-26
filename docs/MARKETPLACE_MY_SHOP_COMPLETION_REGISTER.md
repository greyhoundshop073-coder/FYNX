# FYNX Marketplace & My Shop — Engineering Completion Register

**Last updated:** 2026-10-26  
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
