# FYNX Marketplace Completion — Workstream Handoff

**Canonical branch:** `integration/fynx-single-apk-20261013`  
**Working branch:** `feat/marketplace-completion-hardening-20261125`  
**Base commit:** `088abea8cef2141c4c56275543224f8ea44406ff`  
**Scope:** Marketplace buyer/seller, My Shop, Marketplace Wallet settlement and backend/runtime verification.  
**Explicit exclusion:** Marketplace Proof Video Calls / camera Live Proof. That work remains with the separate owner of PR #599; this workstream must not modify its call engine, Live Proof UI, call authorization, shared Marketplace panel or call CI contracts.

## What this grouped change is intended to close

- Keep seller contact phone private: expose it only through the authenticated seller's own-shop endpoints, never the public storefront API.
- Preserve existing shop fields and branding when a seller submits a partial edit.
- Complete existing seller-center, storefront, order-lifecycle, evidence and refund-request UI integration without replacing existing Marketplace screens.
- Make saved-product routes safe for PostgreSQL BIGINT IDs, blocked sellers and unavailable/reserved stock.
- Give Railway and Neon one owner for Buyer Wallet route registration: the shared scalability layer. Generated runtime bootstrap must not register those routes a second time.
- Count seller reputation and public product/seller reviews only while the linked purchase remains a genuinely completed order. Refunded, incomplete, self-purchased or disputed orders must not inflate sales or ratings.
- Preserve the existing idempotent escrow/ledger, dispute hold, provider refund and payout reconciliation safeguards. Do not activate real-money payouts from code alone.

## Important boundaries

FYNX does not need to copy every OPay feature or adopt OPay's exact review window. The existing FYNX inspection and dispute policy should remain unless product/legal review changes it deliberately.

The internal escrow and ledger records are not proof of regulated custody or provider-held funds. Live transactions require confirmed provider capabilities, custody/compliance approval, valid production credentials and end-to-end reconciliation. Marketplace Wallet checkout currently has NGN-only restrictions; do not claim USD Wallet payments are enabled.

## Acceptance gates before calling this work complete

1. Backend CI green on the exact PR head, including Neon runtime build, route registration, PostgreSQL shop/review tests, saved-product authorization, protection/dispute/refund lifecycle and ledger invariants.
2. Android build green on the same PR head; no camera/Live Proof scope mixed into this change.
3. Runtime proof from the canonical integrated APK: seller shop edit and public storefront privacy, buyer checkout/order status, seller fulfillment, evidence/dispute submission, Admin resolution and buyer/seller review.
4. Provider-dependent tests clearly separated from source/CI tests; no real payout enabled until external approval.
5. Only after merge, build and identify the single canonical APK from `integration/fynx-single-apk-20261013`.

## Existing work reused

This workstream consolidates compatible work already present in the open My Shop and saved-products branches rather than rebuilding those features. Their original PRs are inputs to the grouped change, not evidence that the changes are already integrated or certified.


## Latest branch verification

- PR: [#605](https://github.com/greyhoundshop073-coder/FYNX/pull/605)
- Exact verified head: `7982c54bc9b8f4bbe75a2a982c1a05829f1c3846`
- Backend CI: [run 9331](https://github.com/greyhoundshop073-coder/FYNX/actions/runs/45637100049) — all backend validation steps passed, including My Shop privacy/partial edits, Saved Products authorization, PostgreSQL reviews, financial lifecycle and Neon runtime route checks.
- Android Build: [run 16213](https://github.com/greyhoundshop073-coder/FYNX/actions/runs/45637100183) — workflow passed; fast build/test/lint checks passed. Full Android emulator instrumentation, screenshots and APK artifact steps were skipped.
- Chat Group Parity + Layout Safety: [run 5328](https://github.com/greyhoundshop073-coder/FYNX/actions/runs/45637099965) — passed.
- Chat Final Closure: [run 1593](https://github.com/greyhoundshop073-coder/FYNX/actions/runs/45637100123) — passed.
- The optional live production backend probe was skipped. Green CI does not certify real Paystack custody, live payouts, production refunds, or authenticated multi-account buyer/seller runtime.
- Marketplace Proof Video Calls remains owned by [PR #599](https://github.com/greyhoundshop073-coder/FYNX/pull/599); no Live Proof/call-engine files are included in this PR.
