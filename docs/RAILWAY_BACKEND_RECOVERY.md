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
- Live config before pending patch: NIXPACKS builder; historical deployment metadata used main.
- Pending patch: STAGED, 11 changes, patch ID 0e560e21-bc20-4134-bd0a-0781b3fb1eea, updated 2026-11-26T08:18:42Z.
- Staged changes: source branch integration/fynx-single-apk-20261013; builder RAILPACK; encrypted DATABASE_URL; encrypted FYNX_MEDIA_STORAGE_PROVIDER; encrypted FYNX_NEON_S3_ACCESS_KEY_ID, FYNX_NEON_S3_ENDPOINT, FYNX_NEON_S3_REGION, FYNX_NEON_S3_SECRET_ACCESS_KEY, FYNX_NEON_STORAGE_BUCKET; encrypted CLOUDFLARE_SFU_APP_ID and CLOUDFLARE_SFU_APP_SECRET.

The variable names are verified, but their values are protected and were not read or exposed. Presence does not prove correctness or connectivity. Live variable names also include GEMINI_API_KEY, JWT_SECRET and PAYSTACK_SECRET_KEY. Gemini key presence is verified by name only; validity and runtime use are not verified.

## Original build failure — evidence and limitation
Failed deployment ID: ad50abc2-a52b-40e8-aad1-8d3de1330e08, created 2026-11-03T18:45:13Z. Deployment diagnosis records source commit 0498632d1b72bea8aac5610a6778b75417b94af9, branch main, root /backend, builder NIXPACKS and failure stage BUILD_IMAGE.

Decisive log:
~~~text
[railpack] hash used secrets
error: secret FYNX not found
Build Failed: failed to solve: secret FYNX not found
~~~

This was an old main/NIXPACKS deployment, not the currently staged canonical-branch/RAILPACK configuration. The current accessible service config and checked canonical repository paths do not reveal a build-secret declaration named FYNX. Checked candidate paths included root/backend Railway, Nixpacks, Railpack and Dockerfile configuration names; none exists on the canonical branch. The failed build log shows Railway loading a generated railpack-plan.json, which is a build artifact, not a committed file.

**Exact-reference status:** the historical builder attempted to resolve a build secret named FYNX, but its originating declaration (historical Railway build configuration versus generated build plan/source metadata) has not been isolated. Do not claim a specific source file references it. Do not create a placeholder secret. Treat this as a historical NIXPACKS build-secret resolution failure until a current canonical RAILPACK build proves otherwise.

A separate 2026-11-03 deployment built an image and passed Railway's /ready healthcheck, but was later marked REMOVED and its metadata points to main. It is not proof of a live canonical backend.

## PR #597 — Neon endpoint proposal
PR #597 is open and proposes changing Android's default API URL and production reachability verifier from the Railway service URL to a deployed Neon Function URL. It also migrates saved Railway endpoints to Neon.

This conflicts with the owner's explicit Stage 3 decision: Railway remains the primary FYNX application backend; Neon remains PostgreSQL and object storage. Do not merge/adopt the Android endpoint cutover as the production default under this decision. Keep the Neon Function as an alternative/experiment unless the owner explicitly reverses the decision. Do not change login or introduce a second backend client. Once Railway has a verified canonical deployment, Android defaults and backend probes must align with that Railway host.

## Safe deployment procedure
1. Confirm the exact project, environment and service IDs above.
2. Re-check canonical branch head and inspect the pending patch. Stop if source, root directory, builder, start command, health path, port or variable names differ from this record.
3. Preserve existing protected secrets. Never recreate sealed values, print credentials, or invent FYNX.
4. Commit the owner-authorized staged patch. This triggers a canonical-branch build with /backend as root and RAILPACK as builder.
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
| Canonical branch | STAGED — NOT LIVE | Pending patch sets canonical branch |
| RAILPACK | STAGED — NOT LIVE | Pending patch changes NIXPACKS to RAILPACK |
| DATABASE_URL | PRESENT — VALUE PROTECTED | Encrypted staged name; validate by readiness |
| Neon S3 variables | PRESENT — VALUE PROTECTED | Five encrypted names; bucket access unverified |
| Cloudflare SFU | PRESENT — VALUE PROTECTED | Two encrypted names; connectivity unverified |
| Gemini | NAME PRESENT — UNVERIFIED | GEMINI_API_KEY exists by name only |
| FYNX secret failure | PARTIALLY ISOLATED | Old NIXPACKS deployment; originating declaration not located |
| Canonical Railway deployment | NOT VERIFIED | Requires successful deployment |
| /health and /ready | NOT VERIFIED | No current canonical success to probe |
| Authenticated APIs | NOT VERIFIED | Requires successful deployment and test account |
| Backend CI | NOT VERIFIED IN THIS AUDIT | Must verify exact canonical commit |
| PR #597 | CONFLICT IDENTIFIED | Neon Android cutover conflicts with Railway-primary decision |

## Stage 3 exit criteria
Stage 3 remains CONDITIONAL — BLOCKED until Railway reports SUCCESS for the canonical branch and the exact deployed commit passes health, readiness, authenticated API, database, storage/provider checks and backend CI.
