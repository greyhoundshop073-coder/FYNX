# FYNX Neon Functions testing deployment

## Target

Deploy the existing Node backend as a Neon Function on the isolated `fynx-testing` branch of project `patient-lab-43403708` (branch id `br-floral-shape-b5mbajl5`). The Function receives Neon's branch-scoped `DATABASE_URL`; no production or recovery branch is targeted.

The function runs Node.js 24, keeps the existing Express routes, adapts HTTP requests through `serverless-http`, and adapts the existing authenticated `/realtime` WebSocket handler through Neon's `upgradeWebSocket` API.

Realtime is designed for multiple Neon Function isolates: transient user-targeted and global events are written to `fynx_realtime_events`, active WebSocket connections are tracked in `fynx_realtime_connections`, and call state is shared through `fynx_realtime_calls`. Each isolate polls the outbox only while it has local WebSocket clients; event rows are retained for one hour. A PostgreSQL integration check verifies user-targeted and global event fanout between two simulated isolates.

The `fynx-marketplace-maintenance` schedule invokes `/internal/cron` every minute. It runs payment reservation and checkout expiry, interrupted-payment and terminal-financial recovery, inspection expiry, payout settlement scanning, up to 20 queued background jobs per invocation, and stale realtime cleanup. These scheduled jobs replace the corresponding process-local timers in the Neon request runtime.

## One-time deployment credentials

In GitHub, open **greyhoundshop073-coder/FYNX → Settings → Secrets and variables → Actions**.

Add these repository **Secrets** (values must be copied from the existing Railway `fynx-backend-testing` environment; never paste them into chat or commit them):

- `NEON_API_KEY` — create an API key in Neon.
- `FYNX_JWT_SECRET` — copy the exact current Railway `JWT_SECRET`. Do not generate a replacement; existing tester login tokens depend on it.
- `FYNX_GEMINI_API_KEY` — copy the current Railway `GEMINI_API_KEY`.
- `FYNX_PAYSTACK_SECRET_KEY` — copy the current Railway `PAYSTACK_SECRET_KEY`.

Add these repository **Variables** using their exact current Railway values:

- `FYNX_OFFICIAL_USERNAME`
- `FYNX_WALLET_TEST_MODE_ENABLED`
- `FYNX_WALLET_CARD_FUNDING_ENABLED`
- `FYNX_WALLET_FUNDING_MIN_AMOUNT`
- `FYNX_WALLET_FUNDING_MAX_AMOUNT`

Then open **Actions → FYNX Neon Function Deploy → Run workflow**. The workflow deploys only to `fynx-testing`. It is manual-only; ordinary pushes do not deploy a backend.

## Runtime configuration

The deployment maps the above secrets and variables into the Neon Function. Neon injects the branch-scoped `DATABASE_URL` automatically. Do not copy Railway's `DATABASE_URL` into the Function.

Optional integrations can be added later as Function environment variables without changing the Android app:

- `OPENAI_API_KEY`, `OPENAI_MODEL`
- `FYNX_EMAIL_HOST`, `FYNX_EMAIL_PORT`, `FYNX_EMAIL_USER`, `FYNX_EMAIL_PASSWORD`, `FYNX_EMAIL_FROM`, `FYNX_EMAIL_SECURE`, `FYNX_EMAIL_REQUIRE_TLS`
- `FYNX_MEDIA_STORAGE_PROVIDER` (leave unset or set to `postgres` until Neon Object Storage credentials are configured)
- `FYNX_NEON_S3_ENDPOINT`, `FYNX_NEON_S3_REGION`, `FYNX_NEON_S3_ACCESS_KEY_ID`, `FYNX_NEON_S3_SECRET_ACCESS_KEY`, `FYNX_NEON_STORAGE_BUCKET`
- `FYNX_R2_ACCOUNT_ID`, `FYNX_R2_ACCESS_KEY_ID`, `FYNX_R2_SECRET_ACCESS_KEY`, `FYNX_R2_BUCKET`
- `FYNX_RECEIPT_SIGNING_SECRET`, `FYNX_MARKETPLACE_FEE_BPS`, `FYNX_MARKETPLACE_FEE_BUYER_SHARE_BPS`, `FYNX_MARKETPLACE_FEE_FIXED`, `FYNX_MARKETPLACE_FEE_MODE`, `FYNX_MARKETPLACE_FEE_POLICY_VERSION`, `FYNX_MARKETPLACE_PROVIDER_FEE_PAYER`, `FYNX_AD_ADMIN_IDS`

## Safety gates before changing Android

- Confirm the Function's branch is `fynx-testing`.
- Configure the original `JWT_SECRET` and verify login with the existing test accounts before any Android URL change.
- Verify health, login, token validation, social feed, chat REST, WebSocket realtime, Marketplace, Wallet, media and AI routes.
- Verify scheduled Marketplace settlement/expiry tasks before relying on automatic settlement.
- Keep the Android backend URL unchanged until all gates pass.
- Do not run migrations against `production` or `live-recovery-20261101`; do not reset or delete any branch data.

## Free-plan limits

Neon's Free plan currently includes Functions usage allowances (10 active capacity-hours, 400 waiting capacity-hours and 1 million invocations per project per month). Monitor usage during tester sessions; these are quotas, not unlimited hosting.
