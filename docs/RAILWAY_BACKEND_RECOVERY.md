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

The initial Backend CI run on readiness-gate commit `842d0bac800fc70bea6e05ccbd1ed2a5f2d81605` exposed a stale DB-only `/ready` assertion, and subsequent startup smoke runs found and corrected three additional stale/brittle guards: media route extraction now tolerates the closing brace without requiring a blank separator; CPC runtime verification now asserts awaited session-aware authentication rather than the obsolete non-awaited form; and the Home comment reaction bootstrap uses the shared `String.fromCharCode(10)` route marker construction expected by its verifier. **Final current-head verification passed:** Backend CI run [#45947540256](https://github.com/greyhoundshop073-coder/FYNX/actions/runs/45947540256), exact current PR head `aff51d885c9a626f9f16981d4c6a62fa528205fa`, completed successfully with 103 successful steps and zero failed steps. The exact `npm start` smoke executed the complete `prestart` sequence, started the backend against isolated PostgreSQL 16, verified `/ready` returned the required database/authentication/media statuses and confirmed `/health` succeeded. Only the optional live production reachability probe was skipped by design. This validates CI/startup composition, not a live Railway deployment or real Neon/Cloudflare/Gemini connectivity.

### Exact startup path coverage

Backend CI now executes the actual Railway `npm start` command against its isolated PostgreSQL 16 service, with a newly generated per-run CI-only JWT value and the PostgreSQL media provider. Because npm runs `prestart` before `start`, this exercises the complete assembled preflight/bootstrap chain, starts the HTTP server, and requires `/ready` to report DB, authentication and media readiness before confirming `/health`. This is a local CI smoke check; it does not test Railway networking, production secrets, Neon credentials or Cloudflare connectivity.

## Remaining backend engineering preparation

| Gap | Current evidence | Prepared action / acceptance |
|---|---|---|
| Deterministic dependency install | `backend/package-lock.json` (lockfileVersion 3) is committed. Node 24 generated it and `npm ci --ignore-scripts --no-audit --no-fund` passed in the lockfile-bootstrap workflow. Backend CI now uses `npm ci --no-audit --no-fund` and keys the npm cache to the lockfile. | Keep package manifest/lockfile changes in sync when other open backend PRs (#599, #611, #613) are merged; rerun `npm ci` and exact-head Backend CI after dependency changes. |
| Runtime version parity | Backend CI now runs Node 24 and installs with `npm ci` using `backend/package-lock.json`; the historical successful Railway Railpack build selected Node 24.22.1. Exact-head Backend CI passed after this alignment. | Keep the Node 24 major consistent between CI and Railway; verify the exact Railpack Node patch version during the next successful Railway build. |
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
| Backend CI | PASS — exact current PR head `aff51d885c9a626f9f16981d4c6a62fa528205fa` | [Run #45947540256](https://github.com/greyhoundshop073-coder/FYNX/actions/runs/45947540256): 103 successful steps, zero failed; Node 24 + `npm ci`; full `npm start`/`prestart` smoke passed; `/ready` and `/health` passed against isolated PostgreSQL 16. Only optional live production probe skipped as configured. No Railway live deployment is implied. |
| PR #597 | CONFLICT IDENTIFIED | Neon Android cutover conflicts with Railway-primary decision |

## Stage 3 exit criteria
Stage 3 remains CONDITIONAL — BLOCKED by the remaining Railway account/source and live-provider work, not by the offline backend engineering baseline. Verified offline: `/ready` gates on PostgreSQL reachability, JWT presence and selected media-adapter configuration; backend dependencies are locked; Node 24 `npm ci` is deterministic; exact-head Backend CI is green; and the real `npm start`/`prestart` chain reaches successful `/ready` and `/health` using the isolated CI Postgres service. Still live-only: restore Railway access, verify the connected canonical branch and current `secret FYNX` config origin, deploy, verify production DB/storage (including actual object upload/read/delete), authenticated APIs, Gemini, approved call/SFU behavior, and confirm Cloudflare secret rotation. Coordinate in-flight PRs #599/#589 and package-script additions #611/#613 before integration; do not duplicate their work.


## Current environment inventory and exact-head evidence — 2026-11-27

A fresh Railway service description returned the following **19 variable names** for the testing environment. Values were not read, copied, tested or exposed:

- Core/runtime: `DATABASE_URL`, `JWT_SECRET`, `NODE_ENV`, `FYNX_MEDIA_STORAGE_PROVIDER`.
- Neon S3-compatible object storage: `FYNX_NEON_S3_ACCESS_KEY_ID`, `FYNX_NEON_S3_ENDPOINT`, `FYNX_NEON_S3_REGION`, `FYNX_NEON_S3_SECRET_ACCESS_KEY`, `FYNX_NEON_STORAGE_BUCKET`.
- Cloudflare calls: `CLOUDFLARE_SFU_APP_ID`, `CLOUDFLARE_SFU_APP_SECRET`.
- Gemini: `GEMINI_API_KEY`.
- Wallet / Paystack configuration: `FYNX_WALLET_CARD_FUNDING_ENABLED`, `FYNX_WALLET_FUNDING_MAX_AMOUNT`, `FYNX_WALLET_FUNDING_MIN_AMOUNT`, `FYNX_WALLET_TEST_MODE_ENABLED`, `PAYSTACK_SECRET_KEY`.
- Official account configuration: `FYNX_OFFICIAL_USERNAME`.
- Legacy builder variable: `NIXPACKS_NODE_VERSION`.

Presence of a variable name does not establish that its value is correct, current, valid or used by the deployed process. In particular, `NIXPACKS_NODE_VERSION` is a legacy-looking name while the current builder setting is RAILPACK; no deletion was made because its historical purpose and any external references must be checked after Railway access is restored. No secret value was read. The service API did not expose a sealed-variable-name list, so this report makes no claim about Railway's encryption metadata.

### Exact current PR-head CI refresh

- Current PR head: `62b695d97b99974bf13da58b14bcce916b4d5c62`; base: `9b2401759dfb7e15293ec898685fe86b4ba08a48`.
- Backend CI [run #45947766681](https://github.com/greyhoundshop073-coder/FYNX/actions/runs/45947766681) passed on that exact head: **103 successful steps, one intentionally skipped optional live-production probe, zero failed steps**.
- The Railway `npm start` smoke step passed, including the full `prestart` chain, isolated PostgreSQL 16, expected `/ready` fields and `/health`.
- The three additional workflows triggered on the documentation-only head (Android Build, Chat Group Parity + Layout Safety, Chat Final Closure) also concluded successfully. These checks do not certify Railway or provider runtime.
- Historical Railway build logs were retrieved again for failed deployment `ad50abc2-a52b-40e8-aad1-8d3de1330e08`. They confirm Railpack 0.43.0 loaded generated `railpack-plan.json`, then failed in `[railpack] hash used secrets` with `secret FYNX not found`. No accessible log field identifies the originating declaration, so the source remains unresolved.

### Adjacent open-PR integration watch

- PR #599 (calls/SFU) is 60 commits ahead and 7 behind canonical at this comparison; its Backend CI and chat workflows passed, while its Android build was still in progress. It overlaps call transport owned by #589; do not duplicate it. Rebase and certify before integration.
- PR #611 (AI usage/voice) is 11 commits ahead and 2 behind; its observed Backend CI, Android Build and chat workflows passed on its then-current head. Its package scripts must be reconciled with the lockfile and this startup smoke after rebase/integration.
- PR #613 (Marketplace/My Shop offline readiness) is 3 commits ahead and 0 behind. Its observed Backend CI passed, but Android instrumentation timed out at 45 minutes and Chat Final Closure failed after Maven Central returned HTTP 429 for dependencies. These are external dependency/runner failures in that PR's observed run, not failures in PR #608. Rerun exact-head workflows and confirm the cause is cleared before integration.

## Revised Stage 3 boundary

Offline repository engineering and the exact CI startup path are verified green on the current PR head. Stage 3 is **not production-complete** until Railway account access is restored and the canonical branch can be selected, a fresh RAILPACK build proves whether the historical `FYNX` secret error persists, and deployed health/readiness, authenticated APIs, Neon object upload/read/delete, Gemini and approved calling provider are tested. Those live-only checks cannot be truthfully completed while Railway deployment is blocked by the expired trial.
