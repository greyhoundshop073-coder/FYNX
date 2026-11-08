# FYNX Engineering Standards, Risk Findings and Safe Improvement Plan

**Review date:** 2026-11-08  
**Repository:** `greyhoundshop073-coder/FYNX`  
**Reviewed branch:** `integration/fynx-single-apk-20261013`  
**Purpose:** Record evidence-based engineering risks and a safe, incremental plan to improve delivery quality without damaging existing product behavior.

This is a repository review and improvement plan, not a claim that every listed risk is an active production defect. Findings below distinguish observed facts from recommended controls. No application source, backend data, payment configuration, deployment, or user records were changed as part of this review.

---

## 1. Executive assessment

FYNX already has meaningful engineering safeguards: a canonical Android integration branch, a broad set of verification scripts, Android build/runtime workflows, backend CI, and Chat/Groups parity checks. The goal is to strengthen and make those safeguards easier to understand—not replace them or start a new project.

The largest process risks are:
1. Feature completion can be confused with code implementation or a green subset of CI.
2. Shared UI/navigation/state changes can affect several features at once.
3. Parallel PRs can overlap and become difficult to integrate safely.
4. Backend outages can leave live-dependent behavior unverified unless test boundaries are explicit.
5. A large, concentrated app structure and workflow increase change-impact and maintenance costs.
6. Security, performance, release provenance and rollback need explicit gates across the product lifecycle.

## 2. Evidence-based repository observations

### Finding A — Single Gradle application module

At the reviewed integration ref, `settings.gradle.kts` includes only `:app`. This is not automatically wrong; a single-module Android app can be appropriate. However, FYNX now has many product areas and shared responsibilities. Package-level separation alone does not enforce independent Gradle dependency boundaries.

**Risk:** shared code can become tightly coupled, and broad refactors may have a large blast radius.

**Recommendation:** map dependencies first, then gradually introduce clearer package/API boundaries and extract Gradle modules only when the boundary is stable and tests prove the behavior is preserved. Do not perform a large one-shot modularization.

### Finding B — Central navigation/application state concentration

At the reviewed integration ref, `app/src/main/java/com/fynx/app/ui/FynxApp.kt` is approximately 60 KB. Its opening section contains top-level navigation/back-routing logic and state for multiple product areas, including Chat, Groups, Status, Calls and Marketplace.

**Risk:** a change to central navigation or shared state can have effects across otherwise unrelated user journeys.

**Recommendation:** create a navigation/dependency map; isolate route definitions, feature entry points, and state ownership incrementally. Keep feature behavior unchanged during extraction and require navigation regression tests.

### Finding C — Large Android workflow with broad verification coverage

The canonical `.github/workflows/android-build.yml` includes many product-specific verification scripts, which is valuable. The same file also includes a duplicated Google Cloud authentication verification step.

Its path filters include `docs/**`, meaning a documentation-only change can trigger the Android build workflow as currently configured.

**Risk:** duplicated maintenance and avoidable build cost/time; the large workflow can make it harder to identify the failed gate and its owner.

**Recommendation:** remove only verified duplicate steps and split workflow responsibilities carefully into fast source/contract checks, Android build/unit/lint, emulator/runtime certification, backend CI and release packaging. Preserve every existing check and its trigger semantics. Documentation-only changes should run documentation validation rather than a full Android build unless a specific doc is a build input. Validate workflow changes against representative PRs before relying on them.

### Finding D — Existing CI is useful but does not by itself certify every user journey

The repository has dedicated checks for areas such as network transport, group permissions, camera/post, AI security, Marketplace seller flow, Home behavior, and Chat/Groups parity.

**Risk:** a source-contract or focused regression test can pass while a user-facing journey, backend integration, device behavior, or visual issue remains untested.

**Recommendation:** connect each module's acceptance criteria to the exact automated test and manual/runtime evidence that proves it. Maintain explicit distinctions between source-verified, build-verified, emulator-verified, backend-verified and release-certified.

### Finding E — Canonical integration and feature PR coordination

The repository has one agreed canonical Android integration branch and multiple feature PRs. This is a sound foundation, but open PRs and their descriptions show that several changes have exact-head test and screenshot requirements and some intentionally overlap shared areas.

**Risk:** stale PR bases, merge conflicts, feature regressions, and treating a feature-branch test as proof of canonical integration.

**Recommendation:** use short-lived, bounded work branches where practical; declare source ownership and protected files; check the merge base and changed-file overlap before integration; run required checks on the exact final integrated SHA. Avoid broad simultaneous edits to shared navigation, theme, media, or app state.

### Finding F — Backend availability and test confidence

The Android workflow explicitly distinguishes normal feature builds from optional live backend reachability checks. This is useful because it lets some Android work continue while a backend is unavailable.

