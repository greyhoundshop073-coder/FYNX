# FYNX Workflow Handoff — Single APK Integration

## Canonical integration and APK rule

- The only shared Android integration destination is `integration/fynx-single-apk-20261013`.
- Every feature pull request must target that branch as its base.
- Feature branches are working branches only. A debug APK or PR artifact is diagnostic evidence, never the shared/latest FYNX APK.
- The eventual shared APK must be built from the latest approved, merged canonical commit, with commit SHA, workflow run, artifact name, APK SHA-256, install link and real install/runtime evidence recorded.
- Do not merge into canonical or publish the official APK without the user's explicit approval.

## CI trigger and verification rules

- The Android workflow is `.github/workflows/android-build.yml`; other scoped workflows include Backend CI and Chat/Group Parity + Layout Safety.
- Pull requests targeting the canonical integration branch trigger the applicable workflows according to each workflow's path and event filters. Do not assume a feature-branch push alone will run every workflow, and do not assume a missing run means the code failed.
- Inspect the actual workflow YAML and the current PR head SHA before deciding which checks should run.
- A workflow result belongs to the exact commit SHA it tested. Re-check the PR head before accepting any green result.
- Treat build, unit tests, static contracts, Android instrumentation, installed-app visual/runtime capture, backend CI and live-service probes as separate evidence.
- A skipped live Railway/Neon/payment/provider probe is **not** a successful live-service test. Record it as blocked/not run, with the exact reconnection step.
- Investigate every red job from its logs. Fix the underlying cause; never weaken or delete a required check merely to get green.
- Preserve real screenshots, UI hierarchies, runtime reports and artifact checksums. Do not claim a screen or action worked without corresponding evidence.

## Engineering delivery rules

1. Inspect the current canonical SHA, open PRs, changed files, callers and CI workflows before editing.
2. Resume the existing workstream branch where appropriate; do not create duplicate features or competing APK paths.
3. Group related app, backend, test, workflow and handoff changes into meaningful pushes.
4. Selectively port missing changes from divergent branches; do not wholesale-merge stale branches or overwrite newer work.
5. Preserve authentication, account isolation, user data and existing workstreams. Keep login open for testing; never weaken authorization to make a screen accessible.
6. Complete backend-independent behavior and local tests while services are unavailable. Keep API contracts, safe offline states, retry behavior and reconnection steps ready.
7. Separate local fixture proof from server-authenticated user data and separate a diagnostic APK from the final shared APK.
8. Do not alter production infrastructure/data without explicit approval.

## Marketplace reference upgrade rule

The Marketplace reference board is a visual/UX target, not permission to replace FYNX's working Marketplace backend or invent data. Reference components must consume the existing real Marketplace listings, seller identity, prices, locations, orders, checkout and actions. Do not fabricate products, sellers, ratings, followers, sales, activity, orders, payments or balances merely to match the reference image.

Marketplace work is an upgrade of the existing Marketplace, not a replacement. When a reference component exists separately from the production panel, verify the actual production render path before claiming the component is integrated.

## Handoff checklist

Before declaring a workstream complete, report the PR and exact head commit; Android build/tests/lint; Backend CI; Chat/Group parity; installed-app runtime evidence; APK artifact and SHA-256 when available; policy/security checks; and all live-only blockers. A feature-branch artifact is not the shared APK.

Future FYNX contributors must read this handoff together with `AGENTS.md`, the single-APK policy, development compliance rules, Google Play readiness plan and backend-ready development policy.
