# FYNX Marketplace UX and Shop Implementation Roadmap

Status: implementation contract; this document does not claim unverified UI or backend functionality.
Working branch: `feat/marketplace-driver-custody-20261016`
Do not merge PR #191 as part of this work.

## Product principle

Marketplace must feel like one clean shopping destination, not a grid of unrelated tools. Keep the shopping surface focused on discovery and product choice; put seller management in My Shop/Seller Center; put payment, delivery and dispute actions inside the order journey. Prefer small, progressive disclosures over permanent rows of controls.

## Current architecture discovered

- Android shopping/discovery surface: `app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt`.
- Existing seller management: `FynxMarketplaceSellerCenterPanel.kt`; currently reached through the Features destination as Seller Center, not from a clearly named Marketplace-native My Shop destination.
- Public seller storefront: `FynxMarketplaceSellerStoreDialog.kt`; currently combines seller profile details with seller-filtered listing data. Store name is currently represented on listings and may be derived from listing data rather than a dedicated Marketplace shop record.
- Android listing client: `FynxMarketplaceClient.kt`; reads `/api/marketplace/my-listings`, creates canonical listings, and updates/removes listing records.
- Canonical listing schema: `backend/marketplaceSchema.js`; listing fields include seller ID, store name, product details, price/currency, category, condition, quantity, location, delivery/pickup, media and active state.
- Business identity is a separate existing feature: `backend/businessRoutes.js`, `FynxBusinessClient.kt`, and `FynxBusinessAccount.kt`. Business profiles are one-per-owner and have business name/username/category/description/location/contact fields. Existing business integration explicitly permits personal listings to remain unlinked.
- Existing R6-G integration in `backend/r6gIntegrationRoutes.js` links canonical listings to business profiles and allows product context to be shared to Home, messages and groups without creating a second listing.
- Price watches are stored in `marketplace_price_watches`. This is price-alert state, not a saved-products collection.
- Existing cart, checkout, payment verification, protected orders, seller reputation, delivery evidence, settlement and disputes must be preserved.

## Target navigation

1. **Discover** — search, category filters, nearby items, recommendations and product cards.
2. **Product details** — media, description, price/currency, stock, fulfillment, seller/shop identity, reputation, contact, save, price watch, add to cart and buy.
3. **Saved** — buyer-curated saved products; keep separate from price alerts.
4. **Cart** — quantities and checkout; preserve current currency and product-count constraints.
5. **Orders** — payment state, fulfillment, driver/handover evidence, receipt confirmation, inspection and dispute path.
6. **My Shop** — private owner tools: shop identity, products, stock, seller orders/delivery, earnings and payout controls. Reuse the current Seller Center rather than build a duplicate seller-management system.
7. **Public shop** — buyer-facing shop identity, available products, seller reputation and contact.

Keep only the most useful actions visible at once. Secondary functions belong in detail screens or compact overflow/sheets. Do not add a permanent tab for every capability.

## Shop identity decision

Do not force every personal seller to create a Business Account. Do not treat a listing's `store_name` as a complete persistent shop identity. Before adding schema, verify whether an existing shop/business model already meets the need. If it does not, add the smallest dedicated Marketplace shop profile keyed to the seller account, with stable identity and one canonical listing association. Existing business profiles may be linked optionally, but personal listings must remain valid without that link.

Any schema change must be idempotent and backward-compatible. Existing listings, orders, reviews, price watches, media references and payout records must remain valid. Do not auto-merge personal and business identities or change seller ownership.

## Implementation batches and gates

### Push 1 — identity and compatibility
- Inspect route registration, listing create/update/delete paths, schema initialization and all consumers.
- Choose reuse vs. a dedicated Marketplace shop profile based on actual schema/API behavior.
- Establish persistent shop identity and safe listing association; keep old listings readable and writable.
- Gate: authenticated ownership checks; existing listings and order references survive; schema is safe on a fresh and already-populated database.

### Push 2 — My Shop entry and management
- Provide a clear Marketplace-native entry to the existing Seller Center.
- Group seller tools into Products, Orders & delivery, and Earnings/Payouts.
- Avoid copying order or payout logic into another screen.
- Gate: seller can find and manage existing products and order tasks with predictable back navigation.

### Push 3 — public shop and saved products
- Render public shop details from persistent identity and existing reputation.
- Audit existing save functionality before adding anything. Keep saved items separate from price watches and discovery event logs.
- Gate: save/unsave persists per account; public shop links use the canonical seller and listing IDs.

### Push 4 — progressive category fields
- Keep shared product fields compact.
- Reveal category-specific optional fields only when relevant.
- Begin with categories where missing details create meaningful buyer uncertainty (property, food, electronics, fashion, furniture, services).
- Gate: existing category values remain compatible; no irrelevant fields shown for other categories.

### Push 5 — transaction confidence
- Audit inventory/quantity, price/currency, fulfillment options, protected payment, handover evidence, buyer receipt/inspection, dispute and payout state.
- Keep financial and order-state decisions server-authoritative.
- Gate: no UI action can falsely mark payment, delivery, completion or payout as successful.

### Push 6 — integration and release proof
- Audit Home product sharing, chat links, business profile links, storefront, saved items, cart, orders and Seller Center.
- Check loading/error/empty states, contrast, small screens and Android back behavior.
- Run relevant backend verifiers and Android CI. Verify the newest APK and real in-app journeys before claiming completion.

## Required engineering safeguards

- Inspect callers and tests before changing shared contracts.
- Do not duplicate listing, business, order, reputation, checkout, settlement or driver systems.
- Preserve the current test-only login; this Marketplace work must not add a login/OTP dependency.
- Do not reintroduce Render; backend direction remains Railway + Neon.
- Do not merge PR #191 without explicit permission.
- Make coherent changes; fix every failed CI run before another push.
- A green source/CI result is not proof of working backend connectivity or correct APK UI. Final acceptance requires newest APK evidence.

## Visual acceptance rules

- One clear primary shopping task per screen.
- Consistent spacing, card corners, typography and high-contrast text.
- Search and cart remain easy to reach without crowding the header.
- Category list is compact initially; full category selection is discoverable.
- Product cards prioritize image, title, price, seller and availability; secondary metadata must not compete with price.
- Public shop and private management are visually distinct but share the same canonical data.
- Every detail screen has a predictable back path; loading, empty, error and retry states are designed, not accidental.
