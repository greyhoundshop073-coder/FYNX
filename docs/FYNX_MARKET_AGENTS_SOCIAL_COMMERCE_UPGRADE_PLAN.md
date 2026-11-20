# FYNX Market Agents — Social Commerce Upgrade Specification

**Status:** Product plan only — approved for documentation, NOT approved for implementation  
**Date:** 20 November 2026  
**Repository:** `greyhoundshop073-coder/FYNX`  
**Canonical Android/APK branch:** `integration/fynx-single-apk-20261013`  
**Feature name (working):** FYNX Market Agents  
**Product area:** Marketplace, My Shop, Seller Center, Local Business Directory and Wallet

---

## 1. Purpose and product vision

FYNX Market Agents is a future social-commerce upgrade that helps traditional traders and small local businesses sell online even when they do not know how to use social media, photograph products, create listings or manage online orders.

A person who understands FYNX and online selling can become an authorised **Market Agent**. The agent visits a trader, learns about the products, takes photographs, records prices and stock, creates or manages the trader's digital storefront, promotes the products through FYNX and helps process customer orders.

The trader supplies and owns the goods. The agent supplies the digital marketing and selling service. The customer buys through the existing FYNX Marketplace checkout. The agent earns an agreed commission or margin for completed sales. FYNX applies its disclosed Marketplace fees using the existing server-authoritative fee and ledger rules.

The intended beneficiaries are:

1. **Traditional traders and local businesses:** gain online reach without having to learn social media or operate every part of FYNX.
2. **Young people and other agents:** gain a legitimate earning opportunity by helping local businesses sell.
3. **Customers:** discover more products from real local shops and markets, with clear availability, pricing, delivery and support.
4. **FYNX:** gains more genuine merchants, products, customers and completed Marketplace orders.

This is an extension of the existing FYNX product, not a second marketplace, a separate shopping app or a new payment system.

## 2. Non-negotiable product principles

- **Easy for the trader:** the trader should not need to learn social media, create posts, upload product photos or operate the full Seller Center.
- **Easy for the agent:** one Agent Dashboard should manage several separately identified traders without mixing their products, orders, stock or money.
- **Clear to the buyer:** the buyer sees a normal FYNX product listing, a clear seller/store identity, an accurate price, availability, delivery terms and normal Marketplace protections.
- **Trader remains the owner:** the agent is a delegated manager/marketer, not the owner of the trader's business, goods or sale proceeds.
- **Permission before publishing:** an agent cannot list a trader, use their name/photos or represent their business without recorded authorisation.
- **One order and payment path:** use the existing Marketplace cart, checkout, escrow/protection, order, delivery, refund and Wallet accounting paths wherever they meet the requirements. Do not build a parallel checkout or wallet.
- **Server is authoritative:** price snapshots, commission, FYNX fees, settlement, stock reservations and refunds must be calculated and enforced by the backend, never trusted from client-only calculations.
- **No misleading earnings:** show estimates as estimates and confirmed/settled money as confirmed/settled. Never invent balances or mark a pending sale as earned cash.
- **No login regression:** do not change, replace or redesign the existing login, sign-in, OTP or session flows for this feature.
- **No unrelated scope:** do not modify Home/Feed, Avatar/Profile, Chat/Groups, Status/Stories, AI, Admin or unrelated app areas unless a separately reviewed integration dependency is approved.
- **No duplicate features:** extend the existing Marketplace, My Shop, Seller Center, Local Business Directory and Wallet.
- **Single canonical APK:** any future Android implementation must integrate into `integration/fynx-single-apk-20261013`. Never publish a feature-branch or temporary APK as the official FYNX APK.

## 3. Repository and existing-product fit

The repository and recent integration records show existing foundations that this feature should reuse:

| Existing FYNX area | Existing foundation / known evidence | Intended use in Market Agents |
|---|---|---|
| Marketplace | Product discovery, categories, product cards/details, seller listings and buyer journey | Agent-managed products appear as ordinary Marketplace listings with a clear store identity |
| My Shop | Seller storefront, inventory/listing management and shop overview | Remains the place for a trader's own shop; authorised agent management is added without creating duplicate shops |
| Seller Center | Seller orders, delivery coordination and seller earnings | Add an agent work view and per-trader context; preserve the existing seller experience |
| Local Business Directory | Local business navigation and business-detail/test-fixture work exist | Discover and onboard real local traders; do not assume the directory already provides agent delegation |
| Wallet / Marketplace accounting | Existing order fee snapshots, seller-net calculations, protected escrow and commission ledger; default seller-side FYNX Marketplace fee described as 5% in existing work | Reuse the authoritative ledger and settlement flow; add a separately auditable agent payable/commission component |
| Product media | Seller-uploaded listing media and on-device still-photo enhancement work | Reuse secure media upload and listing media; agent captures and uploads real product photos with consent |

Relevant repository history to review again before implementation:
- PR #390 — Marketplace category artwork, seller thumbnails and product-photo enhancement.
- PR #418 — Wallet funding/withdrawal UX and Marketplace commission UX; existing default seller-side FYNX commission policy and protected escrow/commission ledger.
- PR #461 — seller earnings breakdown; seller-side FYNX fee, delivery fees and net earnings are separate accounting concepts.
- PR #476 — My Shop overview redesign.
- PR #555 and #558 — Marketplace, Seller Center, Local Business Directory and Wallet Android runtime fixture/certification repairs.
- PR #563 — consolidated integration candidate; its own description explicitly says live Marketplace listings, My Shop and Wallet screens were not fully runtime-certified and it is not the final APK.

These references are architectural starting points, not proof that Market Agents already exists. At implementation time, inspect the current canonical branch and all relevant open/merged PRs, including any newer local-business or seller-management work. Reconcile before editing. Do not infer that a PR is merged just because its feature is described here.

## 4. Roles and ownership model

### 4.1 Trader / Local Business Owner

The trader is the person or business that owns the goods and authorises the agent. Examples include market women, foodstuff sellers, farmers, small shops, artisans and other local merchants.

The trader:
- Owns the goods and remains the recorded merchant/business.
- Approves the agent's authority and the products/prices the agent may publish.
- Agrees the minimum amount payable to the trader, or an explicit commission arrangement.
- Supplies or confirms stock and product condition.
- Fulfils or coordinates fulfilment according to the agreed arrangement.
- Can inspect orders, sales, statements and payment status through a simple owner view, SMS/link or assisted support path.
- Can suspend or revoke an agent's access.
- Can appoint a different agent without losing the shop, product history or reviews.

The trader must not be forced to install FYNX or learn its full feature set to participate in the first release. A lightweight owner confirmation and notification route should be designed. If the trader later joins FYNX, their business record should be safely claimable/linked after verification; do not create duplicate shops.

### 4.2 Market Agent

The agent is a FYNX user who helps traders sell online.

The agent:
- Uses their existing FYNX account; no separate agent login system.
- Applies for/activates the Market Agent role and completes the required checks.
- Finds a trader or registers a trader with their explicit permission.
- Visits the trader, photographs products and records product details, price and stock.
- Creates and maintains listings on behalf of the trader.
- Promotes the listings using permitted FYNX Marketplace/social sharing tools.
- Handles customer questions and order coordination within the permissions granted.
- Tracks each trader's sales and their own commission separately.
- Cannot withdraw or redirect trader funds, change protected settlement destinations, impersonate the trader, or remove the trader's ownership.

One agent can manage many traders. Each trader can have one or more authorised agents only if the trader explicitly permits it and responsibilities are unambiguous. The first release should default to one active primary agent per trader to reduce disputes; multi-agent access can follow after the permission and accounting model is proven.

### 4.3 Customer

The customer shops normally in FYNX Marketplace. The customer should not need to understand the agent arrangement to buy. The listing/store detail should provide a clear seller identity and a simple disclosure such as “Listed with help from a FYNX Market Agent” where useful. The buyer's order, payment, refund and support experience remains the existing Marketplace journey.

### 4.4 FYNX platform

