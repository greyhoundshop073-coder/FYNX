# FYNX Marketplace Master Architecture

## Purpose

This document is the authoritative design map for the FYNX marketplace financial, product, social-feed, business, advertising, delivery, dispute, and payout systems.

The existing marketplace implementation is substantial and must be extended, not replaced. Every R6 change must preserve working payment, escrow, ledger, webhook, reconciliation, order, protection, and UI behavior unless the change explicitly fixes a verified defect.

## Core principle

FYNX should make the marketplace extremely simple for users while keeping the backend strict about money, inventory, authorization, idempotency, and state transitions.

User promise:

> Buyer pays → payment is protected → seller ships → buyer receives and confirms → seller gets paid.

If something goes wrong:

> Dispute → payout and refund are frozen → authorized resolution → exactly one valid financial outcome.

## 1. Shared product identity

A marketplace product is one authoritative product/listing record. It must not be copied into separate independent records for the Home feed, business profile, or advertising system.

The canonical product/listing identity is referenced by:

- Marketplace listing
- Optional Home/social feed product post
- Seller/business profile
- Advertising campaign creative
- Product detail/share links
- Buyer orders
- Reviews
- Seller chat entry
- Inventory
- Product analytics

A feed post or advertisement may have its own lifecycle, permissions, metrics, and content, but it must reference the canonical product/listing rather than duplicate its price, stock, seller, or transaction state.

## 2. Product → Social Feed → Business → Advertising

One seller action can expose the same product through several FYNX surfaces:

```text
Canonical Product / Listing
          |
          +--> Marketplace
          |      +--> product detail
          |      +--> checkout
          |
          +--> Home Feed (optional organic share)
          |      +--> product card
          |      +--> open product
          |
          +--> Business Profile
          |      +--> products
          |      +--> business posts
          |
          +--> Advertising (optional paid promotion)
                 +--> campaign
                 +--> targeting
                 +--> budget
                 +--> review/approval
```

The Home feed must not become a stream of automatic spam. Product owners should be able to share a product organically, while FYNX can separately use relevance-based discovery and paid advertising.

Advertising must reference the canonical product/business/post and must not create a second product inventory or payment system.

## 3. Buyer money flow

```text
Buyer
  |
  v
FYNX Checkout
  |
  +--> product price
  +--> delivery/pickup cost
  +--> FYNX marketplace fee (if applicable)
  +--> applicable payment fee treatment
  +--> discounts/credits
  |
  v
Buyer Total
  |
  v
Paystack payment initialization
  |
  v
Paystack checkout
  |
  v
Webhook + verification reconciliation
  |
  v
PAID / protected order
  |
  v
ESCROW = HELD
  |
  v
Seller ships / pickup handover
  |
  v
Buyer receives
  |
  v
Inspection window
  |
  +--> Buyer confirms correct
  |       |
  |       v
  |   RELEASE ELIGIBLE
  |       |
  |       v
  |   PAYOUT
  |
  +--> Buyer reports problem
          |
          v
      DISPUTED / FINANCIAL FREEZE
```

The Android application never contains the Paystack secret key. Payment authorization, webhook verification, payout authorization, refunds, and financial state transitions are backend responsibilities.

## 4. Money decomposition

An order must retain authoritative monetary components instead of relying on one opaque total.

Conceptually:

```text
Product subtotal
+ delivery/pickup charge
+ FYNX marketplace fee (when configured)
+ applicable payment fee treatment
- discounts/credits
--------------------------
Buyer total
```

The exact fee policy is configurable and must be snapshotted for a paid order so later fee-policy changes cannot rewrite an existing transaction.

Every financial amount must be traceable through the financial ledger and operation records.

## 5. FYNX fee model

FYNX may earn a marketplace fee. The fee must be server-calculated and recorded, not calculated as a trusted client-side value.

The system must support a future policy choice of:

- buyer-paid marketplace fee
- seller-paid marketplace fee
- split fee
- zero fee for selected products/promotions

The chosen policy for an order is frozen when the order becomes financially committed.

Payment-provider fees and FYNX marketplace fees must remain distinguishable in accounting.