**Risk:** source/build success may be mistaken for live verification of messaging, Marketplace persistence, calls, Wallet, or other server-backed journeys.

**Recommendation:** use fake/local data sources for UI and offline work, a controlled backend test environment for contract/integration tests, and a separate live health/runtime gate. When a required backend dependency is unavailable, record the feature as CONDITIONAL — BLOCKED, not CERTIFIED — CLOSED.

### Finding G — Repository branch protection state not established by this review

The repository metadata endpoint used for this review did not expose branch-protection/ruleset configuration. Therefore this review does **not** claim that the canonical branch is unprotected.

**Recommendation:** inspect GitHub Settings → Branches / Rulesets for `integration/fynx-single-apk-20261013`. Confirm required PR review, required unique status checks, up-to-date branch policy, conversation resolution, force-push/deletion restrictions and an emergency recovery/admin path. Do not change these settings blindly; first identify the exact check names and confirm the repository's collaboration model.

---

## 3. Required engineering standards for every change

### A. Change design and impact assessment
Before implementation, every work item must state:
- User problem and measurable expected behavior.
- Acceptance criteria and out-of-scope boundaries.
- Files/components expected to change.
- Shared components and dependent screens affected.
- Data/API/schema compatibility impact.
- Security/privacy impact.
- Test plan and rollback/recovery approach.

### B. Small, reviewable, reversible changes
- Prefer one coherent behavior change per PR.
- Avoid unrelated formatting or broad refactoring in a feature fix.
- Avoid giant PRs that combine UI redesign, backend schema, payment, and navigation changes.
- Keep changes additive/backward-compatible where possible.
- Use feature flags or staged activation for risky new behavior where the architecture supports them.
- Keep a known-good commit and release artifact.

### C. Automated quality gates
For every applicable change:
1. Formatting/static checks and fast contract tests.
2. Unit tests for business logic and state holders.
3. Integration/API contract tests for data boundaries.
4. Android build and lint.
5. UI navigation/instrumentation tests for critical journeys.
6. Regression tests for impacted neighboring modules.
7. Security/privacy tests for authorization and sensitive data.
8. Performance checks for high-traffic or media-heavy paths.
9. Actual device/emulator exploratory checks for release-critical user experience.

Do not remove an existing check merely to shorten a workflow. First prove it is redundant or replace it with equivalent or stronger coverage.

### D. Data and backend safety
- No production database migrations without a reviewed migration plan, backup/restore strategy, compatibility plan and rollback procedure.
- Use expand/migrate/contract for schema changes where feasible.
- Test authorization at the server/API boundary, not only by hiding Android controls.
- Never use real payment credentials or real-money transfers in automated tests.
- Keep test accounts and fixtures separate from real user data.
- Document which functionality can work offline and how queued actions reconcile safely.

### E. Security and privacy
Adopt OWASP MASVS as the mobile security baseline and map relevant controls to FYNX features:
- Secure storage and cryptography.
- Authentication and authorization.
- Network transport.
- Android platform interaction and exported components.
- Code integrity and resilience.
- Privacy and data minimization.

Additional FYNX-specific controls:
- AI must not receive private Wallet, payment, transfer or receive-history data.
- Users must not access other users' private chats, attachments, or account-scoped seller data.
- Owner/Admin capabilities must be enforced server-side.
- Logs, analytics and crash reports must not expose tokens, passwords, payment secrets or private message content.

### F. Performance and reliability
Set measurable baselines before optimization:
- Cold start and warm start.
- Frame/jank behavior on Home and media-heavy screens.
- Memory use during camera, video and image editing.
- Image/video cache size and cleanup behavior.
- Network request latency, retries and failure rates.
- Crash-free sessions and ANR rate when real telemetry becomes available.

Use Android profiling, Macrobenchmark and Baseline Profiles where they provide measurable value. Do not optimize based only on subjective impressions.

### G. Release management
Every release candidate must have:
- Exact canonical commit SHA and versionCode/versionName.
- Reproducible build workflow and artifact link.
- Build/test/lint/runtime/security results.
- Actual screenshots and a concise release test report.
- Known limitations and backend dependency status.
- Previous known-good APK and commit.
- A documented rollback/recovery decision.

For mobile apps, rollback may mean stopping rollout, reverting the source and publishing a corrected higher-version APK; do not assume users can be downgraded automatically.

---

## 4. Proposed safe improvement roadmap

### Phase 0 — Establish current truth (no product behavior changes)
- Refresh the module completion register from current PRs, branch heads and workflow runs.
- Inspect canonical branch protection/rulesets in GitHub Settings.
- Build a dependency map for navigation, theme, media, networking, account state and shared UI.
- Identify large/high-churn source files and overlapping PR file lists.
- Record baseline build time and current required test coverage.

