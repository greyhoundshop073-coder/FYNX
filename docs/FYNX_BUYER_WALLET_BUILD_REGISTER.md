# FYNX Buyer Wallet — Build Register

## Product boundary

This workstream is the buyer's NGN wallet inside the canonical FYNX Android application. It is separate from the existing seller earnings and Marketplace escrow system. Existing Paystack direct checkout remains available and unchanged.

The wallet must not be represented as a real-money product until a suitable provider arrangement, custody model, regulatory review and end-to-end reconciliation have been confirmed.

The buyer wallet is isolated in `backend/buyerWallet.js`; seller settlement remains in `backend/marketplaceSettlement.js`. The two systems share Marketplace order lifecycle rules but not wallet balances or ledger tables.

## User experience

- Direct Wallet access from the main FYNX Features hub, the Marketplace toolbar and Money Center; all three routes open the same wallet screen.
- Azure Blue balance card (#007FFF) with white text and a balance-visibility control.
- Clear labels for available, reserved and withdrawal-pending funds.
- Add money and Withdraw actions are visible but remain unavailable until the provider-backed flows are implemented and verified.
- Confirmed incoming funding and refund activity uses Classic Green (#008000).
- Summary and activity have independent loading states; the provider-preparation notice is withheld until the summary request settles, and the emulator waits for both requests to finish (or show a clear error) before accepting a screenshot. No sample balance is presented as real.
- Wallet summary failures and activity-history failures are displayed separately. A failed history request must never be presented as a successful empty wallet.
- Activity rows show timezone-aware dates and transaction references; tapping a row opens a transaction detail with a copyable reference.
- Activity can be filtered across the complete server history into all, money-in and money-out views; Load more keeps the selected filter and uses a stable server-side cursor.
- Available, reserved and withdrawal-pending funds have plain-language explanations. Balance hiding applies across the summary, buckets and activity amounts.
- A bank-withdrawal guide explains the intended cash-out safeguards without presenting withdrawals as live.
- The wallet feature branch runs full Android emulator certification and captures the real Wallet screen and UI hierarchy from the installed exact-commit APK; this is a read-only navigation test and never initiates money movement.

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
| UX hardening | Distinct wallet-vs-activity errors, truthful empty state, timezone-aware activity, status guidance, directional filters, cursor pagination, transaction details, reference copying, balance explanations and complete amount masking | Expanded in this follow-up |
| Provider decision | Written eligibility, licensing/custody confirmation, production API access, fee/limit/KYC/refund/withdrawal terms | Pending |
| Funding | Provider funding instructions, webhook signature and reference validation, idempotent credit posting, reconciliation | Blocked on provider approval |
| Withdrawals | Verified beneficiary, fee preview, available-balance reservation, transfer initiation and verification/recovery | Blocked on provider approval |
| Marketplace checkout | Atomic wallet debit integrated with existing quote/order/escrow lifecycle | Not started |
| Release hardening | End-to-end provider sandbox tests, concurrency and failure tests, Android device walkthrough, limited rollout | Wallet UI emulator walkthrough enabled; provider sandbox and money-movement release gates remain blocked |

## Current implementation boundary

This foundation intentionally exposes read-only summary and activity endpoints. It does not create provider accounts, accept deposits, initiate transfers, debit checkout or claim a wallet balance is active. Funding, spending and withdrawal capabilities return false until the corresponding provider-backed implementation and tests are delivered.

Google Merchant Center and virtual cards are outside this release.


## Wallet action screens and commission presentation — 31 October 2026

- Add Money now opens dedicated funding-method choices for virtual-account transfer, card and USSD; all routes remain informational until an approved provider is connected.
- The bank-transfer screen explicitly shows that a personal account has not yet been assigned. It never renders a placeholder account number or invites users to transfer to an unverified account.
- Withdraw now opens a dedicated payout-preparation screen with available-balance context, account-verification steps and fee transparency expectations. It does not collect sensitive bank details or submit a payout while the provider flow is unavailable.
- Seller Center earnings now labels each recorded order fee as **FYNX commission** and explains the default 5% seller-paid policy. The buyer wallet remains separate from seller proceeds and FYNX platform revenue.
- Fixed the marketplace fee-mode code fallback so an unset mode defaults to SELLER (with the existing 500-basis-point / 5% default); an explicitly configured ZERO, BUYER or SPLIT policy remains respected. Invalid explicit modes now fail closed instead of silently turning commission off.
- Provider-generated dedicated account numbers, signed payment webhooks, withdrawals, KYC/custody approval and live-money end-to-end tests remain provider/backend release dependencies.

- The Withdraw screen also lays out disabled bank, account-number and provider-verified account-name fields, so the intended destination form is visible without collecting or storing sensitive bank details before provider-backed verification exists.