## 6. Payment-provider boundary

Paystack is the payment/transfer rail. FYNX is the marketplace business-logic authority.

FYNX must reconcile provider events rather than trusting a single mobile callback.

Required behavior:

- authenticated payment initialization
- unique/idempotent order payment reference
- amount and currency validation
- order metadata validation
- signed webhook validation
- webhook idempotency
- verification reconciliation
- provider failure handling
- refund event reconciliation
- transfer event reconciliation
- transfer reversal handling

The production custody/settlement model must be confirmed against Paystack's supported marketplace structure and applicable Nigerian payment regulations before live-money launch. Do not create an informal FYNX bank-account escrow arrangement.

## 7. Order state machine

Normal path:

```text
PAYMENT_PENDING
      |
      v
    PAID
      |
      v
   SHIPPED
      |
      v
  DELIVERED / RECEIVED
      |
      v
 INSPECTION
      |
      v
  COMPLETED
```

Alternative paths must be explicit and validated:

```text
PAYMENT_PENDING -> EXPIRED -> inventory released
PAID -> CANCELLED -> REFUNDED (where cancellation policy permits)
PAID/SHIPPED/DELIVERED/INSPECTION -> DISPUTED
DISPUTED -> REFUNDED
DISPUTED -> RELEASE_ELIGIBLE / seller-favorable resolution
```

No route may invent its own state transition rules. Financially important transitions must converge through one authoritative transition layer.

## 8. Financial state machine

Order status and financial status are related but separate.

```text
HELD
  |
  +--> RELEASE_ELIGIBLE --> RELEASE_PENDING --> RELEASED
  |
  +--> REFUND_PENDING --> REFUNDED
  |
  +--> DISPUTED
```

Terminal or protected combinations must be impossible, including:

- successful refund + successful seller payout
- refund pending + payout pending
- disputed order + successful payout without authorized resolution
- paid cancellation without a corresponding refund outcome
- two successful payouts for one order
- two successful refunds for one order

These invariants must be enforced by backend/database safeguards, not just Android UI logic.

## 9. Inventory protection

Paid orders reserve inventory. Unpaid checkout attempts must not reserve inventory indefinitely.

Required lifecycle:

```text
available
   |
   v
reserved
   |
   +--> paid order -> protected reservation
   |
   +--> payment timeout -> released
   |
   +--> cancellation/refund -> released when policy permits
   |
   +--> completed sale -> sold/decremented
```

Inventory changes must use one authoritative transition mechanism so concurrent checkout, cancellation, refund, dispute resolution, and completion cannot double-release or over-sell stock.

## 10. Shipping, delivery, and pickup

Delivery and pickup are distinct fulfillment methods.

Delivery should support shipping/tracking evidence when available.

Pickup should have an explicit handover/confirmation event rather than pretending pickup is identical to delivery.

The seller must not be able to manufacture a buyer receipt/confirmation event.

## 11. Buyer protection

The buyer experience should be simple:

```text
Payment protected
      |
Seller ships
      |
Item arrives
      |
+-----------------------+
| Is this what you      |
| ordered?              |
+-----------------------+
       |           |
      YES         PROBLEM
       |           |
       v           v
  Release       Dispute
```

Automatic inspection completion must remain blocked by an active dispute.

## 12. Dispute system

A dispute freezes the financial outcome.

While disputed:

- seller payout is blocked
- automatic completion is blocked
- buyer cannot force an immediate refund merely by pressing a button
- seller cannot force payout
- evidence is restricted to authorized participants
- financial actions are recorded in the audit trail

Possible authorized outcomes:

- buyer refund
- seller release/payout eligibility
- continued investigation where supported

Dispute creation and resolution must synchronize order state, escrow state, protection-case state, inventory state, financial operations, and ledger entries atomically where possible.

Protection cases must retain the relevant pre-dispute state needed for safe restoration/resolution.

## 13. Administration and authorization

Administrative marketplace resolution must use explicit authorization only.

A user's database position, age, first-created account, or similar accidental property must never grant administrator powers.

Admin actions must be auditable and attributable to an explicitly authorized admin identity.