FYNX verifies accounts and agent eligibility, records authorisations, enforces listing and price rules, routes orders, calculates fees, protects funds, records commission and resolves abuse reports under published policies.

## 5. Core user journeys

### Journey A — Agent joins

1. User opens Marketplace or Seller Center.
2. User selects **Become a Market Agent**.
3. A short explanation states what agents do, how earnings work, what responsibilities they accept and what is prohibited.
4. User reviews and accepts the agent terms.
5. User completes required identity/contact and any risk checks using existing verified account information where possible. Do not create a new login or duplicate KYC identity.
6. FYNX activates the agent role only after the required checks.
7. The user lands on a simple Agent Dashboard with three primary actions: **Add a Trader**, **Add Products**, and **View Orders & Earnings**.
8. Empty states explain the next step in plain language.

### Journey B — Agent meets a trader and gets permission

1. Agent selects **Add a Trader**.
2. Agent searches the Local Business Directory or creates a new trader/business record.
3. Agent records the business name, market/location, contact number, product type and preferred contact/notification method.
4. Agent explains the arrangement to the trader in plain language, including who owns the goods, the agreed trader amount, agent earnings, FYNX fees, delivery responsibility, returns and how to stop the arrangement.
5. Trader authorises the agent. The preferred design is a lightweight confirmation link/code sent to the trader's phone, or another auditable assisted-consent method approved by product/legal. This business-authorisation flow is separate from and must not change FYNX login/OTP.
6. Store the consent record: trader/business, agent, scope of permissions, date/time, method, terms version, agreed commercial model and status.
7. Until permission is confirmed, the trader record remains a draft and products cannot be publicly published.
8. Trader receives a simple confirmation/receipt and a way to contact FYNX or revoke permission.

If a trader has no smartphone or data, the future design must still provide an accessible assisted process, but it must leave an auditable record and prevent agents from fabricating consent. The exact offline/voice/paper fallback requires product, legal and fraud review before implementation.

### Journey C — Agent photographs and lists products

1. Agent opens the authorised trader's workspace.
2. Agent chooses **Add Product**.
3. Agent takes real photos of the trader's goods or selects photos with the trader's permission.
4. The form asks only for essential details first: product name, category, price/amount payable to trader, agent commission arrangement, available quantity, unit (e.g. bag, paint, basket, kg), condition, location and fulfilment method.
5. Optional details can be expanded; category-specific fields should reuse the existing Marketplace taxonomy.
6. Agent previews the customer-facing listing and sees a transparent breakdown of trader proceeds, agent earnings and FYNX fees.
7. Trader confirms the product/price/terms where required by the permission policy.
8. Listing is published through the existing Marketplace listing/discovery system and is clearly associated with the trader's store/business.
9. The product is visible in the agent's trader workspace and the trader's simple product view.
10. Agent can pause or mark unavailable; changes to agreed price, ownership or key terms may require trader re-approval.

Do not create a separate agent-only product catalogue. The same product must not appear as two duplicate listings merely because the trader and agent both view it.

### Journey D — Customer buys

1. Customer discovers the listing in the normal Marketplace.
2. Product details show price, unit/quantity, stock freshness, trader/store identity, location, delivery/collection options, returns policy and any agent-assisted disclosure.
3. Customer adds to the existing cart and checks out through the existing checkout/payment flow.
4. Backend creates an order with immutable snapshots of the product price, trader, agent, commission arrangement, FYNX fee, delivery fee, taxes/other applicable charges and terms at purchase time.
5. Stock is reserved atomically to prevent two agents or buyers from selling the same last item.
6. Order is routed to the trader and agent according to the agreed fulfilment responsibilities.
7. Buyer sees normal order updates and support options.

### Journey E — Fulfilment and completion

The trader and agent must agree who handles each step. Supported models should be explicit:
- Trader prepares goods; agent collects and hands to delivery partner.
- Trader hands goods directly to delivery partner.
- Agent coordinates buyer pickup at the trader's shop/market stall.
- Other approved delivery arrangements already supported by FYNX.

The order detail should show who is responsible for preparation, handover, delivery coordination and buyer communication. Do not promise tracking or delivery capabilities that the current backend/provider does not support.

