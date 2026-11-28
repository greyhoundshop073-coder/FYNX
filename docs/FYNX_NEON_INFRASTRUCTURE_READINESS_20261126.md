# FYNX Neon Infrastructure Readiness Report

**Date:** 2026-11-27  
**Owner/workstream:** Chat — Neon Infrastructure  
**Repository:** `greyhoundshop073-coder/FYNX`  
**Canonical branch:** `integration/fynx-single-apk-20261013`  
**Canonical base inspected:** `4382023be9dfd51d43728231504ce8fe9950e125`  
**Feature branch:** `infra/fynx-neon-readiness-20261126`  
**Scope:** Neon database and object-storage engineering readiness, deployment-path verification, safe recovery planning, and repository coordination.

## Executive result

The historical Neon project `patient-lab-43403708` is the data-bearing FYNX project. The best-preserved full-feature data set is on `live-recovery-20261101`; `fynx-dev-backend-20261122` is an exact schema and key-record-count clone of that recovery branch and is the appropriate isolated development/testing candidate. The default `production` branch is not an acceptable current FYNX data source: it has only 47 application tables and zero users, listings and orders. The separate reset project is also not a replacement.

No database rows, schemas, branches, buckets, objects, credentials, Railway variables or deployments were modified during this audit.

**Readiness status: ENGINEERING PREPARATION COMPLETE; LIVE MEDIA AND RECOVERY GATES REMAIN.** The active Neon Function is deployed, and live `/health` and `/ready` probes both confirm that the database is ready. The backend already contains the provider-neutral PostgreSQL/Neon/R2 media adapter, signed upload-intent/finalization flow, object verification, protected retrieval, cleanup and mocked regression tests. Neon Object Storage is enabled with a private bucket, but its live signed object lifecycle has not yet been exercised. No user media has been migrated.

## 0. Current live deployment update — 2026-11-27

This section supersedes the earlier 2026-11-26 snapshot wherever service status differs.

- The active backend is the Neon Function `fynxapi` on project `patient-lab-43403708`, branch `fynx-testing` (`br-floral-shape-b5mbajl5`).
- Invocation URL: `https://br-floral-shape-b5mbajl5-fynxapi.compute.c-7.us-east-2.aws.neon.tech/`.
- Active deployment #3 completed at `2026-11-27T04:57:31.450316Z`; runtime `nodejs24`, memory 2048 MiB.
- Live read-only GET probes to `/health` and `/ready` both returned `{"ok":true,"service":"fynx-backend","database":"ready"}` on 2026-11-27. This verifies that the deployed Function can reach its configured Neon database.
- `/version` responds, but `commit`, `branch`, `environment` and `deploymentId` are all `unknown`. The endpoint currently reads Railway/`SOURCE_VERSION` variables, which are not populated for the Neon Function. This is a deployment-provenance/observability gap, not a database-readiness failure.
- The active testing branch currently has 81 public tables. Recovery and dev have 88 each and matching schema fingerprints. The testing branch includes core group, notification and media upload-intent tables, but differs from the full recovery/dev schema. Feature-specific routes may create some schemas lazily; `/health` alone does not certify every feature route.
- Testing-branch Object Storage is enabled in `us-east-2`; its endpoint is `https://br-floral-shape-b5mbajl5.storage.c-7.us-east-2.aws.neon.tech`. The `fynx-media` bucket is private and lists zero objects.
- The backend media adapter and local/mock regression tests are already present. The live signed PUT/HEAD/GET/DELETE lifecycle was not run because the available connection can inspect bucket metadata and presign URLs but cannot send an authenticated object-upload request.
- The Railway `fynx-backend-testing` service remains Offline and its historical image build failed with `secret FYNX not found` on noncanonical `main` commit `0498632d1b72bea8aac5610a6778b75417b94af9`. Railway is an inactive fallback; it is not the current active backend path. Do not maintain two live backends.
- PR #597 already targets this same Neon Function URL on `fynx-testing`, but it remains unmerged; the canonical Android default URL therefore still needs that existing PR's normal review/merge path.

### Final engineering boundary

**Engineering preparation for the Neon database/media integration is complete.** The database lineage is mapped, the preservation/dev branches are identified, the media adapter and contracts exist, the private bucket is configured, and the deployed testing Function is live with database readiness verified. The remaining items are live operational certification (real signed media transfer and authenticated feature journeys), deployment provenance, and owner-approved backup/restore readiness. Do not restart the Railway backend, migrate BYTEA media, reset branches, change production, or buy a higher Neon plan as part of this closeout.

