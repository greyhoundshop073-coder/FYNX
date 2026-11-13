# FYNX Backend-Ready Development & Reconnection Policy

**Applies to every feature, chat, agent and contributor.** The goal is to make FYNX usable and testable while the production backend is unavailable, while ensuring that restoring Railway/Neon and media services later is configuration, deployment and verification work—not a rewrite of every screen.

## Non-negotiable rule

Every feature must be built with a clear, documented boundary between the Android/UI layer and its backend, storage, payment, notification or realtime provider. Implement and connect every part that can safely work now. For anything that genuinely requires a live service, finish the client contract, server contract, configuration, error/retry states and tests now; record the exact external dependency. Do not leave unexplained dead buttons, placeholder callbacks, TODO-only integrations or undocumented disconnections.

**Do not fake production connectivity.** No fabricated users, balances, orders, reports, messages, media, successful payments, admin actions or pretend server responses. A local preview or mock is allowed only in an explicitly labelled development/test mode, isolated from production data and mutations.

## Required architecture for every feature

1. **UI is provider-independent.** Screens call a feature repository/use-case interface, not raw URLs, SQL, provider SDKs or storage internals. Keep loading, success, empty, permission-denied, offline, timeout, server-error and retry states explicit.
2. **One network/configuration path.** Use the existing shared backend client and centralized environment/base-URL configuration. Never hardcode a production host in a screen, create a second API client, or put credentials/secrets in the APK or repository.
3. **Typed API contracts.** Define request/response models, field names, nullability, pagination, status/error codes and ownership rules. Keep Android and backend contracts aligned. Document routes and auth requirements in the relevant API documentation or feature handoff.
4. **Backend implementation is production-shaped.** Use authenticated routes, server-side authorization and ownership checks, validation, bounded inputs/results, rate limits where appropriate, database transactions/constraints and idempotency for repeatable mutations. Never rely on client-side role checks as security.
5. **Storage is abstracted.** UI/features use the existing media/storage client abstraction. Persist stable media IDs or provider-neutral object references where possible; do not bind business records to temporary signed URLs or one vendor's public URL format. Define upload, finalize, fetch, delete, authorization, size/type limits and failure/retry behavior.
6. **Realtime is recoverable.** Realtime subscriptions are an enhancement over durable authenticated fetch/send APIs. Define reconnect, resubscribe, duplicate suppression, ordering/acknowledgement and foreground/background recovery. A WebSocket outage must not silently destroy durable messages or state.
7. **Configuration is externalized.** Provider endpoints, feature flags and non-secret environment settings are centrally configured. Secrets belong only in deployment secret stores/GitHub Actions secrets, never source control or Android resources. Document required variable names, purpose and environment without recording secret values.
8. **Database changes are repeatable.** Add versioned/idempotent migrations, indexes, constraints and rollback/compatibility notes as appropriate. Do not rely on process memory for durable ownership, queues, roles, balances or application state.
9. **User actions remain honest.** If a live dependency is unavailable, explain that clearly in the UI, preserve safe user input where appropriate, provide retry, and disable only the action that cannot safely complete. Never show success until the server/provider confirms it.

## What to complete while production services are offline

Contributors MUST still complete all work that does not require live production credentials or live user data:

- Finish the UI, navigation, validation, accessibility, responsive layouts and all local state transitions.
- Implement the typed Android repository/client methods and matching backend routes/contracts.
- Implement migrations, authorization/ownership rules, validation and server-side business logic.
- Add unit tests, backend tests, API contract tests and deterministic local/in-memory test fixtures.
- Add development-only mock/test adapters behind explicit dependency injection or build/test configuration; production builds must never silently fall back to mocks.
- Add offline, timeout, empty, denied, malformed-response and retry handling.
- Add safe local persistence/queueing only where the feature's semantics support it; define replay, expiry, deduplication and conflict rules before queueing mutations.
- Add diagnostics that distinguish configuration missing, DNS/TLS/network failure, unhealthy service, authentication failure, authorization denial, schema mismatch and provider failure.
- Add CI checks that validate the contracts and build without needing production credentials.
- Write a concise reconnection checklist naming the exact service, environment variables, migration/deploy step, endpoint/permissions and live test required.

## What must wait for a live service

Only actions whose correctness depends on a real external system or production account may remain blocked—for example: real login/session validation, sending a real push notification, actual Paystack charge/refund/settlement, live media upload/download against the production bucket, live realtime delivery, and real Owner/Admin actions against production data.

For each blocked item, document:
- exact screen/action and feature owner;
- exact API route/provider operation and expected request/response;
- authentication, role and ownership requirements;
- configuration/secrets required (names only);
- database migration/deployment dependency;
- safe test procedure and expected evidence;
- current blocker and how to verify it is resolved.

Do not label an entire feature “disconnected” when only its live-provider verification is blocked. Mark each capability as **implemented and locally tested**, **contract-ready / awaiting live service**, **live verified**, or **blocked with reason**.

## Required verification before a feature push

- Inspect existing implementation, callers, shared clients, backend routes, migrations, open PRs and CI before editing. Extend existing architecture; do not create parallel clients, duplicate endpoints or competing implementations.
- Verify every visible control has a real action or a clear disabled/explanation state. No inert buttons.
- Run relevant Android unit/lint/build checks, backend tests, contract checks and feature-specific regression tests.
- Test both configured and deliberately unavailable backend modes. Confirm no crash, fake success, data loss or unsafe retry.
- Verify authorization and account isolation for every protected route.
- Keep changes grouped by feature and push only after local/static verification. Investigate and fix red CI; do not describe a failed or skipped check as green.
- Update the feature handoff/status matrix with exact commit, tests, live-only blockers and reconnection steps.

## Backend restoration: required reconnection sequence

When Railway/Neon and media/realtime/payment providers are available again, do this in order:

1. Confirm the canonical deployment target and environment; verify deployed commit, health and readiness endpoints.
2. Provision required secrets/configuration in the hosting platform and GitHub Actions secret store. Never commit values.
3. Apply and verify versioned database migrations, indexes and constraints; confirm expected schema and data ownership.
4. Verify backend-to-database and backend-to-object-storage connectivity using non-destructive health checks.
5. Run authenticated API contract/smoke tests using dedicated test accounts and provider sandbox credentials.
6. Verify upload/finalize/fetch/delete for media; verify realtime reconnect and durable fallback; verify notifications and payment sandbox flows separately.
7. Run complete real-user journeys from the canonical Android APK, including authorization, error, retry and account-isolation cases.
8. Record evidence, deployed commit, environment, test account class (never credentials), and any remaining production approval/provider blockers.
9. Only then mark the capability **live verified**. Do not claim that a healthy backend alone proves every feature is connected.

## Required feature handoff template

Every feature PR or workstream handoff must include:

- **Feature / owner:**
- **Android entry point and repository:**
- **Backend route(s) and authorization:**
- **Database migration/schema:**
- **Media/realtime/payment/notification dependencies:**
- **Configuration names required (no values):**
- **Local tests and results:**
- **Unavailable-service tests and results:**
- **Live verification:** not run / blocked / passed, with evidence
- **Remaining reconnection steps:**
- **Known risks and next owner:**

## Scope and safety

This policy does not authorize broad rewrites, premature multi-region infrastructure, production data mutation, bypassing login, weakening authorization, or merging another workstream's code. Preserve the canonical APK branch and all existing working systems. Make the smallest coherent preparation relevant to the feature being changed, and document larger shared dependencies for a dedicated integration task.