After successful delivery/collection and the existing order completion/protection conditions:
- Trader payable is calculated.
- Agent commission becomes eligible according to the agreed policy.
- FYNX commission is recorded separately.
- Delivery charges are reconciled separately.
- Refunds, chargebacks, disputes or reversals follow the existing order/ledger lifecycle.

### Journey F — Trader reviews activity or leaves

Trader can receive concise SMS/WhatsApp notifications or use a lightweight owner link/view to see:
- Products listed and current prices.
- New orders and order status.
- Amount due to trader, amount protected/pending and amount paid.
- Agent earnings/commission charged under the agreement.
- FYNX fees and delivery charges where applicable.
- Contact/support and **Stop agent access**.

Revoking an agent must immediately block future listing edits and new sales activity by that agent. Existing orders remain traceable and must be completed/refunded through the normal process. The trader's store, listings, order history, reviews and settlement records must not be deleted or transferred to the agent.

## 6. Navigation and screen plan — keep it simple

Do not add a large new primary navigation tab in the first release. Add a clear entry point from existing Marketplace/Seller Center surfaces.

### Marketplace entry

- Keep the existing Marketplace discovery, categories, promotional banner, search, product cards and buyer journey.
- Add a modest **Become a Market Agent** entry for eligible users, likely within the existing seller/Marketplace actions rather than a competing top-level destination.
- Add **Find Local Sellers** only if it improves the existing Local Business Directory; do not duplicate its navigation or search.

### Agent Dashboard

One home screen with:
- Header: **Market Agent** and agent status.
- Main actions: **Add a Trader**, **Add Products**, **Orders**.
- Summary: active traders, active listings, orders needing attention, commission pending and commission available/paid.
- Trader list: shop name, market/location, active product count and order attention indicator.
- Search/filter for the agent's own authorised traders.
- Clear empty, loading, offline and error states.

### Trader workspace

When an agent selects a trader:
- Business/shop identity and authorisation status.
- Products and stock.
- Orders and fulfilment responsibilities.
- Agreed price/commission terms.
- Trader contact and notification status.
- Edit permissions and revoke/renew authority.

Always show which trader workspace the agent is currently inside. Never rely on colour alone; use the shop name and a clear breadcrumb/header to prevent editing the wrong trader.

### Add Product flow

Use a short guided flow:
1. Photograph product.
2. Name, category and unit.
3. Trader amount and agent earnings agreement.
4. Quantity and availability.
5. Delivery/collection arrangement.
6. Preview and submit for approval/publish.

Show only essential fields first; advanced details can be optional. Save drafts so poor connectivity does not force the agent to restart. Never publish a draft just because it was saved offline.

### Earnings

Separate views/labels for:
- Trader product proceeds.
- Agent commission (estimated, pending, available, paid, reversed).
- FYNX Marketplace fee.
- Delivery fee.
- Refunds/adjustments.

Use plain language. Avoid ambiguous labels like “Profit” when the number is gross sales or pending commission.

### Trader owner view

A very simple phone-friendly view:
- “Your products”
- “Your orders”
- “Your money”
- “Your agent”
- “Contact FYNX / Stop agent”

Do not force the trader into the agent's dashboard.

## 7. Commercial model and commission rules

This section defines the required system behaviour, not final public prices or a final FYNX fee policy.

### 7.1 Supported agent earning methods

The product may support these arrangements, with exactly one clearly selected per product/order agreement:

**Option A — Percentage commission**
- Trader and agent agree that the agent receives a defined percentage of the eligible merchandise amount.
- The calculation base must be explicitly defined (recommended: merchandise subtotal excluding delivery, taxes and FYNX fee).
- The exact percentage and amount are snapshotted at order time.

**Option B — Fixed amount per unit or completed sale**
- Trader and agent agree a fixed amount (for example, ₦2,000 per completed sale or per unit).
- The unit basis, quantity and refund treatment must be explicit.
- Fixed commission cannot exceed the available sale margin or cause trader proceeds to fall below the agreed floor.

