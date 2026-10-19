# FYNX BUILD PLAN

## Rule
Build one major stage at a time. Inspect existing code first. Keep each stage consolidated. Build Android after every stage. 🟢 Green = continue. 🔴 Red = fix before continuing. Never duplicate existing functions. Never put API secrets in the APK. Full integration verification is performed as one consolidated pass, not as separate feature-by-feature builds.

## LOCKED FYNX APK / UX INTEGRATION RULE
Whenever a feature is intended for users, it is not complete merely because code exists or a verifier passes. It must be correctly positioned in the APK, have the expected entry point and controls, provide the complete user interaction and states, connect to the real backend/storage where required, handle navigation/keyboard/insets/errors/retry appropriately, and be verified in the applicable real user journey. Established social/messaging/camera products may be used as UX references for familiar interaction patterns, but FYNX's existing visual language and architecture must be preserved. GREEN means this complete standard is met. Once GREEN, keep it locked and do not reopen it for theoretical concerns; reopen only for an actual regression or failed test. This rule is canonical for future FYNX chats and must be followed before moving to another workstream.

## ACTIVE PHASE — FYNX REALITY / FUNCTIONALITY FIX PASS
Continue the existing FYNX Android social + marketplace app. Do not rebuild or replace working systems. The current execution order is runtime/navigation, identity, people/profiles/privacy, chat/realtime/media, groups/social, marketplace/payment protection, camera/media reliability, notifications/preferences, AI flows, trust/safety, then final integration/performance/security/regression.

### People Discovery → Complete Profile
- [x] Authenticated backend profile-detail contract
- [x] Backend profile visibility enforcement
- [x] Authoritative display name and username
- [x] Backend-backed bio, country and verification fields when available
- [x] Privacy-aware activity visibility
- [x] Mutual connection and post counts where permitted
- [x] Backend-driven friend/request state
- [x] Message, block and remove-friend actions use existing pipelines
- [x] Loading, empty, denied and retry states
- [x] Backend-backed profile photo editing/upload
- [x] Backend-backed follow/unfollow
- [x] Backend-backed report case submission/status
- [x] Profile content/media feed reuse
- [x] Remote profile photo display with privacy-aware media fetch

### Chat & Group Chat
- [x] Private conversation camera entry and shared CameraX foundation
- [x] Group conversation camera entry using the shared camera
- [x] Server-authoritative group creation/membership foundation
- [x] Authenticated group message send/load pipeline
- [x] Group media message ownership validation
- [x] Original FYNX conversation wallpaper/background
- [x] Full real-device voice/video call journey foundation restored; runtime device verification remains in final audit

### Professional behavior
- [x] Server-enforced privacy/blocking protections for completed systems
- [x] Message/media reliability foundations
- [x] Realtime/background recovery foundations
- [x] Full real-device user-journey audit across every major surface

### Large Badge #4 — Full Real-Device User-Journey Certification
- [x] Emulator instrumentation is executed against the production-shaped app
- [x] Home, Chat, Friends, Stories, Marketplace, Calls, Notifications, Privacy, Money and AI routes are wired
- [x] Runtime screenshots and UI hierarchy are captured
- [x] Existing smoke, deep-link, recovery, call and account-isolation instrumentation remains present
- [x] No fake application data is introduced during verification
- [x] Consolidated workflow is GREEN

### Large Badge #5 — Camera + Media Reliability & Full Media Display Certification
- [x] Shared CameraX photo/video capture remains production-wired, including permission, lens, flash, zoom, exposure, timer, preview, retake and send states
- [x] Remote image/video/audio media uses authenticated backend download, account-scoped caching, bounded media size, retry/error states and resource cleanup
- [x] Social posts and Status/Stories reuse the same real remote-media path with correct aspect-ratio presentation
- [x] Profile avatars use real backend media and privacy-aware fallback behavior
- [x] Existing media/post/camera verification gates remain GREEN
- [x] Android unit/instrumentation coverage validates media lifecycle and camera-to-post behavior
- [x] No fake media records or fabricated application data are introduced
- [x] Badge is not complete until the consolidated workflow is GREEN

