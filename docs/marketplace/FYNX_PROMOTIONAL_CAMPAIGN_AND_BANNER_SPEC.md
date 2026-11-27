# FYNX Marketplace Promotional Campaign & Banner Specification

**Status:** Product/engineering contract — preserve these decisions; implementation status must be verified against the canonical branch before any feature is marked complete.  
**Canonical Android branch:** `integration/fynx-single-apk-20261013`  
**Last reviewed:** 2026-11-27

## 1. Purpose

The Marketplace promotional banner is a **visual, clickable campaign placement**, not a text-only announcement box. Its image, copy, CTA, advertiser/owner and destination are one campaign record. A user must be taken to the exact in-app destination configured for that campaign.

Do not implement a separate banner, campaign manager, uploader, destination router or sponsored-ad system if an existing canonical implementation already provides it. Inspect and reuse the work referenced below.

## 2. User-visible banner

- Use the agreed full-width, photo-led carousel (PR #439 specifies 224 dp slides), with a real campaign/product image as the visual focus.
- Overlay or place a concise headline, optional supporting description and a clear CTA on the image in a readable, accessible treatment.
- Show page indicators and retain automatic horizontal rotation (PR #439 specifies 6.5 seconds), manual swipe, loop-boundary recovery and consistent height.
- Preserve the Sponsored label for paid placements. Keep organic featured products, seasonal/official announcements and paid sponsored campaigns distinguishable.
- Do not render a plain text-only gradient as a successful campaign creative. If no approved image exists, use the explicitly designed branded storefront fallback for the evergreen/empty state; do not invent a product, seller or photograph.
- Preserve existing Marketplace discovery content below the banner (Top Sellers, Recommended Products, Near Me, categories and product discovery), and existing Orders/Refresh actions.
- Optional small looping animated sticker/video-like creative may be supported inside the banner only where the media pipeline, file-size limits, playback lifecycle, accessibility and reduced-motion behavior are implemented. It must not replace the main campaign image or make the banner a full-screen video.

## 3. Campaign destinations and click behavior

Every campaign must have an explicit typed destination. The entire visible banner and its CTA should invoke the same configured destination unless a deliberate, documented secondary action is added.

Supported in-app destination types already described by PR #452:

| Destination type | Required target | Tap result |
|---|---|---|
| Marketplace | No target ID | Open Marketplace discovery |
| Category | Canonical Marketplace category | Open that exact category using the same category taxonomy buyers browse |
| Product listing | Canonical product/listing ID | Re-fetch and validate the listing; open its product details only if still active and in stock |
| Seller shop | Seller username/canonical seller identity | Open that seller's public shop/storefront, not the viewer's private My Shop |

Rules:
- Product promotion: show the promoted product's real image and details; tap opens that exact product listing.
- Seller/shop promotion: show the seller's campaign creative; tap opens that seller's public storefront.
- Category promotion: tap opens the selected category.
- General Marketplace/official announcement: tap opens the explicitly selected Marketplace destination.
- Never silently route an invalid/missing product or seller target to a generic Marketplace page. Show a safe unavailable/expired state or omit the campaign.
- Backend must validate destination type and required value, canonicalize category labels, verify product targets are active/in stock, and verify seller targets exist. Revalidate product availability at tap time.
- Preserve the destination in authenticated create/update/read campaign payloads and across app navigation.

## 4. Owner/Admin campaign management

Management belongs in the existing Owner Control Center / Admin Control Center under **Marketplace**. Do not create a second management area or duplicate uploader.

The authorized campaign editor must support:
- Uploading/selecting actual campaign artwork through authenticated FYNX media storage, with image preview and clear upload/error/progress states.
- Entering campaign headline, supporting write-up, CTA label and typed destination with the target selector appropriate to that destination.
- Selecting campaign type (for example official/seasonal announcement, featured product/shop placement, or sponsored campaign) while keeping their eligibility and accounting rules separate.
- Ordering campaigns, scheduling start/end timestamps, publishing, pausing, replacing artwork/details, and removing/archiving a campaign.
- Previewing the actual composed banner before publishing, including its image, text, CTA, Sponsored label where applicable, and destination summary.
- Validating required fields before publish. A campaign must not go live with a missing/broken image or an unresolved destination, except the documented branded evergreen fallback state.

Image and media security:
- Use authenticated FYNX media upload/storage, not an ephemeral device-local URI or a test-only bundled image as the production campaign asset.
- Verify the uploading Owner/Admin is authorized to manage the campaign and owns/has permission to use the media.
- Persist a stable media reference/object key and retrieve it through the authorized media path. Do not store a short-lived local file path as the campaign image.
- For scheduled seasonal artwork, preserve the PR #439 rule that Marketplace viewers can retrieve it only while the associated campaign is active and inside its date window.
- Keep secrets and privileged storage credentials on the server; do not embed them in the Android app.

## 5. Campaign lifecycle and carousel eligibility

- A campaign is eligible only when approved/published, within its configured start/end window, not paused/removed, and its image and destination are valid.
- Respect campaign ordering/rotation and manual swipe. Rotation must not reset unpredictably during recomposition or when isolated tests render a single slide.
- Sponsored product campaigns must remain linked to the advertiser's own active listing with available stock and must satisfy the existing payment/approval rules.
- Audience targeting must remain disabled until audience matching is actually implemented; do not imply targeting exists.
- Seasonal/official campaigns are not paid ads and must not affect Seller of the Week ranking.
- Do not fabricate Best Seller winners. Existing weekly seller awards must continue to use verified completed-order evidence, not paid impressions, clicks or ad spend.

## 6. Metrics and commercial controls

- Preserve existing sponsored impression/click measurement and Sponsored disclosure from PR #439.
- Count a view only under the existing agreed visibility rule (PR #439 specifies at least one second of visible slide time); exclude advertiser-owned views/clicks.
- Keep paid advertising separate from organic seller-award scoring.
- A banner, Sponsored label or click counter does **not** by itself mean advertising is commercially ready.
- Before charging advertisers, verify the canonical implementation of approved pricing, payment verification, prepaid budget, qualified-click/impression rules, spend limits/pacing, idempotency/deduplication, refunds/credits, ledger entries, advertiser reporting and reconciliation. PR #439 explicitly listed budget pacing and per-event spend settlement as gaps at that time; PR #457 later describes prepaid CPC work and must be checked for merge/current-canonical status before treating those gaps as closed.
- Never invent an ad price or charge a seller without an approved pricing model and verified payment/ledger flow.

## 7. Acceptance tests — real behavior, not text-fixture checks

A feature is not complete merely because a form accepts a headline or a test screenshot contains text.

1. **Image rendering:** publish/fixture-load two different real image assets and verify the visible banner image changes with the active campaign; no text-only gradient is accepted as image proof.
2. **Image persistence:** upload an image, close/reopen or reload campaign data, and verify the same stored image is retrieved from the media service.
3. **Product click-through:** create a product campaign for listing A; tap both the image area and CTA; both open listing A. Change the campaign to listing B and verify both now open B.
4. **Seller click-through:** create a seller-shop campaign; tap and verify it opens the correct seller's public storefront, not the current user's private shop.
5. **Category click-through:** select a category and verify navigation opens that exact canonical category.
6. **Marketplace destination:** verify the explicit Marketplace destination opens Marketplace discovery.
7. **Invalid/stale targets:** reject unknown seller/category/product IDs and prevent inactive/out-of-stock product navigation at tap time.
8. **Lifecycle:** verify scheduled, active, expired, paused, removed and replaced campaigns appear or disappear correctly.
9. **Carousel:** verify automatic rotation, swipe, indicators, stable dimensions, and no accidental rotation in deterministic single-slide tests.
10. **Disclosure and measurement:** verify Sponsored labels, visibility threshold, advertiser self-view exclusion and that ad metrics never affect organic Seller of the Week awards.
11. **Access control:** verify only authorized Owner/Admin roles can manage official campaigns; seller-sponsored campaigns follow the separate advertiser permissions and payment approval path.
12. **Runtime evidence:** capture screenshots from the actual canonical Android runtime showing image-backed banner(s), the campaign editor with image preview/destination, and the correct destination after tapping. A synthetic GET-only screenshot proves layout only; it does not prove upload, persistence, publishing or live navigation.
13. **Regression:** verify Marketplace product discovery, My Shop, cart/checkout, orders, receipts, Wallet, Home, Profile, Status/Stories and Chat/Groups are not regressed.

## 8. Repository evidence and implementation status discipline

The following PR records describe prior agreed work. A PR description is evidence of intended/claimed work, **not proof by itself** that it is merged, present on the canonical branch, deployed, or verified at runtime.

- **PR #439 — Upgrade Marketplace to full-width promotional banners and weekly seller awards.** Describes 224 dp photo-led slides, featured-product and authenticated seasonal artwork, rotation/swipe, branded empty-state illustration, sponsored campaigns/metrics, scheduling, media privacy and seller awards. It also explicitly records historical commercial-readiness gaps.
- **PR #452 — Route Marketplace campaign banners to verified destinations.** Describes typed destinations (Marketplace/category/product/seller shop), Owner/Admin destination controls, backend validation and real tap navigation.
- **PR #582 — Owner/Admin Marketplace campaign publishing.** Describes the existing campaign manager in the Marketplace section with artwork, details, destinations and scheduling, reusing existing services. Its PR notes say screenshots are synthetic/GET-only and live authenticated publishing remains a separate runtime gate; verify current checks and screenshots before calling it complete.
- **PR #514 — Local business discovery and seller commerce.** Describes advertiser-owned active/in-stock product linkage for sponsored campaigns.

### Known verification distinction at this review

The prior text-only banner screenshot shown in the conversation is **not** acceptable proof of the image-backed campaign experience. PR #582's synthetic GET-only screenshots are not proof of a real media upload, persisted image, live publishing or click-through. The exact current canonical source, merged PR state, backend availability and runtime behavior must be inspected and recorded before assigning a final status to each acceptance test.

## 9. Required investigation before implementation

On the canonical integration branch:
1. Inspect current campaign models/schema/API for image/media reference, campaign type, typed destination, schedule, approval and status.
2. Inspect the existing authenticated media uploader/storage service and verify the campaign manager actually calls it.
3. Inspect the banner composable to confirm it renders the campaign image (not only text) and uses the campaign's own destination on click.
4. Trace each destination end-to-end: Owner/Admin editor → request payload → backend validation/storage → campaign read model → carousel → tap navigation.
5. Compare current code and tests with PRs #439, #452, #514 and #582; identify what is merged, missing, stale or only fixture-covered.
6. Verify image storage provider/bucket configuration and access policy. If backend/storage is unavailable, finish and test app-side contracts with honest local fixtures, but mark live upload/persistence as blocked—not complete.
7. Run grouped Android/backend/security/regression checks and capture actual canonical runtime evidence. Do not publish a separate APK or replace canonical work.

## 10. Non-negotiable implementation rule

**The image, copy, CTA and destination are one campaign.** Changing the campaign must change the creative and its destination together. No placeholder text test, generic Marketplace fallback, fabricated product image, duplicate uploader or duplicate banner implementation may be presented as the finished feature.
