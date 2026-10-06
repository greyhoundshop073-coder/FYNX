# FYNX Workflow Handoff — CI and Marketplace

## Canonical CI trigger behavior

- The canonical Android workflow is `.github/workflows/android-build.yml`.
- It runs automatically for pushes to `main` when the configured paths match.
- It runs automatically for pull requests targeting `main` when the configured paths match.
- A push to an isolated feature branch by itself does **not** create an Android workflow run.
- Therefore, after a feature-branch push, open a PR targeting `main` before judging CI status.
- Do not create a duplicate or parallel workflow just to make an isolated branch run.

## FYNX development rule

1. Inspect the real repository and current callers before editing.
2. Use an isolated branch for the work.
3. Keep the change consolidated by workstream.
4. Push the completed change to the branch.
5. Open/target the PR against `main` so the canonical workflows run.
6. Investigate every red job from its actual logs before changing code.
7. Do not call a push green until the relevant workflow is actually green.
8. Preserve the green baseline before beginning the next batch.
9. Only merge after the required CI evidence is green.

## Marketplace reference upgrade rule

The Marketplace reference board is a visual/UX target, not permission to replace FYNX's working Marketplace backend or invent data. Reference components must consume the existing real Marketplace listings, seller identity, prices, locations, orders, checkout and actions. Do not fabricate products, sellers, ratings, followers, sales, activity, orders, payments or balances merely to match the reference image.

Marketplace work is an upgrade of the existing Marketplace, not a replacement. When a reference component exists separately from the production panel, verify the actual production render path before claiming the component is integrated.

## Recent Marketplace CI lesson

Pushes made directly to isolated Marketplace branches initially showed no workflow because the workflow trigger targets `main` pushes and `main` pull requests. The correct recovery was to create a PR targeting `main`, not to guess at a build failure or create another workflow.

Future FYNX chats must read this handoff before starting work that depends on CI behavior, so the workflow-trigger discovery is not lost when work moves between chats.
