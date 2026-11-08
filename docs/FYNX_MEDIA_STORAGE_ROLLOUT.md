# FYNX media storage rollout

## Architecture

PostgreSQL remains the source of truth for FYNX users, media IDs, ownership, social visibility, Marketplace references and order evidence. Media bytes are stored separately when an object provider is deliberately enabled.

Each `message_media` row keeps its existing ID, MIME type and byte size and records one of:

- `storage_provider = 'postgres'`: legacy `data BYTEA` is the source.
- `storage_provider = 'neon'`: `object_key` points to a private Neon Object Storage object.
- `storage_provider = 'r2'`: `object_key` points to a private Cloudflare R2 object.

The default remains `postgres`. The API performs its existing FYNX authorization query before issuing a short-lived signed GET URL. Legacy PostgreSQL rows remain readable. The adapter can read Neon and R2 rows side-by-side, which is required for safe provider migration and rollback.

## First provider: Neon Object Storage

The existing Neon project `patient-lab-43403708` is in `aws-us-east-2`, and the Neon API currently reports Object Storage enabled on `fynx-testing`, `live-recovery-20261101` and `production`. At the time of this investigation, none of those branches had a bucket. No bucket or credential has been created by this code change.

Neon requires path-style S3 addressing. The adapter sets `forcePathStyle: true` and uses branch-specific endpoint, region and credentials. Neon credentials are branch-scoped, so the credential and endpoint must belong to the exact branch used by the backend service (or an ancestor branch in that branch's lineage).

Set these variables only in the intended backend service environment; never commit credentials:

- `FYNX_MEDIA_STORAGE_PROVIDER=neon`
- `FYNX_NEON_S3_ENDPOINT` (branch-specific HTTPS S3 endpoint)
- `FYNX_NEON_S3_REGION` (for example, `us-east-2`)
- `FYNX_NEON_S3_ACCESS_KEY_ID` (Neon credential token ID)
- `FYNX_NEON_S3_SECRET_ACCESS_KEY` (Neon S3 secret)
- `FYNX_NEON_STORAGE_BUCKET` (private bucket name)

The Neon credential inventory already shows a default object-storage credential with `storage:read` and `storage:write` scopes anchored on the production parent branch. Neon documents that branch credentials are inherited by descendants. Do not rotate that shared credential. Before activation, confirm the exact Railway database branch and provision a dedicated testing credential if the existing secret is not available to the backend owner. Keep the bucket private. Store credentials only in the backend's secret environment, never in Android or source control. The current Railway testing service does not yet list any `FYNX_NEON_*` variables.

## Optional later provider: Cloudflare R2

R2 remains available as a later provider. The adapter retains the existing R2 environment contract:

- `FYNX_MEDIA_STORAGE_PROVIDER=r2`
- `FYNX_R2_ACCOUNT_ID`
- `FYNX_R2_ACCESS_KEY_ID`
- `FYNX_R2_SECRET_ACCESS_KEY`
- `FYNX_R2_BUCKET`

The provider abstraction keeps storage operations portable. R2 should only be activated after the account's billing and free allowance are checked.

## Safe activation and migration

1. Keep `FYNX_MEDIA_STORAGE_PROVIDER=postgres` until a private bucket, branch-scoped credential and isolated backend configuration have been verified.
2. Configure the isolated testing backend only after confirming its exact Neon branch. Smoke-test image, video and audio uploads, reads, signed URL expiry, privacy denials and cleanup.
3. Enable object storage for new testing uploads. Existing PostgreSQL rows retain their media IDs and continue to work.
4. Migrate legacy files in small resumable batches only after verifying the exact database target. Copy and checksum an object before updating its row. Keep the original BYTEA until the new object has been read back and verified.
5. Keep both provider credentials available during any provider transition. The reader uses each row's recorded provider, so a partially migrated dataset remains readable.
6. Add automated migration thresholds only after measuring storage use, operation volume, failure rates and actual provider costs. Thresholds should alert and prepare a migration; paid provisioning and production cutover must remain approval-gated.
7. Repeat the complete read/write/privacy/recovery checks on the intended production environment before switching it.

## Safety boundaries

- No bucket is provisioned, no credential is issued, no backend environment variable is changed and no deployment is triggered by this code change.
- No existing media bytes are copied, changed or deleted.
- Existing Android base64 upload contracts and media IDs remain unchanged.
- Direct signed client uploads, resumable legacy migration, image variants, video transcoding and CDN delivery are separate later phases.
- Never delete a source object or BYTEA payload until checksums, read-back, permissions and rollback have been verified.
