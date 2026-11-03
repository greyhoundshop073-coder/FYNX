# FYNX media storage rollout

## Architecture

PostgreSQL remains the source of truth for FYNX users, media IDs, ownership, social visibility, Marketplace references and order evidence. Uploaded media bytes can be stored in a private Cloudflare R2 Standard bucket. Each message_media row keeps its existing ID and MIME/size metadata and records either:

- storage_provider = 'postgres': legacy data BYTEA remains the source.
- storage_provider = 'r2': object_key points to the private R2 object and data is NULL.

The API checks the same FYNX authorization rules before it issues a short-lived (120-second) signed GET URL. PostgreSQL media remains readable through the existing bounded cache. Old media IDs and client routes do not change.

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
6. Consider direct-to-R2 signed PUT uploads as a later client upgrade. This foundation preserves the current base64 upload contract so existing app builds continue to work.

No bucket is provisioned, no credentials are added, no existing media bytes are migrated or deleted, and no live provider switch is performed by this code change.


## Direct uploads, staging promotion and safe legacy migration

The Android upload client requests an authenticated upload intent. When the backend still uses PostgreSQL storage, the API returns legacy mode and the app uses the existing upload route. When R2 is configured, the backend returns a 15-minute signed PUT URL for a private media-staging object. Android uploads directly to that staging key without sending the FYNX bearer token to Cloudflare, then calls the authenticated completion route.

Completion verifies the staged object's exact content type and byte size, copies it server-side to a separate immutable media key, verifies the final object, and only then creates the normal message_media row. The signed PUT URL can no longer overwrite the final media object after completion. Staging objects are retained only until their signed URL expiry and are cleaned by the expiry cleanup command. Existing media IDs and PostgreSQL-only rows remain compatible.

To inspect expired staging uploads without changing data, run this command from the backend folder:

    npm run cleanup:media-upload-intents

Only run cleanup with the documented explicit confirmation variable after R2 is configured. Completed media rows are never removed by this cleanup.

### Legacy media migration

The migration is deliberately manual and defaults to a dry-run. It copies each eligible PostgreSQL BYTEA object to R2, downloads it again, compares SHA-256 checksums, and only then changes the row's storage provider and object key. The original PostgreSQL BYTEA is retained as a rollback backup.

First run the inventory from the backend folder:

    npm run migrate:media-to-r2

After confirming the R2 bucket, credentials, active database branch and backup, apply a small test batch:

    FYNX_MEDIA_MIGRATION_CONFIRM=COPY_LEGACY_MEDIA_TO_R2_KEEP_POSTGRES_BACKUP npm run migrate:media-to-r2 -- --apply --max-items=5

Then verify those five migrated media items through FYNX before running the full migration. Do not clear PostgreSQL BYTEA backups as part of this migration. A separate reviewed cleanup is required after independent backups and production validation.

The backend must be configured with FYNX_MEDIA_STORAGE_PROVIDER=r2, FYNX_R2_ACCOUNT_ID, FYNX_R2_ACCESS_KEY_ID, FYNX_R2_SECRET_ACCESS_KEY, and FYNX_R2_BUCKET. Never put access keys in Android, source control, issue comments, or chat messages. Add them only to protected backend service variables. Keep FYNX_MEDIA_STORAGE_PROVIDER=postgres until a private bucket and test credentials have been verified.
