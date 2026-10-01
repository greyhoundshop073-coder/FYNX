# FYNX Security Review Checklist

This document records the security areas that must be verified before production release. It does not replace existing authentication, backend authorization, privacy, or media controls.

## Required checks

- Authentication tokens must not be logged or exposed in UI/error messages.
- Protected API calls must use authenticated transport; the backend remains the authority for authorization.
- User-controlled text and media metadata must be validated before use.
- AI requests must remain within the explicitly authorized capability/data scope.
- Private messages, profiles, media, and other private data must never be sent to AI unless an explicit product permission authorizes it.
- Media access must enforce backend ownership/privacy rules; client-side hiding is not sufficient.
- Sensitive local data should use Android-protected storage mechanisms rather than plain files or unencrypted preferences where secrets are involved.
- Production builds must not include debug-only tooling or secrets.
- Network communication must use HTTPS/TLS and certificate/security defaults should not be weakened.
- Dependencies and SDKs should be kept current and checked for known security issues before release.
- Error handling should expose useful user-safe messages without leaking server internals, credentials, tokens, SQL/database details, or stack traces.

## Release gate

A green build alone does not prove security. The repository implementation and backend authorization must be reviewed together, followed by testing of authentication, private messaging, media, privacy, AI permissions, and failure/expiry cases.