## 1. Neon project and branch lineage

### Historical project

- Project: `patient-lab-43403708`
- Project ID: `patient-lab-43403708`
- Region: `aws-us-east-2` (`us-east-2`)
- PostgreSQL: 18
- Branch logical-size limit: 1 GiB
- Project history retention: 21,600 seconds (6 hours)
- Project-level default compute range: 0.25–2 CU; suspend timeout setting 0 (Neon plan default)
- Project public connections are not blocked; IP allow list is empty.

### Branch lineage

| Branch | Branch ID | Parent | State | Tables | Indexes | Logical size |
|---|---|---|---|---:|---:|---:|
| production | `br-patient-cloud-b5wuts3c` | root/default | ready | 47 | 103 | 34,381,824 bytes |
| fynx-testing | `br-floral-shape-b5mbajl5` | production | ready | 81 | 223 | 80,633,856 bytes |
| live-recovery-20261101 | `br-soft-butterfly-b5ddas0u` | fynx-testing | ready | 88 | 246 | 96,739,328 bytes |
| fynx-dev-backend-20261122 | `br-rough-voice-b5e21c2o` | live-recovery-20261101 | ready | 88 | 246 | 96,739,328 bytes |

All four branches are currently reported as unprotected. Do not reset, delete, rebase or overwrite the recovery branch.

### Separate reset project

- Project: `fynx-dev-backend-reset-20261122`
- Project ID: `spring-mode-34864088`
- Its default branch is `fynx-dev` (`br-little-cherry-befotl0z`).
- It has 12 public application tables and no `fynx-media` bucket.
- It is a separate, reduced-schema reset project, not the authoritative FYNX database.

The named project `fynx-dev-backend-20261122` was not found as a separate Neon project; it is a branch in `patient-lab-43403708`.

## 2. Schema and data comparison

Read-only SQL was executed against the actual branches. The recovery and development branches have:

- 88 public application tables each.
- 799 public columns each.
- 246 public indexes each.
- Identical ordered public table-name sets.
- Identical column-definition fingerprint: `2b8b496607c75d6d50d7a40f6d9121c8`.
- Identical index-definition fingerprint: `1736095bc8f8d16926be77b30f577a33`.

Exact row counts were checked for the following tables:

| Table | fynx-testing | recovery | dev clone |
|---|---:|---:|---:|
| users | 239 | 241 | 241 |
| marketplace_listings | 11 | 11 | 11 |
| marketplace_orders | 4 | 5 | 5 |
| messages | 58 | 59 | 59 |
| message_media | 148 | 153 | 153 |
| social_posts | 39 | 41 | 41 |
| statuses | 8 | 9 | 9 |
| fynx_groups | — | 55 | 55 |
| fynx_group_members | — | 55 | 55 |
| fynx_group_messages | — | 160 | 160 |
| notification_devices | — | 103 | 103 |
| buyer_wallets | — | 2 | 2 |
| buyer_wallet_transactions | — | 0 | 0 |

The production branch has zero users, zero Marketplace listings and zero Marketplace orders. It also has fewer tables and lacks several full-feature tables; do not use it as the backend target for the current app.

**Conclusion:** recovery is the preservation/reference branch; dev is its matching development clone. The older `fynx-testing` branch has less complete schema and fewer records. No record migration is needed to make the dev clone match the recovery branch at the time of this inspection.

## 3. Media storage findings

Neon Object Storage is enabled in `us-east-2` on the testing, recovery and dev branches. Each has a private `fynx-media` bucket:

| Branch | Bucket | Access | Objects returned by listing |
|---|---|---|---:|
| fynx-testing | fynx-media | private | 0 |
| live-recovery-20261101 | fynx-media | private | 0 |
| fynx-dev-backend-20261122 | fynx-media | private | 0 |
| production | no bucket listed | — | — |
| reset project / fynx-dev | no bucket listed | — | — |

Expected dev branch S3 endpoint (derived from the live Neon branch storage metadata):
`https://br-rough-voice-b5e21c2o.storage.c-7.us-east-2.aws.neon.tech`

The recovery and dev `message_media` tables each contain 153 rows representing 54,811,970 declared bytes. All 153 rows have `storage_provider='postgres'`, inline `data BYTEA`, and no `object_key`. This means the current media records inspected are still stored in PostgreSQL, not in Neon Object Storage. The empty bucket is not proof that media is lost; it indicates that the object-storage bucket has not yet been populated by a media backfill or new Neon-backed uploads.