**Exit:** every active workstream has a named owner, bounded scope, current SHA, risk list and acceptance criteria.

### Phase 1 — Protect the integration lane
- Confirm required status checks and review policy.
- Define a merge checklist and exact-head verification.
- Establish a known-good canonical APK/artifact and provenance record.
- Require a regression plan for shared-component changes.
- Document rollback and emergency hotfix procedure.

**Exit:** no feature can silently bypass the agreed integration and release gates.

### Phase 2 — Improve feedback without weakening tests
- Separate fast checks from expensive emulator/runtime checks.
- Remove the duplicated workflow step after confirming no dependency on it.
- Stop running the full Android build for documentation-only edits unless needed.
- Preserve all current test scripts and their intended PR/push coverage.
- Make failures attributable to a module and acceptance criterion.

**Exit:** faster, clearer CI with equivalent or stronger test coverage.

### Phase 3 — Reduce architectural risk incrementally
- Map FynxApp navigation and state ownership.
- Extract one stable boundary at a time.
- Add tests before moving code.
- Avoid changing UI behavior while extracting.
- Consider Gradle modules only where boundaries and dependency direction are proven.

**Exit:** smaller, independently testable responsibilities without regressions.

### Phase 4 — Raise product quality
- Complete module certification one module at a time.
- Add missing failure/retry/offline and lifecycle coverage.
- Adopt the OWASP MASVS control checklist.
- Establish performance baselines and budgets.
- Verify accessibility, responsive layouts and low-end-device behavior.

**Exit:** each release-critical module has test evidence, security coverage and a certified status.

### Phase 5 — Release readiness
- Verify the exact canonical APK.
- Run release smoke tests and critical user journeys.
- Confirm privacy/security and financial controls.
- Check storage limits, backup/recovery, monitoring and incident response.
- Publish only when the release gate is satisfied.

**Exit:** a traceable, tested release candidate with a known recovery path.

---

## 5. Rules that prevent damage to existing work

1. No broad refactor and feature redesign in the same PR unless inseparable and explicitly approved.
2. No replacing a working implementation before the new implementation passes parity tests.
3. No deleting old tests or verification scripts just to obtain a green build.
4. No database/schema changes without backup, migration and rollback planning.
5. No UI-only security controls where the backend must enforce authorization.
6. No claiming a live feature works when its backend was not available for verification.
7. No merging based on a stale PR head or checks from a different SHA.
8. No release APK from a feature branch.
9. No reopening certified modules for unrelated preferences; record improvements separately.
10. If a change causes a regression, stop the integration sequence, isolate the change, fix or revert it, and rerun affected checks.

## 6. Engineering scorecard

Track these measures monthly; do not use raw commit count as a proxy for progress.

| Metric | What to learn |
|---|---|
| Change failure rate | How often changes cause regressions or rollbacks |
| Time to restore | How quickly a defect is contained and corrected |
| Lead time for changes | How long reviewed work takes to reach the canonical build |
| Rework share | How much effort goes into correcting previously completed work |
| Certification coverage | Share of release-critical modules with complete evidence |
| Critical journey pass rate | Whether the core user flows work on the release candidate |
| Security findings | Open high/critical issues and time to resolution |
| Performance budget | Startup, jank, memory and media-cache behavior |

Interpret metrics together. Do not reward developers for pushing more changes if failure and rework rise.

---

## 7. Authoritative references

- Android Developers — Guide to app architecture: https://developer.android.com/topic/architecture
- Android Developers — Recommendations for Android architecture: https://developer.android.com/topic/architecture/recommendations
- Android Developers — Modularization: https://developer.android.com/topic/modularization
- GitHub Docs — Protected branches: https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches
- DORA — Continuous integration: https://dora.dev/capabilities/continuous-integration/
- DORA — Test automation: https://dora.dev/capabilities/test-automation/
- DORA — Continuous delivery: https://dora.dev/capabilities/continuous-delivery/
- Google SRE — Release engineering: https://sre.google/sre-book/release-engineering/
- Google SRE Workbook — Canarying releases: https://sre.google/workbook/canarying-releases/
- OWASP — Mobile Application Security Verification Standard: https://mas.owasp.org/MASVS/

## 8. Final decision

FYNX does not need a disruptive rewrite. It needs stronger engineering controls around the application that already exists.

The safest sequence is: establish evidence → protect the canonical integration lane → improve CI feedback → reduce coupling incrementally → certify features → release with rollback and security evidence.

**Do not start architectural extraction, workflow restructuring, branch-rule changes, or database changes as a broad batch. Each should be a separately reviewed, test-backed improvement with a measurable exit condition.**
