# FYNX Buyer Wallet — Build Register

## Product boundary

This workstream is the buyer's NGN wallet inside the canonical FYNX Android application. It is separate from the existing seller earnings and Marketplace escrow system. Existing Paystack direct checkout remains available and unchanged.

The wallet must not be represented as a real-money product until a suitable provider arrangement, custody model, regulatory review and end-to-end reconciliation have been confirmed.

The buyer wallet is isolated in `backend/buyerWallet.js`; seller settlement remains in `backend/marketplaceSettlement.js`. The two systems share Marketplace order lifecycle rules but not wallet balances or ledger tables.

## User experience

- One direct Wallet entry in FYNX Features and a second entry from Money Center.
- Azure Blue balance card (#007FFF) with white text and a balance-visibility control.
- Clear labels for available, reserved and withdrawal-pending funds.
- Add money and Withdraw actions are visible but remain unavailable until the provider-backed flows are implemented and verified.
- Confirmed incoming funding and refund activity uses Classic Green (#008000).
- Loading, retry, empty-history and provider-preparation states are explicit; no sample balance is presented as real.

## Financial invariants

- Buyer wallet balances are derived from server-side ledger entries, never from client-side arithmetic or a client-submitted balance.
- NGN-only first release.
- Every successful transaction must balance total debits and credits.
- Ledger rows are append-only. Corrections use reversal transactions rather than editing history.
- Wallet ledger entries must match the parent transaction's wallet and currency.
- Available, reserved and withdrawal-pending balances may never become negative.
- Operations must use unique transaction references and idempotency keys.
- Buyer wallet money is separate from seller Marketplace escrow and seller payout accounting.
- Every wallet endpoint is authenticated, buyer-scoped and returned with private no-store caching.

## Delivery stages

| Stage | Scope | Status |
|---|---|---|
| Foundation | Buyer wallet screen, authenticated read APIs in a dedicated buyer-wallet module, NGN wallet/transaction/ledger schema, ledger invariants, static verification | In this change |
| Provider decision | Written eligibility, licensing/custody confirmation, production API access, fee/limit/KYC/refund/withdrawal terms | Pending |
| Funding | Provider funding instructions, webhook signature and reference validation, idempotent credit posting, reconciliation | Not started |
| Withdrawals | Verified beneficiary, fee preview, available-balance reservation, transfer initiation and verification/recovery | Not started |
| Marketplace checkout | Atomic wallet debit integrated with existing quote/order/escrow lifecycle | Not started |
| Release hardening | End-to-end provider sandbox tests, concurrency and failure tests, Android device walkthrough, limited rollout | Not started |

## Current implementation boundary

This foundation intentionally exposes read-only summary and activity endpoints. It does not create provider accounts, accept deposits, initiate transfers, debit checkout or claim a wallet balance is active. Funding, spending and withdrawal capabilities return false until the corresponding provider-backed implementation and tests are delivered.

Google Merchant Center and virtual cards are outside this release.
