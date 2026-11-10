# Marketplace Completion & Backend-Recovery Checklist

Last reviewed: 2026-11-10
Canonical Android branch at review: `integration/fynx-single-apk-20261013`
Review baseline commit: `43839989b1abd8037db0c2af202208ac46944819`

## Purpose and operating rule

This file is the durable handoff for Marketplace, My Shop, Wallet, order protection, fulfilment and support work. Do not restart broad investigations or rebuild systems already present. Read this checklist and the referenced implementation/verifier files first in every follow-up chat.

The backend is currently unavailable/expired. Do not spend money, recreate infrastructure, alter credentials, or fabricate users, orders, products, payments, balances, delivery events or support cases. Complete source-only, Android UI, offline, unit, static, and mock-based work where safe. Mark anything needing a live database, provider, webhook, scheduled worker or two-account session as **BLOCKED ON LIVE BACKEND** and resume it immediately when service is restored.

A source verifier or architecture checkbox is not proof of a complete real user journey. Green requires client entry point + complete interaction states + backend contract + authorization + persistent state + retry/error handling + relevant Android and backend tests + real journey evidence.

## Existing foundations — preserve and verify, do not rebuild

The canonical source already contains dedicated implementations/verifiers for:
- Protected checkout quote and order creation, stable retry identity and server-side payment verification:
  `backend/marketplaceCheckout.js`, `backend/marketplaceCheckoutOrder.js`,
  `scripts/verify_marketplace_checkout_fee_breakdown.py`.
- Disputes, escrow state and mutually exclusive refund/payout resolution:
  `backend/marketplaceProtection.js`, `backend/marketplaceProtectionResolution.js`,
  `backend/marketplaceSettlement.js`, `scripts/verify_r6a_marketplace_protection.py`.
- Shipping, pickup handover, inspection, driver assignment/acceptance/evidence and completion:
  `backend/marketplaceCompletion.js`, `backend/marketplaceInspectionExpiry.js`,
  `backend/marketplaceShippingTimeline.js`, `backend/verify-marketplace-fulfillment.mjs`.
- Discovery pagination, stock availability, block-aware seller/storefront navigation, checkout and payment entry points:
  `backend/discoveryRoutes.js`, `app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt`,
  `scripts/verify_marketplace_integration_security.py`.
- Buyer Wallet, wallet history and marketplace payment connection:
  `app/src/main/java/com/fynx/app/ui/FynxBuyerWalletClient.kt`,
  `app/src/main/java/com/fynx/app/ui/FynxBuyerWalletPanel.kt`,
  `backend/buyerWallet.js`, `scripts/verify_buyer_wallet_integration.py`.
- Seller reputation and public shop profile foundations:
  `backend/marketplaceReputation.js`, `backend/marketplaceShopProfiles.js`.
- Support Centre implementation is being handled in open PRs #438 and #440. Inspect both against the latest canonical base before treating support as absent or complete.

## Work to complete without a live backend

- [ ] Reconcile Marketplace/My Shop/Wallet UI and tests from open PR #502 with the current canonical branch. PR #502 is diverged from canonical (34 commits ahead, 58 behind at review) and MUST NOT be merged as-is. Port only independently reviewed, non-conflicting changes; keep the PR open until the reconciled changes pass CI.
- [ ] Run every Marketplace verifier and backend unit/mock test locally or in CI without requiring live secrets. Fix genuine source/test failures. Do not weaken assertions to get green.
- [ ] Add/retain deterministic tests for all currency arithmetic (NGN and USD), rounding, fee allocation, seller net, delivery fees, discounts, refunds and ledger reconciliation. Client display must use server quote values; client must not authoritatively calculate settlement.
- [ ] Add mock/database-isolated tests for race conditions: duplicate checkout retries; concurrent stock reservation; cancellation vs payment confirmation; duplicate webhook; dispute vs auto-completion; refund vs seller payout; driver double acceptance; duplicate handover/delivery evidence; inventory release exactly once.
- [ ] Verify UI states for empty/loading/error/offline/retry/permission-denied/expired quote/failed payment/pending verification/disputed/refunded/released/driver not assigned. Ensure no button reports success before authoritative response.
- [ ] Verify storefront contact/privacy: block in either direction, hidden/removed seller, invalid username, empty shop, stale listing, contact disabled, and public fields do not expose private account or location data.
- [ ] Verify stock and media UX offline: no purchase action for sold-out/fully reserved/inactive listings; stable pagination keys; bounded image sizes/counts; retry and placeholder behavior; no unbounded image prefetch.
- [ ] Verify support Centre Android navigation and order-linked case creation against the UI contract from PRs #438/#440; keep case history/evidence and staff-only resolution controls distinct from ordinary support messages.
- [ ] Verify APK integration on the canonical app: Marketplace, My Shop, public seller page, cart/checkout, Wallet, buyer order timeline, seller order controls, delivery and support entry points. Capture actual emulator screenshots from the built APK, not mockups.

