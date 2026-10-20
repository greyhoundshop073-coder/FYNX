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


## 22. Financial receipts and reconciliation contract

A financial receipt is distinct from the buyer's fulfillment confirmation that goods were received. Receipt generation must not mutate order, escrow, dispute, inventory, or payout state.

### Authoritative amount model
Use the existing backend quote, canonical order snapshot, checkout-group allocations, payment provider records, and settlement ledger. Do not calculate receipt totals from UI text or trust client-supplied totals.

For each item line, using integer minor units or the project's established decimal-safe money representation:

- `line_gross = unit_price × quantity`
- `line_net = line_gross − allocated_line_discount`
- `buyer_amount_due = merchandise_subtotal − applicable_discounts + applicable_delivery + buyer_paid_fees + taxes_actually_charged`
- `seller_net_proceeds = authoritative_seller_gross − seller_borne_fees − seller_borne_provider_costs − refunds/adjustments charged to seller`

These are reconciliation shapes, not permission to invent or reassign a fee. The implementation must map every term to current authoritative fields and the configured fee policy. A fee paid by the buyer must not be deducted from seller proceeds a second time. If the stored provider amount, canonical order total, checkout-group allocation, or ledger does not reconcile, do not issue a misleading receipt; return a safe error and investigate the transaction.

### Required receipt variants and privacy
- **Buyer receipt:** all items in the buyer's canonical order, item quantity/unit and line totals, seller/shop identity, currency, subtotal, discount, delivery, each applicable fee/tax, verified amount paid, transaction date/time and timezone, order ID, receipt ID, payment reference and payment status.
- **Seller sales receipt:** only that seller's item/sub-order rows, quantities, gross sales, relevant allocated discounts, explicit fee treatment, authoritative net proceeds and separate payout state. Never expose other sellers' line items or unnecessary buyer contact/delivery data.
- A multi-seller cart may have one full buyer receipt plus a separate seller receipt per seller sub-order. All records must link to the canonical parent checkout/order without creating a duplicate transaction.
- Store authoritative event time in UTC and display a clearly labelled timezone/offset. Preserve purchase-time item/title/price snapshots if the listing changes later.
- Receipt creation is stable/idempotent and only enabled once the backend confirms payment. Refunds, disputes, delivery, inspection, settlement eligibility and payout remain separately labelled events/states; later adjustments must be recorded as linked adjustments/refund evidence, not silently rewrite history.
- Provide authorized receipt retrieval, PDF generation, download to a user-accessible location, and Android share-sheet sharing. Sharing into FYNX chats should reuse the established message attachment/content-URI flow; never send a private filesystem path or leak bearer tokens.

### Checkout and release gates
Both single-item and multi-item checkout must show the exact server quote, itemized subtotal, discounts, delivery, buyer fees/taxes when applicable, currency and final amount in a consistent, uncluttered layout. The payment provider's hosted page remains provider-controlled.

Before release, test calculation/rounding boundaries, NGN/USD isolation, multiple sellers, discount and delivery allocation, fee payer treatment, partial/full refunds, duplicate provider events, stale quotes, amount/currency mismatch, unverified payment, unauthorized receipt access, long receipts/PDF pagination, repeat download/share, and reconciliation across buyer total, seller allocations, platform/provider amounts and ledger. Run all existing Android, backend, chat/group, marketplace lifecycle/security and required instrumentation checks; stop and repair any red gate before proceeding.