## Future backlog
- [x] Personal status/timeline expansion — backend-first timeline now surfaces authenticated Status media/text/audio while retaining Stories creation/viewing
- [x] FYNX official announcements/admin controls — server-backed announcements are exposed in the APK with authenticated retry/error handling
- [x] Owner/Admin control center — server-authoritative dashboard, admin grants/revocation, account status and marketplace protection controls
- [x] Anti-scam, account safety and appeals — server-backed safety/report/appeal flows certified by Large Badge #9
- [x] Server-backed notification preferences foundation

## Stage 12 follow-up
- [x] AI Creation stays connected to the existing media/posting pipeline
- [x] AI-assisted captions, rewrites, ideas and marketplace descriptions
- [x] AI Photo Editor integration
- [x] AI Money Coach
- [x] Chat profile information improvements — conversation header uses real remote display name, username, joined/country data and remote profile identity
- [x] Consistent profile media display foundation
- [x] Consistent full media display across status/social/media surfaces — shared authenticated remote media path is reused across Status, social/profile content and chat/group media

## Removed permanently
AI image generation and AI video generation are NOT part of the FYNX roadmap. Do not add them back.


### Large Badge #6 — Marketplace Protected Transaction Full-Lifecycle Certification
- [x] Buyer checkout creates a protected order before payment and preserves the canonical order ID
- [x] Payment is verified server-side before protected-order progression
- [x] Seller shipping, buyer receipt confirmation and post-inspection completion use the real backend lifecycle
- [x] Disputes lock the canonical order and preserve prior order/escrow state
- [x] Refund/payout resolution is mutually exclusive and idempotent
- [x] Escrow and financial operations expose explicit pending, disputed, released/refunded states with unique idempotency/provider references
- [x] Order evidence and inventory reservation remain server-authoritative
- [x] Existing marketplace security gates plus real Android instrumentation remain GREEN
- [x] No fabricated buyers, sellers, listings, orders, payments or balances are introduced
- [x] Badge is not complete until the consolidated workflow is GREEN


### Large Badge #7 — Notifications + Preferences + Delivery Certification
- [x] Server-backed notification feed, read/unread state and account isolation
- [x] Server-backed per-category notification preferences and quiet mode
- [x] Authenticated device registration/unregistration with unique device ownership
- [x] Server-side FCM delivery honors user preferences and handles retry/invalid tokens
- [x] Real event wiring covers messaging, groups and social activity without fabricated records
- [x] Existing notification/security gates plus real Android instrumentation remain GREEN
- [x] Badge is not complete until the consolidated workflow is GREEN



### Large Badge #9 — Trust, Safety & Account Protection Certification
- [x] Server-backed report creation, account-scoped report history and abuse-case status
- [x] Real block/unblock enforcement remains server-authoritative across social/profile/media flows
- [x] Account safety controls persist server-side and are authenticated/account-isolated
- [x] Appeals are authenticated, tied to the submitting account and cannot access another user's report
- [x] Anti-scam content inspection remains server-side and protected by the existing abuse guard
- [x] Privacy & Safety APK surface exposes real report submission, report history and appeals states
- [x] Android and backend verification cover the complete trust/safety contract with no fabricated safety records
- [x] Badge is not complete until the consolidated workflow is GREEN

### Large Badge #8 — FYNX AI Flows & Intelligence Certification
- [x] Real FYNX Assistant entry point, text composer, send, retry/error and conversation controls are production-wired
- [x] Persistent authenticated AI conversations and real media attachments remain user-scoped
- [x] AI context, tool execution, confirmation-gated messaging and sensitive-action restrictions remain server-authoritative
- [x] Voice/realtime AI uses authenticated FYNX session transport with bounded tool processing
- [x] Provider credentials remain server-side and abuse/security controls remain active
- [x] Existing AI extension and security gates remain GREEN
- [x] No fabricated AI application data or secrets are introduced
- [x] Badge is not complete until the consolidated workflow is GREEN


### Large Badge #10 — Final Integration & Release Certification
- [x] Major social, messaging, marketplace, media, notifications, AI and trust/safety certification gates remain consolidated in CI
- [x] Production journey and security certification remain wired before Android build/test
- [x] Android lint, unit tests and APK assembly remain mandatory
- [x] Real Android instrumentation executes before release artifact publication
- [x] Runtime screenshots and UI hierarchies are captured from the built APK
- [x] Exact-commit debug APK artifact is published only after successful build/test/instrumentation
- [x] No common API-secret patterns are permitted in critical client files
- [x] Final certification is complete — consolidated workflow is GREEN


