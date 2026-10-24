# FYNX Marketplace / My Shop — Push Register 1–20

## Scope and guardrails

- Workstream: Marketplace and My Shop only.
- Feature branch: `fix/marketplace-category-discovery-20261023`
- Review PR: [#347 — Marketplace Pushes 1–20](https://github.com/greyhoundshop073-coder/FYNX/pull/347)
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

## Continuation: Marketplace Pushes 9–20

### Push 9 — Show product photos inside My Shop inventory
**Finding:** The active My Shop Listings section showed only product title, price, stock, category, and condition. It did not render the product's uploaded media even though the listing model already carried the media IDs, making the seller's own inventory difficult to recognize and manage.

**Change:** Each My Shop listing card now displays the first uploaded product image using the authenticated media loader and shared backend media URL. Listings with no media show a neutral inventory placeholder instead of a blank image area. Existing edit, delete, stock, and delivery/pickup controls remain in place.

**Regression guard:** Extended the seller-flow verification to assert the real media thumbnail path and explicit no-media fallback.

### Push 10 — Make My Shop listing editing cover the real product record
**Finding:** My Shop's edit dialog changed only title, price, and quantity. Sellers could not correct an inaccurate description, category, condition, or location after publishing, and the backend update endpoint did not accept those fields.

**Change:** The edit form now exposes description, category, condition (`NEW`, `USED`, `REFURBISHED`), and product location alongside price/stock. The expanded dialog remains scrollable on small screens. The client validates the inputs and sends the full update; the authenticated backend locks the seller-owned listing, preserves omitted fields for older clients, validates the effective record, updates the fields atomically, and returns the updated values. Existing price-watch notifications remain triggered only when price actually changes.

**Regression guard:** Seller-flow verification now checks the form, client payload, scroll behavior, seller ownership lock, server update fields, and price-change notification path.

### Push 11 — Keep the cart consistent after a successful Buy Now payment
**Finding:** Multi-product checkout already removed purchased listings from the saved cart after confirmed payment, but the single-item Buy Now success handler left that listing and its quantity in the cart if the buyer had previously added it there.

**Change:** The single-item payment success callback now removes the purchased listing and its quantity from the cart only after the payment dialog reports confirmed success, then continues to the protected-order state and refreshes marketplace data. Failed/cancelled payments leave the cart unchanged.

**Regression guard:** Added a seller-flow contract check for the success-only cart and quantity removal.

### Push 12 — Make listing condition selection safe and consistent
**Finding:** The expanded listing editor accepted condition as free text, although the backend supports only `NEW`, `USED`, and `REFURBISHED`. A typo or casing mismatch disabled saving and gave sellers no clear selectable options.

**Change:** Replaced free text with three constrained choice chips, using readable labels and preserving the backend's canonical uppercase values. The controls disable while saving and remain within the scrollable editor.

**Regression guard:** Seller-flow verification now requires the three supported choices instead of the former free-text field.

### Push 13 — Make a seller's own active listings visible in Marketplace safely
**Finding:** The Marketplace discovery query explicitly excluded the signed-in seller's own products. That meant a seller could publish successfully and see the item in My Shop, but still could not find that active listing in the Marketplace feed they use to inspect the customer-facing listing.

**Change:** Marketplace discovery now includes all active, in-stock NGN/USD listings, including the current seller's own items. The product detail UI detects the current seller by username, labels their own product, and disables both Buy Now and Add to cart for it. The backend's existing authenticated checkout guard still rejects any self-purchase attempt, so visibility does not weaken purchase protections.

**Regression guard:** Added an integrated seller-flow assertion for discovery inclusion, UI self-purchase disabling, and the backend checkout rejection.


**Push 13 verification follow-up:** The first Android static gate found an existing out-of-stock assertion that expected the previous button expression. The behavior is now stricter (`canPurchase && quantity > 0`), so I updated that assertion to cover both out-of-stock and seller-owned items. The current-head workflow is rerunning; the unrelated Home avatar touch-target gate remains out of scope.

### Push 14 — Respect block relationships in Marketplace discovery
**Finding:** The discovery feed excluded a seller's own products, but its listing query did not exclude sellers blocked in either direction. The media endpoint could refuse the image while discovery still exposed the listing's title, price, and seller information.

**Change:** Discovery now includes the authenticated viewer ID as a query parameter and filters listings when either party has blocked the other. The general `/api/marketplace/listings` fallback and public storefront route now use the same two-way block filter and no longer blanket-exclude the signed-in seller's own products. This preserves Push 13's own-listing visibility while keeping block privacy consistent across discovery, fallback, and storefront. Search/category/location placeholders remain dynamically numbered after the viewer parameter.

**Regression guard:** Added checks for two-way block filtering and continued visibility of the seller's own listings without allowing self-purchase.


**Push 14 verification follow-up:** Backend CI found an older shop-profile assertion that still expected the previous seller-ID exclusion. I updated it to assert the new contract: seller-owned listings remain visible and two-way block filtering still applies. Exact-head Backend CI is rerunning.

### Push 15 — Prevent seller stock edits from undercutting active reservations
**Finding:** My Shop edited `marketplace_listings.quantity` directly without checking `reserved_quantity`. A seller could lower total stock below units already reserved by active checkout/orders, making inventory availability inconsistent and risking overselling.

**Change:** The authenticated update route now reads `reserved_quantity` under the same seller-owned row lock and rejects any new total stock below the existing reservation count with HTTP 409. The editor label now says **Total stock** (rather than available stock) and explains that active reservations cannot be reduced away. Existing orders/reservations are left untouched.

**Regression guards:** Added checks in both the shop-profile backend verifier and seller-flow verifier for the locked reservation count, 409 guard, and matching UI copy.

- **Push 15:** Backend CI GREEN, including the shop-profile verifier that now checks reserved-stock protection. Marketplace seller-flow gate also passed. Android Build remains red at the same separate Home profile-avatar 48dp touch-target gate, before Gradle compilation; no Home/Avatar files were changed.

### Push 16 — Show real available stock after active reservations
**Finding:** Marketplace buyer-facing listing payloads exposed total `quantity` without subtracting `reserved_quantity`, while checkout correctly treated reservations as unavailable. Buyers could therefore see stock counts that were higher than what checkout would actually allow.

**Change:** Public listing, seller inventory fallback, saved-product, discovery, and exact-listing responses now include `available_quantity = max(0, quantity - reserved_quantity)`. The buyer-facing Marketplace parser uses that field when present, so out-of-stock and low-stock messaging reflect actual purchasable inventory. The separate My Shop management model still uses total stock, matching Push 15's reserved-inventory guard.

**Regression guard:** Added seller-flow checks that all buyer-facing listing routes expose available quantity and the buyer parser prefers it, while My Shop continues to edit total stock.

### Push 17 — Apply block privacy to Saved Products
**Finding:** Discovery, listing fallback, and storefront now hide sellers blocked in either direction, but the Saved Products endpoint still returned a blocked seller's listing metadata. That could expose the title/price/seller details in a saved-products screen even though the listing image was no longer accessible.

**Change:** The authenticated Saved Products query now excludes listings whenever either the current viewer or seller has blocked the other, consistent with Marketplace discovery and storefront behavior. Saved records remain stored, so unblocking can make them visible again without losing the user's saved state.

**Regression guard:** Added a seller-flow assertion for the two-way block filter in the Saved Products query.

- **Pushes 16–17:** Backend CI GREEN; Marketplace seller-flow gate passed, including available-stock parsing, seller-owned product visibility, and Saved Products block filtering. The Android workflow is still stopped at the separate Home avatar 48dp touch-target gate; no APK certification from this draft branch.

### Push 18 — Stop cart checkout when stock changed after an item was added
**Finding:** A product could be added to the cart while in stock and then become fully reserved/out of stock before checkout. The quantity controls were capped, but the checkout button still allowed the stale selected item to proceed with an invalid quantity and only fail later at quote/order validation.

**Change:** The cart now detects selected products with zero available stock or a stored cart quantity above current available stock, displays a clear recovery message, and disables checkout until the buyer removes the item or reduces quantities. Mixed-currency protection and the 20-product cap remain unchanged.

**Regression guard:** Added a seller-flow check for the unavailable-stock predicate, visible message, and disabled checkout condition.

- **Push 18:** Backend CI GREEN; Marketplace seller-flow, integration/security audit, buyer-protection, accounting, and financial-hardening checks passed. Android Build remains red at the separate Home profile-avatar 48dp touch-target integrity gate, which stops that workflow before Gradle compilation. The parallel integration/Chat-Groups workflow also reported `FynxApp.kt:394,408 Unresolved reference: clickable`; I left that out-of-scope file untouched. No Home/Avatar changes made.

### Push 19 — Keep My Shop stock labels consistent with reservation semantics
**Finding:** Push 15 clarified that the editor changes total stock, but the listing card still called that same number “in stock,” which could be mistaken for immediately available units when some units are reserved.

**Change:** My Shop inventory cards now say “total stock,” while buyer-facing Marketplace uses the available count from Push 16. This separates seller-managed inventory from buyer-purchasable quantity and reduces confusion during active reservations.

**Regression guard:** Extended the seller-flow check to require the total-stock label in both the editor and inventory card.

- **Push 19:** Backend CI GREEN; Marketplace seller-flow gate passed. The only failing Android gate remains the unrelated Home avatar 48dp touch-target check; Chat/Groups parity is still running as its separate workstream. No APK has been built or certified from these draft branches.

### Push 20 — Fix Marketplace client Kotlin compilation
**Root cause:** The editor added locale-safe condition normalization using `uppercase(Locale.US)`, but the Kotlin client lacked the `java.util.Locale` import. The exact-head Android regression build reported `FynxMarketplaceClient.kt:161:57 Unresolved reference 'Locale'` on `22174a43b76d17e37689c7a781b62f05267c3d79`.

**Change:** Add the explicit `java.util.Locale` import. No Home, Avatar, Chat, or Groups source is changed.

**Verification at parent head `5e47b0c`:** Backend `validate` is green, including Marketplace receipt, lifecycle, shipping, refund/dispute, accounting, and shop-profile checks. Android compilation no longer reports the earlier Marketplace `Locale` symbol error; the actual Kotlin build now fails at the untouched `FynxApp.kt:394,408` unresolved `clickable` references. The separate build workflow also stops at the Home avatar 48dp target guard before Gradle. Both are outside this branch's Marketplace-only change; this new head's CI is pending.

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


### Push 21 — Use available stock in buyer-facing Marketplace discovery and detail parsing

**Root cause:** Backend listing APIs return both `quantity` (seller-managed total stock) and `available_quantity = max(0, quantity - reserved_quantity)`. The paginated `FynxDiscoveryClient` and the `FynxMarketplaceClient` public detail/fallback parsers read only `quantity`. A buyer could therefore see reserved units reported as “available” even though the checkout backend would reject the extra quantity.

**Change:** Discovery maps `available_quantity` with a compatibility fallback to `quantity`. Public listing detail and fallback parsing explicitly use available stock; the authenticated `/api/marketplace/my-listings` path explicitly keeps total stock for the seller's inventory cards and stock editor. Added regression assertions for both paths.

**Verification:** Pending CI for this exact head. The Android integration build remains blocked by the untouched `FynxApp.kt:394,408` compile errors and the pre-Gradle Home avatar touch-target gate. No files from Home, Chat/Groups, Status, Profile, or Avatar were edited.



### Push 22 — Fail closed for ambiguous shared-reference refund webhooks

**Root cause:** Refund webhook recovery counted only `PENDING` and `FAILED` operations for a checkout-group payment reference. If one child refund had already succeeded and another child refund was still pending, a replay without an exact provider refund identity could hide the succeeded operation and be incorrectly assigned to the other child order.

**Change:** The fallback lookup now counts all refund operations sharing the resolved payment reference before selecting a child; zero or multiple candidates fail closed. Exact provider refund identities remain usable only when they identify one eligible operation. A `SUCCEEDED` refund is treated as terminal: duplicate `refund.processed` events are no-ops, and stale pending/failed events cannot rewrite its protection or escrow state.

**Regression guard:** The refund/dispute verifier now asserts the fallback candidate query does not pre-filter completed refunds and that terminal refund events are handled monotonically.

**Verification:** CI for the pushed exact head must confirm Backend CI is green. The known Home interaction and Chat/Groups Android blockers remain outside this workstream; this PR stays unmerged until the required integration checks are green.

**Register numbering note:** The merged discovery fix PR #316 also used the label “Push 43”, while PR #348 labels its payment work “Pushes 43–61”. Those historical labels overlap; reconcile the global numbering before treating them as one continuous sequence.


### Push 23 — Register the Paystack webhook and preserve its signed request bytes

**Root cause:** The Paystack webhook module defined its route, but the live backend entrypoint did not import or register it. In addition, the Express JSON parser did not preserve the raw request body, while the handler requires those exact bytes to validate `x-paystack-signature`. Consequently the refund/transfer webhook handler could not be relied on to receive signed events.

**Change:** The backend now registers the Marketplace Paystack webhook with the active database pool. The JSON parser captures a byte-for-byte copy only for `/api/marketplace/payments/paystack/webhook`, before JSON parsing, so HMAC verification can run without changing normal API body handling.

**Regression guard:** The refund/dispute verifier now checks both server registration and raw-body preservation alongside refund identity/ambiguity safety.

**Verification:** Exact-head Backend CI must pass before this PR can be considered integration-ready. No unrelated Home, Status, Profile/Avatar, or Chat/Groups files were touched.


### Push 24 — Keep dispute resolution blocked while refund outcomes are unresolved

**Root cause:** The dispute resolver treated only `PENDING` and `SUCCEEDED` financial operations as conflicts. A `BLOCKED` refund (for example, a provider amount/currency mismatch) or a `FAILED` refund caused by a timeout/request error without confirmed provider state could be ignored when resolving for the seller or cancelling protection. The idempotent buyer refund path also allowed an existing `BLOCKED` operation to fall through to a duplicate insert attempt.

**Change:** Buyer refund approval now blocks on active, succeeded, or blocked payouts. Seller resolution and protection cancellation block on `PENDING`, `SUCCEEDED`, and `BLOCKED` refunds, and on `FAILED` refunds unless metadata proves Paystack explicitly returned a terminal `refund.failed` event or the recovery worker verified a failed provider state. An existing blocked refund returns HTTP 409 with a reconciliation message instead of attempting another refund.

**Paired worker fix:** Push 24 also tightens the isolated PR #348 payout worker to honor the same unresolved-refund rule before transferring seller funds. It does not merge the PRs or touch non-Marketplace source.

**Regression guard:** Added checks for blocked payouts/refunds, failed-without-provider-confirmation, and the duplicate-refund prevention guard. Exact-head CI remains required.

**Verification:** Pending exact-head Backend CI on both PRs. No APK or live deployment is claimed from these branch-only changes.


### Push 25 — Add a safe seller-only archive and republish path

**Root cause:** Removing a Marketplace listing is a soft delete (`active=FALSE`). My Shop deliberately filtered inactive rows out of the inventory, but there was no owner-authorized route or UI action to restore a listing. Listings removed by the seller or deactivated when stock ran out could remain invisible permanently even after the seller had replenished stock.

**Change:** My Shop continues to keep archived products out of the active buyer inventory and now shows them in a separate **Archived listings** section. Sellers can edit/restock an archived row and explicitly republish it. The backend locks the seller-owned row, checks that total stock exceeds units reserved by existing orders, and only then sets `active=TRUE`. Removing an item moves it into the in-session archive state instead of making it disappear from the seller's view.

**Regression guard:** Seller-flow verification now checks archive separation, owner-scoped republish, unreserved-stock validation, and success/error feedback.

**Verification:** Exact-head Android and Backend CI are pending. Republish is not live in Railway until the Marketplace PR is merged and the canonical deployment is verified.


### Push 26 — Reconcile the Paystack hook with the generated runtime entrypoint

**Root cause:** Production starts through `realtimeIsolationBootstrap.js` and `serverBootstrap.js`, which inject Marketplace checkout and Paystack webhook routes into the generated runtime. Push 23 initially added an additional static `server.js` Paystack import/registration, which the generated-runtime injector would add a second time. Its generic raw-body capture also unnecessarily copied every JSON request body. The refund ambiguity guard had a separate test bug: `split(..., 1)` drops the segment the assertion intended to inspect, creating a false failure.

**Change:** Keep the existing generated-runtime bootstrap as the single source for route registration. Restore the static `server.js` markers it expects, and preserve raw request bytes only when the request path is the signed Paystack webhook. The refund verifier now inspects the candidate SQL query using explicit string indices and verifies the runtime bootstrap, rather than incorrectly requiring a static source registration.

**Regression guard:** Updated both refund/dispute and Stage 15G runtime integration checks to assert the actual bootstrap path and webhook-specific raw-body capture. This is a test correction based on the current code structure, not a skipped or weakened check.

**Verification:** Backend CI on the resulting exact commit must pass. Railway remains on the last successful canonical deployment; no deployment or device test is claimed from this PR.


### Push 27 — Explain unavailable Paystack without implying checkout succeeded

**Root cause:** The current Railway testing environment has no `PAYSTACK_SECRET_KEY`; the server correctly returns HTTP 503 before contacting Paystack, but the Android dialog displayed the raw backend HTTP/JSON error. That was technically honest but confusing for a buyer.

**Change:** The checkout dialog now maps only the explicit provider-not-configured response to a clear message that no payment started and the cart remains unchanged. Other failures keep their actual error detail, and the order/receipt success callback still runs only after backend payment verification.

**Regression guard:** The end-to-end verifier asserts the explicit no-charge/retry copy and that payment verification remains the only path that invokes the successful checkout callback.

**Verification:** This is a UI/source change only; exact-head Android and Backend CI remain required. Paystack is still not configured in Railway, so live payments remain a release blocker. No payment or deployment is claimed.


### Push 28 — Do not reserve inventory when the payment provider is unavailable

**Root cause:** Both protected checkout order endpoints reserved stock and committed `PAYMENT_PENDING` orders before Android attempted Paystack initialization. With no `PAYSTACK_SECRET_KEY`, the payment endpoint returns HTTP 503 after the reservation, leaving an unpaid reservation until the expiry worker runs. Repeated attempts can unnecessarily hold stock even though the checkout can never be paid in the current environment.

**Change:** New single-product and multi-product checkout creation now fails closed with HTTP 503 / `PAYMENT_PROVIDER_NOT_CONFIGURED` before the listing row locks, order inserts, or inventory reservations when the Paystack secret is absent/blank. Existing checkout IDs still follow their idempotent read path, so this does not duplicate or rewrite an already-created order. Both Android checkout dialogs use the truthful “no payment started/cart unchanged” message for this preflight failure.

**Regression guard:** The Batch 3 checkout guard asserts that the provider check occurs after idempotency resolution but before stock locks. The end-to-end guard verifies both single and multi checkout UIs map the provider-unavailable error without invoking the paid callback.

**Verification:** Exact-head Backend CI and Android Build are required. Railway currently has no Paystack secret; no payment, deployment, or device test is claimed.


### Push 29 — Recover payout operations blocked by a refund that later resolves (paired change in PR #348)

**Root cause:** The payout worker correctly blocks a payout when a refund remains unresolved, but marking the payout operation `BLOCKED` removes it from the periodic scan, which otherwise only revisits `PENDING` operations. The seller retry endpoint accepts only `FAILED` payout operations and `RELEASE_ELIGIBLE` escrow, so a previously blocked payout could remain stranded even after a refund later became a confirmed terminal failure.

**Change:** PR #348 now scans only for payout operations blocked with the exact unresolved-refund reason. Recovery locks the order, payout operation and escrow; requires a completed order and `RELEASE_PENDING` escrow; rechecks that no refund remains unresolved; validates the seller recipient and amount/fee/currency reconciliation; then changes that one operation back to `PENDING` and enqueues the existing idempotent payout release job. Other blocked payout reasons, unresolved/blocked refunds, mismatched accounting and disputed escrow stay blocked.

**Regression guard:** `backend/verify-marketplace-reconciliation.mjs` verifies the specific reason filter, eligibility locks/guards, refund predicate and guarded `BLOCKED → PENDING` transition.

**Verification:** Exact-head Backend CI is queued for PR #348. PR #347 Android Build remains blocked by the unrelated Home interactions gate; no files outside Marketplace scope were changed. No live payout, deployment, or device test is claimed.


### Push 30 — Validate delivery-proof photos before recording them

**Root cause:** The assigned FYNX driver delivery endpoint accepted any string as `photoReference` and persisted it as `PHOTO_REFERENCE` evidence without confirming that the reference was a real uploaded image owned by that driver. Because order participants can retrieve evidence media by reference, a forged or unrelated media ID could be attached to a delivery record.

**Change:** Delivery photo references must now be numeric uploaded-media IDs. After confirming the signed-in user is the assigned, currently approved FYNX driver and the order is eligible for delivery confirmation, the backend verifies the media row belongs to that driver and has an allowed image MIME type (JPEG, PNG, WebP, or GIF). Only the canonical media ID is stored. Invalid, missing, foreign-owned, or non-image media is rejected before the delivery timestamp/evidence is written. Delivery notes without a photo remain supported.

**Regression guard:** `backend/verify-marketplace-fulfillment.mjs` asserts numeric ID validation, owner-scoped media lookup, allowed image MIME checking, and canonical photo evidence insertion.

**Verification:** Exact-head Backend CI is required. No deployment or device test is claimed.


**Android follow-through:** The driver assignment dialog also exposes an optional delivery-photo picker; it uploads the selected image as the signed-in driver and passes the returned media ID to the validated delivery endpoint. The driver can still submit a recipient name plus note without a photo.