## Blocked until backend/database/provider is restored

These are explicit restart items. Do not mark complete from static source checks alone.

- [ ] **Database migrations and persistence:** apply Marketplace, Wallet, reputation, support, dispute, driver and ledger schema on a disposable test database first; check migration idempotency and rollback/upgrade behavior.
- [ ] **Real buyer/seller transaction:** use two authorized test accounts and a real test listing; create order, quote, payment initialization, provider confirmation/webhook, seller fulfilment, buyer inspection and completion.
- [ ] **Paystack payment/refund:** verify test-mode NGN and USD behavior where provider/account configuration supports it; validate webhook signatures, amount/currency/reference matching, idempotency, late/duplicate events, refund confirmation and reconciliation. Never use live funds during testing.
- [ ] **Wallet accounting:** reconcile available/reserved/pending/withdrawable balances against ledger rows through top-up, protected checkout, refund, release and withdrawal; verify no double-credit/debit and no cross-currency mixing.
- [ ] **Seller payout:** verify payout account ownership/verification, amount source, transfer request, provider reference, webhook confirmation, reversal/failure and refund/payout mutual exclusion.
- [ ] **Disputes and resolution:** create a real test dispute, prove order/escrow freeze, preserve pre-dispute state, restrict evidence to participants, verify authorized admin identity, then exercise buyer refund and seller-release outcomes separately.
- [ ] **Inventory concurrency:** run parallel checkouts and concurrent cancel/refund/completion operations against a real database; prove no oversell, double release or lost stock.
- [ ] **Delivery journey:** verify approved driver onboarding/approval, seller assignment, driver acceptance, seller handover evidence, driver delivery evidence, buyer receipt, pickup handover and 48-hour inspection/expiry behavior using separate buyer/seller/driver accounts.
- [ ] **Support operations:** verify case submission, attachments, account isolation, buyer/seller replies, staff/admin assignment, status transitions, audit history and order/dispute linkage end-to-end. Confirm support agents cannot execute owner/admin-only financial resolution.
- [ ] **Production-like reliability:** test pagination and query plans at realistic catalogue/order sizes; image upload/download against configured object storage; slow/interrupted mobile network; worker retries; database timeouts; scheduled inspection expiry; backups and recovery.
- [ ] **Live two-account call from product listing:** initiate product-linked video call and verify listing identity/title survives invite, ringing, accept, active call, reconnect and hang-up. Existing source chain is not proof of a real two-account call.
- [ ] **Release certification:** after backend tests are green, run consolidated Android build, unit tests, lint, emulator instrumentation, authenticated user journey and screenshot capture on the exact canonical commit; publish and verify the official full APK artifact and checksum.

## Exact restart sequence when backend returns

1. Confirm service health, database readiness and the active branch/environment without changing infrastructure or secrets.
2. Run schema/migration checks against a disposable test database.
3. Run backend unit, integration, security and Marketplace fulfillment verifiers; fix red gates before continuing.
4. Run payment/webhook/ledger/refund/payout tests in provider test mode only.
5. Run separate buyer/seller/driver/admin test-account journeys and capture request IDs, order IDs and audit references (never tokens or secrets).
6. Run Android authenticated instrumentation and inspect the resulting screenshots/UI hierarchy.
7. Reconcile remaining PRs into the canonical branch without force-pushing or merging a diverged PR blindly.
8. Build the official APK from the final canonical commit; record commit SHA, artifact ID, exact byte size and SHA-256.

## Status vocabulary

- **SOURCE-VERIFIED:** code and deterministic tests support the behavior; not proof of live service.
- **APK-VERIFIED:** actual built APK entry point and UI journey inspected.
- **BACKEND-VERIFIED:** behavior exercised against a running backend and persistent database.
- **PROVIDER-VERIFIED:** external provider test-mode confirmation/webhook/refund/payout exercised.
- **BLOCKED ON LIVE BACKEND:** cannot be honestly completed until the backend and required external test services are available.

Never change a blocked item to complete merely because its route, screen, verifier or documentation exists.
