# FYNX private media storage

## Provider policy

FYNX supports three media storage modes:

- `postgres`: compatibility fallback for existing installations and legacy authenticated uploads.
- `neon`: preferred private object storage. Uses Neon Object Storage through its S3-compatible endpoint, with path-style addressing.
- `r2`: supported Cloudflare R2 alternative.

Do not make a public bucket. Media remains private; the FYNX API authorizes access before issuing a short-lived download redirect.

## Neon testing configuration

Set these variables on the **testing backend only** after creating a Neon Object Storage credential scoped to the FYNX testing branch with `storage:read` and `storage:write`:

- `FYNX_MEDIA_STORAGE_PROVIDER=neon`
- `FYNX_NEON_S3_ENDPOINT` — the branch-specific Neon S3 endpoint
- `FYNX_NEON_S3_REGION` — the endpoint's AWS region
- `FYNX_NEON_S3_ACCESS_KEY_ID` — the Neon S3 access key
- `FYNX_NEON_S3_SECRET_ACCESS_KEY` — the matching Neon S3 secret
- `FYNX_NEON_STORAGE_BUCKET=fynx-media`

The adapter also accepts Neon's AWS-standard variables (`AWS_ENDPOINT_URL_S3`, `AWS_REGION`, `AWS_ACCESS_KEY_ID`, and `AWS_SECRET_ACCESS_KEY`) while keeping the bucket name explicit.

Neon S3 credentials are branch-scoped. Create separate credentials for testing and production; do not reuse a testing credential in production. Never commit credentials to Git or place them in Android resources.

## Direct upload and retrieval flow

1. The authenticated Android client requests `POST /api/media/upload-intent` with the media MIME type and byte size.
2. FYNX validates the user, MIME type and 12 MB limit, creates a short-lived temporary `uploads/<uuid>` object key, and returns a signed PUT URL.
3. Android uploads directly to Neon/R2. It does not forward the FYNX bearer token to object storage.
4. Android calls `POST /api/media/upload-complete`. FYNX checks the temporary object with HEAD, verifies exact size and MIME type, copies it to a permanent `media/<uuid>` key, verifies the copy, and commits the media metadata.
5. FYNX media retrieval routes enforce ownership, group membership, status audience, block, Marketplace and other existing privacy rules before returning a short-lived signed GET redirect.
6. Android follows that redirect only for a trusted Neon or R2 host and never forwards the FYNX bearer token to the storage host.

The PostgreSQL compatibility path remains available when `FYNX_MEDIA_STORAGE_PROVIDER=postgres`. Existing media IDs and retrieval authorization are preserved.

## R2 alternative

To use Cloudflare R2 instead, set `FYNX_MEDIA_STORAGE_PROVIDER=r2` and configure `FYNX_R2_ACCOUNT_ID`, `FYNX_R2_ACCESS_KEY_ID`, `FYNX_R2_SECRET_ACCESS_KEY`, and `FYNX_R2_BUCKET`. R2 remains supported for existing stored objects and future deployments.

## Current testing environment

As of 20 November 2026:

- Neon project region: `aws-us-east-2`.
- The private `fynx-media` bucket exists on the `fynx-testing` branch.
- The private `fynx-media` bucket also exists on `live-recovery`.
- Both buckets were empty when inspected.
- The Railway testing backend did not yet have the Neon S3 environment variables configured. Its last recorded deployment failed during Railpack build with `secret FYNX not found`; this is a Railway build configuration blocker, not a media-upload code failure.
- No production bucket, production credential, Railway secret or deployment was changed as part of the code integration.

## Verification

Backend storage tests exercise Neon path-style upload URL signing, temporary-object HEAD verification, promotion, cleanup, R2 compatibility and PostgreSQL fallback. Android unit tests verify that only Neon and R2 storage host patterns are accepted. The CI runtime upload path remains separate from the live provider credential check.


## Current candidate verification checkpoint — 22 November 2026

The current canonical-based media-storage code candidate is PR #572. CI tests can verify provider routing, signed upload intent, storage-provider persistence, HEAD/COPY/cleanup logic, Android trusted-host allowlisting and PostgreSQL/R2 compatibility using local fakes. Live Neon uploads are not certified until the testing backend is configured with a branch-scoped Neon storage credential and the service deployment is healthy. A skipped production-backend probe is not proof of storage connectivity. Keep all buckets private and never copy testing credentials into production or Android resources.
