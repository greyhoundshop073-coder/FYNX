# FYNX — Google Play Readiness Plan

**Status:** Preparation document; not a declaration of compliance or a final privacy policy.  
**Reviewed:** 2026-11-10  
**Canonical Android source reviewed:** `integration/fynx-single-apk-20261013`  
**Package/application ID observed:** `com.fynx.app`



## Stage 1 privacy source baseline — 2026-11-10

The source-backed data-flow inventory is maintained in [`docs/PRIVACY_DATA_INVENTORY.md`](PRIVACY_DATA_INVENTORY.md). It records verified source behavior, unverified provider/runtime facts, account-deletion dependencies, and the legal details that must be supplied before public disclosures can be finalized.

This Stage 1 work adds a versioned, account-scoped disclosure in the Contacts feature before the app reads device/SIM contacts or requests Android Contacts permission. It explains that normalised phone numbers are sent individually to FYNX over HTTPS for matching and that contact names are not included in those lookup requests. It also defers the Android 13+ notification permission prompt until the user enables Push alerts in Notification Preferences, shows an explanation first, and registers the FCM token only when the saved push preference is enabled and Android permission is granted. These changes require Android CI and runtime verification before being marked complete.

Account deletion, a public deletion web resource, a publishable privacy policy, and final Terms of Use remain launch blockers. Do not publish or link placeholder legal pages, and do not claim that this source inventory completes Play Console Data safety declarations.

## 1. Purpose and release rule

Prepare Google Play policy, store-listing, testing, privacy, safety, and release requirements during development. Do not submit FYNX for production or begin the mandatory closed-test clock until the release candidate is stable, its required services work, and the compliance blockers below have been verified.

The Google Play Console is https://play.google.com/console/.

For a new personal developer account created after 2023-11-13, Google currently requires a closed test with at least 12 testers continuously opted in for at least 14 days before applying for production access. Recruit a reserve group (target 15–18), but count only qualifying opted-in testers. Google may request further testing if engagement is insufficient. Organization accounts have different requirements; confirm the actual account type and creation date in the account owner’s Play Console.

Google Play distribution uses a signed Android App Bundle (AAB). Keep APKs for direct installation/testing, but prepare an AAB for Play Console. The Android application ID is already set to `com.fynx.app`; verify and permanently approve this identifier before the first Play upload because an app's package name cannot be changed after its first artifact is uploaded.

## 2. Initial source inspection — facts observed

From `app/src/main/AndroidManifest.xml`:
- Declared permissions include Internet, network state, coarse/fine location, notifications, exact alarms, boot completed, microphone/audio recording, camera, contacts, image/video media access, legacy external storage (up to API 32), and vibration.
- Camera hardware is declared optional.
- Firebase Cloud Messaging service is declared.
- The manifest includes an HTTPS app-link host of `fynx.app`; this does **not** establish that a public website, privacy policy, or account-deletion page is already deployed.

From `app/build.gradle.kts`:
- Application ID: `com.fynx.app`
- Target SDK: 36
- Firebase Messaging is included.
- Other observed dependencies include WebRTC, ML Kit face detection and selfie segmentation, Media3, CameraX, OkHttp, ZXing, and AndroidX components.

These are source-level observations only. They do not prove which permissions are requested at runtime, what data is transmitted in every flow, or what each SDK collects. A full runtime and dependency/data-flow audit is required before completing Play Console Data safety declarations or publishing a privacy policy.

## 3. Release blockers to verify and complete

### A. Privacy, transparency, and account deletion
- [ ] Inventory every data category collected, generated, stored, accessed, or shared by the app and backend.
- [ ] Inventory every third-party SDK/service and its data practices, including Firebase/FCM, AI providers, email delivery, payment providers, hosting/database, media/object storage, analytics/crash reporting if present, and WebRTC infrastructure.
- [ ] Map each data category to purpose, collection method, retention period, deletion behavior, and recipients/processors.
- [ ] Verify whether account deletion can be initiated inside FYNX and through a public web resource.
- [ ] Verify that deletion actually removes associated account data from the database, media/object storage, backups where applicable, and relevant processors, subject only to clearly disclosed lawful retention.
- [ ] If deletion is missing or incomplete, implement and test it before release. A logout, account freeze, or profile deactivation is not account deletion.
- [ ] Publish a public, globally accessible, non-PDF privacy policy on an HTTPS URL; link it inside the app and in Play Console.
- [ ] Publish a separate public account-deletion request webpage/flow and enter its URL in Play Console.
- [ ] Ensure the policy names FYNX and the responsible developer/company, includes a privacy contact, data categories, purposes, sharing, security, retention, and deletion.
- [ ] Do not publish a guessed or generic privacy policy. Complete it only after the data-flow and processor inventory is verified.

### B. Permissions and sensitive data
- [ ] For location, contacts, microphone, camera, photos/videos, notifications, exact alarms, and boot receiver behavior, document the user-facing feature and necessity.
- [ ] Request sensitive permissions only at the point the related feature is used; explain why before the system prompt where required.
- [ ] Prefer Android Photo Picker or other scoped access where it can replace broad media permissions.
- [ ] Confirm contacts are not uploaded or synced without a clear disclosure and user action.
- [ ] Confirm location is not collected in the background unless a compliant, core use case requires it.
- [ ] Verify audio/video call and recording behavior; do not imply recording when none occurs.
- [ ] Test permission denial, one-time grants, revocation, and Android-version differences.

