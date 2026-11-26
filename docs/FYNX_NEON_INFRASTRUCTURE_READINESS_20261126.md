# FYNX Neon Infrastructure Readiness Report

**Date:** 2026-11-26  
**Owner/workstream:** Chat — Neon Infrastructure  
**Repository:** `greyhoundshop073-coder/FYNX`  
**Canonical branch:** `integration/fynx-single-apk-20261013`  
**Canonical base inspected:** `4382023be9dfd51d43728231504ce8fe9950e125`  
**Feature branch:** `infra/fynx-neon-readiness-20261126`  
**Scope:** Read-only Neon, object-storage, Railway staged-configuration, and repository coordination audit.

## Executive result

The historical Neon project `patient-lab-43403708` is the data-bearing FYNX project. The best-preserved full-feature data set is on `live-recovery-20261101`; `fynx-dev-backend-20261122` is an exact schema and key-record-count clone of that recovery branch and is the appropriate isolated development/testing candidate. The default `production` branch is not an acceptable current FYNX data source: it has only 47 application tables and zero users, listings and orders. The separate reset project is also not a replacement.

No database rows, schemas, branches, buckets, objects, credentials, Railway variables or deployments were modified during this audit.

**Readiness status: CONDITIONAL — BLOCKED.** Database lineage and schema/data comparisons are verified. The exact value/target of Railway's staged `DATABASE_URL` and the live S3 credential handshake cannot be verified through the connected Railway account because variable values are redacted. The Railway service is offline and its configuration changes remain staged, not live.

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

Observed service state:

- Service is `live-with-staged-changes`.
- Current runtime status is Offline; no running replica was reported.
- Latest deployment record is FAILED (2026-11-03); prior deployments are removed/failed.
- Current source configuration is the FYNX repository under `/backend`; the staged source branch is `integration/fynx-single-apk-20261013`.
- Builder change from NIXPACKS to RAILPACK is staged.
- Pending patch `0e560e21-bc20-4134-bd0a-0781b3fb1eea` is STAGED with 11 changes. It has not been committed and no new deployment was triggered.

The staged patch adds `DATABASE_URL` and the Neon media configuration variables:
`FYNX_MEDIA_STORAGE_PROVIDER`, `FYNX_NEON_S3_ENDPOINT`, `FYNX_NEON_S3_REGION`, `FYNX_NEON_S3_ACCESS_KEY_ID`, `FYNX_NEON_S3_SECRET_ACCESS_KEY`, and `FYNX_NEON_STORAGE_BUCKET`, plus two Cloudflare SFU variables. The staged change list confirms these values are additions, not currently live values.

**Exact Railway DATABASE_URL target: NOT VERIFIED.** The connected Railway account returns variable names only and marks values as redacted. The actual staged hostname, database name and branch therefore cannot be inspected. A previous PR #580 handoff says the staged Railway URL was intended to target `fynx-dev-backend-20261122`, but that historical note is not proof of the current encrypted value. Do not claim the target has been verified. The staged S3 endpoint and credentials are similarly redacted and cannot be confirmed to match the dev branch.

