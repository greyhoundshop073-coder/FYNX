# Instructions for all FYNX coding agents and workstreams

**Shared APK destination — do not change it:** `integration/fynx-single-apk-20261013`

Before editing code, read [the single-APK integration policy](docs/SINGLE_APK_INTEGRATION_POLICY.md) and inspect the latest canonical branch head and open pull requests.

- Start every work branch from the latest `integration/fynx-single-apk-20261013`.
- Every feature PR must use `integration/fynx-single-apk-20261013` as its base. Do not merge work into `main` or a competing APK branch as the delivery path.
- Do not create or announce a separate “latest APK” from a feature branch. The shared APK is built only from the latest merged canonical branch commit.
- Preserve other workstreams' changes. Inspect the diff and callers; selectively port missing work instead of merging divergent branches wholesale.
- Keep Railway + Neon aligned to the canonical branch without pinning Railway to a one-off commit. Verify deployed commit and health/readiness; never treat staged or failed deployment code as live.
- **Development APK access is intentionally open during active construction and inspection.** In debuggable builds, open directly into the app with a local-only session even when the backend is unavailable; save that local inspection identity consistently for panels that read `FynxAuthStore`. Do not fabricate a server token, claim server authentication, or weaken server authorization. Keep the real login/authentication gate intact in release builds. Restore the debug login gate only when the maintainer explicitly confirms the app is ready for launch.
- Keep release authentication and backend authorization strict. Do not add OTP or bypass credentials in release builds. When backend login fails, diagnose the configured deployment and route; do not merely suppress the actual failure.
- Before handoff, report PR/commit, Android build, Backend CI, Chat/Group parity, APK artifact and SHA-256 when available, and any runtime failures. Never claim an unverified APK or backend is green.

This rule applies equally to every parallel chat, AI agent, and human contributor.
