# Marketplace Backend-Dependent Completion Checklist

Last reviewed: 2026-11-10
Canonical Android branch at review: `integration/fynx-single-apk-20261013`
Review baseline commit: `43839989b1abd8037db0c2af202208ac46944819`

## Purpose

This is ONLY the list of work that cannot be honestly completed until the FYNX backend/database and required external test services are available. All source-only, Android UI, offline, static, unit, mock-based, documentation, and other backend-independent work is to be completed now in the active development work—not parked in this file.

Do not spend money, recreate infrastructure, alter credentials, or fabricate users, orders, products, payments, balances, delivery events or support cases. When the backend returns, start this checklist immediately without repeating the source investigation.

## Existing foundations to preserve

The canonical source already contains substantial foundations for protected checkout, quote validation, payment verification, escrow/disputes, settlement, delivery, driver custody, Wallet and seller profiles. Relevant starting points:

- Checkout and quote: `backend/marketplaceCheckout.js`, `backend/marketplaceCheckoutOrder.js`.
- Disputes and financial protection: `backend/marketplaceProtection.js`, `backend/marketplaceProtectionResolution.js`, `backend/marketplaceSettlement.js`.
- Delivery and inspection: `backend/marketplaceCompletion.js`, `backend/marketplaceInspectionExpiry.js`, `backend/marketplaceShippingTimeline.js`, `backend/verify-marketplace-fulfillment.mjs`.
- Wallet: `backend/buyerWallet.js`, `app/src/main/java/com/fynx/app/ui/FynxBuyerWalletClient.kt`.
- Reputation and shop profiles: `backend/marketplaceReputation.js`, `backend/marketplaceShopProfiles.js`.
- Source verifiers: `scripts/verify_marketplace_checkout_fee_breakdown.py`, `scripts/verify_r6a_marketplace_protection.py`, `scripts/verify_marketplace_integration_security.py`, `scripts/verify_buyer_wallet_integration.py`.

These are implementation starting points, not proof of successful real-world journeys.

## Backend/database/provider-dependent tasks

- [ ] **Database migration and persistence:** apply Marketplace, Wallet, reputation, support, dispute, driver and ledger schemas on a disposable test database first; verify idempotent migrations, upgrade behavior, indexes, constraints and rollback/recovery.
- [ ] **Real buyer/seller transaction:** use two authorized test accounts and a real test listing; create order and quote, initialize provider payment, confirm provider webhook, seller fulfilment, buyer inspection and completion.
- [ ] **Paystack payment/refund:** exercise test-mode payment and refund; verify webhook signatures, amount/currency/reference matching, duplicate and late events, idempotency, refund confirmation and reconciliation. Use no live funds.
- [ ] **Wallet accounting:** reconcile available, reserved, pending and withdrawable balances against ledger rows through top-up, protected checkout, refund, release and withdrawal; prove no double-credit/debit and no NGN/USD mixing.
- [ ] **Seller payout:** verify payout account ownership and verification, amount source, transfer request, provider reference, confirmation webhook, failure/reversal, and mutual exclusion between refund and payout.
- [ ] **Disputes and resolution:** create a real test dispute; prove order/escrow freeze, preservation of pre-dispute state, participant-only evidence, correct admin authorization, and buyer-refund and seller-release outcomes.
- [ ] **Inventory concurrency:** run parallel checkouts and concurrent cancel/refund/completion operations against a real database; prove no oversell, double release or lost stock.
- [ ] **Delivery journey:** use separate buyer/seller/driver/admin test accounts to verify approved driver onboarding, seller assignment, driver acceptance, seller handover evidence, driver delivery evidence, buyer receipt, pickup handover and inspection expiry.
- [ ] **Support operations:** verify case submission, attachments, account isolation, buyer/seller replies, staff/admin assignment, status transitions, audit history and order/dispute linkage. Confirm support agents cannot execute owner/admin-only financial resolution.
- [ ] **Real-time call from a listing:** initiate a product-linked call between two test accounts; verify listing identity/title through invitation, ringing, acceptance, active call, reconnect and hang-up.
- [ ] **Production-like reliability:** test catalogue/order pagination and query plans at realistic sizes; object-storage uploads/downloads; slow/interrupted mobile network; worker retries; database timeouts; scheduled inspection expiry; backups and recovery.

## Exact restart sequence

1. Confirm service health, database readiness and active environment. Do not alter infrastructure or secrets without approval.
2. Apply migrations to a disposable test database and verify schema readiness.
3. Run backend integration/security/fulfilment tests and fix red gates.
4. Run Paystack test-mode payment, webhook, ledger, refund and payout tests.
5. Run separate buyer/seller/driver/admin journeys and record non-secret order IDs, provider references and audit evidence.
6. Run authenticated Android end-to-end journeys against that backend and inspect actual APK screenshots/UI hierarchy.
7. Reconcile open PRs into the canonical branch safely; never merge a diverged PR blindly or force-push.
8. Build the official APK from the final canonical commit and record commit SHA, artifact ID, exact byte size and SHA-256.

## Completion evidence labels

- **BACKEND-VERIFIED:** exercised against a running backend and persistent database.
- **PROVIDER-VERIFIED:** external provider test-mode confirmation/webhook/refund/payout exercised.
- **END-TO-END-VERIFIED:** buyer/seller/driver/admin Android journeys completed against the backend.
- **BLOCKED ON BACKEND:** cannot be completed honestly until the backend and required external test services are available.

Never mark an item complete merely because a route, screen, verifier or documentation exists.