## 14. Seller payout

Seller payout requires:

- authenticated seller ownership
- valid order state
- valid escrow state
- no active dispute
- no refund operation that conflicts with payout
- verified active payout account
- idempotent payout operation
- provider reference protection
- transfer verification
- webhook reconciliation
- failure/reversal handling

Seller payout amount must be derived from the authoritative financial breakdown/escrow amount, not independently recomputed from an untrusted client total.

## 15. Advertising integration

Advertising supports at least:

- product campaigns
- business campaigns
- post campaigns

An advertisement references an existing FYNX object whenever possible.

Advertising has its own:

- campaign status
- review/approval
- budget
- payment
- targeting
- metrics
- audit trail

Advertising must never bypass marketplace product authorization, inventory, seller ownership, privacy, or financial protection.

A paid product advertisement links to the canonical product page. It does not create a separate checkout or duplicate product record.

## 16. Business integration

A business/seller identity can own products and posts.

The business profile can expose:

- business identity
- products
- organic posts
- followers/engagement where allowed
- customer contact/chat entry
- reviews/reputation where implemented
- advertisements

Marketplace, business, and social surfaces must share authoritative ownership and privacy checks.

## 17. Home feed rules

Product content can appear in Home through controlled mechanisms:

1. seller chooses to share a product organically;
2. FYNX relevance/discovery systems surface eligible product content;
3. an approved paid advertisement is shown.

The Home feed must clearly distinguish ordinary social content from paid advertising where required.

Removing/unlisting a product must prevent stale buy actions while preserving historical order records.

## 18. Chat integration

A product card can provide a seller-chat entry point.

The chat must reference the product/listing ID so the seller and buyer can understand what is being discussed without creating a second product record.

Chat must never be the authority for price, stock, payment status, delivery status, or payout state. Those come from the marketplace backend.

## 19. Audit and reconciliation

Every critical financial event should be traceable:

```text
who -> what -> order -> product -> amount -> provider reference -> timestamp -> resulting state
```

The reconciliation system must detect impossible or incomplete states, including:

- provider says paid but FYNX remains pending
- FYNX says payout succeeded without provider confirmation
- refund provider event without matching financial operation
- financial operation without matching order/escrow state
- ledger totals that cannot be reconciled
- inventory inconsistent with order lifecycle

## 20. R6 implementation order

Do not mix these batches or skip ahead when a preceding batch is red.

### R6-A — Financial/state foundation
- unify authoritative order/escrow/dispute transitions
- remove unsafe admin fallback
- preserve pre-dispute state
- safe dispute cancellation semantics
- payout/refund mutual exclusion
- financial invariants
- fee snapshot fields/design

### R6-B — Payment reliability
- payment/webhook/verification convergence
- payment idempotency
- external-provider calls outside long DB locks where practical
- unpaid-order expiry
- reservation release
- provider failure reconciliation

### R6-C — Seller payout
- verified payout accounts
- payout authorization
- transfer worker safeguards
- transfer webhook/reversal reconciliation
- duplicate payout prevention

### R6-D — Buyer protection
- inspection enforcement
- dispute/evidence lifecycle
- authorized resolution
- refund workflow
- dispute-safe automatic completion

### R6-E — Fees and accounting
- configurable marketplace fee policy
- buyer/seller fee treatment
- provider fee treatment
- seller net amount
- refund accounting
- ledger reconciliation

### R6-F — Fulfillment/inventory
- reservation expiry
- shipping/tracking
- delivery confirmation
- pickup handover
- cancellation/refund inventory rules

### R6-G — Shared product/social/business/advertising integration
- canonical product identity across surfaces
- optional Home product sharing
- business product linking
- product-linked ads
- product-linked chat
- stale/unlisted product handling
- privacy/ownership enforcement

### R6-H — UX
- simple checkout
- clear protection messaging
- buyer order timeline
- seller order workflow
- dispute UX
- payout UX
- receipts and fee breakdowns

### R6-I — Failure/security verification
Test at minimum:

- duplicate payment
- duplicate webhook
- delayed webhook
- payment verification race
- payment timeout
- duplicate payout
- payout/refund race
- transfer failure
- transfer reversal
- duplicate refund
- active dispute during automatic completion
- unauthorized seller completion
- unauthorized buyer refund
- concurrent inventory purchase
- server restart during financial operation
- database retry during financial operation
- stale/unlisted product linked from feed or advertisement

## 21. Build discipline

Each R6 batch must be:

1. inspected against current `main`;
2. implemented without duplicating working systems;
3. committed as one coherent batch where the tooling permits;
4. built and verified by the existing CI gates;
5. GREEN before the next batch starts.

If a build or verifier is RED, stop the sequence and fix the exact failure before continuing.

The R6 batches are ordered deliberately: financial correctness first, payment reliability second, payout third, protection fourth, accounting fifth, fulfillment sixth, cross-surface integration seventh, UX eighth, and adversarial verification last.


---

# Marketplace Visual Blueprint and Push Register (2026-10-20)

This section is the durable UI/UX implementation contract for Marketplace redesign work. It supplements the financial/backend architecture above. Do not replace the existing working Marketplace with a mock screen or a parallel data model. Migrate the real screens incrementally, reusing existing listing, seller, cart, order, payment, delivery, and receipt sources of truth.

## A. Visual identity and semantic colour rules

- **Primary action / navigation blue:** `#155EEF`. Use for selected navigation, primary buttons, links, selected filters, active controls, and key interactive icons.
- **Secondary blue:** `#7FB0FF`. Use sparingly for secondary accents on existing dark Marketplace surfaces. Validate contrast in both light and dark themes before applying broadly.
- **Success green:** `#16A34A`. Reserve for verified successful payments, confirmed/completed actions, completed delivery milestones, and positive outcomes. Do not use green as the general brand/navigation accent.
- **Pending / needs attention amber:** `#F59E0B`. Use for payment pending, awaiting pickup, and user action required.
- **Failure / danger red:** `#DC2626`. Use for failed payments, destructive actions, serious warnings, and errors.
- **Surface and text:** Prefer clean white or light neutral surfaces where the existing app theme expects them, readable dark text, subtle neutral borders, and restrained elevation. Keep dark-theme support legible. Product photography should remain visually dominant; do not paint every card bright blue.
- Colour is not the only status signal: pair colour with readable labels/icons, and preserve existing accessibility semantics.
- Do not change payment outcome logic to make a colour look successful. Green is allowed only when the underlying state is actually successful/confirmed.

## B. Screen hierarchy and what belongs where

### 1. Marketplace Home / Discover
Order content from global actions to discovery, then supporting recommendations:
1. Compact header: Marketplace title/brand, account-appropriate location context, cart icon with count.
2. Search field: searches products, seller/shop names, and categories; typing must use the existing real search flow.
3. Nearby/location chip and horizontally scrolling category chips.
4. One featured/deals area only when real eligible listings exist; do not show fake inventory or placeholder promotions as real offers.
5. Recommended/nearby product grid using real listings. Cards prioritize product image, short title, price + currency, seller identity/avatar, seller location, and a restrained save/favorite action.
6. Seller/shop discovery and top-seller/reputation content only when backed by real data. Avoid repeating the same listings in several sections without a clear reason.
7. Clear shortcuts to Saved Products, Orders, and My Shop; these are Marketplace destinations and must not break the app's global navigation.
8. Empty/loading/error states should be compact and actionable, with retry when appropriate. Preserve loaded feed data when a transient refresh fails.

### 2. Search and category results
- Keep the search query visible and editable; provide clear-query behavior.
- Put category, nearby, and available filters near the top in a compact horizontal row or sheet.
- Show result count/sort only when backed by the actual result set.
- Use a consistent responsive product grid and preserve scroll/pagination state where possible.
- Do not silently redirect a generic search to the Marketplace home or a single category.

### 3. Product card
- Image first, with an unobtrusive condition/availability badge where data exists.
- Title (maximum two lines), clear currency + price, seller avatar/name, and seller location.
- Seller rating/review count only when verified review data exists; never invent a rating.
- Save/favorite action must reflect its real state.
- Card opens the real product details. Seller and contact actions must have their own click targets so they do not accidentally trigger the card action.

