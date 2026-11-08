# FYNX media storage rollout

## Architecture

PostgreSQL remains the source of truth for FYNX users, media IDs, ownership, social visibility, Marketplace references and order evidence. Media bytes are separated from relational metadata through a provider adapter. The supported providers are:

- `storage_provider = 'postgres'`: existing `message_media.data BYTEA` records and the default for all environments until a provider is deliberately enabled.
- `storage_provider = 'neon'`: a private Neon Object Storage bucket. `object_key` points to the object and `data` is NULL for new object-backed rows.
- `storage_provider = 'r2'`: an optional private Cloudflare R2 bucket, retained as a future portability option.

Existing media IDs, MIME types, byte sizes and authorization checks remain unchanged. The API authorizes the requester before issuing a short-lived (120-second) signed GET URL. Object-backed reads and signed redirects are routed by each row's `storage_provider`, not by the active write setting. During a migration, FYNX can therefore write new uploads to one provider while continuing to read old objects from another. PostgreSQL rows continue through the bounded cache.

## First provider: Neon Object Storage

The existing Neon project is in `aws-us-east-2`, a supported Object Storage region. Its `fynx-testing` and `live-recovery-20261101` branches report Object Storage enabled, but the current inspection found no bucket on those branches. This code change does not create a bucket or activate a provider.

Neon requires S3 path-style addressing. The adapter sets `forcePathStyle: true` and reads the branch-specific credentials supplied by Neon:

- `AWS_ACCESS_KEY_ID`
- `AWS_SECRET_ACCESS_KEY`
- `AWS_ENDPOINT_URL_S3`
- `AWS_REGION`
- `FYNX_MEDIA_STORAGE_BUCKET` (the FYNX bucket name, e.g. `fynx-media`)

Configure `FYNX_MEDIA_STORAGE_PROVIDER=neon` only after a private bucket exists on the exact Neon branch backing the target backend environment and that same branch's endpoint/credentials are installed in the backend environment. Do not reuse a production endpoint or credentials on a testing environment, or vice versa. Never commit credentials or expose them to Android clients. For a migration that has both Neon and R2 objects, keep both providers' server-side credentials available during the transition; each media row selects the correct read adapter.

Private buckets are the correct default for user uploads and attachments. The API must continue to check FYNX ownership, visibility and conversation/listing permissions before creating signed read URLs. The application should not expose raw long-lived storage credentials or public object URLs.

## Optional future provider: Cloudflare R2

R2 remains supported by the same adapter for a later migration or capacity decision. Its configuration is separate:

- `FYNX_MEDIA_STORAGE_PROVIDER=r2`
- `FYNX_R2_ACCOUNT_ID`
- `FYNX_R2_ACCESS_KEY_ID`
- `FYNX_R2_SECRET_ACCESS_KEY`
- `FYNX_R2_BUCKET`

Use a private bucket and a bucket-scoped token with only the object permissions FYNX requires. Do not use a public `r2.dev` URL for private user media. Check account billing and expected operation costs before activating a paid provider.

## Safe rollout stages

1. Merge and deploy the provider adapter with `FYNX_MEDIA_STORAGE_PROVIDER=postgres`. This is the safe default and does not change where new media is stored.
2. Provision a private bucket on the isolated `fynx-testing` Neon branch only after the test environment and branch pairing are confirmed. Install that branch's storage credentials in the isolated backend environment; do not modify production.
3. Smoke-test image, video and audio uploads/downloads, signed URL expiry, private access denials, database insert failure cleanup and existing PostgreSQL media reads.
4. Enable Neon for new uploads on the isolated testing backend only after the smoke tests pass. Existing PostgreSQL records retain their IDs and continue to work.
5. Add direct-to-object signed uploads, image derivatives and asynchronous media processing as separate measured improvements. This foundation intentionally preserves the current base64 `POST /api/media` contract for existing Android builds.
6. Migrate old PostgreSQL BYTEA records in small resumable batches only after measuring actual demand and confirming the exact database/provider branch. Copy and checksum each object before updating its row; retain the original BYTEA during the first migration.
7. Keep both source and destination provider credentials available while a migration is in progress. The active provider setting controls new writes; row-level `storage_provider` controls reads and signed URLs. This permits staged conversion and rollback without temporarily making already-migrated files unreadable.\n8. Test rollback, interrupted batch recovery, and database/object consistency before any production rollout.

## Scaling and automation boundary

This adapter supports provider-aware reads during a mixed-provider transition; it does **not** yet automatically migrate objects when a storage threshold is reached. Automatic migration requires a separate worker, usage monitoring, idempotent jobs, checksums, retry state, alerts and a rollback plan. Thresholds must be based on actual capacity and measured cost, not an arbitrary user count. Paid upgrades or production provider changes require explicit approval.

## Current safety boundary

This change does not provision a bucket, add backend credentials, change Railway variables, migrate existing bytes, delete objects, modify production data, deploy a backend, or change the canonical Android branch. The default provider stays PostgreSQL.
