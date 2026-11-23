# FYNX account-deletion architecture and readiness

**Review date:** 2026-11-23  
**Canonical source baseline:** `integration/fynx-single-apk-20261013` at `dfb9b83d420b41512224ad7180eab797b9664f6d`  
**Status:** Account deletion is **not implemented or certified**. This document defines the gates for a safe implementation; it is not a live deletion request page or a promise of erasure.

## Current verified state

The canonical source does not contain a complete in-app account-deletion journey, a public web deletion request endpoint/page, or a deletion worker that handles database rows and external media objects. The current Android manifest's `fynx.app` HTTPS intent filter is not proof that a public deletion page is deployed on that domain.

A read-only metadata comparison on 2026-11-23 found:
- `spring-mode-34864088 / fynx-dev`: 12 public base tables.
- `patient-lab-43403708 / production`: 47 public base tables.
- `patient-lab-43403708 / live-recovery`: 88 public base tables.

The review queried table/column/foreign-key metadata only. It did not inspect personal rows, payment values or customer content, and it did not alter any database. The production and recovery schema differences mean the small isolated development database is not sufficient to certify deletion in the larger data environment.

## Why a direct users-table delete is unsafe

The recovery schema contains multiple classes of user relationships:

- **CASCADE:** many profile, social, message, group, status, notification, AI and related content records. Cascades may also erase reports, moderation context, reviews or ownership history that may need a lawful retention or safety decision.
- **RESTRICT:** financial and operational relationships, including buyer/seller wallet records, wallet transactions/funding/provider operations, orders, escrow, payout accounts, disputes, delivery evidence, protection cases and selected announcement/seasonal-promotion records. A direct delete can fail while these references remain.
- **SET NULL:** selected audit/attribution relationships, such as moderation targets, event actors and revenue/award attribution. These rows need a clear pseudonymisation/retention rule rather than accidental loss of actor context.
- **Media objects:** `message_media` stores media metadata and may contain database bytes or `storage_provider`/`object_key` references. The recovery branch has a private Neon Object Storage bucket named `fynx-media`. Database cascades alone cannot prove that object bytes, derived copies, cached thumbnails or processor copies are erased.
- **Provider records:** push tokens, SMTP logs, AI provider requests, payment processor references and realtime/call infrastructure may retain information outside the FYNX database.

The current branches are not fully migration-aligned. The account-deletion process must operate only after each environment's schema and provider configuration have been verified.

## Required product and operational flow

A compliant request experience should eventually provide both:

1. An authenticated, discoverable in-app account-deletion request path.
2. A public HTTPS web request page that can be used after the app has been uninstalled.

The public page must be hosted on a verified FYNX-controlled domain and name the actual operator/contact. No such live page is being claimed by this PR because the domain, legal operator and monitored privacy contact have not been confirmed.

The request flow should:
- identify the account and verify ownership using an existing secure account method (do not introduce an unrelated OTP login path);
- explain that deletion is a request, show the consequences, and require an explicit final confirmation;
- provide a request reference and status, support escalation and a way to correct an unauthorised request;
- immediately stop unnecessary processing and deactivate public surfaces/listings where appropriate;
- classify open orders, unpaid balances, refunds, disputes, delivery claims, payout and fraud/security obligations before destructive work;
- revoke active sessions/tokens and remove account-owned data that is not subject to an approved retention exception;
- delete FYNX-controlled media objects, thumbnails, derivatives, local caches where applicable and relevant object metadata;
- issue deletion/erasure requests to processors where required and track completion/failure;
- retry idempotently, record an audit trail without retaining unnecessary personal content, and notify the user when the request is completed or cannot yet be completed;
- state accurately which categories are retained, why, and for how long, after those periods have been approved and implemented.

## Implementation gate before any destructive endpoint

The following decisions and evidence are required:

1. **Controller/legal owner:** registered entity or individual developer, country/jurisdiction, business address and monitored privacy contact.
2. **Retention rules:** approved periods and legal bases for payment/accounting, tax, fraud, dispute, moderation and security records; backup and log expiry.
3. **Full schema map:** run the FK/runtime-table audit after migrations on the actual target branch; classify every CASCADE, SET NULL and RESTRICT edge.
4. **Media inventory:** identify the active storage provider, every object-key format, derivatives, thumbnails, cache layers and processor copies; verify delete APIs and retries.
5. **Processor inventory:** identify actual Neon, email, FCM, Gemini, payment, geocoding and call/realtime configurations and their deletion mechanisms/retention terms.
6. **Financial lifecycle:** define how accounts with pending orders, escrow, wallet funds, refunds, payouts, chargebacks or disputes are locked, settled, anonymised or lawfully retained.
7. **Security model:** ensure the request cannot be submitted for another user, reused, replayed or executed from an expired session; preserve only the minimum audit information needed.
8. **Tests:** cover ordinary account, active order, held funds, open dispute, driver evidence, group owner, moderation reporter, media in object storage, external-provider failure, backup expiry and retry/idempotency.

Do not treat successful execution of `DELETE FROM users` or a cascade as successful account erasure. Do not physically delete a user until every retained obligation is classified and all linked data/objects/processors have a verified disposition.

## Public-page and legal details not supplied

The following are intentionally unresolved: controller identity, address, privacy contact email, public policy URL, public account-deletion URL, data-region assertions, retention duration, backup period and third-party deletion commitments. Do not fill these with placeholders and publish them as if real. Google's Play account-deletion policy/help article must be checked against the final release listing and form before submitting: https://support.google.com/googleplay/android-developer/answer/13327111.

## Delivery status

- In-app deletion request: not implemented.
- Public deletion request page: not hosted or publicly verified.
- Destructive deletion endpoint/worker: intentionally not implemented until the gates above are complete.
- Database schema review: read-only metadata comparison completed; production/recovery/dev schema drift remains.
- Legal/retention review: pending operator and provider information.
