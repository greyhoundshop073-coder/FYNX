# Instructions for all FYNX coding agents and workstreams

**Shared APK destination — do not change it:** `integration/fynx-single-apk-20261013`

Before editing code, every contributor/chat/agent MUST read:
1. [the single-APK integration policy](docs/SINGLE_APK_INTEGRATION_POLICY.md)
2. [the mandatory development and platform compliance rules](docs/DEVELOPMENT_POLICY_COMPLIANCE.md)
3. [the Google Play launch readiness plan](docs/GOOGLE_PLAY_READINESS.md)

Inspect the latest canonical branch head and open pull requests before starting.

- Start every work branch from the latest `integration/fynx-single-apk-20261013`.
- Every feature PR must use `integration/fynx-single-apk-20261013` as its base. Do not merge work into `main` or a competing APK branch as the delivery path.
- Do not create or announce a separate “latest APK” from a feature branch. The shared APK is built only from the latest merged canonical branch commit.
- Preserve other workstreams' changes. Inspect the diff and callers; selectively port missing work instead of merging divergent branches wholesale.
- Keep Railway + Neon aligned to the canonical branch without pinning Railway to a one-off commit. Verify deployed commit and health/readiness; never treat staged or failed deployment code as live.
- Do not alter the working login flow or add OTP as a shortcut. Fix backend auth integration without weakening authorization.
- **Platform compliance is a development requirement, not a final launch task.** Every feature must follow the applicable Google Play, Android, privacy, security, user-generated-content, payments, AI-provider, and other relevant platform rules. Read and follow `docs/DEVELOPMENT_POLICY_COMPLIANCE.md` before implementing or modifying features.
- Any change to data collection/sharing/retention, permissions, SDKs, account creation/deletion, moderation/reporting/blocking, payments/wallet, AI processing, ads, subscriptions, age targeting, or other regulated behavior must update the compliance inventory/checklist and include verification evidence.
- Never invent a privacy policy, deletion URL, Data safety answer, reviewer credential, legal approval, or claim of compliance. Record unresolved items as blockers and verify implementation against current official policies.
- Before handoff, report PR/commit, Android build, Backend CI, Chat/Group parity, APK artifact and SHA-256 when available, policy/security checks, and any runtime failures. Never claim an unverified APK, backend, or compliance state is green.

This rule applies equally to every parallel chat, AI agent, and human contributor.
