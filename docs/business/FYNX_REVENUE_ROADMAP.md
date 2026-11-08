# FYNX Revenue Roadmap

**Status:** Planning and tracking only — not authorization to implement future revenue features.
**Last reviewed:** 2026-11-08
**Canonical Android branch:** `integration/fynx-single-apk-20261013`

## Purpose and rules

This document preserves the revenue decisions for FYNX so future work can resume without reconstructing decisions from chat history.

- Do not start future monetization implementation merely because it appears in this roadmap.
- Before any future implementation, inspect this roadmap, the canonical branch, open/merged PRs, existing database migrations, payment flows and feature flags. Reuse existing work; do not duplicate it.
- Do not interrupt current Home, Marketplace, Wallet, AI or other agreed workstreams to build future revenue features.
- Do not rebuild the existing 5% Marketplace commission. Treat it as an existing feature and verify it when needed.
- Keep ordinary social use (posting, browsing, comments, following, chat and groups) free.
- Do not activate paid services until the product has real demand, clear pricing, working support/refund handling, and verified payment/accounting.
- Any thresholds below are planning gates, not promises or automatic triggers. Review actual active users, repeat purchases, seller outcomes, costs and support capacity before proceeding.

## 1. Existing work found during repository search

The following items are already represented in repository PR history. A PR reference is evidence of work proposed or implemented in that PR; it is **not by itself proof that the change is merged into the canonical branch or commercially ready**. Verify current branch contents and PR state before changing anything.

| Area | Existing repository evidence | Roadmap treatment |
|---|---|---|
| 5% Marketplace commission | PR #418 documents the default 500-basis-point seller-paid commission; PR #420 describes fee ledger and reporting; PR #88 describes `fynx_marketplace_fee` and NGN/USD financial contract; PR #461 improves seller earnings display | Existing. Do not rebuild. Verify canonical implementation and settlement behavior only. |
| Marketplace promotional carousel/banner | PR #387 and PR #439 describe automatic featured-product/promotional banner work | Existing promotional placement UI. Do not create a duplicate carousel. |
| Sponsored campaigns and metrics | PR #439 describes approved sponsored campaigns, SPONSORED labels, impression/click tracking, and separation from organic seller awards | Existing campaign foundation. Commercial readiness must be checked. |
| Campaign destinations | PR #452 describes typed campaign destinations to Marketplace, category, product or seller shop, with validation | Existing campaign-navigation work. Reuse it. |
| Advertising payment contract | PR #166 describes campaign payment verification and Business overview reliability | Existing advertising integration work; verify current branch/PR status before extending. |
| Prepaid CPC billing | PR #457 describes NGN cost-per-click bids, prepaid budgets, qualified-click billing, deduplication, daily/total spend limits and approval/payment gates; default starter bid described there is ₦5 per qualified click | Existing/in-progress PR evidence. Verify merge status, canonical code, tests, payment reconciliation and live readiness before any further ad-billing work. |
| Seller earnings/commission reporting | PR #420 and #461 describe seller earnings and FYNX commission reporting | Existing work. Do not duplicate. |

### Advertising readiness gaps explicitly identified in existing PR descriptions

PR #439 says daily budget pacing and per-impression/per-click spend settlement were not yet implemented in that PR, and requires an approved pricing model, spend ledger, pacing and reconciliation before commercial readiness. PR #452 says it does not itself claim paid ad billing/spend settlement. PR #457 describes a prepaid CPC implementation, but its merge/current-canonical status must be verified before treating that gap as closed.

**Important:** A promotional banner, sponsored label or impression/click counter is not sufficient to declare advertising monetization complete. Verify payment receipt, approved campaign state, prepaid balance, charge idempotency, spend limits, refunds/credits, reconciliation, advertiser reporting and abuse controls.

## 2. Future revenue features agreed for later

These are roadmap items, not current implementation tasks.

| Feature | Intended value | Earliest sensible stage | Activation gate |
|---|---|---|---|
| Product/shop boosts | Sellers pay for additional visibility | After launch and real buyer activity | Organic discovery works; sellers receive real views/orders; paid placement is clearly labelled and measured |
| FYNX Business Pro | Optional advanced seller tools and reporting | Once sellers repeatedly use My Shop | Interview active sellers; prove tools save time or improve business outcomes; price covers support and service cost |
| Wider business advertising | Paid campaigns from local and larger businesses | Once FYNX has meaningful reach | Verified audience/reach, advertiser controls, moderation, campaign billing and reporting are reliable |
| AI Premium | Optional higher limits/advanced AI creative tools | After AI is stable and cost-measured | Per-feature inference/storage cost known; free tier remains useful; subscription and usage limits tested |
| Creator memberships | Recurring support for creators/exclusive creator content | Once creators have repeat audiences | Creator demand, content moderation, recurring billing, cancellation/refund and creator payout rules ready |
| Virtual gifts | Optional creator support | Later community-growth stage | Fraud/abuse controls, gift ledger, creator settlement and applicable app-store billing rules verified |
| Delivery partnerships | Referral/service revenue from completed deliveries | When Marketplace orders justify partnerships | Reliable order volume, partner service levels, delivery tracking, dispute and refund responsibilities agreed |
| Affiliate partnerships | Referral commission on qualifying external purchases | Expansion stage | Transparent disclosures, trackable referrals, partner terms and attribution/refund rules |

