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
5. Completion is idempotent. A retry returns the already-created media ID rather than creating another row. The database stores the stable provider and object key, never the temporary upload URL or signed download URL. If the database connection is lost while COMMIT is being acknowledged, the backend preserves the promoted object because the transaction may already have committed; the same owner can safely retry completion, and the normal expired-intent sweep removes only objects whose intent is still uncompleted. The legacy metadata-write path follows the same rule for ambiguous database transport failures.

The PostgreSQL compatibility path remains available when the backend is explicitly configured with `FYNX_MEDIA_STORAGE_PROVIDER=postgres`. Neon and R2 use direct signed uploads; an unconfigured object-storage provider fails closed rather than silently storing media on the wrong provider.

## Retrieval, privacy and deletion

FYNX media routes first apply their existing authorization rules: account ownership, message/group membership, status audience, blocks, Marketplace listing/order evidence and the other feature-specific visibility rules. Only after authorization does the backend issue a short-lived signed GET or use a protected proxy response. Android follows signed redirects only to validated Neon/R2 hostnames and never forwards the FYNX bearer token to the storage host.

Object deletion is supported for both Neon and R2. The backend uses it to roll back permanent objects after failed metadata commits and to clean expired incomplete upload intents. Intent cleanup uses the provider recorded when the intent was created, so changing the configured provider does not redirect a cleanup request to another bucket. Deletion is idempotent and retried by later cleanup sweeps after transient failures.

The canonical app does not currently expose a generic user-facing `DELETE /api/media/:id` operation. Do not hard-delete an object simply because one UI reference disappears: posts, comments, messages, profile photos, statuses, Marketplace listings, group attachments and order evidence may still reference that media ID. A user-facing deletion feature must first detach/check every reference and preserve the existing authorization and retention rules.

## Diagnostics and resilience

The backend enforces a bounded deadline for each object-store operation, including the full streamed response-body read (not only the initial response headers), and returns stable diagnostics without exposing raw provider messages, credentials or signed URLs. Cleanup logs contain only provider/category/code/retryability metadata; raw object-store exceptions and signed links are not written to logs. Missing provider configuration is returned using the same stable diagnostic contract:

- `MEDIA_STORAGE_NOT_CONFIGURED` / `MEDIA_STORAGE_BUCKET_UNAVAILABLE`: missing or unavailable endpoint, bucket or configuration.
- `MEDIA_STORAGE_AUTHENTICATION_FAILED`: invalid credentials or access denied.
- `MEDIA_STORAGE_SIGNATURE_EXPIRED`: expired signed request; start a new upload.
- `MEDIA_STORAGE_OBJECT_NOT_FOUND`: requested object is missing.
- `MEDIA_STORAGE_NETWORK_FAILURE`: timeout, DNS or transport failure; retry is safe only for idempotent operations.
- `MEDIA_STORAGE_PROVIDER_ERROR`: unexpected provider failure requiring investigation.

## Deployment and verification status — 23 November 2026

The existing FYNX Neon project is `patient-lab-43403708` in `aws-us-east-2`. The populated testing branch is `fynx-testing` (`br-floral-shape-b5mbajl5`); read-only inspection found 239 users, 55 groups, 161 group messages, one Marketplace listing and three Marketplace orders. It already has the private `fynx-media` bucket and the default branch-scoped storage credential. Production remains a separate branch and is not the target of this testing deployment.

The canonical repository already contains the FYNX Neon Functions runtime and a GitHub deployment workflow. The previous workflow was incorrectly pinned to the empty reset project `fynx-dev-backend-reset-20261122` and its empty `fynx-dev` branch. This change corrects deployment to the existing `patient-lab-43403708` project and populated `fynx-testing` branch. It declares the existing `fynx-media` bucket as private and explicitly selects `FYNX_MEDIA_STORAGE_PROVIDER=neon`; Neon injects branch-scoped S3 credentials into the Function. No Railway or Render service is required for the Neon backend runtime.

The workflow validates the canonical Neon runtime and requires existing GitHub deployment secrets `NEON_API_KEY`, `FYNX_JWT_SECRET`, `FYNX_GEMINI_API_KEY` and `FYNX_PAYSTACK_SECRET_KEY`. Wallet test mode and card funding remain disabled by default. The scheduled Marketplace maintenance trigger remains disabled until its tasks are separately certified.

Automated tests verify Neon path-style signing, exact byte-size signing, upload completion's HEAD/COPY/HEAD contract, retrieval, deletion, provider-pinned cleanup, R2 compatibility, PostgreSQL compatibility, invalid keys/MIME/sizes, missing configuration, timeouts and diagnostic classification. The Neon runtime verifier checks the function route contract, realtime bridge, scheduled task wiring, private bucket configuration and deployment target.

**Live Neon Function deployment and app cutover: NOT YET CERTIFIED.** After the corrected deployment workflow succeeds, the function URL must be checked for health and database readiness, then the Android backend endpoint must be switched to that exact URL and the canonical APK rebuilt. Live signed PUT, finalization, authorized GET and safe deletion must then be exercised against this test branch. No production branch, populated data, bucket contents or secrets have been reset or rotated.
