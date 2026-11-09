# FYNX Neon Functions testing deployment

## Target

Deploy the existing Node backend as a Neon Function on the isolated `fynx-testing` branch of project `patient-lab-43403708` (branch id `br-floral-shape-b5mbajl5`). The Function receives Neon's branch-scoped `DATABASE_URL`; no production or recovery branch is targeted.

The function runs Node.js 24, keeps the existing Express routes, adapts HTTP requests through `serverless-http`, and adapts the existing authenticated `/realtime` WebSocket handler through Neon's `upgradeWebSocket` API.

## One-time deployment credential

1. In GitHub, open **greyhoundshop073-coder/FYNX → Settings → Secrets and variables → Actions**.
2. Add repository secret `NEON_API_KEY` using a Neon API key. Do not paste the key into a chat or commit it.
3. Open **Actions → FYNX Neon Function Deploy → Run workflow**. The workflow deploys only to `fynx-testing`.

The workflow is manual-only; ordinary pushes do not deploy a backend.

## Function environment

After the first deployment, configure these variables on the `fynxapi` Function in the `fynx-testing` branch using the Neon Console. Never commit their values.

Required to preserve existing tester sessions and app services:

- `JWT_SECRET` — copy the exact existing value from the Railway `fynx-backend-testing` service. Do not generate a replacement; existing login tokens depend on it.
- `GEMINI_API_KEY` — existing FYNX Gemini credential.
- `PAYSTACK_SECRET_KEY` — existing Paystack test secret.
- `PAYSTACK_PUBLIC_KEY` — existing Paystack public key, if the client config endpoint reads it.
- `FYNX_WALLET_TEST_MODE_ENABLED`, `FYNX_WALLET_CARD_FUNDING_ENABLED`, `FYNX_WALLET_FUNDING_MIN_AMOUNT`, `FYNX_WALLET_FUNDING_MAX_AMOUNT` — preserve the current test-wallet configuration.

Optional integrations:

- `OPENAI_API_KEY`, `OPENAI_MODEL`
- `FYNX_EMAIL_HOST`, `FYNX_EMAIL_PORT`, `FYNX_EMAIL_USER`, `FYNX_EMAIL_PASSWORD`, `FYNX_EMAIL_FROM`, `FYNX_EMAIL_SECURE`, `FYNX_EMAIL_REQUIRE_TLS`
- `FYNX_MEDIA_STORAGE_PROVIDER` (leave unset or set to `postgres` until Neon Object Storage credentials are configured)
- `FYNX_NEON_S3_ENDPOINT`, `FYNX_NEON_S3_REGION`, `FYNX_NEON_S3_ACCESS_KEY_ID`, `FYNX_NEON_S3_SECRET_ACCESS_KEY`, `FYNX_NEON_STORAGE_BUCKET`
- `FYNX_R2_ACCOUNT_ID`, `FYNX_R2_ACCESS_KEY_ID`, `FYNX_R2_SECRET_ACCESS_KEY`, `FYNX_R2_BUCKET`
- `FYNX_RECEIPT_SIGNING_SECRET`, `FYNX_MARKETPLACE_FEE_BPS`, `FYNX_MARKETPLACE_FEE_BUYER_SHARE_BPS`, `FYNX_MARKETPLACE_FEE_FIXED`, `FYNX_MARKETPLACE_FEE_MODE`, `FYNX_MARKETPLACE_FEE_POLICY_VERSION`, `FYNX_MARKETPLACE_PROVIDER_FEE_PAYER`, `FYNX_AD_ADMIN_IDS`, `FYNX_OFFICIAL_USERNAME`

Neon injects `DATABASE_URL` for the deployed branch. Do not copy Railway's `DATABASE_URL` into the Function.

## Safety gates before changing Android

- Confirm the Function's branch is `fynx-testing`.
- Configure the original `JWT_SECRET` and verify login with the existing test accounts before any Android URL change.
- Verify health, login, token validation, social feed, chat REST, WebSocket realtime, Marketplace, Wallet, media and AI routes.
- Verify scheduled Marketplace settlement/expiry tasks before relying on automatic settlement.
- Keep the Android backend URL unchanged until all gates pass.
- Do not run migrations against `production` or `live-recovery-20261101`; do not reset or delete any branch data.

## Free-plan limits

Neon's Free plan currently includes Functions usage allowances (10 active capacity-hours, 400 waiting capacity-hours and 1 million invocations per project per month). Monitor usage during tester sessions; these are quotas, not unlimited hosting.
