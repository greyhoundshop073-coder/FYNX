# Instructions for all FYNX coding agents and workstreams

**Shared APK destination — do not change it:** `integration/fynx-single-apk-20261013`

Before editing code, read [the single-APK integration policy](docs/SINGLE_APK_INTEGRATION_POLICY.md) and inspect the latest canonical branch head and open pull requests.

- Start every work branch from the latest `integration/fynx-single-apk-20261013`.
- Every feature PR must use `integration/fynx-single-apk-20261013` as its base. Do not merge work into `main` or a competing APK branch as the delivery path.
- Do not create or announce a separate “latest APK” from a feature branch. The shared APK is built only from the latest merged canonical branch commit.
- Preserve other workstreams' changes. Inspect the diff and callers; selectively port missing work instead of merging divergent branches wholesale.
- Keep Railway + Neon aligned to the canonical branch without pinning Railway to a one-off commit. Verify deployed commit and health/readiness; never treat staged or failed deployment code as live.
- Do not alter the working login flow or add OTP as a shortcut. Fix backend auth integration without weakening authorization.
- Before handoff, report PR/commit, Android build, Backend CI, Chat/Group parity, APK artifact and SHA-256 when available, and any runtime failures. Never claim an unverified APK or backend is green.

This rule applies equally to every parallel chat, AI agent, and human contributor.