### Storage credentials

- The dev branch has a branch-scoped credential named `FYNX Railway testing media 2026-11-23`.
- It has `storage:read` and `storage:write` scopes and expires on 2027-02-21.
- Its short token identifier is `760eb30d952a`; no secret was retrieved or exposed.
- Neon reports no `last_used_at` value for this credential, so successful S3 use is not demonstrated.
- The recovery branch's old `FYNX Railway recovery media` credential was revoked on 2026-11-14.
- Default object-storage credentials exist on the branches, but the staged Railway setup is intended to use the dev-scoped credential.

The bucket metadata/listing calls succeeded, but an authenticated S3 PUT/HEAD/GET/DELETE round trip was not performed. No test object was written.

### Required media migration caution

Do not bulk-copy the 153 PostgreSQL media payloads yet. First verify the new backend's upload/finalize/retrieve path on dev, then plan a resumable, idempotent backfill that preserves existing media IDs and ownership, verifies object size/MIME/checksum and references, and only marks a row as object-backed after successful object verification. Keep the PostgreSQL payload as rollback source until every reference and retrieval path is verified. This audit did not perform a media migration.

## 4. Railway staged configuration and live state

Railway project: `FYNX-Isolated-Testing`  
Project ID: `807bb8e2-00f4-4f2c-98cb-e393993f510a`  
Environment: `testing`  
Environment ID: `51b3423c-ef07-48d8-8c1c-e8dca91d3b81`  
Service: `fynx-backend-testing`  
Service ID: `aa4eb74c-8907-400a-bfc3-6f6d1cda03b7`

Observed service state (rechecked 2026-11-26):

- Service resource state is `live` (configuration exists), but Railway environment health reports runtime `offline` with no active deployments/replicas.
- Latest deployment `ad50abc2-a52b-40e8-aad1-8d3de1330e08` is FAILED, created 2026-11-03.
- Deployment source was repository `greyhoundshop073-coder/FYNX`, branch `main`, commit `0498632d1b72bea8aac5610a6778b75417b94af9`, root directory `/backend` — not the canonical integration branch.
- Builder is currently `RAILPACK`; start command `npm start`; readiness path `/ready`; service domain `fynx-backend-testing-testing.up.railway.app` targets port 8080.
- The earlier staged patch `0e560e21-bc20-4134-bd0a-0781b3fb1eea` is no longer present: current `staged=null`, with no pending environment work. This is a state change, not evidence that the service is healthy.
- `DATABASE_URL`, `FYNX_MEDIA_STORAGE_PROVIDER`, `FYNX_NEON_S3_ENDPOINT`, `FYNX_NEON_S3_REGION`, `FYNX_NEON_S3_ACCESS_KEY_ID`, `FYNX_NEON_S3_SECRET_ACCESS_KEY`, and `FYNX_NEON_STORAGE_BUCKET` now appear in the service's live variable-name list. The connector redacts values; it cannot verify their contents or target.

**Exact Railway DATABASE_URL target: NOT VERIFIED.** The connected Railway account returns variable names only and marks values as redacted. The actual hostname, database name and branch cannot be inspected. A previous PR #580 handoff says the intended target was `fynx-dev-backend-20261122`, but that historical note is not proof of the current encrypted value. The live S3 endpoint and credentials are similarly redacted and cannot be confirmed to match the dev branch.

The 2026-11-03 failed build was investigated. Railway's diagnosis reports failure stage `BUILD_IMAGE`; build logs end with `secret FYNX not found`. The service's failed deployment was built from `main` commit `0498632d1b72bea8aac5610a6778b75417b94af9`, not the canonical integration branch. This is a Railway build-configuration/secret-reference failure, not evidence of a Neon SQL or media adapter failure. The correct secret value/purpose cannot be inferred safely. The owner must inspect Railway's build configuration for a stale or required build-secret reference named `FYNX`, remove the reference if obsolete, or set the intended value securely in Railway if genuinely required. Do not invent or commit a secret. Do not redeploy this `main`-branch source as a substitute for the canonical app/backend.

