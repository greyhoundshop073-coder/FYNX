# FYNX Neon Functions testing deployment

## Target

Deploy the existing Node backend as a Neon Function on the populated `fynx-testing` branch (`br-floral-shape-b5mbajl5`) of project `patient-lab-43403708`. This is the existing FYNX test data branch (239 users, 55 groups and 161 group messages at the last read-only inspection). The Function receives Neon's branch-scoped `DATABASE_URL`; the production branch is not targeted.

The function runs Node.js 24, keeps the existing Express routes, adapts HTTP requests through `serverless-http`, and adapts the existing authenticated `/realtime` WebSocket handler through Neon's `upgradeWebSocket` API.

Realtime is designed for multiple Neon Function isolates: transient user-targeted and global events are written to `fynx_realtime_events`, active WebSocket connections are tracked in `fynx_realtime_connections`, and call state is shared through `fynx_realtime_calls`. Each isolate polls the outbox only while it has local WebSocket clients; event rows are retained for one hour. A PostgreSQL integration check verifies user-targeted and global event fanout between two simulated isolates.

The `fynx-marketplace-maintenance` schedule is deliberately deployed **disabled** for the first testing pass. This branch contains existing test users and historical Marketplace data. Do not enable background settlement, payment recovery, or notification jobs until the test Paystack key and test-mode settings have been verified. Once the API and wallet flows pass their isolated tests, enable the schedule on this development branch only. It invokes `/internal/cron` every minute and runs payment reservation and checkout expiry, interrupted-payment and terminal-financial recovery, inspection expiry, payout settlement scanning, up to 20 queued background jobs per invocation, and stale realtime cleanup.

## One-time deployment credentials

In GitHub, open **greyhoundshop073-coder/FYNX → Settings → Secrets and variables → Actions**.

Add these repository **Secrets** (values must be copied from the existing Railway `fynx-backend-testing` environment; never paste them into chat or commit them):

- `NEON_API_KEY` — create an API key in Neon.
- `FYNX_JWT_SECRET` — use a dedicated development JWT secret. Existing tokens from another environment will not validate; create/sign in with development test accounts.
- `FYNX_GEMINI_API_KEY` — optional; add a development API key if AI testing is required.
- `FYNX_PAYSTACK_SECRET_KEY` — use a **Paystack test-mode secret key only**. Until one is available, do not run wallet funding or payouts; core API deployment should not depend on enabling real payments.

Repository **Variables** are optional for the first development deployment. The workflow supplies safe defaults when they are absent:

- `FYNX_OFFICIAL_USERNAME` — defaults to `fynx`.
- `FYNX_WALLET_TEST_MODE_ENABLED` — defaults to `false`.
- `FYNX_WALLET_CARD_FUNDING_ENABLED` — defaults to `false`.
- `FYNX_WALLET_FUNDING_MIN_AMOUNT` — defaults to `100` (NGN).
- `FYNX_WALLET_FUNDING_MAX_AMOUNT` — defaults to `100000` (NGN).

These defaults let the core API deploy without activating wallet funding or real payments. The deployment rejects any Paystack key that does not start with `sk_test_`. Do not change the wallet flags for the initial Neon deployment. Wallet test-mode behavior needs a separate runtime safety review because the current test-mode gate includes a Railway testing-environment check.

The workflow is pinned to project `patient-lab-43403708` and branch `fynx-testing`. It can be run manually or is triggered by canonical backend changes. It must not be repointed to `production` or `live-recovery-20261101`.

## Runtime configuration

The deployment maps the above secrets and variables into the Neon Function. Neon injects the branch-scoped `DATABASE_URL` automatically. Do not copy Railway's `DATABASE_URL` into the Function.

Optional integrations can be configured as Function environment variables without changing the Android app:

- `OPENAI_API_KEY`, `OPENAI_MODEL`
- SMTP secrets: `FYNX_EMAIL_HOST`, `FYNX_EMAIL_PORT`, `FYNX_EMAIL_USER`, `FYNX_EMAIL_PASSWORD`, `FYNX_EMAIL_FROM`, `FYNX_EMAIL_SECURE`, `FYNX_EMAIL_REQUIRE_TLS`. The deploy workflow passes these through when the matching GitHub secrets exist; otherwise email delivery remains fail-closed until configured.
- `FYNX_MEDIA_STORAGE_PROVIDER` is set to `neon` on the Function.
- `FYNX_NEON_STORAGE_BUCKET` is set to `fynx-media`; Neon injects the branch-scoped `AWS_ENDPOINT_URL_S3`, `AWS_REGION`, `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` because the private bucket is declared in `neon.ts`.
- `FYNX_R2_ACCOUNT_ID`, `FYNX_R2_ACCESS_KEY_ID`, `FYNX_R2_SECRET_ACCESS_KEY`, `FYNX_R2_BUCKET`
- `FYNX_RECEIPT_SIGNING_SECRET`, `FYNX_MARKETPLACE_FEE_BPS`, `FYNX_MARKETPLACE_FEE_BUYER_SHARE_BPS`, `FYNX_MARKETPLACE_FEE_FIXED`, `FYNX_MARKETPLACE_FEE_MODE`, `FYNX_MARKETPLACE_FEE_POLICY_VERSION`, `FYNX_MARKETPLACE_PROVIDER_FEE_PAYER`, `FYNX_AD_ADMIN_IDS`

## Safety gates before changing Android

- Confirm the Function's branch is `fynx-testing` and its URL is healthy.
- Configure the development JWT secret. Reuse the previous testing secret if it is available; otherwise use a separate development-only secret and have test accounts sign in again (old tokens will no longer validate). Never reuse a live production secret in this branch.
- Verify health, login, token validation, social feed, chat REST, WebSocket realtime, Marketplace, Wallet, media and AI routes.
- Keep the maintenance trigger disabled while validating APIs and historical test data. Enable it only after confirming Paystack test-mode credentials, wallet test flags, and that queued jobs cannot issue real payouts or notifications.
- Keep the Android backend URL unchanged until all gates pass.
- Do not run migrations against `production` or `live-recovery-20261101`; do not reset or delete any branch data.

## Free-plan limits

Neon's Free plan currently includes Functions usage allowances (10 active capacity-hours, 400 waiting capacity-hours and 1 million invocations per project per month). Monitor usage during tester sessions; these are quotas, not unlimited hosting.
