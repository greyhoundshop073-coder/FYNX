# Email and account-recovery readiness audit

## Current evidence (repository source audit)

The Android auth client and backend currently support username/password registration and login. The backend hashes passwords with bcrypt, compares hashes during login, and applies a per-process auth rate limit. Registration collects a phone number, but the auth screen explicitly says phone/SMS verification is not being faked.

The backend's `users` schema uses `username` as the unique login identity and contains no email address field. The backend package manifest has no email-delivery provider dependency. The auth router currently exposes register and login only; there are no email verification, password-reset, recovery, or resend-token endpoints.

## Why this change does not add a fake email button

A safe recovery flow needs a confirmed account identity model, expiring single-use tokens stored securely, rate limits and anti-enumeration responses, email delivery configured outside source control, and test-environment delivery evidence. Adding only UI or generating a token without reliable delivery would create a false security promise.

## Acceptance path for the dedicated implementation

1. Decide whether email is a login identity, a verified contact channel, or both.
2. Select and configure the delivery provider through deployment secrets/configuration.
3. Add migration-safe email and token storage with expiry, single-use consumption, and appropriate indexes.
4. Implement generic recovery responses, rate limits, token expiry/replay tests, and delivery failure/retry handling.
5. Verify delivery in a test environment, then wire Android screens to the real endpoints.
6. Run backend tests and Android CI against the final integrated commit.

Run `python3 scripts/verify_email_auth_readiness.py` to keep the current contract visible and prevent future code changes from silently weakening existing auth behavior or implying email recovery is already available.