No prices are approved by this roadmap. Any prices previously discussed (for example, proposed Business Pro or boost prices) are brainstorming examples only and must be tested with sellers before approval.

## 3. Rollout stages and decision gates

### Stage A — Before first public launch (current planning stage)

- Finish and certify the core app experience.
- Preserve the existing 5% Marketplace commission and existing financial safeguards.
- Verify seller fee disclosure, order-level fee calculation, seller net, ledger, refunds, escrow and settlement.
- Audit the existing sponsored-campaign implementation and PR #457; do not rebuild it.
- Keep optional paid monetization inactive unless the Owner explicitly approves a launch-ready feature.
- Record future requirements here and in linked GitHub issues.

### Stage B — Early launch and product validation

Focus on activation, retention, real buyer/seller transactions, trust and reliability. Keep ordinary social use free. Use the existing Marketplace commission when real completed orders occur. Do not assume sellers will pay for reach before FYNX can demonstrate useful reach.

### Stage C — Marketplace traction

Consider product boosts and Business Pro only after repeat buyers and active sellers demonstrate demand. Start with a small, controlled pilot. Measure seller return on spend, conversion, refunds, complaints and support effort before broad rollout.

### Stage D — Meaningful audience and operating capacity

Consider broader advertising only when reach and campaign reporting are credible. Consider AI Premium only when unit costs and service reliability are known.

### Stage E — Established creator and delivery ecosystems

Consider creator memberships, gifts, delivery partnerships and affiliate revenue only when there is a real creator audience, sufficient completed-order volume, trusted partners and compliant payout/payment handling.

## 4. Reusable architecture to consider when the relevant feature is approved

This section is a design checklist only. **Do not create migrations, endpoints, screens, services or feature flags solely because this section exists.** First inspect and reuse the existing Marketplace fee ledger, campaign tables, payment references, seller summaries and feature-gating patterns.

When a future feature is approved, assess whether existing systems already provide:

- A central, auditable revenue/transaction ledger with separate gross amount, platform revenue, provider fee, tax, refund, seller/creator payable and net settlement.
- Idempotent payment references, webhook verification, reversals and reconciliation.
- Subscription entitlements, renewal/cancellation state and grace-period handling (only if subscriptions are approved).
- Campaign budgets, prepaid balance, spend events, pacing, click/impression deduplication and advertiser statements (reuse existing advertising/CPC work).
- Feature entitlements and server-side feature gates.
- Admin approval, moderation, fraud controls, audit logs and customer support/refund workflows.
- NGN/USD currency correctness and explicit currency-specific rounding.
- Clear separation between seller/customer funds and FYNX-earned revenue.
- Privacy-safe analytics and advertising consent/preferences.

## 5. Mandatory checklist before starting any future revenue feature

- [ ] Re-read this roadmap and its linked issue(s).
- [ ] Inspect the canonical branch and current PR state; identify existing code, migrations, tests and feature flags.
- [ ] Search for duplicate or overlapping work before opening a new PR.
- [ ] Confirm the user-growth/demand gate has been met with real metrics.
- [ ] Confirm pricing, who pays, who receives funds, provider fees, refunds, taxes and net accounting.
- [ ] Confirm legal, privacy, app-store and payment-provider requirements.
- [ ] Write acceptance criteria, migration/rollback plan and test plan.
- [ ] Obtain explicit Owner approval to begin implementation.
- [ ] Implement in a focused branch; run relevant backend, Android, security, accounting and regression tests.
- [ ] Verify exact canonical integration and release evidence before calling it complete.

## 6. Explicit decisions and non-decisions

**Agreed**
- The existing 5% Marketplace commission is already built; do not schedule it as new work.
- Keep basic social participation free.
- Preserve the future revenue ideas in this roadmap so they are not forgotten.
- Prepare documentation and planning now; implement future monetization only when the relevant stage and approval are reached.
- Reuse the existing sponsored campaign/banner/CPC work rather than starting another ad system.

**Not yet approved**
- Any subscription or boost price.
- Any automatic user-count trigger that launches paid features.
- Any claim that advertising is commercially ready solely because sponsored banners or CPC code exist.
- Any creator payout, delivery fee or affiliate commission rate.
- Any new monetization screen, backend endpoint, database migration or payment flow.

## Repository references

- PR #88 — Marketplace NGN/USD financial contract and existing fee ledger.
- PR #166 — advertising payment contract and Business overview reliability.
- PR #387 — automatic Marketplace product advertising carousel.
- PR #418 — seller-paid 5% commission disclosure and Wallet UX.
- PR #420 — Wallet, seller earnings and commission ledger reporting.
- PR #439 — promotional banners, sponsored campaigns, metrics and commercial-readiness gaps.
- PR #452 — campaign destination routing and validation.
- PR #457 — prepaid CPC billing proposal/implementation; verify merge and canonical status.
- PR #461 — My Shop seller earnings breakdown.

This document is a roadmap and audit record, not proof that any PR is merged, any backend is live, or any future feature has been implemented.