### 4. Product details
Recommended order:
1. Product image gallery/media.
2. Product title, currency + price, stock/availability, and condition.
3. Seller identity block: avatar, display name/shop name, location, reputation when available, and link to public shop.
4. Clear buying actions: Add to Cart and Buy Now, with quantity/stock safeguards.
5. Buyer protection and fulfillment options (delivery vs pickup), using truthful current policy/status.
6. Full description/specifications, then reviews and related products.
7. Share, save/watch, and buying assistant as secondary actions.
- Keep seller identity consistent with the same profile/avatar used elsewhere in FYNX.
- Never imply an order is paid or protected before backend verification confirms it.

### 5. Public Seller Shop vs private My Shop
These are different destinations and must not be confused:
- **Public Seller Shop:** buyer-facing storefront with shop identity, location, seller profile/avatar, active products, reputation/reviews, and a clear way to contact the seller.
- **My Shop / Seller Center:** private owner workspace. It should feel like owning and running a shop, not a long crowded settings form.
- Shop identity belongs near the top of the owner dashboard: logo/avatar, shop name, location, short description, profile completeness, and Preview Public Shop / Edit Shop actions.
- Owner dashboard overview should show compact, real summaries (active listings, orders needing action, earnings/payout status) only where API data supports them.
- Put management tools into clear sections/destinations: Products, Orders, Delivery & Drivers, Earnings & Payouts, and Shop Profile/Settings. Use cards/rows with counts and status labels instead of placing every form expanded on one long page.
- Keep existing capabilities working while reorganizing; do not drop payout account editing, listing edit/delete, seller order handling, driver assignment, settlement visibility, or shop profile saving.
- Dangerous actions (delete listing, payout changes) need clear labels and confirmation where already supported/appropriate.

### 6. Cart and checkout
- Cart supports 1–20 items, multiple sellers, and one currency per checkout. Prevent mixed-currency checkout with an explicit explanation rather than silently converting.
- Each cart row: thumbnail, title, seller, unit price, quantity controls, stock state, and remove action.
- Group by seller when helpful, while maintaining one clear order summary.
- Checkout summary separately shows item subtotal, delivery/pickup costs, FYNX fee, applicable payment fee treatment, discounts, and total. Use the authoritative server quote/fee calculation; client totals are for display only.
- Primary payment action uses blue while pending/in progress; show green only after backend/provider verification confirms success. Amber is pending/action-needed; red is failure.
- Payment retry must not create duplicate orders/charges. Preserve Paystack amount/currency/reference validation and the existing safe retry guidance.

### 7. Orders, fulfillment, and driver handover
- Orders list should group by clear order/payment/fulfillment status with readable labels, date/reference, product/seller, and next required action.
- Order details: order reference, separate payment and fulfillment statuses, item/price summary, timeline, delivery or pickup method, seller/buyer context, receipt, help/dispute actions, and available confirmation action.
- For Nigerian local handover, show agreed meeting point (e.g. bus stop/roadside), next-step owner, driver contact/identity details available to the order, and optional vehicle/plate/colour/photo evidence when supplied. Do not imply the driver should visit a buyer's home by default.
- A driver may have a basic phone; the flow must not assume every driver has the FYNX app. Use supported contact/call handover paths where implemented.
- Multi-seller checkout must retain separate child-order fulfillment, seller actions, and per-item status even when the buyer also receives one aggregate payment receipt.
- Never let a client-only button manufacture delivery, receipt, inspection, dispute-resolution, or payout events.

### 8. Receipts and disputes
- Individual and multi-seller receipts must display actual backend-confirmed payment evidence, stable receipt/order/checkout references, itemized amounts, and separate payment vs fulfillment status.
- Payout status must come from backend escrow/payout operation records; do not imply the seller was paid merely because the buyer paid.
- Keep signed verification/QR and PDF/share/download behavior consistent with the existing receipt implementation and backend release.
- Dispute options should be explicit: item not received, wrong item, damaged, not as described, or suspected scam where supported. Show evidence/help steps and explain that a dispute freezes the financial outcome pending authorized resolution.
- Success green only for verified completed states; unresolved, pending, failed, and disputed states must remain semantically distinct.

