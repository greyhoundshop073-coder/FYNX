# FYNX Reference Board and APK Delivery Contract

Status: ACTIVE — this file is a durable handoff for every future FYNX session.
Repository: `greyhoundshop073-coder/FYNX`
Primary test candidate at creation: `build/fynx-one-apk-integrated-20261009` at `b6a0f6e4f75fb1a940dc0bbf7ee3c45b2c29150f`.

## Non-negotiable delivery rule

A feature is not delivered because a file was added, a branch was pushed, a PR was opened, or a fast CI job passed. It is delivered only when:

1. The feature's source commit is included in the selected APK candidate.
2. The real screen/navigation entry point calls the implementation (not an unused reference component).
3. Loading, empty, error, offline and success states are handled as applicable.
4. The relevant full Android build and instrumentation/runtime checks pass on the exact candidate commit.
5. The exact APK artifact and commit SHA are recorded here, and the resulting screen/flow is checked against the reference board.
6. No unrelated green feature or bottom navigation destination was removed or regressed.

Do not say “done”, “green”, or “in the APK” when any required link is missing. Mark it BLOCKED or NOT VERIFIED and identify the specific missing proof.

## Reference image source of truth

The user's supplied FYNX reference images are the visual authority. Match structure, hierarchy, spacing, placement and interaction patterns while preserving FYNX branding and existing working behavior. Do not replace real functionality with static mock content.

**Canonical image location (add the original image, unchanged):**
- Marketplace board: `docs/reference/images/fynx-marketplace-reference-board.png`
- Other screen boards, when supplied: `docs/reference/images/fynx-<screen>-reference.png`

Do not recreate, redraw, or substitute an approximation and label it the original. If the original attachment is not accessible to the current session, record that fact and ask for the image to be re-attached; then commit the exact original file at the path above. Keep this document and image in Git so future chats can recover the visual target from the repository itself.

## Marketplace reference checklist

The agreed Marketplace board covers six connected surfaces. Verify the actual runtime UI, not only composable declarations.

| Surface | Required reference elements | Delivery status |
|---|---|---|
| Marketplace Home | Dark navy FYNX header/branding; search prompt “Search products, sellers or categories”; category chips; Great Deals/Better Prices; Recommended for you; Popular near you; Explore categories; New on FYNX; floating Sell action | NOT VERIFIED against original image in current APK |
| All Products | Two-column product-card grid, coherent image/price/title hierarchy and working product open | NOT VERIFIED against original image in current APK |
| Product Details | Product media, title, price, seller identity, relevant purchase/contact actions | NOT VERIFIED end-to-end |
| Search | Search products, sellers and categories; real result/empty/error behavior | PARTIAL — existing search code exists; reference search integration was on PR #116, not yet part of the integrated candidate at its base |
| Seller Profile | Seller details/reputation and products; working profile navigation | NOT VERIFIED end-to-end |
| Empty Marketplace | Genuine empty state and useful seller/start action; filtered-empty state can clear filters | Code exists; runtime/reference comparison NOT VERIFIED |

Keep existing Marketplace operations: backend listing fetch/search, seller flow, product detail, cart and orders, multi-product checkout, NGN/USD separation and financial safeguards. Visual integration must not remove these.

## Branch and PR reconciliation

Before implementation, compare the source commit of each candidate with the target branch. Do not merge an old branch blindly: inspect its diff and callers, resolve conflicts against the current candidate, and preserve stronger/current implementations.

Known branch/PR evidence at creation:
- PR #76 added reusable reference components and is merged.
- PR #79 contains a separate production integration attempt and is open with conflicts.
- PR #72 is another reference UI attempt and is open with conflicts.
- PR #115 is the consolidated Home + Marketplace NGN/USD APK candidate and is open against `main`.
- PR #116 is a Marketplace reference/search and cart-badge update against the consolidated candidate; it is open and unmerged.
- A green fast-only PR #116 workflow is not a substitute for full runtime verification.

Re-check live GitHub status before acting; this list is a handoff snapshot, not a claim that status can never change.

## Exact APK proof ledger

Update this table whenever a new candidate is produced. Use full commit SHAs and run URLs, not branch names alone.

| Candidate commit SHA | APK artifact/run | Full build + lint | Android instrumentation/screenshots | Reference comparison | Device/user-flow test | Overall |
|---|---|---|---|---|---|---|
| `b6a0f6e4f75fb1a940dc0bbf7ee3c45b2c29150f` | Actions run `37925212770` succeeded; artifact uploaded by that run | PASS | PASS in workflow | NOT VERIFIED against original board image | NOT VERIFIED on user's physical phone | NOT COMPLETE |
| `fef80413427888fe4a0b31dcf7a636eb4b0e0e69` | Actions run `37968822711`; exact-commit artifact uploaded | Fast checks PASS; full build skipped | SKIPPED | NOT VERIFIED | NOT VERIFIED | NOT COMPLETE |

Run references:
- [Integrated candidate runtime run #37925212770](https://github.com/greyhoundshop073-coder/FYNX/actions/runs/37925212770)
- [Marketplace feature-branch run #37968822711](https://github.com/greyhoundshop073-coder/FYNX/actions/runs/37968822711)
- [Main runtime failure #37888398484](https://github.com/greyhoundshop073-coder/FYNX/actions/runs/37888398484)

## Required work order

1. Preserve the original reference image in this repository and link it from this document.
2. Select one APK candidate branch. The candidate must include the intended Home, login and Marketplace work together; do not leave required features on disconnected branches.
3. Build an integration ledger for every feature requested or previously claimed: source commit → branch/PR → merged into candidate → active caller/entry point → exact APK SHA → runtime evidence.
4. Resolve conflicts by reviewing actual diffs. Do not cherry-pick or merge blindly and do not reset/rebuild the app from scratch.
5. Integrate Marketplace reference components into the active Marketplace entry point while preserving backend and checkout behavior. Compare each of the six surfaces against the original board.
6. Run full build/test/lint and Android instrumentation/runtime screenshot capture on the exact final candidate commit. Inspect the screenshots and UI hierarchy for the reference surfaces.
7. Inspect failed job/step/log and fix red results before any next push. Do not repeatedly push speculative fixes.
8. Record the final artifact name/link and SHA here. The user must be able to identify which APK to install and what to test.
9. Keep the existing bottom navigation destinations and order untouched: Home, Chat, Friends, Marketplace, More.

## Session handoff template

Every future FYNX chat should read this file and the referenced original image before editing code, then report:
- selected candidate branch + full SHA;
- what is already present and actually wired;
- what is missing or disconnected;
- exact changes made;
- workflow run + failed/succeeded steps;
- APK artifact for the same SHA;
- runtime/reference evidence;
- unresolved blockers, if any.

Never start another redesign or duplicate implementation before completing this reconciliation.