**Option C — Agreed trader floor / agent margin**
- Trader agrees the exact amount they must receive per unit.
- Agent may advertise at an agreed customer price within any trader-approved price limits.
- The difference is the agent's gross margin before applicable FYNX fees/adjustments.
- Price changes beyond the agreed range require trader approval.

Do not combine percentage and markup automatically. If a future arrangement allows a hybrid, show a separate contract and breakdown.

### 7.2 Price transparency

Before a listing goes live, show a human-readable preview:

- Customer product price.
- Amount payable to trader.
- Agent commission or margin.
- FYNX Marketplace fee and who bears it.
- Delivery fee (separate).
- Any other mandatory charge.
- Estimated trader net and agent net.

The backend must calculate the final values. The Android preview is explanatory only and must match the backend quote.

### 7.3 FYNX Marketplace fee

Existing FYNX work documents a default 5% seller-side Marketplace fee and server-side fee-mode handling. Market Agents must not silently alter or bypass it.

Before implementation, confirm the current canonical backend configuration and decide, with explicit product approval, whether the fee is borne by:
- the trader,
- the agent's agreed margin,
- the buyer (only if existing policy and applicable law permit), or
- an explicitly disclosed split.

Never charge both trader and agent for the same fee unintentionally. Do not assume the previous illustrative ₦80,000/₦90,000 example is a final policy. All fee modes must be visible before acceptance and stored in the immutable order snapshot.

### 7.4 When commission is earned

- Commission is not earned merely because a listing is created, shared or added to cart.
- It becomes eligible only when the order reaches the existing successful completion/settlement condition.
- Commission must be pending/protected while the order is not yet eligible.
- Cancelled, returned, refunded, disputed or charged-back orders must reverse or adjust commission exactly once.
- Partial fulfilment/refund must support proportional or policy-defined recalculation.
- Use idempotent ledger entries and reconciliation; never credit from Android client state.

## 8. Data model and backend contract — design proposal

Inspect the actual existing schema and APIs before implementation. The following is a conceptual model to guide that inspection, not a command to create these tables verbatim.

Core entities/relationships likely required:

- **TraderBusiness:** durable business identity, display name, contact, market/location, verification/claim state, owner contact and notification preferences.
- **AgentProfile/AgentStatus:** references an existing FYNX user; verification state, terms acceptance and eligibility.
- **AgentMandate:** trader-business ID, agent user ID, consent status, permission scope, commercial terms, start/end/revocation time, consent evidence and terms version.
- **ManagedStore:** links the existing storefront/business identity to one or more authorised managers; avoid duplicate shop records.
- **ManagedListing:** reuses existing Marketplace listing identity and references the trader/business, creator agent and active mandate.
- **ProductPriceAgreement:** trader floor/price rules, agent commission type/value/base, FYNX fee payer/mode, currency, effective dates and approval state.
- **Order attribution snapshot:** immutable trader/business ID, agent ID, mandate ID, listing ID, price agreement version, product subtotal, delivery, FYNX fee, trader proceeds and agent commission.
- **AgentCommissionLedger:** auditable payable entries linked to order and existing Wallet/settlement ledger, with pending/available/paid/reversed states.
- **TraderNotification/ConsentAudit:** notification and authorisation evidence without storing unnecessary sensitive content.

Important constraints:
- A trader/business can exist independently of a FYNX user account.
- An agent must never become the owner of the trader's business merely by creating the listing.
- All ownership, mandate, role and order relationships must be checked server-side.
- Every order must preserve the exact agreement in force at purchase time.
- Use currency-safe decimal/minor-unit arithmetic and explicit currency; do not mix NGN and USD.
- Use database transactions and idempotency for stock reservations, order attribution, settlement and commission.
- Do not put secrets, full payment credentials or unnecessary identity data in client logs.
- Add indexes for agent-to-trader, trader-to-listing, order attribution, mandate status and commission reconciliation.
- Define retention, deletion, export and dispute audit requirements before collecting personal data.

### Required API behaviours

Exact route names are for later design. Reuse existing route conventions and authentication middleware.