## C. Cross-app identity and connection rules

- A seller avatar/profile must resolve to the same user identity across Marketplace cards, public shop, product detail, statuses/feed, and chat entry points.
- Product share links should open the exact canonical listing/detail, not a duplicate product record.
- Product-aware chat must preserve the listing context when opened from a product or shop.
- Saved Products must show the actual saved state and lead back to the canonical listing.
- Notifications should lead to the relevant order/listing/action when that destination is supported.
- AI/buying assistant, social sharing, seller reputation, and analytics may enhance the canonical listing but must not duplicate inventory, price, seller, payment, or order truth.

## D. Non-negotiable preservation rules

- Keep working test login open; do not introduce OTP or a new login/backend dependency as part of visual work.
- Do not regress Marketplace network access, listing images/media privacy, search, cart persistence, seller identity, payment verification, order lifecycle, signed receipts, seller payout state, driver custody, or disputes.
- Do not touch another chat's assigned Home, Avatar/Profile, Chat/Groups, or driver-custody area except where a narrow shared contract is essential; coordinate and document any overlap.
- Do not claim a feature is network-backed unless its real API path and failure states are confirmed.
- No mock products, fake ratings, fake transaction success, or fabricated payout/delivery states in production UI.
- Build on the canonical integration branch `integration/fynx-single-apk-20261013`. Work on isolated feature branches and PRs; do not silently send work to a different APK/build line.
- A green CI result is not proof of a real device flow. APK identity must be tied to the exact commit/artifact; report runtime/device verification separately.

## E. Push-by-push execution and acceptance checklist

For each numbered Marketplace push:
1. Read this blueprint and inspect current callers, source files, recent PRs, and CI before editing.
2. State the exact screen/flow and the bounded scope of the push; check for overlapping work before touching shared files.
3. Implement on a feature branch from the canonical integration line; preserve all working behavior and real API/data bindings.
4. Run the relevant Android build, layout/parity checks, and backend tests if applicable. If red, inspect logs, fix the cause, and rerun before moving on.
5. Verify the exact head commit and PR base; do not assume that a closed PR is merged or that another APK includes it.
6. Confirm a downloadable APK artifact exists for the exact green commit before saying an APK is ready. Record device/runtime results separately.
7. Update this document's push register with commit/PR, files changed, check results, artifact link if available, and remaining work.

## F. Current visual redesign push register

