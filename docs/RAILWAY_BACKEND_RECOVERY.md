# FYNX Railway Backend Recovery — 2026-11-26

## Ownership and scope
- Workstream: Stage 3 — Railway Backend Recovery
- Owner label: Chat — Railway Backend
- Primary host: Railway; database/storage: existing Neon services
- Canonical repository: greyhoundshop073-coder/FYNX
- Canonical branch: integration/fynx-single-apk-20261013
- Correct Railway project: FYNX-Isolated-Testing, project ID 807bb8e2-00f4-4f2c-98cb-e393993f510a
- Environment: testing, ID 51b3423c-ef07-48d8-8c1c-e8dca91d3b81
- Service: fynx-backend-testing, ID aa4eb74c-8907-400a-bfc3-6f6d1cda03b7

A second Railway project with the same display name exists but has no services. The project above is the one containing the target backend.

## Verified Railway configuration
- Repository: greyhoundshop073-coder/FYNX
- Root directory: /backend
- Start command: npm start
- Healthcheck: /ready
- Public domain: fynx-backend-testing-testing.up.railway.app
- Public target port: 8080
- Region: sfo
- Historical deployment metadata used branch main and NIXPACKS. The current service configuration confirms RAILPACK, repository and root directory, but the returned source configuration does not include a branch field. Historical deployment records still show main. The canonical source branch selection is therefore **NOT VERIFIED**.
- The 11-change patch was committed on 2026-11-26 using Railway accept-deploy (workflow ID commitChanges/51b3423c-ef07-48d8-8c1c-e8dca91d3b81/0e560e21-bc20-4134-bd0a-0781b3fb1eea). The service now reports no staged changes. This did not produce a deployment.
- The currently listed variables include DATABASE_URL, FYNX_MEDIA_STORAGE_PROVIDER, five FYNX_NEON_S3_* storage settings, two CLOUDFLARE_SFU_* settings, FYNX_OFFICIAL_USERNAME, wallet test/funding flags and limits, JWT_SECRET, PAYSTACK_SECRET_KEY, NODE_ENV, and GEMINI_API_KEY. Values were not read. The API did not report these variables as sealed; therefore this report only confirms variable-name presence and treats values as protected, not a cryptographic-encryption claim.
- GEMINI_API_KEY is also present by variable name. Its value and provider validity remain unverified.
- NIXPACKS_NODE_VERSION remains in the environment-variable name list although the active builder is RAILPACK. It is not evidence of a remaining NIXPACKS build-secret reference, but can be cleaned up only after confirming it is unused and after owner approval for config changes.

The variable names are verified, but their values are protected and were not read or exposed. Presence does not prove correctness or connectivity. Live variable names also include GEMINI_API_KEY, JWT_SECRET and PAYSTACK_SECRET_KEY. Gemini key presence is verified by name only; validity and runtime use are not verified.

## Original build failure — evidence and limitation
Failed deployment ID: ad50abc2-a52b-40e8-aad1-8d3de1330e08, created 2026-11-03T18:45:13Z. Deployment diagnosis records source commit 0498632d1b72bea8aac5610a6778b75417b94af9, branch main, root /backend, builder RAILPACK 0.43.0 and failure stage BUILD_IMAGE.

Decisive log:
~~~text
[railpack] hash used secrets
error: secret FYNX not found
Build Failed: failed to solve: secret FYNX not found
~~~

This was a 2026-11-03 main-branch RAILPACK 0.43.0 deployment, not a canonical-branch deployment. The failure is in Railpack's generated build plan, not a Nixpacks build. The current accessible service config and checked canonical repository paths do not reveal a build-secret declaration named FYNX. Checked candidate paths included root/backend Railway, Nixpacks, Railpack and Dockerfile configuration names; none exists on the canonical branch. The failed build log shows Railway loading a generated railpack-plan.json, which is a build artifact, not a committed file.

**Exact-reference status:** the historical builder attempted to resolve a build secret named FYNX, but its originating declaration (historical Railway build configuration versus generated build plan/source metadata) has not been isolated. The currently applied service configuration reports RAILPACK, but no new build has run to establish whether the error is eliminated. A code search of the repository's default-branch index did not locate a committed `FYNX` build-secret mount/reference; this search is not a complete proof for the canonical branch. The generated historical `railpack-plan.json` and historical Railway service/build configuration are not available as committed canonical source files. The exact reference remains unresolved. Do not claim a specific source file references it. Do not create a placeholder secret. Treat this as a historical RAILPACK generated-plan build-secret resolution failure until a fresh canonical-branch RAILPACK build proves whether it persists.

