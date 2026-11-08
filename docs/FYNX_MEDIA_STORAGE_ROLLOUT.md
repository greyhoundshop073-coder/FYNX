# FYNX media storage rollout

## Architecture

PostgreSQL remains the source of truth for FYNX users, media IDs, ownership, social visibility, Marketplace references and order evidence. Media bytes are stored separately when an object provider is deliberately enabled.

Each `message_media` row keeps its existing ID, MIME type and byte size and records one of:

- `storage_provider = 'postgres'`: legacy `data BYTEA` is the source.
- `storage_provider = 'neon'`: `object_key` points to a private Neon Object Storage object.
- `storage_provider = 'r2'`: `object_key` points to a private Cloudflare R2 object.

The default remains `postgres`. The API performs its existing FYNX authorization query before issuing a short-lived signed GET URL. Legacy PostgreSQL rows remain readable. The adapter can read Neon and R2 rows side-by-side, which is required for safe provider migration and rollback.

## Hosting decision: Railway primary, Render fallback

- **Railway is the selected primary backend host.** FYNX should run one active backend/API service there. The Android app and CI should use the Railway environment URL assigned to the intended environment.
- **Render is retained as a migration-ready fallback.** It must not receive production traffic or become a second writer merely because its service exists. Keep its configuration portable and use it only after a deliberate migration decision.
- Do not pay for or operate both hosts as active production backends. Before a future switch, deploy the same reviewed commit to Render, configure the same intended Neon database branch and secrets, pass health and functional smoke tests, then change the single backend base URL and verify payment/webhook callbacks. Do not dual-write user data.
- Keep host-specific settings in environment configuration. Do not hard-code Railway URLs into business logic or couple application features to Railway-only APIs unless guarded by a provider-neutral interface.

### Account inspection snapshot (2026-11-08)

- Railway project `FYNX-Isolated-Testing` contains `fynx-backend-testing` in its `testing` environment. The service's latest recorded deployment is `FAILED` and the service is currently offline. The failure log reports `secret FYNX not found` during image build. This is a Railway build-configuration/secret issue, not a media-adapter test failure. Resolve it in Railway's service/build configuration without inventing or committing a secret value.
- The inspected Railway service currently lists `DATABASE_URL`, `JWT_SECRET`, `GEMINI_API_KEY`, Paystack and wallet settings, but no `FYNX_NEON_*` storage settings.
- The existing Render `FYNX-AI-Backend` service is still listed on its Free plan and is not marked suspended by the account API. It remains a fallback only; account listing does not prove that it is healthy or configured for a production cutover.
- The inspected Neon branches report Object Storage enabled, but no bucket is provisioned on those branches. No bucket, credential, secret, environment variable, data migration or deployment was created by this PR.

## First object-storage provider: Neon Object Storage

The existing Neon project `patient-lab-43403708` is in `aws-us-east-2`. Neon Object Storage reports enabled on `fynx-testing`, `live-recovery-20261101` and `production`, but those branches had no storage buckets at the time of inspection.

Neon requires path-style S3 addressing. The adapter sets `forcePathStyle: true` and uses branch-specific endpoint, region and credentials. Neon credentials are branch-scoped, so the credential and endpoint must belong to the exact branch used by the backend service (or an ancestor branch in that branch's lineage).

Set these variables only in the intended Railway backend service environment; never commit credentials:

- `FYNX_MEDIA_STORAGE_PROVIDER=neon`
- `FYNX_NEON_S3_ENDPOINT` (branch-specific HTTPS S3 endpoint)
- `FYNX_NEON_S3_REGION` (for example, `us-east-2`)
- `FYNX_NEON_S3_ACCESS_KEY_ID` (Neon credential token ID)
- `FYNX_NEON_S3_SECRET_ACCESS_KEY` (Neon S3 secret)
- `FYNX_NEON_STORAGE_BUCKET` (private bucket name)

The Neon credential inventory previously showed a default object-storage credential with `storage:read` and `storage:write` scopes anchored on the production parent branch. Do not rotate that shared credential. Before activation, confirm the exact Railway database branch and provision a dedicated testing credential if the existing secret is not available to the backend owner. Keep the bucket private. Store credentials only in the backend's secret environment, never in Android or source control.

## Optional later object-storage provider: Cloudflare R2

R2 remains available as a later provider and migration target. The adapter retains this environment contract:

- `FYNX_MEDIA_STORAGE_PROVIDER=r2`
- `FYNX_R2_ACCOUNT_ID`
- `FYNX_R2_ACCESS_KEY_ID`
- `FYNX_R2_SECRET_ACCESS_KEY`
- `FYNX_R2_BUCKET`

R2 should only be activated after the account's billing and free allowance are checked. Do not operate Neon Object Storage and R2 as duplicate active stores. The row-level provider marker permits a controlled migration where old and new objects coexist while each media ID remains readable.

## Safe activation and migration

1. Keep `FYNX_MEDIA_STORAGE_PROVIDER=postgres` until a private bucket, branch-scoped credential and isolated backend configuration have been verified.
2. Resolve the Railway build failure first. Confirm the intended Railway service can build and pass its `/ready` health check; do not treat an offline service as an available primary.
3. Confirm the exact Neon branch used by the Railway testing backend. Configure a private bucket and branch-scoped credentials only after the branch is verified.
4. Configure the isolated testing backend. Smoke-test image, video and audio uploads, reads, signed URL expiry, privacy denials and cleanup.
5. Enable object storage for new testing uploads. Existing PostgreSQL rows retain their media IDs and continue to work.
6. Migrate legacy files in small resumable batches only after verifying the exact database target. Copy and checksum an object before updating its row. Keep the original BYTEA until the new object has been read back and verified.
7. Keep both provider credentials available during any provider transition. The reader uses each row's recorded provider, so a partially migrated dataset remains readable.
8. Add automated migration thresholds only after measuring storage use, operation volume, failure rates and actual provider costs. Thresholds should alert and prepare a migration; paid provisioning and production cutover must remain approval-gated.
9. Repeat the complete read/write/privacy/recovery checks on the intended production environment before switching it.

## Comment attachments: remaining integration

The generic media adapter and media table can store image, video and audio objects, but the current social comment endpoints still accept text only. Comment attachment support is therefore **not complete**. It requires a coordinated backend and canonical Android integration: comment media references and ownership checks in PostgreSQL, protected comment-media retrieval that enforces post visibility and block rules, and the Home comment composer/display flow. Do not claim photo/video comments are backed by object storage until those routes, Android flows and regression tests are implemented.

## Safety boundaries

- No bucket is provisioned, no credential is issued, no backend environment variable is changed and no deployment is triggered by this code change.
- No existing media bytes are copied, changed or deleted.
- Existing Android base64 upload contracts and media IDs remain unchanged.
- Direct signed client uploads, resumable legacy migration, image variants, video transcoding and CDN delivery are separate later phases.
- Never delete a source object or BYTEA payload until checksums, read-back, permissions and rollback have been verified.