- Apply/activate agent role and fetch agent dashboard.
- Search existing local businesses and create a draft trader record.
- Send/confirm trader authorisation and revoke/expire mandate.
- List agent's authorised traders and a specific trader workspace.
- Create/update/pause listings on behalf of a trader with mandate and scope checks.
- Preview/quote commercial terms and save an approved price agreement.
- Fetch trader-facing products/orders/earnings summary.
- Fetch agent-facing orders/commission summary.
- Attribute Marketplace order to trader and agent from trusted listing/mandate data.
- Reconcile commission with existing settlement/refund/dispute lifecycle.

The server must reject requests if the mandate is absent, expired, revoked or lacks the requested permission.

## 9. Stock, product quality and market realities

Traditional market inventory changes quickly. This is a core risk, not a minor UI detail.

- Capture quantity and unit precisely: bag, carton, crate, bottle, basket, bunch, kg, litre, piece, etc.
- Record when stock was last confirmed.
- Let agents mark an item “check availability” or pause it when stock is uncertain.
- For fast-moving or perishable goods, require frequent reconfirmation or use short listing availability windows.
- Reserve stock atomically at order placement; prevent overselling across multiple agents.
- Do not accept an order if stock is unconfirmed or insufficient.
- Define substitution rules for produce/food: no substitution without buyer consent.
- Show accurate product photos; do not use category illustrations as real product photos.
- Product description should state grade, size, quantity/unit, freshness/condition and relevant expiry/handling information where applicable.
- Keep the existing category taxonomy and seller media pipeline; do not create a second photo storage system.
- Provide a draft/offline capture mode if useful, but clearly label unsynced drafts and require a live server check before publishing or accepting orders.

## 10. Delivery, returns, refunds and disputes

- Reuse existing FYNX delivery/driver coordination and order lifecycle where supported.
- Every listing/order must declare who prepares the goods, who hands them over and who coordinates delivery.
- Delivery charges must be separate from product price and agent commission.
- Perishable/fragile goods need clear packaging and handover expectations.
- Buyer sees one clear support route; internal responsibility between trader and agent must not confuse the buyer.
- Returns/refunds follow the existing Marketplace policy and ledger.
- If the trader or agent fails to fulfil, the order must be cancellable/refundable under the applicable policy.
- A trader-agent disagreement must not block a buyer's legitimate refund.
- Maintain an auditable dispute trail and freeze disputed commission/settlement as required.
- Never allow an agent to mark an order delivered without the required evidence/confirmation defined by the existing delivery policy.

## 11. Trust, safety, fraud and compliance

Required protections before public launch:

- Agent eligibility and identity verification appropriate to transaction risk.
- Trader consent and proof of authority.
- Clear agent code of conduct: no false claims, fake stock, counterfeit goods, price manipulation, unauthorised photos, harassment or taking customer payments outside FYNX to evade protections.
- Product/category restrictions and prohibited-goods enforcement remain identical to Marketplace policy.
- Detect suspicious agent-trader networks, self-dealing, repeated cancellations, fake orders, review manipulation and commission abuse.
- Rate limits and audit logs for onboarding, price changes, listing changes and payout/settlement events.
- No agent access to trader bank details, Wallet PIN, credentials or unrelated personal data.
- Agent cannot change payout destination or withdraw trader funds.
- Trader can pause listings and revoke the agent.
- FYNX can suspend an agent or mandate while preserving orders and financial records.
- Customer reviews remain tied to verified completed purchases; agents cannot review their own listings or manufacture trader reputation.
- Clear privacy notice for business contacts, product photographs, location and communications.
- Legal review required for agency/commission terms, consumer protection, tax, data protection, payments and marketplace responsibilities in Nigeria and any future country.
- Do not activate real-money features until existing provider, custody, KYC, webhook, reconciliation and compliance release gates are satisfied.

## 12. Notifications and communication

Use existing supported notification mechanisms; do not invent delivery guarantees.

Events to consider:
- Trader authorisation requested/confirmed/revoked.
- Product awaiting trader approval or published.
- Price or stock changed.
- New order / order cancelled.
- Handover or delivery action required.
- Order completed and settlement/commission status changed.
- Refund, dispute or adjustment.
- Agent access suspended or expired.

