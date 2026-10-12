# FYNX repository instructions for GitHub Copilot

Follow `/AGENTS.md` and `/docs/FYNX_RELEASE_GATE.md` before editing and after every push. These rules are mandatory.

- Investigate the live branch, exact commit, current implementation, callers, tests and workflows before changing code.
- Trace UI-to-backend/storage-to-UI behavior; preserve existing working features and avoid unrelated changes.
- Own blockers: inspect exact failure logs, determine root cause, research reliable documentation when needed, implement the smallest safe fix, rerun checks, and monitor required workflows to completion. Do not hand routine debugging back to the user.
- Never bypass tests, weaken verifiers, fake data, or disable security/payment protections to get green CI.
- For relevant social features, research how established platforms behave and compare actual user journeys; separate verified facts from assumptions and design for FYNX rather than copying blindly.
- After each push, inspect the exact SHA and diff; run relevant tests and investigate every required failure.
- For user-visible Android changes, verify the exact-commit APK at runtime where possible, exercise the changed journey, and inspect genuine runtime screenshots for visual work. Source presence or green compilation alone is not proof.
- If a required check cannot run, report NOT VERIFIED with the exact external blocker. Never call pending/skipped/red checks green.
- Do not claim completion until the release gate and applicable runtime/APK checks pass. Report commit, changed files, CI, runtime evidence, regressions and remaining work.


## Substantial delivery rule
- Measure progress by working features in the APK, not by number of pushes.
- Plan each delivery as a complete, coherent feature batch. Include related UI placement, interactions, state/data wiring, failure/empty/offline handling, and tests wherever applicable.
- Avoid repeated tiny pushes for parts of the same feature. Make one substantial, reviewable push when safe. Separate a small fix only when it is needed to unblock a failed gate or prevent a regression, and state the reason.
- Never enlarge a batch with unrelated or speculative edits. Preserve protected/working areas.
- Confirm the exact pushed commit is the one built into the tested APK, then verify real placement and behavior. CI green or code present is not enough.
- Track what is runtime-verified, merely pushed, missing, or externally blocked so work is not falsely repeated or declared done.
