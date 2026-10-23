# FYNX Marketplace / My Shop — Push Register 1–8

## Scope and guardrails

- Workstream: Marketplace and My Shop only.
- Feature branch: `fix/marketplace-category-discovery-20261023`
- Review PR: [#347 — Marketplace Pushes 1–8](https://github.com/greyhoundshop073-coder/FYNX/pull/347)
- Integration target: `integration/fynx-single-apk-20261013`
- This branch does not take ownership of Home/avatar or Chat/Groups files.
- Do not merge or hand out an APK until the integration checks are green and the separate Chat/Groups workstream is complete.

## Push register

### Push 1/8 — Publish and seller inventory confirmation
**Finding:** Public Marketplace discovery intentionally excludes the signed-in seller's own listings. A successful create could therefore fail to return the new listing if the client immediately searched public discovery.

**Change:** Listing creation now confirms the new listing through the seller-owned `myListings` endpoint. A regression assertion covers this contract.

**Commits:** `62687b85e5e0483c96be3d10a607b76ae0ddc38e` (client fix already on this branch); `9c5282026bcc17cd8a6793ad1c9a8920b0df5b81` (regression guard).

### Push 2/8 — Active inventory and soft-deleted products
**Finding:** The backend retains soft-deleted listings as inactive history. My Shop must not display those entries as active inventory after refresh or reopening.

**Change:** Seller inventory refresh filters out inactive listings, and the seller-flow verifier guards the behavior.

**Commits:** `d5f72cb64441ab9f17ab7309b690ac125142feca`, `3193ccf713d9322515e06a924ca6c03699af979c`, `923dc4e981dc02ac9b19a505f55c2459675cbb57`.

### Push 3/8 — Product search and local shop discovery
**Finding:** The fallback listing search matched title/description and seller identity, but it omitted shop name, location, and category. That made results inconsistent with the main discovery endpoint.

**Change:** The fallback query now searches those fields with parameterized ILIKE terms. The regression gate checks both search paths.

**Commits:** `a9c1a8e9f5b611f877d992a858f41f2088e1a27b`, `33bf8e5de647cb6a140536f5b95fceb8f7bbcb47`.

### Push 4/8 — Honest My Shop loading and retry
**Finding:** An inventory request failure could look like a genuine empty shop.

**Change:** My Shop now distinguishes a load failure from an empty inventory and offers a retry button. The seller-flow verifier guards the error state.

**Commits:** `1117c89465f2e00aeae88c97c12b70a33cb2e3c7`, `0538bbda363c7bc2986632f79d5a87fb06f6349d`.

### Push 5/8 — Multi-item checkout quote lock order
**Finding:** The create-order route already locks listing rows in ascending listing-ID order, but the multi-item quote route walked the buyer's cart order. Concurrent quotes for the same listings in reverse order could contend in inconsistent lock order.

**Change:** The quote route now sorts by listing ID before its transactional row locks; the lifecycle verifier checks both quote and order paths.

**Commits:** `817fed548639b0a875b7b8107ab1c532f2c35454`, `3ce37cc360dee74cb132e99a255ddb81305c35c3`, `f80cde26ebdc326ade88fdae04d9533fdd5b3b84`.

### Push 6/8 — Buyer-side driver and pickup details recovery
**Finding:** The buyer order view loaded the assigned driver's phone, public pickup point, and optional vehicle details, but silently ignored a failed delivery-details request.

**Change:** The buyer order view now shows loading and error states and provides a retry action. This keeps the agreed bus-stop/roadside pickup details recoverable instead of silently disappearing after a network failure.

**Commits:** `9a7b0663525b81a38fc3ad0a95e76d41852fed18`, `e41ac69578b42ad4afb6833e05f95eb322b95dee`.

### Push 7/8 — Protected refunds and inventory-safe webhook reconciliation
**Findings:**
- Child orders in a multi-product checkout share their payment reference on the checkout-group record rather than each child's `marketplace_orders.payment_reference`. A protection refund lookup that only read the child field could reject a valid refund.
- Several child refunds can share a single Paystack transaction reference; the webhook must identify the specific refund operation instead of choosing an arbitrary child.
- Replayed `refund.processed` events could repeat a reservation decrement.
- A refund after order completion must not release inventory reservation that had already been consumed at completion.

**Changes:** Refund resolution falls back to the group's verified payment reference when the group schema is available; webhook matching prefers the provider refund ID, declines to guess when a shared reference is ambiguous, ignores an already-succeeded processed refund, and only releases a reservation for pre-completion order states.

**Commits:** `16b28f8d919e095463a9bedb1f102637a8670a6e`, `2c5d0e506d322fc12702ec60becb88b3b133db95`, `a7ae0c453bb2d8585b36235d1fd18183a06a4b7a`, `0619c98a9fe9fc57f03c62c8438b72680ce6fa1e`.

### Push 8/8 — Integration register and final CI gate
This document is the handoff register for the eight numbered batches. Push 8 remains an integration gate, not permission to merge regardless of CI. Verify the exact PR head and wait for backend CI, Android Build, and Chat/Groups checks before marking the overall workstream green.

## Verification evidence and remaining blockers

- Backend CI passed for code head `0619c98a9fe9fc57f03c62c8438b72680ce6fa1e` in run [39965515303](https://github.com/greyhoundshop073-coder/FYNX/actions/runs/39965515303). This run includes backend syntax checks and the repository's Marketplace verification gates, including the expanded multi-product lifecycle and refund/dispute guards.
- The Android Build workflow failed before Gradle on the separate Home interaction gate: `HOME INTERACTIONS RED: missing profile avatar has its own non-overlapped 48dp touch target`. Home/avatar is owned by another workstream and was intentionally not edited here.
- The separate Chat/Groups workflow has previously failed in `FynxApp.kt` on unresolved `clickable` references at lines 394 and 408. That file is outside this Marketplace-only scope; the Chat/Groups owner must fix and rerun it.
- The branch remains in draft and unmerged. Backend green alone is not full app/integration green. No APK is certified by this Marketplace branch.

## Reference standards considered

- [Jumia VendorHub Nigeria — FAQ](https://vendorhub.jumia.com.ng/faq/)
- [Jumia VendorHub — Product creation/update status](https://vendorhub.jumia.com.ng/product-creation-update-status/)
- [Jumia VendorHub — Quality check](https://vendorhub.jumia.com.ng/quality-check/)
- [Etsy Help — Edit listing photos](https://help.etsy.com/hc/en-us/articles/360016260113-How-to-Edit-Your-Listing-Photos?segment=selling)
- [Paystack — Refunds](https://paystack.com/docs/payments/refunds/)

The relevant patterns adapted here are accurate stock/inventory presentation, consistent seller and location discovery, recoverable upload/order detail failures, and reconciliation against provider-verified payment state. FYNX's Nigerian delivery flow still keeps the seller-selected local phone driver and agreed public meeting point.
