# FYNX privacy and personal-data inventory

**Review date:** 2026-11-23  
**Source baseline:** `integration/fynx-single-apk-20261013` at `dfb9b83d420b41512224ad7180eab797b9664f6d`  
**Status:** Engineering inventory; incomplete. This is not a Privacy Policy or a completed Google Play Data safety declaration.

## Scope and limitations

This inventory is based on a source review of the Android manifest and Settings/privacy clients, backend route modules, email adapter, Neon deployment configuration, and read-only Neon schema metadata. The schema inspection used only `information_schema` metadata; no user or transaction records were queried.

It does **not** establish actual production environment-variable values, current provider contracts, data-processing terms, backup schedules, edge/access-log retention, the configured email vendor, the exact location/geocoding processor, call-provider processing, or every runtime data path on physical devices. Where the source points to a processor but deployment configuration is not confirmed, that provider is listed as an integration candidate, not asserted as the active production recipient.

## Data categories indicated by the source

| Category | Source-indicated data and purpose | Storage or recipient indicated | Unverified items |
| --- | --- | --- | --- |
| Account and identity | Username, display name, phone, optional email, password hash, verification timestamps/counters, session version and profile metadata for registration, sign-in, recovery and account management | FYNX backend PostgreSQL; the Android token store encrypts access tokens with Android Keystore | Legal controller, account-data retention, production region, logs and backup lifecycle |
| Profile and social graph | Bio, country, profile-photo reference, follower/friend relationships, blocks, saved items and discovery events | FYNX PostgreSQL and configured media storage | Complete retention and export/delete behavior for every social table |
| User content | Posts, comments, replies, reactions, reposts, audience settings, status/story media and interactions | FYNX PostgreSQL plus media storage | Content moderation retention, expiry and processor deletion propagation |
| Messages and groups | Private/group message text, participant IDs, timestamps, delivery/read state, group membership, group content, polls, invites, voice/video-call signalling and call metadata | FYNX database, media storage and realtime transport | Message/call retention, provider identity, call recordings (if any), and encryption claims. Do not claim end-to-end encryption without evidence |
| Contacts matching | Android Contacts Provider names and phone numbers, including SIM-backed rows. The reviewed client requests permission and sends normalized phone-number lookups to FYNX over HTTPS; it does not send the contact display name in that lookup request | Device Contacts Provider and FYNX user-search endpoint | Hosting query-string/access-log retention and runtime denial/revocation verification |
| Location | Manifest declares approximate and precise location permissions. Source includes user-initiated location features and place-label resolution | Android location/geocoding APIs and FYNX endpoints where the feature is used | Exact entry points, geocoding processor, whether coordinates or labels are transmitted in every feature, and retention |
| Camera, microphone and selected media | Camera capture, microphone recording, selected photos/videos and attachments | Android app cache, FYNX media records and the configured object/media provider | Every permission prompt/denial path, storage provider actually selected in deployment, object region and lifecycle |
| AI assistant | User prompts, bounded conversation history and user-selected image attachments can be sent from the backend to Google's Gemini API | FYNX AI conversation records and Gemini API | Active Google project, model/configuration, region, provider settings, training/retention treatment and contract |
| Notifications | FYNX notification preferences, notification content and Firebase Cloud Messaging device tokens | FYNX database and Firebase Cloud Messaging where enabled | Firebase project/processor settings, payload content, retention and token deletion timing |
| Email | Email address, HMAC-protected verification/reset code hashes, expiry/attempt/send timestamps. Verification and password recovery are implemented; account security notices are sent after password/email changes when delivery is configured | FYNX database and the SMTP provider selected through deployment secrets | The actual SMTP provider, region, access logs, sub-processors, delivery retention and provider account configuration |
| Marketplace and financial | Listings, orders, buyer/seller IDs, prices/currencies, shipping data, delivery evidence, disputes, escrows, wallet balances/transactions, payout account metadata, payment references and provider operations | FYNX PostgreSQL and configured payment/banking/media providers | Full provider-by-provider data map, merchant/custody roles, legal retention, production approval, financial reconciliation and dispute closure rules |
| Advertising, moderation and support | Campaign ownership/metrics, impressions/clicks, user reports, appeals, moderation decisions, support records and administrator actions | FYNX PostgreSQL and push/email integrations as configured | Staff access model, operational retention, escalation contacts and provider-side records |
| Local app data | Preferences, cached settings, account-scoped media cache and encrypted authentication token | Android app-private preferences/cache and Keystore | Backup/device-migration behavior should be retested against release build; manifest currently sets `allowBackup=false` |

The presence of a source route, database column or SDK is evidence of an implementation path, not proof that the corresponding feature is enabled in production or that a third party actually receives data in every use.

## Android permission inventory

The current canonical `app/src/main/AndroidManifest.xml` declares:

- `ACCESS_COARSE_LOCATION` and `ACCESS_FINE_LOCATION`
- `POST_NOTIFICATIONS`
- `SCHEDULE_EXACT_ALARM` and `RECEIVE_BOOT_COMPLETED`
- `RECORD_AUDIO` and `CAMERA`
- `READ_CONTACTS`
- `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, and `READ_EXTERNAL_STORAGE` limited to API 32 and below
- `INTERNET`, `ACCESS_NETWORK_STATE` and `VIBRATE`

Camera and autofocus are declared as optional device features. The manifest disables cleartext traffic and Android app backup.

Source-level observations:
- Contacts matching has a versioned, account-scoped explanation before reading device/SIM contacts or requesting `READ_CONTACTS`; declining permission leaves other app features available.
- The current `MainActivity` calls notification-token registration from `onCreate`. For a signed-in account on Android 13+, if notification permission is missing it immediately launches the OS permission prompt. The Notification Preferences Push alerts switch persists the app preference, but the current canonical path does not itself explain that OS permission prompt first. Permission denial/runtime recovery needs device verification.
- Camera, microphone, location, photos/videos, reminders and call permission flows require feature-by-feature denial, revocation and OS-version testing. An older open privacy branch contains a broader proposed disclosure update but is not mergeable as-is; its runtime behavior is not treated as part of this canonical source.
- The Media & Storage Settings screen clears only the current account's dedicated `cacheDir/fynx_media_<account>` directory. It does not call a database delete or delete original posts/messages.

## Processor and deployment inventory

Source and deployment configuration indicate integration points for:
- **Neon:** PostgreSQL and Neon Functions for the current isolated `fynx-dev` backend; Neon Object Storage is an available media-storage direction.
- **Google Gemini:** AI request provider.
- **Firebase Cloud Messaging:** push notification delivery.
- **SMTP:** optional email provider selected with `FYNX_EMAIL_*` deployment variables.
- **Paystack:** payment integration appears in backend configuration and Marketplace/wallet source; active production credentials and processing roles must be verified.
- **Realtime/call transport:** FYNX has realtime and WebRTC-related code; the actual call/media processor and deployment configuration need confirmation.
- **Android platform services:** Contacts Provider, Photo Picker/MediaStore, CameraX, location and geocoding APIs.

Do not publish a final processor list, data-region statement, retention schedule or Data safety answer until current deployed configuration, provider account details, SDK behavior and contractual terms are reviewed. Google's Data safety guidance says developers are responsible for complete accurate declarations and must consider data collection or sharing by included third-party SDKs: https://developer.android.com/privacy-and-security/declare-data-use.

## Neon schema evidence relevant to data handling

A read-only metadata comparison on 2026-11-23 found different public schema sizes:
- isolated development project `spring-mode-34864088`, branch `fynx-dev`: 12 public base tables;
- recovery project `patient-lab-43403708`, branch `live-recovery`: 88 public base tables;
- `patient-lab-43403708` `production` branch: 47 public base tables.

These are table counts, not user counts or data-volume claims. The branch schemas are not interchangeable; no production migrations or data were changed in this review. The recovery branch also has a private `fynx-media` Object Storage bucket. Its existence does not prove that the current dev Function uses it.

The current dedicated dev Function's runtime environment did not expose any `FYNX_EMAIL_*` keys at the time of review. The Settings/email PR adds an optional secure GitHub Actions-to-Neon configuration path, but the secrets have not been populated and no real SMTP delivery has been verified.

## Publication gates

Before publishing a Privacy Policy or completing Play Console Data safety:
1. Confirm the legal controller/developer identity, jurisdiction, monitored privacy contact and countries served.
2. Inspect the current deployment configuration and list every active provider, sub-processor and data region.
3. Verify each collection/sharing path and included SDK on release builds.
4. Agree and document retention periods, backup/log deletion, security/fraud/accounting retention and user-rights handling.
5. Complete the financial, Marketplace and media-object inventory in the account-deletion design.
6. Complete runtime permission tests (grant, deny, permanent deny, revoke, approximate location and supported Android versions).
7. Reconcile the final answers with the live Play Console form.

No company identity, mailing address, contact email, public privacy URL or retention commitment is invented in this inventory.


## Transactional email events (engineering inventory update)
FYNX may send important service messages to the account's verified email address, including Marketplace payment/order/protection progress and Wallet top-up/withdrawal outcomes. These are not marketing/promotional messages. Delivery depends on a configured SMTP provider, and the user can control routine Marketplace and Wallet email activity in Notification Settings. Security/account-protection notices may be sent separately. The SMTP provider, controller/legal operator details, processing location, retention, and contractual terms must be confirmed before a publishable Privacy Policy or Play Data Safety declaration is prepared.
