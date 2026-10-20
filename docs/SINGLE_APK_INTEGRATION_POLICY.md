# FYNX Single-APK Integration Policy

**Canonical integration branch:** `integration/fynx-single-apk-20261013`

This branch is the one shared source of truth for the FYNX Android APK and the backend revision paired with it. The date in the branch name is historical; do not create a newer “main APK” branch just because the date is old.

## Rules for every FYNX workstream and chat

1. **Target the canonical integration branch.** Start work from its latest commit, use a dedicated feature branch, and open a pull request whose base is `integration/fynx-single-apk-20261013`. Do not target `main`, a stale integration branch, or another chat's feature branch for delivery into the APK.
2. **Never deliver a feature-branch APK as the shared APK.** A local APK, PR artifact, or artifact from another branch is for diagnostics only. The shared release candidate must be built from the canonical integration branch after the change has merged.
3. **Do not merge whole divergent branches blindly.** Inspect commits, file diffs, callers, tests, and existing fixes. Port only the missing compatible changes. Preserve work already merged by other workstreams.
4. **One owner per workstream, shared destination.** Home/feed, Avatar/profile, Chat/Groups, Marketplace/My Shop, backend/network and build/release work may proceed on separate feature branches, but all deliver through the canonical branch. Before touching files, inspect current integration history and open PRs; coordinate overlapping files and do not overwrite another workstream's newer implementation.
5. **Require evidence before merging.** Review the diff and relevant callers; run the appropriate Android build, backend CI, and Chat/Group parity/layout checks. Fix red checks before continuing. A successful compile is not proof that runtime networking works.
6. **Build and identify the APK from the merged commit.** Record the exact canonical commit SHA, workflow run, artifact name, APK SHA-256, and installable download link. Never describe an APK built from an older or feature branch as the latest shared APK.
7. **Keep backend and APK aligned.** Use Railway + Neon, not the retired Render endpoint. Verify the Railway service's configured repository, canonical branch and deployed commit; confirm health/readiness and relevant authenticated API routes. Test auth using the existing supported login flow—do not introduce OTP or change the working login flow as a shortcut. Staging a Railway change is not the same as deploying it.
8. **Keep the integration path open.** After each workstream merges, update this branch and let subsequent chats branch from its latest head. Do not close the shared integration branch or establish a competing APK branch. Add new changes as normal feature PRs into this same destination.

## Handoff template for every workstream

- Workstream / push number:
- Feature branch and PR (base must be the canonical branch):
- Files and behavior changed:
- Relevant checks and exact workflow links:
- Merge commit on canonical branch:
- Runtime/backend verification (what was actually tested):
- Remaining work / known red checks:

## Current deployment caution

The Railway `fynx-backend-testing` service can have staged changes. Do not claim that staged code is live. Verify the deployed commit and live endpoints after deployment, and do not switch production traffic or introduce paid infrastructure without explicit authorization.
