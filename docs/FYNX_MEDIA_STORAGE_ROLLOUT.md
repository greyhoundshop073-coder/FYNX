# FYNX media storage rollout

## Architecture

PostgreSQL remains the source of truth for FYNX users, media IDs, ownership, social visibility, Marketplace references and order evidence. Uploaded media bytes can be stored in a private Cloudflare R2 Standard bucket. Each message_media row keeps its existing ID and MIME/size metadata and records either:

- storage_provider = 'postgres': legacy data BYTEA remains the source.
- storage_provider = 'r2': object_key points to the private R2 object and data is NULL.

The API checks the same FYNX authorization rules before it issues a short-lived (120-second) signed GET URL. For new R2 uploads, the API issues a short-lived signed PUT URL tied to an owner-bound upload intent. The phone uploads only to a temporary `uploads/` key; the backend checks the stored object's exact size and MIME, copies it to a separate permanent `media/` key, verifies the copy, and only then creates the existing FYNX media row. The signed PUT URL can no longer overwrite published media after completion. Abandoned temporary objects are deleted by the expiry sweeper. Completed temporary objects are retained only until their five-minute signed PUT URL has expired, then the sweeper removes them and keeps permanent media untouched. PostgreSQL media remains readable through the existing bounded cache. Old media IDs and client routes do not change.

## Safe activation

The code defaults to PostgreSQL. Do not enable R2 until a private Standard bucket and a bucket-scoped S3 token have been created in the intended Cloudflare account.

Set these variables only in the Railway service environment; never commit credentials:

- FYNX_MEDIA_STORAGE_PROVIDER=r2
- FYNX_R2_ACCOUNT_ID
- FYNX_R2_ACCESS_KEY_ID
- FYNX_R2_SECRET_ACCESS_KEY
- FYNX_R2_BUCKET

The R2 token should have object read/write/delete permission only for the FYNX media bucket. Keep the bucket private. The app does not need a public r2.dev URL or a public bucket.

Before activation, check Cloudflare's R2 subscription/checkout requirements and the account's current billing settings. The R2 free monthly allowance is not a promise that account activation has no billing step; usage above the free allowance is billed.

## Rollout stages

1. Merge and deploy the adapter with FYNX_MEDIA_STORAGE_PROVIDER=postgres. This is a no-op for storage until configuration is deliberately enabled.
2. Configure and smoke-test a private R2 bucket on the isolated Railway testing service. Verify one image, one video and one audio upload/read, signed URL expiry, privacy denials, and rollback cleanup.
3. Enable R2 for new uploads on testing. Existing PostgreSQL records remain available through the postgres provider and keep the same media IDs.
4. Migrate legacy files in small, resumable batches only after the live DATABASE_URL target is verified. Copy and checksum an object before updating its row; never delete the original BYTEA during the first migration.
5. Repeat the complete read/write/privacy checks on the intended production environment before switching it.
6. Signed upload intents are now supported: the authenticated app requests a short-lived upload URL, uploads directly to the private bucket, and calls the completion endpoint. The backend verifies object size and MIME with HEAD before assigning a permanent FYNX media ID. PostgreSQL-only mode still returns a legacy-upload instruction for older client builds. A periodic sweeper removes objects from abandoned, expired, incomplete upload intents while leaving completed media untouched.

No bucket is provisioned, no credentials are added, no existing media bytes are migrated or deleted, and no live provider switch is performed by this code change.
