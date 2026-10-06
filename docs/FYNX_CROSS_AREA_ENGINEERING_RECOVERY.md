# FYNX Cross-Area Engineering Recovery Guide

## Purpose

This guide records a proven workflow for making safe changes in FYNX when a feature touches large existing files, GitHub Actions, or shared production code. It applies across Marketplace, Home, Chat, Status, Friends, and other areas.

## Proven method

1. Start from the latest known-green baseline.
2. Inspect the real production implementation and its callers before editing.
3. Keep the change isolated on a feature branch.
4. Do not replace a large production file wholesale when a controlled edit is possible.
5. When using GitHub Contents API, fetch the current file and use its current blob SHA. A stale SHA means the file changed; refresh before retrying.
6. Keep existing data models, navigation, callbacks, backend behavior, and user flows intact unless the task explicitly requires changing them.
7. For visual/reference upgrades, change presentation around the existing real data. Do not introduce fake products, sellers, ratings, activity, or other fabricated production data.
8. Push one coherent batch rather than many speculative edits.
9. If the branch itself does not trigger Android CI, do not assume CI is broken. Inspect `.github/workflows/android-build.yml` and use the repository's established PR-to-main verification path.
10. Treat compiler errors as evidence. Inspect the exact failing file/line and fix the underlying issue before rerunning CI; do not blindly rerun red workflows.
11. After a fix, verify every required workflow, not just one convenient check.
12. Confirm the APK was built from the exact checked-out commit when APK verification is part of the gate.
13. Only after the complete gate is green should the change be considered a proven baseline.

## Failure lessons from Marketplace Push 6/7 and final audit

- A feature branch push alone did not start the configured Android workflow. The established PR targeting `main` supplied the required CI event.
- A Marketplace reference component initially attempted to use a private production composable. The safe fix was to remove that dependency and keep the reference presentation isolated while retaining real Marketplace data/callbacks.
- A reference component also contained a missing closing brace. CI identified the exact Kotlin syntax failure; the correction was made at the reported location rather than changing unrelated code.
- A large production Marketplace panel must be treated as an integration boundary. Reference components can exist independently, but they are not considered integrated merely because they compile; the production render path must be inspected.
- GitHub file writes require the current blob SHA. Never substitute a placeholder SHA or overwrite a large file with guessed content.

## Definition of done

A cross-area change is complete only when:

- the intended production path uses the change;
- existing real data and actions remain connected;
- relevant repository checks pass;
- Android CI is green;
- the APK is produced from the verified commit when required;
- no unrelated area was changed merely to make CI pass;
- the final proven recovery method is recorded here if a new reusable failure mode was discovered.

## Rule for future chats

Read this guide before modifying a large/shared FYNX production file or troubleshooting a missing/red Android workflow. Preserve the green baseline first, investigate the actual repository state, and make the smallest complete change that can be verified end-to-end.