Before enabling/redeploying the Railway service, an owner with Railway dashboard access must verify, without sharing credentials:
1. The live `DATABASE_URL` host identifies the dev branch endpoint `ep-mute-sun-b5qe9rlr` in project `patient-lab-43403708`; the database path is the intended database (`neondb` unless the app's configured database name differs).
2. The hostname is the intended direct or pooled endpoint for the backend runtime. Use direct connections for migrations/dumps; use the Neon pooler hostname for high-concurrency application traffic only after confirming it is enabled and compatible with the runtime.
3. `FYNX_NEON_S3_ENDPOINT` exactly matches the dev branch endpoint above; region is `us-east-2`; bucket is `fynx-media`; provider is `neon`.
4. The access key and secret are the matching dev-scoped credential, not the revoked recovery credential.
5. Values are stored only in the Railway testing environment and remain absent from source control.

The current branch compute endpoint is `ep-mute-sun-b5qe9rlr`, read-write, idle, with 0.25–2 CU autoscaling limits and suspend timeout setting 0. Recovery endpoint is `ep-solitary-boat-b5jh9ym6`, read-write, idle, 0.25 CU minimum and maximum. The testing endpoint is `ep-proud-star-b54d600q`, read-write, idle, 0.25 CU minimum and maximum. Legacy endpoint metadata reports `pooler_enabled=false`; confirm the actual `-pooler` hostname/availability before relying on pooling.

## 5. Backups, recovery and protection

- Project history retention is 6 hours.
- No project snapshots were returned by the snapshot listing.
- No automatic snapshot schedule was returned for recovery or the existing backup branch.
- Existing branch `backup-live-recovery-20261114-pre-backend-repair` is ready and descends from recovery, but it is not protected.
- The recovery, dev, production, testing and existing backup branches all report `protected=false`.
- An attempt to protect `live-recovery-20261101` through Neon was rejected with HTTP 422: the current plan has reached its maximum number of protected branches. Inspection confirms there are currently zero protected branches, so the plan appears to permit no protected branches at this tier.

The existing backup branch is useful as a historical clone, but it is not a substitute for a verified scheduled backup or protected recovery process. The account's current plan blocks branch protection. Do not unprotect/delete anything to work around this, and do not upgrade without owner approval. Before any production migration, reset, bulk media backfill or schema-changing operation, establish an owner-approved snapshot/backup and test restoration in an isolated branch. Do not change retention or buy/upgrade infrastructure without approval.

## 6. Indexes, query and compute observations

Read-only diagnostics on recovery:

- Table sizes: `message_media` is the largest table at approximately 54 MB; the next largest application tables are small (users approximately 232 kB).
- Index sizes were enumerated; the largest individual index reported was 48 kB.
- The unused-index diagnostic returned indexes with fewer than 50 recorded scans, including some zero-scan candidates. This is not evidence that they are safe to drop; several may be new or low-volume. No indexes were removed or altered.
- Sequential scan counters are present on several frequently queried tables. Counters alone do not establish a missing-index defect; inspect actual query plans and representative workload before tuning.
- No locks or queries running longer than five minutes were returned at inspection time.

Neon compute autoscaling is configured at the endpoint level; idle state is expected with scale-to-zero. First-query cold-start latency should be measured in a representative backend test. No compute size, timeout, pooling or project settings were changed.

## 7. Existing repository work and overlap

Canonical base inspected: `4382023be9dfd51d43728231504ce8fe9950e125`. On audit head `a32d963259ea8e6ac210e0669a5b46c4abbd20a8`, GitHub Actions runs #5377 (Chat Group Parity + Layout Safety), #1637 (Chat Final Closure), and #16316 (Android Build) all completed with `success`. These are repository CI results only; they do not prove live Railway/Neon/S3 operation.

Relevant open PRs:

- **PR #597 — fix(neon): connect canonical FYNX Android client to Neon backend.** Its description says the Android client defaults to a Neon Function on `fynx-testing`. Its branch currently compares as 12 commits ahead and 11 behind canonical. It edits `FynxBackendClient.kt`, Android tests, a workflow, reachability checks and `docs/FYNX_NEON_MEDIA_STORAGE.md`.
- **PR #580 — Consolidate FYNX runtime, Chat/Groups reliability and Neon media storage.** Its branch currently compares as 21 commits ahead and 18 behind canonical. It edits `backend/mediaStorage.js`, `backend/server.js`, `FynxBackendClient.kt`, storage tests, runtime checks and the Neon media-storage documentation.
- Both PRs are open and diverged from the current canonical head. Their descriptions point to different environments: #597 describes a Neon Function on `fynx-testing`; #580's historical Railway staging handoff describes the dev branch. This runtime/database target discrepancy must be reconciled by the owners before either deployment path is treated as canonical. Do not merge either PR wholesale and do not duplicate their backend or Android changes.

The existing `docs/FYNX_NEON_MEDIA_STORAGE.md` contains an older 23 November snapshot (including older table and listing counts). It is being edited by #597 and #580, so this infrastructure workstream deliberately leaves it untouched and records this fresh audit in a separate report.

## 8. Current reconnection and operational sequence

1. Keep the current Neon Function URL on `fynx-testing` as the active integration-test endpoint. The live `/health` and `/ready` probes already confirm database connectivity.
2. Complete the canonical Android endpoint change through existing PR #597 review. Do not create a second Android API client or a parallel backend.
3. Use a dedicated test account for authenticated smoke tests. Exercise existing login, one protected read route, media upload-intent, upload completion, authorized media retrieval and cleanup. Record request results and object counts.
4. Run the signed object-storage lifecycle test against the private `fynx-media` bucket: PUT, HEAD, finalization/promotion, authorized GET and deletion/cleanup. This audit could not perform the signed upload itself; do not mark media live-verified until it is exercised.
5. Keep PostgreSQL BYTEA media as the rollback source. Do not backfill or delete any inline media as part of this handoff.
6. Before production cutover or any destructive migration, create and verify an owner-approved restorable backup. The current plan rejected branch protection with HTTP 422; do not delete branches or pay for an upgrade without approval.
7. Keep `live-recovery-20261101` as the preservation/reference branch and `fynx-dev-backend-20261122` as the fuller-schema clone. Do not point production at the empty default branch or change the active testing target without a reviewed migration plan.
8. Leave the failed Railway service parked. Do not restart it unless the owner explicitly chooses Railway as the fallback and approves resolving its missing build-secret reference and canonical source configuration.

## 9. Final verification matrix

| Requirement | Result | Evidence / remaining action |
|---|---|---|
| Identify authoritative data-bearing Neon project | Verified | Historical project and branch lineage inspected |
| Preserve full recovery/reference data | Verified | Recovery branch untouched; matching 88-table dev clone exists |
| Active Neon Function deployment | Verified | `fynxapi` deployment #3 completed 2026-11-27 on `fynx-testing` |
| Live backend and database health | Verified | `/health` and `/ready` both returned `ok:true`, `database:"ready"` |
| Private Neon Object Storage bucket | Verified | `fynx-media` exists, private, region `us-east-2`; zero objects |
| Backend media-storage implementation | Implemented | Existing adapter supports PostgreSQL, Neon and R2; signed upload, object inspection/promotion, retrieval and cleanup contracts are present |
| Mocked media-storage regression coverage | Present in source | Existing tests cover provider config, signing, HEAD/COPY, retrieval, cleanup, bounds and error classification |
| Live signed object lifecycle | Not run | Requires a real signed PUT/HEAD/GET/DELETE round trip |
| Authenticated feature-level API parity | Not run in this workstream | Requires a dedicated test account and route-by-route smoke tests |
| Active testing-branch schema parity | Partial | 81 tables on `fynx-testing` vs 88 on recovery/dev; module-specific lazy schema creation and all feature routes not exercised |
| Deployment provenance | Partial | `/version` currently reports commit/branch/environment/deployment ID as `unknown` on Neon Functions |
| Snapshot/restore and protected recovery | Blocked / deficient | No snapshot schedule found; Neon rejected branch protection with HTTP 422 at current plan level |
| Railway fallback | Inactive | Service Offline; last build failed with `secret FYNX not found` on noncanonical `main`; not used by active Neon Function |
| Production cutover | Not authorised / not performed | Requires owner approval and verified restorable backup |

## 10. Explicit non-actions and conclusion

This workstream did not alter production data, change schema definitions, reset/delete branches, create/delete media objects, change credentials, edit Railway variables, deploy code, migrate BYTEA media, switch the canonical Android endpoint, or purchase/upgrade infrastructure.

**Stage 1 engineering preparation is complete.** The authoritative Neon project and safe branch roles are documented; the media integration contract and test coverage already exist; the private bucket and deployment configuration are positioned; and the live Neon Function is responding with its database ready. The remaining tasks are live-only certification (signed media lifecycle and authenticated route smoke tests), an observability improvement for Neon deployment provenance, and an owner-approved backup/restore process. PR #597 remains the existing Android endpoint cutover path. No new backend, duplicate endpoint, APK branch, or media migration is required by this infrastructure handoff.