### Large Badge #11 — Final Product Completeness & UX Integration
- [x] Official FYNX announcements are reachable in the APK and backed by the authenticated backend
- [x] Owner/Admin control center uses server-authoritative dashboard, role protection, account controls and marketplace protection review
- [x] Chat profile information displays real remote identity details without fabricated user data
- [x] Full media display is consistent across Chat, Groups, Status/Stories, social/profile and marketplace surfaces using the shared remote-media path
- [x] Trust/safety, appeals and account-protection continuity remains connected to the product surfaces
- [x] Dedicated product-completeness verifier is wired into the Android release workflow
- [x] Badge is complete when the consolidated workflow remains GREEN


### Large Badge #12 — Authenticated Runtime Visual Certification
- [ ] A dedicated real FYNX account is supplied to CI through GitHub Actions secrets only
- [ ] The emulator signs into FYNX through the existing production authentication flow
- [ ] Authenticated Home is captured after the real session is established
- [ ] Authenticated Chat, Friends, Stories/Status, Features, Money and FYNX AI surfaces are captured from the running APK
- [ ] No fabricated account, token, post, message, listing or other application record is created by the visual test
- [ ] Credentials never enter the APK or repository
- [ ] Authenticated visual certification is mandatory before the next product badge


### Large Badge #13 — Regression, Security & Release Hardening Certification
- [ ] Critical backend/client security invariants remain enforced: authentication, HTTPS transport, trusted media host, bounded responses/media, request limits, rate limits and database timeouts
- [ ] Secure Android token storage and logout/session invalidation remain intact
- [ ] Existing production, network, security, marketplace, trust/safety and product-completeness gates remain consolidated before APK publication
- [ ] Authenticated runtime visual certification remains mandatory and uses CI secrets only
- [ ] Real Android instrumentation remains mandatory before APK publication
- [ ] No common API credentials/private keys are committed to Android source or manifest
- [ ] Exact-commit APK publication remains success-gated
- [ ] Badge is not complete until the consolidated workflow is GREEN


### Marketplace Push 22 — Itemised buyer/seller receipts, exact financial reconciliation, and checkout presentation (PLANNED; not implemented yet)

