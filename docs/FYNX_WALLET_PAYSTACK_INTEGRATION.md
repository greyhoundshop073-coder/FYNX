# FYNX Wallet — Paystack integration and release gates

## Scope

This change connects the existing Android Wallet screens to provider-backed wallet APIs. It does not create a second balance store: the buyer wallet ledger remains the source of truth for available, reserved, pending-withdrawal and reversal movements.

The Android app never receives the Paystack secret key and never collects or stores card numbers. Payment initiation and verification happen on the backend.

## Funding methods

### Bank transfer

FYNX starts a Paystack bank-transfer charge for a specific funding transaction. Paystack returns a **temporary account number for that top-up**, its bank and account name, a reference, exact amount and expiry. The account is not a permanent personal virtual account and must not be reused after the transaction expires.

The backend persists the returned instructions with the user's pending wallet transaction. It credits the wallet only after a signed Paystack `charge.success` webhook or a server-to-server transaction verification confirms all of the following:

- The reference matches the pending FYNX funding transaction.
- The amount matches exactly in kobo.
- The currency is NGN.
- The provider status is successful.

The posting is one idempotent, balanced ledger transaction: debit provider clearing and credit the user's available wallet balance. A failed or rejected transfer never credits the wallet.

### Card and USSD

FYNX creates a Paystack-hosted checkout session for the selected method and opens only the HTTPS `checkout.paystack.com` URL returned by Paystack. Card details remain on the provider's hosted checkout. The wallet is not credited from the redirect/deep-link result; it is credited only by the verified webhook or server-side verification.

## Withdrawals

- The user selects a Nigerian bank and enters a 10-digit account number.
- The backend resolves the account name with Paystack and creates a transfer recipient.
- FYNX stores the provider recipient code and masked last four digits, not the full bank account number.
- A salted scrypt hash protects the six-digit FYNX Wallet PIN; repeated failures trigger a 30-minute lock.
- A withdrawal request is idempotent. Pending provider payouts reserve the requested amount plus the configured FYNX withdrawal fee.
- The available wallet balance is not debited until Paystack confirms payout success. The ledger then debits available funds and credits the external-bank and FYNX-fee accounts.
- Failed transfers release the reservation without a debit. A provider reversal is a separate balanced reversal transaction that restores the amount and fee.
- OTP-required transfers remain pending until the user submits the provider OTP. A successful API response alone is not considered paid unless the provider status says success.

## Backend environment variables

Set these only in the Railway service's secret/environment settings. Never commit provider credentials:

| Variable | Purpose |
| --- | --- |
| `PAYSTACK_SECRET_KEY` | Paystack secret API key, backend only |
| `FYNX_WALLET_PROVIDER_APPROVED` | Platform approval gate for the wallet provider |
| `FYNX_WALLET_CUSTODY_APPROVED` | Platform custody/settlement approval gate |
| `FYNX_WALLET_WEBHOOK_CONFIGURED` | Confirms the signed webhook endpoint is configured in Paystack |
| `FYNX_WALLET_FUNDING_ENABLED` | Enables funding after provider, custody and webhook gates pass |
| `FYNX_WALLET_WITHDRAWALS_ENABLED` | Enables payout routes after provider gates pass |
| `FYNX_WALLET_TRANSFERS_APPROVED` | Confirms Paystack transfers are enabled for the FYNX business |
| `FYNX_WALLET_KYC_APPROVED` | Additional platform-level KYC/compliance release gate; **not a substitute for per-user KYC** |
| `FYNX_WALLET_MAX_FUNDING_NGN` | Optional funding limit; default 5,000,000 NGN |
| `FYNX_WALLET_MAX_WITHDRAWAL_NGN` | Optional per-withdrawal limit; default 1,000,000 NGN |
| `FYNX_WALLET_WITHDRAWAL_FEE_NGN` | FYNX withdrawal fee shown before confirmation; default 0 |
| `FYNX_WALLET_MAX_WITHDRAWAL_FEE_NGN` | Maximum permitted configured withdrawal fee; default 1,000 NGN |

All approval/enablement booleans are off unless explicitly set to `true`. Do not enable customer withdrawals until a per-user KYC workflow, custody/settlement arrangement, provider transfer balance, dispute/refund handling and compliance sign-off are in place. The global KYC flag is an additional platform release gate only; this release does not claim per-user KYC is complete.

## Paystack dashboard setup

Configure the Paystack webhook destination as:

```text
https://<FYNX-backend-host>/api/wallet/paystack/webhook
```

The backend preserves the original request bytes for this endpoint and verifies the `x-paystack-signature` HMAC-SHA512 signature using `PAYSTACK_SECRET_KEY`. Keep the existing Marketplace Paystack webhook configured independently at `/api/marketplace/payments/paystack/webhook`; the raw-body middleware now supports both endpoints.

## Wallet API routes

Authenticated app routes:

- `POST /api/wallet/funding/transfer`
- `POST /api/wallet/funding/checkout`
- `GET /api/wallet/funding/:reference`
- `GET /api/wallet/banks`
- `POST /api/wallet/banks/resolve`
- `POST /api/wallet/payout-accounts`
- `GET /api/wallet/payout-accounts`
- `GET /api/wallet/security`
- `POST /api/wallet/security/pin`
- `POST /api/wallet/withdrawals`
- `POST /api/wallet/withdrawals/:reference/finalize`
- `GET /api/wallet/withdrawals/:reference`

Provider webhook:

- `POST /api/wallet/paystack/webhook`

Funding and withdrawal creation require an `Idempotency-Key` header. Private routes are scoped to the authenticated user, and responses are marked no-store where they contain financial or bank data.

## Marketplace commission

The Marketplace 5% seller-paid fee is a separate order-settlement policy. It is disclosed in seller order/sales details and recorded in the Marketplace settlement ledger and FYNX's admin-only revenue reporting. It is not deducted from wallet top-ups or ordinary bank withdrawals. This commission does not share buyer-wallet ledger accounts.

## Verification

The backend CI runs the buyer-wallet DDL/ledger test, backend wallet contract verifier, runtime bootstrap integration verifier and syntax-checks every backend JavaScript module. The PostgreSQL wallet test exercises a verified top-up, a fee-bearing payout, a payout reversal, account-number minimisation, transaction immutability and double-entry invariants. The Android CI builds/tests the app and captures the Wallet/Add Money/bank-transfer/Withdraw screens from the emulator.

## Remaining external release dependencies

Code cannot manufacture a Paystack account, API secret, approval for dedicated virtual accounts, a custody licence/arrangement, or permission to use Paystack Transfers. This implementation uses temporary transaction-specific bank accounts for individual top-ups. A permanent personal virtual account is a separate Paystack product and must be added only if/when the FYNX business is eligible and approved. Real-money end-to-end tests must use the approved provider sandbox first; do not enable live keys or customer transfers during UI/CI verification.