Use concise SMS or WhatsApp notifications only after provider, consent, privacy and cost review. In-app notifications remain the primary FYNX experience where available. Never include sensitive financial credentials in notifications.

## 13. Phased delivery plan

Implement only after a separate explicit go-ahead. Each phase must be independently reviewed and tested, with no duplicate APK.

### Phase 0 — Canonical architecture audit (planning gate)
- Read current canonical branch, integration policy, ledger and all relevant open/merged PRs.
- Map actual Marketplace listing ownership, Local Business Directory records, My Shop/Seller Center APIs, order/escrow/fee/Wallet tables, media upload and delivery lifecycle.
- Identify what can be reused unchanged and exact schema/API gaps.
- Produce a reviewed technical design and migration/rollback plan.
- Do not code until this audit is approved.

### Phase 1 — Trader identity, mandate and agent dashboard (small controlled MVP)
- Agent role on existing FYNX account.
- Add/find trader business.
- Auditable trader permission and revocation.
- One primary agent per trader initially.
- Agent dashboard and trader workspace.
- No real-money changes and no parallel checkout.
- Tests for ownership, permissions, revoked/expired mandates and multi-trader isolation.

### Phase 2 — Assisted product listing and stock
- Capture real product photos.
- Create listings against trader-owned business identity using existing Marketplace pipeline.
- Add agreed price/commission preview and trader approval.
- Stock quantity/unit, freshness and pause/reconfirm flow.
- Draft save and reliable retry without accidental duplicate listings.
- Tests for media ownership, permission checks, price approvals and stock races.

### Phase 3 — Buyer orders and fulfilment attribution
- Existing Marketplace buyer discovery/cart/checkout.
- Immutable trader/agent/price agreement snapshot on order.
- Existing delivery coordination with clear responsibility.
- No new payment provider or checkout.
- Tests for normal orders, cancellation, stock reservation, attribution and order isolation.

### Phase 4 — Commission and settlement
- Integrate agent commission with existing seller/Wallet/escrow/commission ledger.
- Show trader proceeds, agent pending/available/paid/reversed commission and FYNX fees separately.
- Reconcile full/partial refunds, disputes, chargebacks and idempotent settlement.
- Keep payout execution disabled unless all existing provider/compliance gates are satisfied.
- Require backend/database integration tests and financial reconciliation tests.

### Phase 5 — Pilot, quality and expansion
- Pilot with a small, manually approved group of agents and traders in one or two markets.
- Train agents and provide trader-friendly help materials.
- Measure order completion, stock accuracy, dispute/refund rate, agent retention, trader satisfaction and time-to-list.
- Fix usability and fraud issues before opening registration broadly.
- Consider multiple agents per trader, agent referrals/ambassadors, more notification channels and additional regions only after the pilot is stable.

## 14. Acceptance criteria

The upgrade is not complete until all applicable criteria pass on the exact integrated commit:

### Usability
- A first-time agent can understand the feature and add a trader without confusing it with opening their own shop.
- The agent can manage at least several traders without mixing their work.
- The current trader/shop context is always obvious.
- A trader who does not use social media can authorise participation and receive understandable updates.
- A customer can buy through the normal Marketplace without learning the agent system.
- Forms are short, progressive, accessible, local-currency aware and usable on small screens/large fonts.

### Ownership and permission
- No listing can be published without a valid mandate.
- Revoked/expired mandates immediately block agent changes and new sales.
- Trader ownership persists when an agent leaves or is suspended.
- Agents cannot access another agent's traders or another trader's private information.
- Price/commission changes follow approval rules and do not rewrite existing order snapshots.

### Financial correctness
- Trader proceeds, agent commission, FYNX fees and delivery are independently visible and reconciled.
- Backend calculations match the customer quote and order snapshot.
- Commission is credited once only after the correct completion condition.
- Cancel/refund/partial-refund/dispute/reversal cases reconcile correctly.
- No client-side balance or commission credit is trusted.
- NGN and USD remain distinct and are never mixed.