### C. User-generated content and social safety
- [ ] Verify in-app reporting for users and user-generated content, with a functioning review/moderation process.
- [ ] Verify user blocking and that blocked users cannot continue the prohibited interactions.
- [ ] Provide and enforce terms/community standards that prohibit objectionable content and abusive behavior.
- [ ] Provide moderation escalation, response handling, and repeat-offender controls.
- [ ] Test reporting/blocking on posts, comments, replies, profiles, messages, groups, and marketplace content as applicable.
- [ ] Make safety controls discoverable and ensure reports reach an actively monitored channel.

### D. Marketplace, wallet, and payments
- [ ] Document whether FYNX facilitates physical goods/services, digital goods, peer-to-peer transfers, stored value, or other financial activity; classify each flow against current Play payments and financial-services policies.
- [ ] Do not enable real-money wallet funding, withdrawals, escrow, or payments until provider approval, legal/regulatory obligations, and Play policy treatment have been verified.
- [ ] Verify payment status only from trusted provider/server confirmation; never mark a transaction complete from a client-side redirect alone.
- [ ] Provide clear seller identity, listing rules, pricing, fees, delivery, cancellation, refund, and dispute information.
- [ ] Add/report suspicious listings and provide moderation and buyer/seller support.
- [ ] Do not expose wallet balances, payment history, secrets, or financial data to AI features.

### E. AI and third-party processing
- [ ] Document AI features, providers, data sent to each provider, retention controls, and user disclosures.
- [ ] Keep wallet/payment secrets and private financial history outside AI context and prompts.
- [ ] Provide safe handling for user-generated prompts and generated content, plus report/escalation paths.
- [ ] Clearly handle backend/provider unavailability; do not present failed or unavailable AI actions as successful.
- [ ] Verify provider terms and any required user notice/consent before release.

### F. Store listing and Play Console declarations
- [ ] App name, short description, full description, category, contact email, and support website.
- [ ] High-quality icon, feature graphic, and genuine current screenshots from the release candidate.
- [ ] Privacy policy URL and account-deletion URL.
- [ ] Accurate Data safety form covering the app and all included SDKs.
- [ ] Content rating questionnaire and target audience/age declarations.
- [ ] Ads declaration, app access instructions, and reviewer credentials where login/restricted features exist.
- [ ] Declare financial features, health features, news, government affiliation, or other special categories only where applicable and accurately.
- [ ] Complete any required developer identity, contact, and organization verification.
- [ ] Review current Play policies immediately before submission; policy requirements can change.

### G. Reliability, signing, and release operations
- [ ] Complete functional audit of every visible action and all major flows.
- [ ] Test crashes, ANRs, startup, offline/poor network, uploads, notifications, deep links, permission denial, and low-storage behavior on multiple Android devices.
- [ ] Confirm backend and all required integrations are live and stable for the full closed test; do not start the 14-day period with known broken core services.
- [ ] Confirm versionCode/versionName strategy and release build configuration.
- [ ] Enrol in Play App Signing and securely preserve the upload key and recovery material.
- [ ] Build, sign, inspect, and archive the exact AAB submitted to Play.
- [ ] Provide reviewers with working test accounts and clear instructions; ensure the accounts remain usable during review.
- [ ] Maintain a tester roster, dated feedback log, issues found, fixes shipped, and evidence of genuine feature engagement.

## 4. Closed-test operating plan

1. Finish the release-candidate functionality and policy audit first.
2. Run internal testing to catch installation, startup, and critical defects.
3. Prepare a closed-test track and a reserve roster of 15–18 genuine Android testers.
4. Give testers a written feature checklist and a feedback channel.
5. Start the qualifying 14-day period only after at least 12 testers have opted in and the test build is usable.
6. Monitor opt-in count and engagement throughout; testers must remain opted in continuously.
7. Collect real feedback, fix defects, and record changes.
8. Apply for production access only when the Console confirms eligibility and the test evidence is ready.

## 5. Tester feedback checklist

Ask testers to exercise and report on:
- Registration, login, verification, logout, and account recovery.
- Home feed, post creation, reactions, comments, replies, reposts, media playback.
- Profiles, follow/friend controls, privacy settings, blocking, and reporting.
- Chat, groups, calls, notifications, and media sharing.
- Status/stories and discovery.
- Marketplace search, product details, seller profiles, cart, checkout, order tracking, refunds, and seller tools.
- Wallet display and any approved test-mode payment flows.
- AI text, voice, image, failure states, and privacy boundaries.
- Accessibility, confusing labels, loading states, crashes, and battery/data use.

## 6. Known unknowns — do not mark compliant until resolved

- Developer account type and creation date have not been confirmed by the account owner.
- The public privacy-policy and account-deletion URLs have not been verified.
- A complete account-deletion flow and permanent deletion semantics have not been confirmed by this source review.
- The complete app/backend/SDK data inventory has not been completed.
- Runtime permission request timing and actual data transmission have not been fully audited.
- Current production availability of backend, AI, media, email, and payment integrations must be verified before testing.
- Store listing assets, support contact, reviewer credentials, and final policy declarations remain to be supplied/verified.

## 7. Official Google references

- Play Console: https://play.google.com/console/
- New personal account testing requirements: https://support.google.com/googleplay/android-developer/answer/14151465
- Set up internal, closed, or open testing: https://support.google.com/googleplay/android-developer/answer/9845334
- Developer Program Policy (privacy, Data safety, account deletion, and other policies): https://support.google.com/googleplay/android-developer/answer/18265346
- User Data / Data safety: https://support.google.com/googleplay/android-developer/answer/10144311
- Upload an app bundle: https://developer.android.com/studio/publish/upload-bundle

---
**Completion rule:** A checklist item is complete only when implementation or configuration has been verified with source evidence, runtime evidence, or a Play Console confirmation. This document is a preparation baseline, not proof of Google approval.
