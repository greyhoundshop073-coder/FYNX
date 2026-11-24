# Instructions for all FYNX coding agents and workstreams

**Shared APK destination — do not change it:** `integration/fynx-single-apk-20261013`

Before editing code, every contributor/chat/agent MUST read:
1. [the single-APK integration policy](docs/SINGLE_APK_INTEGRATION_POLICY.md)
2. [the mandatory development and platform compliance rules](docs/DEVELOPMENT_POLICY_COMPLIANCE.md)
3. [the Google Play launch readiness plan](docs/GOOGLE_PLAY_READINESS.md)
4. [the backend-ready development and reconnection policy](docs/BACKEND_READY_DEVELOPMENT_POLICY.md)

**Backend-ready requirement:** Every contributor MUST follow `docs/BACKEND_READY_DEVELOPMENT_POLICY.md`. Build features so UI, typed API contracts, backend logic, storage/realtime/payment adapters, configuration, failure states and tests are ready even when production services are offline. Connect every safe/local capability now; document exact live-only blockers and reconnection steps. No fake production success, inert controls or undocumented disconnected features.

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

## Multi-chat engineering protocol — mandatory for every session

Every ChatGPT conversation, coding agent, and human contributor MUST follow [docs/MULTI_CHAT_ENGINEERING_PROTOCOL.md](docs/MULTI_CHAT_ENGINEERING_PROTOCOL.md) and check [docs/MULTI_CHAT_WORKSTREAM_REGISTER.md](docs/MULTI_CHAT_WORKSTREAM_REGISTER.md) before starting or resuming work.

- Multiple parallel chats are explicitly supported. Do not ask the owner to use only one chat. Each chat must have a clearly bounded workstream and a named/recorded owner label (for example, “Chat — Video Calls”); ownership is coordination metadata, not a claim about a person's identity.
- Before editing, check the shared register, current canonical head, open PRs, and overlapping files. Claim/update a workstream in the register before making changes. If another active workstream owns overlapping files or shared contracts, coordinate first; do not silently race or overwrite.
- Use one dedicated feature branch per workstream, created from the latest canonical branch. Feature branches and PRs are separate; all approved work converges through PRs into the single canonical integration branch.
- Keep the register and feature handoff current at each meaningful checkpoint and before a chat is abandoned, becomes slow, or hands work to another chat. A replacement chat must resume from recorded branch/PR/commit and verify current state rather than restart.
- The register is a coordination aid, not proof of completion. Only recorded test evidence and current GitHub/CI/runtime results establish status. Never infer that a workstream is active, merged, green, or complete from an old entry.
- Do not edit the canonical branch directly for feature work. Documentation coordination changes also use a branch and PR unless the repository owner explicitly directs otherwise.