A separate 2026-11-03 deployment (ID 6783e2c1-f203-4ab8-af49-0b57187a19f4; commit 7f3cc970aa3f284fe61ece8ff1f12b75b3ff60b4; branch main) used Railpack, successfully built and pushed an image, and passed Railway's `/ready` healthcheck. Railway later marked it REMOVED. This proves the service has previously built and passed readiness under Railpack, but it is not a live deployment and does not prove the canonical branch is deployed.

## Repository and startup-chain findings

The canonical branch has `backend/package.json` with `start: node --import ./renderScalabilityPreload.js realtimeIsolationBootstrap.js`. npm automatically runs the `prestart` script before `start`. That prestart chain executes a long series of media, AI/Gemini, auth, runtime bootstrap, database-pool, marketplace, notification, social and wallet verification/bootstrap scripts. This is a significant deployment-readiness gate: a successful image build alone is not sufficient; the service must complete prestart and then pass `/ready`. The CI workflow `.github/workflows/fynx-backend-ci.yml` separately runs extensive PostgreSQL-backed and mocked provider tests, including Gemini provider tests without requiring a live key.

The backend CI workflow runs on pull requests only when `backend/**`, `scripts/**`, or the workflow file changes; it also supports manual `workflow_dispatch`. This documentation-only PR does not match its path filters, so no backend CI run is expected from this PR. The connected GitHub tools do not expose a workflow-dispatch action; backend CI must be manually dispatched by an authorized repository user or run on the next relevant backend PR. The workflow's optional live production probe is disabled by default and should remain opt-in.

`docs/FYNX_MEDIA_STORAGE_ROLLOUT.md` is not present on the canonical integration branch (404). A default-branch code-search result shows an older copy dated 2026-11-08 with a historical Railway snapshot; do not treat that stale document as current canonical evidence.

## PR #597 — Neon endpoint proposal
PR #597 is open and proposes changing Android's default API URL and production reachability verifier from the Railway service URL to a deployed Neon Function URL. It also migrates saved Railway endpoints to Neon.

This conflicts with the owner's explicit Stage 3 decision: Railway remains the primary FYNX application backend; Neon remains PostgreSQL and object storage. Do not merge/adopt the Android endpoint cutover as the production default under this decision. Keep the Neon Function as an alternative/experiment unless the owner explicitly reverses the decision. Do not change login or introduce a second backend client. Once Railway has a verified canonical deployment, Android defaults and backend probes must align with that Railway host.

## Deployment attempt and current external blocker

The 11-change Railway patch was committed on 2026-11-26. Railway then returned this explicit account-level error when asked to reconnect the same GitHub repository/branch to trigger a build: “Your trial has expired. Please select a plan to continue using Railway.” The service configuration reports RAILPACK and the repository/root, but it does not expose a source branch in the current service description. The attempted source reconnection to the canonical branch was rejected by Railway because the trial expired. The latest deployment remains FAILED deployment ad50abc2-a52b-40e8-aad1-8d3de1330e08 from 2026-11-03. Its exact log shows Railpack 0.43.0 loading a generated `railpack-plan.json`, then failing at `[railpack] hash used secrets` with `failed to solve: secret FYNX not found`. The GitHub source tree at that exact failed commit (0498632d1b72bea8aac5610a6778b75417b94af9) does not contain `railpack-plan.json`, `backend/railpack-plan.json`, `railway.toml`, `backend/railway.toml`, or `backend/railpack.json`; the plan is therefore not a committed file at those paths. This narrows the failing reference to generated plan/build metadata or Railway-side configuration, but does not identify which one. Do not create a secret named FYNX. No new build/deployment was created. Railway account-plan renewal or upgrade requires the owner's decision; no payment or upgrade was made.

## Backend engineering readiness gate

The canonical backend's previous `/ready` endpoint proved only that PostgreSQL answered `SELECT 1`. It could return HTTP 200 even when `JWT_SECRET` was absent or the selected object-storage provider was structurally misconfigured. The backend now uses a shared, secret-safe readiness assessment:

- **Database:** requires a successful `SELECT 1` from the shared pool.
- **Authentication:** requires `JWT_SECRET` to be present; the value is never returned.
- **Media storage:** requires the selected provider's configuration to pass the existing adapter's structural validation. The response reports only the provider name and a status, never credentials or endpoints containing credentials.
- A missing or unavailable core dependency returns HTTP 503. A ready response returns HTTP 200.

The assessment is exercised from `verify-runtime-bootstrap.mjs`, which is already part of the backend `prestart` chain and Backend CI. It tests the all-green case and independent database, auth and storage failure cases. This prevents Railway from being marked ready when core authenticated media journeys cannot start.

**Boundary:** the storage check validates configuration shape, not live Neon S3 connectivity. After Railway returns, still run a safe test-object upload, read, and delete. Gemini, email and Paystack are intentionally not hard readiness dependencies: they must remain independently fail-closed while the core API is usable.


### CI compatibility correction — 2026-11-27

The initial Backend CI run on readiness-gate commit `842d0bac800fc70bea6e05ccbd1ed2a5f2d81605` passed the new startup verifier (including login preservation and the new dependency-readiness helper cases), syntax checks, database-pool, Gemini, Wallet, and Marketplace checks through Stage 15D batch 2. It then failed at the older `verify-stage15d-reliability.mjs` check because that verifier asserted the former DB-only `/ready` implementation. A follow-up update attempted to replace that assertion, but a later documentation-tree write was based on an older tree snapshot and unintentionally restored the old verifier. Backend CI run `45945216209` therefore failed for the same stale assertion on head `78b863a468e27af557c0230f8ffd3723c868438e`. This exact branch-head regression is being corrected in the current grouped commit; do not treat the prior correction commit as effective until the corrected file is verified on the latest head and Backend CI passes.

## Remaining backend engineering preparation

