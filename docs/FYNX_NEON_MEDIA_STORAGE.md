# FYNX private media storage

## Provider policy

Neon Object Storage is FYNX's preferred media store. It is private, S3-compatible, branch-scoped and accessed through the AWS SDK using path-style URLs. The existing adapter also retains PostgreSQL compatibility for older deployments and Cloudflare R2 compatibility for legacy stored objects. Do not make the Neon bucket public.

Never introduce local-file or fake-media fallbacks in production. PostgreSQL mode is a durable database-backed compatibility mode, not a local cache. Set the provider explicitly on the backend.

## Neon reconnection requirements

Use the existing FYNX data-bearing Neon project and its intended testing or production branch. Do not create a replacement project, reset a populated branch, or provision another database as a workaround. Confirm that the selected branch has a **private** bucket named `fynx-media`.

Create or reuse a branch-scoped Neon Object Storage credential with `storage:read` and `storage:write`. Keep testing and production credentials separate. Never commit credential values or put them in Android resources.

Configure these variable names on the existing backend service:

| Variable | Purpose |
|---|---|
| `DATABASE_URL` | Existing Neon PostgreSQL connection string |
| `FYNX_MEDIA_STORAGE_PROVIDER` | Set to `neon` |
| `FYNX_NEON_S3_ENDPOINT` | Exact S3 endpoint for the selected Neon branch |
| `FYNX_NEON_S3_REGION` | Region reported by that branch's Object Storage |
| `FYNX_NEON_S3_ACCESS_KEY_ID` | Branch-scoped Neon S3 access key |
| `FYNX_NEON_S3_SECRET_ACCESS_KEY` | Matching branch-scoped Neon S3 secret |
| `FYNX_NEON_STORAGE_BUCKET` | Set to `fynx-media` |

The adapter also accepts Neon's standard environment names `AWS_ENDPOINT_URL_S3`, `AWS_REGION`, `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` as alternatives for endpoint, region and credentials. Keep `FYNX_NEON_STORAGE_BUCKET` explicit.

## Upload and finalization

1. The signed-in Android client calls `POST /api/media/upload-intent` with MIME type and exact byte size. The backend checks the account, supported image/video/audio MIME type and 1–12 MiB size limit.
2. The backend creates an owner-bound intent and temporary key under `uploads/<uuid>`. The short-lived HTTPS PUT signature includes the exact content type and byte length.
3. Android uploads with fixed-length streaming to the trusted Neon/R2 endpoint. It does not send the FYNX bearer token to object storage. Transient network failures can retry the same idempotent PUT; authorization errors, permanent HTTP failures and expired signatures are not retried.
4. The signed-in client calls `POST /api/media/upload-complete`. The backend locks the intent by both intent ID and owner ID, confirms the original storage provider is still configured, checks the temporary object with HEAD, verifies exact size and MIME type, copies it to a separate permanent `media/<uuid>` key, verifies the copy, and commits the media row.
5. Completion is idempotent. A retry returns the already-created media ID rather than creating another row. The database stores the stable provider and object key, never the temporary upload URL or signed download URL.

The PostgreSQL compatibility path remains available when the backend is explicitly configured with `FYNX_MEDIA_STORAGE_PROVIDER=postgres`. Neon and R2 use direct signed uploads; an unconfigured object-storage provider fails closed rather than silently storing media on the wrong provider.

## Retrieval, privacy and deletion

FYNX media routes first apply their existing authorization rules: account ownership, message/group membership, status audience, blocks, Marketplace listing/order evidence and the other feature-specific visibility rules. Only after authorization does the backend issue a short-lived signed GET or use a protected proxy response. Android follows signed redirects only to validated Neon/R2 hostnames and never forwards the FYNX bearer token to the storage host.

Object deletion is supported for both Neon and R2. The backend uses it to roll back permanent objects after failed metadata commits and to clean expired incomplete upload intents. Intent cleanup uses the provider recorded when the intent was created, so changing the configured provider does not redirect a cleanup request to another bucket. Deletion is idempotent and retried by later cleanup sweeps after transient failures.

The canonical app does not currently expose a generic user-facing `DELETE /api/media/:id` operation. Do not hard-delete an object simply because one UI reference disappears: posts, comments, messages, profile photos, statuses, Marketplace listings, group attachments and order evidence may still reference that media ID. A user-facing deletion feature must first detach/check every reference and preserve the existing authorization and retention rules.

## Diagnostics and resilience

The backend uses bounded object-store operations and returns stable diagnostics without exposing raw provider messages, credentials or signed URLs:

- `MEDIA_STORAGE_NOT_CONFIGURED` / `MEDIA_STORAGE_BUCKET_UNAVAILABLE`: missing or unavailable endpoint, bucket or configuration.
- `MEDIA_STORAGE_AUTHENTICATION_FAILED`: invalid credentials or access denied.
- `MEDIA_STORAGE_SIGNATURE_EXPIRED`: expired signed request; start a new upload.
- `MEDIA_STORAGE_OBJECT_NOT_FOUND`: requested object is missing.
- `MEDIA_STORAGE_NETWORK_FAILURE`: timeout, DNS or transport failure; retry is safe only for idempotent operations.
- `MEDIA_STORAGE_PROVIDER_ERROR`: unexpected provider failure requiring investigation.

## Deployment and verification status — 23 November 2026

The repository's existing testing service is the Railway `fynx-backend-testing` service on the canonical integration branch; no Render service is used. Its current environment has a staged patch containing the Neon variable names, but that patch is not live. The latest recorded live deployment is failed (3 November 2026). Staged variables are not evidence of a healthy backend.

The current Neon connector context exposes only a reset-named development project with no `fynx-media` bucket. That project was not used for upload tests. The existing data-bearing testing branch and its private bucket/credential must be confirmed before live verification. Do not create a new database or reset existing application data to make the test pass.

Automated tests verify Neon path-style signing, exact byte-size signing, upload completion's HEAD/COPY/HEAD contract, retrieval, deletion, provider-pinned cleanup, R2 compatibility, PostgreSQL compatibility, invalid keys/MIME/sizes, missing configuration, timeouts and diagnostic classification. Android host tests allow trusted Neon/R2 endpoints and reject lookalikes. These tests do **not** certify live Neon connectivity.

**Live Neon verification: NOT RUN.** A real authenticated signed PUT, finalization, authorized GET and safe delete must be verified against the intended existing test branch after the existing backend deployment is healthy and its branch-scoped credentials/bucket are confirmed. A green GitHub check or staged deployment configuration is not live-storage proof. No live service deployment, new bucket, new Neon project, database reset or secret rotation was performed in this work.