Before committing the Railway patch, an owner with Railway dashboard access must verify, without sharing credentials:
1. The staged `DATABASE_URL` host identifies the dev branch endpoint `ep-mute-sun-b5qe9rlr` in project `patient-lab-43403708`; the database path is the intended database (`neondb` unless the app's configured database name differs).
2. The hostname is the intended direct or pooled endpoint for the backend runtime. Use direct connections for migrations/dumps; use the Neon pooler hostname for high-concurrency application traffic only after confirming it is enabled and compatible with the runtime.
3. `FYNX_NEON_S3_ENDPOINT` exactly matches the dev branch endpoint above; region is `us-east-2`; bucket is `fynx-media`; provider is `neon`.
4. The access key and secret are the matching dev-scoped credential, not the revoked recovery credential.
5. Values are staged only in the Railway testing environment and remain absent from source control.

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

Canonical head inspected: `4382023be9dfd51d43728231504ce8fe9950e125`. The canonical commit's combined status endpoint returned no status entries; this is not proof of a green build.

Relevant open PRs:

- **PR #597 — fix(neon): connect canonical FYNX Android client to Neon backend.** Its description says the Android client defaults to a Neon Function on `fynx-testing`. Its branch currently compares as 12 commits ahead and 11 behind canonical. It edits `FynxBackendClient.kt`, Android tests, a workflow, reachability checks and `docs/FYNX_NEON_MEDIA_STORAGE.md`.
- **PR #580 — Consolidate FYNX runtime, Chat/Groups reliability and Neon media storage.** Its branch currently compares as 21 commits ahead and 18 behind canonical. It edits `backend/mediaStorage.js`, `backend/server.js`, `FynxBackendClient.kt`, storage tests, runtime checks and the Neon media-storage documentation.
- Both PRs are open and diverged from the current canonical head. Their descriptions point to different environments: #597 describes a Neon Function on `fynx-testing`; #580's historical Railway staging handoff describes the dev branch. This runtime/database target discrepancy must be reconciled by the owners before either deployment path is treated as canonical. Do not merge either PR wholesale and do not duplicate their backend or Android changes.

The existing `docs/FYNX_NEON_MEDIA_STORAGE.md` contains an older 23 November snapshot (including older table and listing counts). It is being edited by #597 and #580, so this infrastructure workstream deliberately leaves it untouched and records this fresh audit in a separate report.

## 8. Safe reconnection and migration sequence

1. Preserve `live-recovery-20261101`; treat it as the recovery/reference dataset. Keep `fynx-dev-backend-20261122` as the isolated development target.
2. Reconcile the backend runtime decision: Railway testing service versus Neon Function, and ensure Android, backend deployment, database and media bucket all point to one agreed testing environment. Do not introduce another backend.
3. Have the Railway owner inspect the staged `DATABASE_URL` host/database and the six Neon media variable values without disclosing secrets. Confirm the dev branch host and branch-scoped credential.
4. Verify the current Railway patch and the exact deployment source. Only after the owner confirms the values and the backend PR is approved should the staged patch be committed. Then observe a terminal successful deployment and a healthy readiness probe; staged does not mean live.
5. Run read-only SQL health/schema checks against dev, then authenticated API smoke tests using dedicated test accounts. Keep production and recovery out of write tests.
6. Verify a new test media upload end-to-end on dev: upload intent, signed PUT, HEAD validation, finalize, authorized GET, expiry, and deletion/cleanup. Do not forward FYNX bearer tokens to object storage.
7. Plan the existing PostgreSQL-inline media backfill separately, with idempotency, stable media IDs, checksum/size verification, retry/resume, reference preservation and rollback to the original BYTEA payload. Do not delete inline payloads during the first migration.
8. Before any production cutover, obtain owner approval, take and verify a restorable backup/snapshot, create a production-ready branch from the approved schema/data baseline, apply versioned migrations, verify data and authorization, then test the canonical app against that environment. Do not point production at the current empty default branch.
9. Record deployed commit, environment, branch ID, schema fingerprint, smoke-test evidence, object counts and recovery procedure. Only mark live verified after real requests and media operations pass.

## 9. Verification matrix

| Requirement | Result | Evidence / remaining action |
|---|---|---|
| Identify authoritative data-bearing project | Verified | Historical project and branch lineage inspected |
| Compare schema and table counts | Verified | 88 tables / 799 columns / 246 indexes on recovery and dev; matching fingerprints |
| Compare important record counts | Verified | Exact read-only counts listed above |
| Preserve recovery branch and data | Verified | No writes or branch mutations performed |
| Identify Railway staged DATABASE_URL target | Blocked | Values redacted by Railway connector; owner must inspect staged host/database |
| Confirm Neon Object Storage enabled | Verified | Enabled on testing/recovery/dev in us-east-2 |
| Confirm private bucket and object counts | Verified | Private `fynx-media`; zero objects on testing/recovery/dev |
| Verify active branch-scoped storage credential exists | Verified | Dev credential metadata and scopes listed; expires 2027-02-21 |
| Verify staged S3 variables match credential/endpoint | Blocked | Railway values redacted |
| Verify actual S3 PUT/HEAD/GET/DELETE | Not run | Requires live credential handshake; no test object written |
| Review compute/autoscaling/pooling | Partially verified | Compute ranges and idle/scale-to-zero state inspected; pooler hostname not exercised |
| Review indexes/query/locks | Verified read-only | Sizes, scan counters, unused-index candidates, locks and long-running queries inspected |
| Verify scheduled snapshots/backup | Blocked / deficient | No snapshots or schedule; no branches protected; Neon rejected protection with HTTP 422 due plan limit |
| Live Railway service readiness | Blocked | Service Offline; last deployment FAILED; patch still staged |
| Production cutover | Not authorised / not performed | Requires owner approval and a verified backup |

## 10. Explicit non-actions

No production data was written. No data was copied or migrated. No branch was reset, deleted or protected. No bucket or object was created, deleted or made public. No credentials were created, rotated, revoked or exposed. No Railway staged patch was committed. No deployment or paid upgrade was triggered.

**Stage 1 conclusion:** The data lineage is sufficiently established to recommend recovery as the preservation reference and the matching dev branch as the testing target. Stage 1 is not fully closed until the owner verifies the encrypted Railway target and staged storage values, the backend/runtime target discrepancy is resolved, live media access is exercised, and a reliable backup/recovery schedule is agreed.
