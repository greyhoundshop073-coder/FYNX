# Instructions for all FYNX coding agents and workstreams

**Shared APK destination — do not change it:** `integration/fynx-single-apk-20261013`

Before editing code, every contributor/chat/agent MUST read:
0. [the permanent global engineering intelligence and builder system](docs/FYNX_GLOBAL_ENGINEERING_INTELLIGENCE.md) — this is mandatory session memory for research, architecture, engineering decisions, Nigeria/Africa realities and global quality.
1. [the single-APK integration policy](docs/SINGLE_APK_INTEGRATION_POLICY.md)
2. [the mandatory development and platform compliance rules](docs/DEVELOPMENT_POLICY_COMPLIANCE.md)
3. [the Google Play launch readiness plan](docs/GOOGLE_PLAY_READINESS.md)
4. [the backend-ready development and reconnection policy](docs/BACKEND_READY_DEVELOPMENT_POLICY.md)

**Backend-ready requirement:** Every contributor MUST follow `docs/BACKEND_READY_DEVELOPMENT_POLICY.md`. Build features so UI, typed API contracts, backend logic, storage/realtime/payment adapters, configuration, failure states and tests are ready even when production services are offline. Connect every safe/local capability now; document exact live-only blockers and reconnection steps. No fake production success, inert controls or undocumented disconnected features.

**Mandatory session-start procedure:** Read the global engineering intelligence document first, then inspect the latest canonical branch SHA, open PRs, current workflow runs, changed-file overlap, current handoff/decision records and last merged work. Verify whether each referenced PR is merged or still open. State the actual continuation point before editing. Never trust an old chat summary as current repository truth.

For every significant feature, defect, security/performance issue or architecture decision, perform the external research-and-comparison loop defined in `docs/FYNX_GLOBAL_ENGINEERING_INTELLIGENCE.md`: use credible primary sources, compare viable approaches, adapt them to FYNX, calculate capacity/cost/performance where relevant, record the decision and verify the exact implementation. Research should be risk-proportionate; never invent sources or national stereotypes.

Maintain the persistent research register and engineering decision log defined by the builder system. Update the relevant workstream handoff and certification ledger after meaningful progress so future sessions can continue without repeating or losing decisions.

Inspect the latest canonical branch head and open pull requests before starting.

**Active Owner/Admin Control Center continuation:** For the remaining work tracked by PR #527, read and follow [docs/ADMIN_CONTROL_CENTER_CONTINUATION.md](docs/ADMIN_CONTROL_CENTER_CONTINUATION.md). Resume its existing branch and grouped work; do not restart or create a parallel implementation. Keep that handoff updated as grouped work is completed.

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
