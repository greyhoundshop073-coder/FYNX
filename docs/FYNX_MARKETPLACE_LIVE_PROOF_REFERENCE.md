# FYNX Marketplace & Live Proof — Reference Design Contract

Status: active implementation reference for `build/fynx-one-apk-integrated-20261009`.

The two user-provided reference images in the October 2026 conversation are the visual authority for this work. They show intended layouts, not permission to seed fake products or fake users. Keep this document alongside the source so later sessions can recover the design contract. The original images remain attached to the conversation; this text record preserves their required layout and behavior in the repository.

## Reference image A — Marketplace experience

### Marketplace discovery home (top-left panel)
- Dark navy/near-black background, FYNX purple/blue accent, rounded cards, compact horizontal category chips.
- Header title “Marketplace”, search field with placeholder “Search products, sellers or categories”, cart and orders/receipt actions.
- Category row includes Near me, All, Electronics, Fashion, Home and other categories.
- A wide “Great Deals / Better Prices” promotional banner with “Shop Now”. The banner must not invent product photos or claim a specific discount unless actual listing/discount data supports it.
- Separate horizontal sections: “Recommended for you”, “Popular near you”, “Explore categories”, and “New on FYNX”, each with “See all” where a destination exists.
- Product cards show actual listing photos when present, actual title, actual price/currency, real seller/store identity and location. No sample or hard-coded products.
- Floating “Sell” action and persistent bottom navigation consistent with the rest of FYNX.

### All-products listing grid (top-middle panel)
- Two-column grid, real listing image, condition badge where supported, save/favorite control if implemented, actual title, price, store/seller and location.
- Filters/sort control should reflect real supported functionality; do not display inert controls as working.

### Product details (top-right panel)
- Large product image with thumbnail gallery when multiple real media items exist; image count must match real media.
- Title, actual condition, price/currency, rating/review data only when supplied by real data, seller/store identity and verification only when verified.
- Follow/message actions, delivery/pickup availability, description, location and actual quantity.
- Buy Now, Save, Share and Report controls must be wired to existing flows.
- Live Proof entry must pass the selected listing identity and reuse the shared FYNX camera/video-call infrastructure.

### Dedicated search (bottom-left panel)
- Search input, recent searches, clear action, popular categories and trending searches.
- Recent/trending values must be genuine user/search data or safe empty states; never fabricate activity.
- Selecting a search or category should return actual results.

### Seller profile (bottom-middle panel)
- Real seller/store identity and verification, real product count/rating/follower count only where backed by data.
- Follow/message actions and a Products/About layout.
- Seller product cards must be real listings belonging to that seller.

### Empty Marketplace (bottom-right panel)
- Store icon, “No products yet”, “Be the first seller on FYNX”, “Sell something” CTA and floating Sell action.
- This is the correct new-app state when no products exist. It must not be replaced with fake products.

## Reference image B — Marketplace Live Proof

The eight storyboard stages define the intended flow:
1. Product listing exposes a Live Proof entry.
2. Marketplace-specific camera screen identifies the selected product and seller and reuses the shared FYNX camera engine.
3. Active live session shows real seller identity, LIVE state, elapsed time, video and call controls.
4. Camera controls include front/back switch, flash when supported, grid, zoom when supported, switch to video, and end session.
5. Marketplace home may mark eligible listings with a Live Proof badge only while the actual capability/session state supports it.
6. Seller view shows the real listing, session status and concise safety tips; seller can end the session or chat with the buyer.
7. Buyer view shows the real seller and product context and supports asking questions through the existing call/chat architecture.
8. Completion state offers “Continue to Chat” and “View Listing”, returning to the correct destination.

## Data integrity and acceptance rules
- Never seed demo products, invented sellers, fake ratings, fake review counts, fake availability, fake discounts, or fabricated Live Proof sessions to make a screenshot look populated.
- No products is a valid state. A backend timeout is a separate “backend unavailable / not verified” state, not proof that there are no products and not proof that the UI is broken.
- Separate source implementation, APK inclusion, automated verification, backend-dependent verification, and live provider verification in all reports.
- Preserve the existing single-product Buy Now flow and shared FYNX camera/video-call system.
- Changes must be minimal, scoped, and tested on the isolated candidate branch before any merge to main.