- **Push 24 — Blue accent foundation:** primary and secondary blue tokens are present in `FynxMarketplaceReferenceComponents.kt` on the canonical integration branch (`Color(0xFF155EEF)` and `Color(0xFF7FB0FF)`). Android Build and Chat Group Parity/Layout Safety both succeeded for the original PR #278 commit `81d792ad10cebba7c244802f60e004cc0782a349`. The original PR branch is still divergent/behind the canonical branch, so do not merge it blindly or reapply the same palette change. **Remaining verification: confirm the exact canonical APK and on-device appearance; no APK/device claim from this source check alone.**
- **Push 30 — Product-detail hierarchy:** branch `feat/marketplace-product-detail-hierarchy-20261020`; commit `bc46276f5fb818b46eebb5bac3256717a7bc2bc3`. Changed `app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt`: moved seller identity, location, stock/condition, fulfillment availability/fee, and direct shop/contact actions into a compact seller/fulfillment block immediately after the product summary and before the long description. Uses the existing listing data and callbacks; does not change payment/order APIs or invent ratings. **Status: implementation committed; CI/build checks not yet verified; not merged; no APK claimed.**
- **Push 31 — Marketplace navigation and My Shop dashboard:** branch `feat/marketplace-my-shop-dashboard-20261020`; current implementation includes `app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt`, `FynxApp.kt`, `FynxMarketplaceSellerCenterPanel.kt`, `FynxSavedMarketplaceProductsPanel.kt`, `scripts/verify_marketplace_seller_flow.py`, and `backend/verify-marketplace-shop-profile.mjs`. Marketplace Home has direct My Shop, Orders, and Saved Products shortcuts; Orders fetches its data independently and shows loading/error retry states; Saved Products has a View product action that opens the exact canonical listing in Marketplace. My Shop has Overview, Shop profile, Listings, Orders, Delivery & drivers, and Earnings & payouts destinations. Existing seller order/driver entry points, listing edit/delete/shipping controls, shop profile save, settlement data/history, and payout account management are preserved. Regression checks cover quick access, saved-product return navigation, and the dashboard destinations. **Status: merged into canonical `integration/fynx-single-apk-20261013` by Push 31 squash commit `a06b0c276d9d269ae3343c46659996fe754d36a0`. CI passed on the feature commit; canonical APK/device verification is tracked separately.**
- **Push 32 — Product-card Save action:** PR [#299](https://github.com/greyhoundshop073-coder/FYNX/pull/299), branch `feat/marketplace-product-card-save-20261020`, fix commit `5a7996ce0644f70473d793f64a06aba3a10a5893`; squash-merged into canonical integration at `d5c63e42efc4e53fdbc67ec198799d30d1e461bf`. Android Build #6799, Chat/Group Parity + Layout Safety #1261, and Backend CI #2448 passed on the fix commit. Android artifact `FYNX-debug-apk-7005ec075731150890b0ff7198e72b14150573e3` was built from the feature commit; it is not a canonical-branch APK proof. Device/runtime verification remains separate.
- **Push 33 — Product-card hierarchy and feedback:** branch `feat/marketplace-product-card-hierarchy-20261020`. Make actual listing quantity visible as an `Out of stock` or `Only N left` badge when applicable; disable the Save control and show a spinner while its real save/unsave request is pending. Existing listing data and save APIs remain authoritative; no mock inventory or payment state is introduced. Regression checks cover quantity-based availability and pending Save feedback. **Status: merged into canonical integration by Push 33 squash commit `f32d182f18a9798a750d8feda810257b13823a6e`; Android Build #6805, Backend CI #2451, and Chat/Group Parity + Layout Safety #1263 all passed on PR head `79c22cf4e585d7111bb7dd63006281c064493ae9`. APK/device verification remains separate.**
- **Push 34 — Seller payout-account feedback semantics:** correct the My Shop payout-account status message so a successful “Payout account verified and connected.” confirmation uses the primary/successful feedback color instead of the error color, while actual failures remain errors. Add a regression assertion. **Status: merged into canonical integration by squash commit `649f37a45d1ce71b7b92f17393aed1b2b7d5dfa9`; Android Build #6811, Backend CI #2454, and Chat/Group Parity + Layout Safety #1265 all passed on PR head `f5dfe7ca8e309ab587c41cad7bb942e9b6062854`.**
- **Push 35 — Success-state semantic colour correction:** architecture rules specify verified success green `#16A34A`, not the primary navigation blue. Corrected the payout-account success confirmation to use the defined success green; failures remain errors. Regression assertion enforces the exact semantic colour. **Status: merged into canonical integration by squash commit `0ca05ea8ac8f81eab665e6c7698abd308ed4aef8`; Android Build #6816, Backend CI #2457, and Chat/Group Parity + Layout Safety #1267 passed on PR #303 head `390ef7bfb3ed184d3078f97bac36833ce430feb9`. Canonical APK/device verification remains separate.**
- **Push 36 — My Shop profile-save feedback semantics:** the existing shop-profile save confirmation used primary blue despite the semantic colour contract reserving green `#16A34A` for verified success. Updated the exact `Shop profile saved.` confirmation to success green; profile failures remain red/error. Added a regression assertion. No shop API or save behavior changed. **Status: implementation on feature branch `feat/marketplace-shop-profile-success-feedback-20261020`; PR/checks pending; no APK claimed.**
- **Next after Push 36 is green:** continue the My Shop action audit against existing seller APIs and error/loading states; then review cart/checkout/order/receipt consistency without changing payment truth or inventing runtime success.