### Reliability and quality
- Duplicate retries do not create duplicate traders, listings, orders or ledger credits.
- Concurrent purchases cannot oversell stock.
- Offline drafts never appear as live listings.
- Media upload failure, poor network and backend outage have clear recoverable states.
- Security, backend integration, Android unit/UI/instrumentation, accessibility and regression tests pass.
- Actual app screenshots and end-to-end journeys are reviewed; compile success alone is not runtime certification.

### FYNX integration
- Existing Marketplace, My Shop, Seller Center, Local Business Directory, Wallet, checkout, delivery and refund flows continue to work.
- No duplicate seller shop, product catalogue, payment path or commission ledger is introduced.
- Login/sign-in/OTP and unrelated Home, Profile, Chat/Groups and Status/Stories work remain untouched.
- The official APK is built only from the canonical branch and exact verified commit.

## 15. Pilot success measures

Measure:
- Number of verified active agents.
- Number of traders who gave valid consent.
- Time from trader onboarding to first approved listing.
- Percentage of listings with current/accurate stock.
- Product view-to-order conversion.
- Successful fulfilment and completion rate.
- Cancellation, refund, dispute and fraud rates.
- Trader net proceeds and time to settlement.
- Agent commission earned and paid, with reversal rate.
- Trader satisfaction and repeat participation.
- Customer complaints and repeat purchases.

Do not reward agents only for recruiting traders or creating listings. Incentives should primarily reflect successful, compliant, completed orders and trader/customer satisfaction.

## 16. Decisions required before implementation

These are explicit product decisions to resolve during Phase 0, not assumptions for a developer to make silently:

1. **Initial agent model:** one primary active agent per trader for MVP; confirm before coding.
2. **Trader consent:** select the legally valid, auditable phone/link/assisted consent method, including a safe route for traders without smartphones.
3. **Commission policy:** choose percentage, fixed amount or trader-floor/agent-margin as the MVP default; allow alternatives only if accounting and UX remain simple.
4. **FYNX fee payer:** confirm who bears the existing Marketplace fee in an agent-assisted sale and how it is disclosed.
5. **Merchant settlement:** confirm whether settlement goes directly to the trader through existing approved rails or another compliant method. Agent must not control trader funds.
6. **Fulfilment:** define the initial supported responsibility options using current FYNX delivery capabilities.
7. **Returns/perishables:** define category-specific cancellation, substitution and return rules.
8. **Verification thresholds:** determine agent/trader verification and transaction limits with legal/compliance review.
9. **Notifications:** choose SMS/WhatsApp/in-app combinations based on consent, provider availability and cost.
10. **Pilot markets:** choose a limited pilot location and trader categories based on operational support.

No future developer should invent these policies or silently select money-handling rules.

## 17. Explicit implementation boundary

**This document is a plan only.**

At the time this document is added:
- Do not create Android screens, Kotlin classes, backend routes, database migrations or feature flags for Market Agents.
- Do not change production data, user accounts, business records, Wallet balances, payment configuration or provider credentials.
- Do not merge unrelated pull requests or resolve unrelated integration conflicts as part of this planning task.
- Do not change login/sign-in/OTP.
- Do not build or publish an APK for this feature.
- Do not create a second Marketplace or separate checkout/wallet.
- Do not treat this document as authorisation to start implementation.

When the owner later approves implementation, begin with **Phase 0 — Canonical architecture audit**, update this plan if the live code has changed, and then request/record approval for the first implementation phase. All code must be integrated into the one canonical FYNX APK branch: `integration/fynx-single-apk-20261013`.

---

## Handoff instruction for future ChatGPT sessions and developers

Read this document before proposing or implementing FYNX Market Agents. Inspect the current repository and canonical branch rather than trusting old screenshots, old APKs, prior chat summaries or assumptions. Re-check the existing Marketplace, My Shop, Seller Center, Local Business Directory, Wallet, order, escrow, commission, media and delivery implementations. Reuse existing systems, identify exact gaps, and preserve all unrelated work. The user has requested planning and documentation only until they explicitly authorise implementation.
