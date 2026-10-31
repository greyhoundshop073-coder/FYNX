# Marketplace and My Shop Runtime Certification

## Purpose

Certify the actual canonical Android APK UI for Marketplace and the private My Shop seller centre. This checklist supplements source-level regression checks and a successful compile; it does not replace them.

## Scope boundaries

- Marketplace discovery, category artwork, search/filter state, real seller listing cards, product details, and the sell form.
- My Shop overview, seller profile, active/archived listings, editing and subcategory persistence, seller orders, delivery/driver area, and earnings/payout states.
- Real seller photos must remain the source for product cards. Category illustrations are navigation artwork only; no sample product, seller, order, payment, earnings, driver, or review records may be fabricated.
- Do not modify Home, Avatar/Profile, Chat/Groups, Status/Story, login/OTP, or backend services as part of this UI certification.

## Required runtime evidence

The canonical Android workflow must run `scripts/run_android_runtime_certification.sh` on the emulator and retain its logcat, activity/package diagnostics, screenshots, UI hierarchies, and `FYNX-marketplace-visual-capture.md` report as workflow artifacts.

The focused read-only Marketplace/My Shop visual walk must confirm destination markers and settled loading states for:
1. Marketplace search/discovery.
2. A genuine priced listing's product details, only when a real listing is returned.
3. The seller-publishing form, without submitting or creating a listing.
4. My Shop overview, Shop profile, Listings, Orders, Delivery & drivers, and Earnings & payouts.

A blocked/empty product-details probe is not a pass for live inventory. Record it as blocked when the service returns no genuine priced listing. Keep every screenshot and hierarchy produced by the real APK; never substitute a generated mockup.

## Seller-flow checks

- Existing category values remain compatible, with optional category-specific subcategories and specifications.
- My Shop edit preserves and saves the selected subcategory in the listing description through the existing authenticated listing update.
- Changing the main category clears a stale subcategory.
- Active and archived cards display real uploaded listing media with consistent cropping and an honest no-photo fallback.
- Photo cleanup is optional, preserves the original, and supports preview / Keep Original / Use Enhanced / Undo where the processor is supported. On Android API 36 and newer, the current beta segmenter is disabled pending safe runtime verification; original photos must remain publishable.
- Editing a published listing supports authenticated photo/video replacement: the Android edit flow uploads selected media, passes the returned media IDs to the listing update, and only updates the visible listing after the server confirms the update. The backend validates that every replacement media record belongs to the signed-in seller and is an image or video before persisting the IDs. This is source-contract evidence only; runtime verification must still prove upload, persistence, and subsequent media retrieval with the live service.

## Backend-dependent evidence boundary

When the live backend is not healthy, runtime certification must state that live inventory, seller profile persistence, orders, protected payments, payouts, delivery/driver assignment, and disputes have not been verified against the service. Read-only debug UI entry verifies rendering/navigation only, not authentication or data persistence. Do not change backend or login behavior in this scope.

## Exit criteria

- Canonical Android workflow is green, including emulator/instrumentation and the focused Marketplace/My Shop capture.
- Artifact includes the exact-commit APK plus genuine screenshot/UI-hierarchy evidence and a readable report.
- Any failure is investigated from the captured hierarchy/logcat, fixed in the Marketplace/My Shop scope, and rerun.
- Any service-dependent or unsupported-platform item remains explicitly blocked rather than being reported complete.
