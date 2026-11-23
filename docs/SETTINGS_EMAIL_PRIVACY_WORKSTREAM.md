# Workstream 7 — Settings, email and privacy closeout

**Review date:** 2026-11-23  
**Base:** `integration/fynx-single-apk-20261013` at `dfb9b83d420b41512224ad7180eab797b9664f6d`

## Existing work retained

The historical Settings PRs #118, #120, #121 and #123–#130 are an old stack targeting `main` and intermediate Settings branches; they are not a safe merge unit for the current canonical branch. Their account/profile, appearance, notification, privacy, cache, data/network and help concepts were inspected against the current implementation.

The canonical app already has:
- Settings home and inner screens for Account/Profile, Privacy & Security, Notifications, Appearance, Language, Media & Storage, Data & Network, Help & Support and About.
- Profile editing and remote profile refresh.
- Local/remote privacy preference save paths, blocked-user controls, report/appeal access and notification preference save/failure states.
- A dedicated account-scoped media cache clear path that leaves original posts/messages and other app files untouched.
- Android network information and protected backend health/account checks.
- Help & Support uses the existing FYNX support case flow, including user case creation/status and the authorised Owner/Admin support inbox; no external address or off-platform contact is invented.
- Existing email registration verification and password recovery endpoints. These remain the same existing code-based email verification/recovery flows; no login or registration screen is replaced.

## Changes made in this workstream

- Added an Account & Profile → Phone & Email screen for reading the saved phone number, viewing the backend email verification state, attaching/changing an email after current-password confirmation, resending verification, and confirming the existing email-verification code.
- Added authenticated email status and resend routes. Public verification and password-reset routes remain available as before.
- Added best-effort email security notices after password change and to the prior verified address after an email change. A provider failure does not roll back the account update or falsely report delivery.
- Extended email tests for verification/reset expiry and reuse, resend cooldown, five failed verification attempts, email-change confirmation, security notices, protected Settings routes and delivery failure.
- Declared the seven optional SMTP Function variables in the Neon config, passed SMTP credentials through GitHub Actions Secrets/Variables into a temporary permission-restricted file, and added partial-configuration validation/cleanup. No credentials are stored in the repository.
- Added a deployment-contract test and Neon setup instructions.
- Made the Data & Network screen scroll and respond to the on-screen keyboard instead of leaving lower controls potentially inaccessible.

## Email delivery readiness

The backend SMTP adapter uses `FYNX_EMAIL_*` variables and fails closed if no provider is configured. The dedicated Neon `fynx-dev` Function had no SMTP keys at the time of inspection. This workstream prepares the secure configuration path; an operator must set the provider secrets/variables and run the manual Neon deployment. Real inbox delivery, provider logs, resend throttles against the deployed database and provider failure/retry behavior remain external verification steps.

No SMTP provider, email address ownership, sender domain or delivery result is invented. The current implementation retries only when the user requests a new message after the configured resend cooldown; it does not claim an automatic provider retry queue.

## Privacy and Play readiness

- Added a source-grounded data inventory and account-deletion architecture note. Neither is a public Privacy Policy or a Data safety declaration.
- The current manifest includes contacts, location, camera, microphone, notification, photos/video, exact-alarm and boot permissions. Contacts matching has a versioned disclosure. Notification permission is currently requested during signed-in startup on Android 13+; the Push alerts setting persists the app preference but is not itself the permission rationale. Permission-denial and revocation behavior still needs device verification.
- A broader privacy/permission PR #503 remains separate and non-mergeable as-is; it changes additional Camera, Calls, Home and Admin areas. Its branch was not merged wholesale or copied into this Settings PR.
- Account deletion is intentionally not exposed as a destructive endpoint. The current database branches have 12, 47 and 88 public tables respectively, and the larger recovery schema contains financial RESTRICT references, audit SET NULL references, cascaded user content and media-object dependencies. Legal operator/contact/retention and processor deletion rules remain unverified.
- A live public account-deletion URL, public privacy-policy URL, verified controller identity, support/privacy contact, retention commitments and final Play Console Data safety answers are not available from the inspected source/configuration.

## Verification boundary

CI should run the Android build/tests and the backend email/deployment contracts for the PR head. A green CI run does not certify SMTP delivery, runtime permission behavior, account deletion, legal policy accuracy, provider data handling or Google Play approval. Do not call the APK or Play release fully certified until those separate gates pass.


## Transactional email notifications

Verified email addresses can receive important Marketplace and Wallet activity notices. This is transactional account activity, not promotional email. The implementation sends only after a confirmed event and respects the account-level email master switch plus Marketplace or Wallet email preference:

- Marketplace: Paystack payment confirmation, FYNX Wallet order payment, seller fulfillment progress, the other party opening a protection case, and unpaid order cancellation to the seller.
- Wallet: confirmed top-ups and completed, failed or reversed withdrawals.
- Security/account-protection notices remain a separate safety channel and are not suppressed by the activity-email switches.

The app notification is stored independently from email/push delivery. The email delivery ledger uses a deterministic notification ID and verified destination to avoid duplicate sends; a failed delivery is recorded as failed rather than shown as sent. User preference defaults are enabled for existing accounts; users can turn off all activity emails or separately disable Marketplace and Wallet email updates.

Actual mailbox delivery is not certified until SMTP credentials are configured on the target Neon Function and a controlled test mailbox receives each event type. The SMTP vendor/controller identity and retention details remain unconfirmed and must not be inferred or published.

