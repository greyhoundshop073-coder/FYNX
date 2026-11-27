# FYNX Marketplace and Wallet — Offline Readiness

Date: 2026-11-27

Canonical integration target: `integration/fynx-single-apk-20261013`

This document records the application/backend work that can be completed and verified without a live Railway deployment, production Neon connectivity, or an available Paystack account. It deliberately separates code readiness from provider certification.

## Engineering readiness in this workstream

- Marketplace listing inputs are bounded by shared title, quantity, media-count and supported-currency limits. A listing must support delivery or buyer pickup.
- Empty-catalogue seller publishing remains reachable; seller identity imagery uses the Marketplace avatar binding and account-scoped profile-photo cache.
- My Shop's contact phone is returned only by the authenticated private My Shop endpoint. Public storefront responses omit it. Partial profile edits preserve fields that were not supplied.
- Saved-product and seller-owned reactivation routes preserve PostgreSQL BIGINT IDs as decimal strings, reject out-of-range IDs, and reject blocked or unavailable listings where required.
- Seller reviews and reputation exclude self-purchases and require completed orders with a completion timestamp. Refunded/incomplete orders do not contribute to reputation.
- Marketplace checkout and order protections retain NGN/USD contracts, server-side amount reconciliation, idempotency and order-state authorization.
- Buyer Wallet route registration has one owner: `backend/scalability.js`. Railway and Neon load this shared layer; generated server bootstrap code must not register the same Wallet routes again.
- Isolated Marketplace UI tests can disable local calendar promotions and carousel auto-rotation without disabling real production campaigns.

## Wallet and Paystack safety contract

Test-mode configuration is not a live-money approval. When `FYNX_WALLET_TEST_MODE_ENABLED=true`, the backend requires all of the following for simulated card funding:

- `RAILWAY_ENVIRONMENT_NAME=testing`
- `PAYSTACK_SECRET_KEY` beginning with `sk_test_`
- `FYNX_WALLET_CARD_FUNDING_ENABLED=true`
- Valid positive `FYNX_WALLET_FUNDING_MIN_AMOUNT` and `FYNX_WALLET_FUNDING_MAX_AMOUNT` limits

In this mode, the wallet provider gate authorizes only simulated card funding. Wallet deposits, withdrawals, spending and Marketplace wallet debits are not enabled. Test balances must never be treated as spendable production money.

Real provider actions require explicit approval flags in addition to the relevant action switch:

- Common: `FYNX_WALLET_PROVIDER_APPROVED=true`, `FYNX_WALLET_CUSTODY_APPROVED=true`, `FYNX_WALLET_COMPLIANCE_APPROVED=true`, and the correct live Paystack secret.
- Deposits: `FYNX_WALLET_DEPOSITS_ENABLED=true`, `FYNX_PAYSTACK_WALLET_BANK`, and a valid `FYNX_WALLET_ACCOUNT_ENCRYPTION_KEY`.
- Card funding: `FYNX_WALLET_CARD_FUNDING_ENABLED=true` plus valid funding limits.
- USSD funding: `FYNX_WALLET_USSD_FUNDING_ENABLED=true` plus valid funding limits.
- Spending: `FYNX_WALLET_SPENDING_ENABLED=true`.
- Withdrawals: `FYNX_PAYSTACK_REGISTERED_BUSINESS_APPROVED=true`, `FYNX_WALLET_WITHDRAWALS_ENABLED=true`, `FYNX_WALLET_PAYOUTS_APPROVED=true`, `FYNX_WALLET_WITHDRAWAL_FEE_POLICY_APPROVED=true`, `FYNX_WALLET_TRANSFER_OTP_DISABLED=true`, and valid withdrawal limits.

Never copy test credentials into production. Never enable the live approval flags simply to make a UI screen look complete. Paystack webhook signatures must be checked against the original request bytes, and payment/order reconciliation must remain idempotent.

## Reconnection and release gates

When the external services become available, complete these checks in order:

1. Deploy the canonical backend and confirm its health response reports the expected FYNX service and a ready database. A generic HTTP 200 or a route returning 404 is not sufficient.
2. Apply/verify the Neon schema migrations and confirm Railway and Neon both load the same Wallet route-registration layer exactly once.
3. Run authenticated buyer and seller tests for listing creation, private My Shop edits, public storefront privacy, saved products, checkout, order fulfillment, disputes and refunds.
4. Configure Paystack test credentials only in the testing environment. Verify initialization, hosted checkout, signed webhooks, success/failure/pending reconciliation, duplicate webhook delivery and recovery from interrupted callbacks.
5. Verify NGN and USD Marketplace checkout amounts, fees, currency handling and receipts against provider responses.
6. Complete provider/business/custody/compliance approval before enabling any live deposit, spending, withdrawal or payout switch.
7. Run the full Android emulator flow on the exact canonical commit, inspect real Marketplace/My Shop/Wallet screenshots, and certify the APK built from that exact commit.

## Evidence boundary

- Backend unit, contract and local PostgreSQL integration tests can run offline and should remain mandatory.
- Emulator screenshots certify rendered UI and navigation only. They do not prove authenticated production data, payment settlement or payout custody.
- A backend HTTP 404, unavailable service, missing listing, or absent Paystack credentials is recorded as blocked; no sample data or fabricated transaction is substituted.
- The single canonical APK is not certified until the exact canonical integration commit passes the required Android build, instrumentation, runtime capture and APK-integrity checks.