| Gap | Current evidence | Prepared action / acceptance |
|---|---|---|
| Deterministic dependency install | `backend/package-lock.json` is now committed on the recovery branch (lockfileVersion 3); Node 24 generated it and a clean `npm ci --ignore-scripts --no-audit --no-fund` passed in the lockfile-bootstrap workflow. The bootstrap workflow removed itself after committing the lockfile. | Retain the manifest/lockfile pair together; switch install steps to `npm ci` only after the in-flight backend PRs (#611 and #613) have reconciled package.json changes, then rerun CI against the resulting exact merged manifest. |
| Runtime version parity | Backend CI explicitly uses Node 20; the historical successful Railway Railpack build selected Node 24.22.1. The backend lockfile was generated and clean-installed with Node 24. | Align CI and Railway to one supported Node major after package changes in PRs #611/#613 are consolidated; rerun the complete PostgreSQL-backed backend matrix on that exact version. |
| Cloudflare SFU integration | The canonical base does not yet contain it, but open PR #599 adds authenticated Node routes in `backend/cloudflareSfuRoutes.js` and registers them in `backend/server.js`. PR #599 is not merged; its branch currently diverges from canonical (59 commits ahead, 7 behind at the last comparison). The SFU adapter is an initial foundation, not live-certified. The Android call panel currently does not call `/api/calls/sfu/*`; the group call panel explicitly describes a four-person peer-to-peer call. | Do not build another SFU implementation. Coordinate PR #599 with overlapping call transport work in PR #589. Bring #599 onto the latest canonical branch, complete native Android SDP/publish/subscribe integration, serialize per-session mutations, invoke proper Cloudflare track/session teardown, add mocked route/API tests for auth, partial failures, timeouts and concurrency, and verify the Cloudflare App Secret was rotated after prior exposure before deployment. Keep the existing four-person mesh as a labelled fallback until SFU runtime evidence passes. |
| Provider runtime reachability | Environment variable names exist, but values/connectivity remain unverified. | After deployment, independently probe Neon object storage, Gemini and any selected call provider. Never print secret values. |

## Safe deployment procedure
1. Confirm the exact project, environment and service IDs above.
2. Re-check canonical branch head and inspect the live Railway source settings. Explicitly verify the source branch is `integration/fynx-single-apk-20261013`; the current service description omits branch, so do not infer it from repository/root alone. Stop if source, root directory, builder, start command, health path, port or variable names differ from this record.
3. Preserve existing protected secrets. Never recreate sealed values, print credentials, or invent FYNX.
4. After Railway access is restored, explicitly connect/select the canonical GitHub branch if it is not already selected, then trigger a build with /backend as root and RAILPACK as builder. Confirm the build metadata records the canonical branch and exact commit SHA.
5. Inspect build logs first. If secret FYNX not found recurs, stop and capture the exact deployment ID/context. Inspect the generated build configuration and correct only the identified stale reference; do not add a dummy secret.
6. Require deployment status SUCCESS. Verify public GET /health and GET /ready return successful responses.
7. Verify readiness against the intended Neon branch using non-destructive probes.
8. Run authenticated API smoke tests with a dedicated test account and the existing login flow. Verify authorization and account isolation.
9. Verify Neon object storage using safe test-object upload/finalize/read/delete. Verify Cloudflare SFU configuration without initiating a billable session. Test Gemini with a minimal provider request. Keep payment and real-money flows outside this stage.
10. Run backend CI against the exact canonical commit. Record deployment ID, commit SHA, endpoint results, API test results and CI URL.

## Rollback procedure
- If a new deployment fails before activation, retain the previous active deployment.
- If a bad deployment becomes active, roll back through Railway to the last known-good deployment; verify its commit and /health + /ready.
- Do not roll back or reset Neon data/branches as part of an application rollback.
- If a migration ran, inspect migration state and use only its documented backward-compatible rollback. Never reset the populated Neon branch.
- If environment variables caused the failure, restore prior values using Railway's protected dashboard/history. Never copy secret values into GitHub or this document.
- Record failed deployment ID, cause, rollback target and post-rollback probes.

## Verification matrix
| Area | Status | Evidence / next step |
|---|---|---|
| Correct Railway project/service | VERIFIED | Correct of two same-named projects; second has no service |
| Repository/root | VERIFIED | greyhoundshop073-coder/FYNX; /backend |
| Canonical branch | NOT VERIFIED | Service description omits branch; source reconnect attempt was blocked by expired trial; historical deployments show main |
| RAILPACK | CONFIGURED — NOT DEPLOYED | Builder setting is now RAILPACK; trial expiry blocks build |
| DATABASE_URL | NAME PRESENT — VALUE UNVERIFIED | Variable name is present; value not inspected; validate by readiness |
| Neon S3 variables | NAMES PRESENT — VALUES UNVERIFIED | Five variable names present; bucket access unverified |
| Cloudflare SFU | NAMES PRESENT — VALUES UNVERIFIED | Two variable names present; connectivity unverified |
| Gemini | NAME PRESENT — UNVERIFIED | GEMINI_API_KEY exists by name only |
| FYNX secret failure | PARTIALLY ISOLATED | Failed main-branch RAILPACK 0.43.0 build; generated plan requests missing secret; source declaration not located |
| Canonical Railway deployment | CONDITIONAL — BLOCKED | Railway tool reports expired trial; account-plan action requires owner approval |
| /health and /ready | IMPLEMENTED — RUNTIME NOT VERIFIED | `/ready` now gates on DB reachability, JWT presence and selected media-provider configuration; exact commit CI and Railway runtime still pending |
| Authenticated APIs | NOT VERIFIED | Requires successful deployment and test account |
| Backend CI | RETEST REQUIRED AFTER LEGACY ASSERTION CORRECTION | Backend CI run `45945216209` on head `78b863a...` failed only at `verify-stage15d-reliability.mjs`, which still expected the old DB-only `/ready` route; all checks through Stage 15D batch 2 before it passed, including the new readiness verifier. The exact static assertion is being corrected on the latest branch head. |
| PR #597 | CONFLICT IDENTIFIED | Neon Android cutover conflicts with Railway-primary decision |

## Stage 3 exit criteria
Stage 3 remains CONDITIONAL — BLOCKED. Engineering readiness improvements now include `/ready` dependency gates and a committed npm lockfile with Node 24 clean-install evidence. Remaining off-Railway gaps are to persist/fix the legacy Stage 15D readiness assertion, align the Node runtime after package PRs #611/#613, and complete the already-started Cloudflare SFU work in PR #599 (not create a duplicate adapter). Immediate external blocker: Railway trial expired; source branch selection is also not verified.