**Carry-forward register — do not silently drop these requirements**
- [x] Push 15 — driver custody foundation (previously completed; preserve existing implementation).
- [x] Push 16 — My Shop entry point (previously completed).
- [x] Push 17 — persistent shop identity (previously completed; merge commit `83ca55a5fb76c409beef76f8497fa794867179df`).
- [x] Push 18 — My Shop listing editor (previously completed; merge commit `0f267b8747ae65ee06321ecd496c1500be40d8cd`).
- [x] Push 19 — fulfillment availability controls (previously completed; merge commit `fc1f403e7673257ef4858e5a3dd3008334a78d02`).
- [x] Push 20 — public storefront and Saved Products (PR #267; previously reported merged).
- [x] Push 21 — category-specific listing forms (PR #269; merge commit `aa1d15453d84cb5b39c0983d99763f9e38bd8b97`). Canonical integration checks verified green: Android run 39282820730, Backend run 39282820713, Chat/Group parity run 39282820786.
- [ ] Push 22 — inspect current implementation first; add buyer and seller itemised financial receipts, PDF export/download, Android share-sheet sharing (including FYNX chats where existing chat attachment flow supports it), receipt re-open access from buyer orders and seller sales, and the agreed neat single-item/multi-item checkout and payment UI. Do not create a parallel order/payment system.

**Receipt correctness checklist**
- [ ] Receipt is issued/displayed as a financial receipt only after backend-authoritative payment verification; never on button tap or from a client-only success state.
- [ ] Use the canonical order/sub-order and provider payment references; stable, unique receipt ID; idempotent receipt generation and repeat download/share.
- [ ] Record and display transaction timestamp from the authoritative backend. Persist UTC; render with an explicit timezone/offset so the time is not ambiguous.
- [ ] Item rows show product title/identity, seller/shop, quantity, unit price, line amount, and applicable line discount. Preserve the historical purchase snapshot even if a listing is later edited or removed.
- [ ] Financial calculation uses integer minor units (kobo/cents) or the backend's canonical decimal-safe money representation; never binary floating-point for authoritative totals. Apply documented currency rounding once at the defined boundary.
- [ ] For every line: line gross = unit price × quantity; line net = line gross − allocated line discount. Validate quantity is positive and bounded; validate all money is finite, non-negative where applicable, and within currency limits.
- [ ] Buyer order total reconciles as: merchandise subtotal − discounts + applicable delivery charges + buyer-paid fees + applicable taxes only when actually charged = amount paid. Do not invent a tax or fee.
- [ ] Every fee/discount is sourced from the server quote/order snapshot and shown once only. Do not add a fee again if already included in the provider amount or line/order total.
- [ ] For multi-seller carts, keep one complete buyer order breakdown and seller-specific receipts/sub-orders. Allocate delivery and discounts according to the backend's recorded allocation rules; the sum of seller allocations plus separately identified platform/provider amounts must reconcile to the canonical order total.
- [ ] Enforce same-currency carts (NGN or USD as supported); never add NGN and USD values together. Format with correct currency code/symbol and precision.
- [ ] Seller receipt shows only that seller's items and authorized transaction details. Seller proceeds come from the authoritative settlement/ledger record, not a client-side recomputation: gross sale, fee payer and fee amounts, provider deductions only when actually charged to seller, refunds/adjustments if any, net proceeds, and payout state.
- [ ] Never deduct a buyer-paid fee from seller proceeds a second time. Clearly distinguish payment verified, fulfillment/delivery state, dispute state, settlement eligibility, and payout paid/pending/failed/reversed. Payment receipt must not claim delivery or payout happened unless those events are independently confirmed.
- [ ] Buyer and seller see only their authorized receipt; no leakage of another seller's items, buyer private address/phone, payment secrets, or another account's data.
- [ ] Receipt verification shows a safe canonical order/receipt reference and verified status; do not expose tokens, internal secrets, or unrestricted private endpoints.
- [ ] PDF has readable layout, item rows, totals, timestamp/timezone, currency, parties, order and payment references, status labels, and page-break handling for long carts. Download saves to a user-accessible phone location; share uses Android's supported content URI/share flow, not a raw private filesystem path.
- [ ] Provide clear loading/error/retry states; repeated share/download must not create duplicate financial events or receipts.
- [ ] Checkout/payment UI follows the accepted tidy layout for both single-product and multi-product flows: aligned fields, itemised summary, clear fulfillment selection, fees/discounts, prominent exact total and secure-payment explanation. Do not alter payment-provider verification or claim control over the provider-hosted page.

**Required verification before Push 22 can be marked complete**
- [ ] Backend tests for server-authoritative receipt eligibility, ownership isolation, stable receipt identity, idempotency, and historical item snapshots.
- [ ] Calculation tests for quantity × unit price; multiple lines; zero/partial/full discount; delivery charge allocation; buyer-paid vs seller-paid fee; refund/adjustment; NGN and USD precision; large quantities/amount bounds; and rounding boundaries.
- [ ] Multi-seller reconciliation test: buyer total equals the sum of the recorded seller/platform/provider allocations under the current fee policy, with no duplicated delivery, fee or discount.
- [ ] Negative tests for pending/failed/unverified payment, mismatched amount/currency/reference, duplicate verification/webhook, unauthorized buyer/seller, and attempts to read another seller's receipt.
- [ ] PDF/share/download tests and UI regression checks for empty/long carts, long product names, large totals, and both checkout variants.
- [ ] Run Android Build, Backend CI, Chat/Group parity, marketplace security/lifecycle verifiers, and real instrumentation required by the existing workflow. Fix every red check before starting another push.
- [ ] Verify the exact successful commit's APK artifact. Do not call it phone/runtime-proven unless an authenticated real-device/emulator flow actually demonstrates it.

**Calculation gate:** do not implement totals by guessing the fee policy. First inspect the current quote response, order snapshot, checkout-group allocation, provider amount, ledger/settlement fields and refund rules. Document the actual formulas and field mapping in tests; where authoritative inputs are absent or contradictory, fail closed and fix the backend contract before generating a receipt.
